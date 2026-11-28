<script setup lang="ts">
import { ref, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { simulateStream, repairSimulation, type SimulationResult } from '@/api/simulation'
import { extractApiError } from '@/api/http'
import { sandboxHtml } from '@/utils/simulationSandbox'
import { downloadBlob } from '@/utils/zipPackager'
import { usePracticeSession, hasPracticeInput } from '../usePracticeSession'
import { experimentPlugins, defaultModel, pluginFor, type Model } from '../models'
import MarkdownContent from '@/components/MarkdownContent.vue'
import ExperimentWorkspace from '../ExperimentWorkspace.vue'
import AiSimulationSandbox from './AiSimulationSandbox.vue'
const state = usePracticeSession(),
  auth = useAuthStore(),
  router = useRouter()
const busy = ref(false),
  repairing = ref(false),
  status = ref('准备生成'),
  received = ref(0),
  error = ref(''),
  runtimeError = ref(''),
  showCode = ref(false),
  feedback = ref(''),
  records = ref<Record<string, string | number>[]>([])
let controller: AbortController | undefined,
  closed = false,
  sequence = 0
function stop() {
  sequence++
  controller?.abort()
  busy.value = false
  repairing.value = false
}
async function generate() {
  stop()
  if (!hasPracticeInput(state.draft)) {
    error.value = '实验需求暂未保存，请返回提问页填写，或选择内置模型。'
    return
  }
  if (!auth.accessToken) {
    error.value = '请先登录后再生成'
    return
  }
  const token = ++sequence
  controller = new AbortController()
  busy.value = true
  state.mode = 'ai'
  state.result = undefined
  runtimeError.value = ''
  error.value = ''
  records.value = []
  received.value = 0
  status.value = '正在理解实验原理与公式…'
  try {
    await simulateStream(
      { ...state.draft },
      auth.accessToken,
      (e) => {
        if (closed || token !== sequence) return
        if (e.event === 'status') status.value = String(e.data.message || '正在生成…')
        if (e.event === 'delta') {
          received.value = Number(e.data.receivedChars) || 0
          if (received.value > 0) status.value = '正在编写物理引擎与交互画布…'
        }
        if (e.event === 'error') error.value = String(e.data.message || '生成失败')
        if (e.event === 'done') {
          if (typeof e.data.html !== 'string' || e.data.html.length > 150000)
            throw Error('仿真输出未通过长度校验')
          state.result = {
            title: String(e.data.title || state.draft.experimentName || '模拟实验'),
            summary: String(e.data.summary || ''),
            html: sandboxHtml(e.data.html),
            usedModel: String(e.data.usedModel || 'deepseek-flash'),
            reasoningMode: String(e.data.reasoningMode || 'fast'),
          }
          status.value = '生成完成，请核对默认参数与原理假设'
        }
      },
      controller.signal,
    )
  } catch (e) {
    if (!closed && token === sequence && !controller.signal.aborted)
      error.value = extractApiError(e)
  } finally {
    if (token === sequence) busy.value = false
  }
}
async function repair() {
  if (!state.result || !runtimeError.value || busy.value || repairing.value) return
  controller = new AbortController()
  const token = ++sequence
  repairing.value = true
  error.value = ''
  try {
    const fixed = await repairSimulation(
      {
        experimentName: state.draft.experimentName,
        simulationGoal: state.draft.simulationGoal,
        brokenHtml: state.result.html,
        runtimeError: runtimeError.value,
      },
      controller.signal,
    )
    if (!closed && token === sequence) {
      state.result = { ...fixed, html: sandboxHtml(fixed.html) }
      runtimeError.value = ''
      records.value = []
      status.value = fixed.fixSummary || '修复完成，请核验画布'
    }
  } catch (e) {
    if (!closed && token === sequence) error.value = extractApiError(e)
  } finally {
    if (token === sequence) repairing.value = false
  }
}
function builtin(kind: string) {
  stop()
  state.mode = 'builtin'
  state.model = defaultModel(kind as Model['kind'])
  error.value = ''
  runtimeError.value = ''
}
function back() {
  stop()
  router.push('/app/college/practice')
}
function setModel(m: Model) {
  state.model = m
}
function snapshot() {
  if (state.model)
    state.snapshots.unshift({
      title: new Date().toLocaleString(),
      model: JSON.parse(JSON.stringify(state.model)),
    })
  state.snapshots = state.snapshots.slice(0, 30)
}
function exportHtml() {
  if (state.result)
    downloadBlob(
      new Blob([sandboxHtml(state.result.html)], { type: 'text/html;charset=utf-8' }),
      'Argus-模拟实验.html',
    )
}
async function copyCode() {
  try {
    if (state.result) await navigator.clipboard.writeText(sandboxHtml(state.result.html))
  } catch {
    error.value = '复制失败，请手动选择代码复制'
  }
}
function exportRecords() {
  if (!records.value.length) return
  const keys = [...new Set(records.value.flatMap((r) => Object.keys(r)))].slice(0, 16)
  const cell = (v: unknown) => {
    let s = String(v ?? '')
    if (typeof v === 'string' && /^[=+@-]/.test(s)) s = "'" + s
    return '"' + s.replace(/"/g, '""') + '"'
  }
  const csv =
    '\ufeff' +
    [
      keys.map(cell).join(','),
      ...records.value.map((r) => keys.map((k) => cell(r[k])).join(',')),
    ].join('\r\n')
  downloadBlob(new Blob([csv], { type: 'text/csv;charset=utf-8' }), 'Argus-仿真记录-非实测.csv')
}
function adjust() {
  if (!feedback.value.trim()) return
  const goal = (state.draft.simulationGoal || '') + '\n本次调整：' + feedback.value.trim()
  if (goal.length > 3000) {
    error.value = '累计需求超过 3000 字，请返回整理需求'
    return
  }
  state.draft.simulationGoal = goal
  feedback.value = ''
  generate()
}
onMounted(() => {
  if (state.mode === 'ai' && !state.result) generate()
  else if (state.mode === 'builtin' && !state.model) state.model = defaultModel('rc')
})
onUnmounted(() => {
  closed = true
  stop()
})
</script>
<template>
  <main class="practice-lab">
    <header class="lab-toolbar">
      <div class="title">
        <button @click="back">← 返回重新提问 / 换材料</button
        ><strong>{{
          state.mode === 'builtin' && state.model
            ? pluginFor(state.model).name
            : state.result?.title || state.draft.experimentName || '专属模拟实验台'
        }}</strong
        ><small>{{
          state.mode === 'builtin' ? '内置确定性模型' : 'DeepSeek-V4.1-Flash 动态原理仿真'
        }}</small>
      </div>
      <div class="tools">
        <button v-if="state.mode === 'ai'" :disabled="busy || repairing" @click="generate">
          重新生成</button
        ><button v-if="state.mode === 'ai'" :disabled="!state.result" @click="showCode = true">
          查看代码</button
        ><button v-if="state.mode === 'ai'" :disabled="!state.result" @click="exportHtml">
          下载单文件 HTML</button
        ><button v-if="records.length" @click="exportRecords">
          导出 {{ records.length }} 条记录</button
        ><select
          aria-label="切换内置 7 套经典实验"
          :value="state.mode === 'builtin' ? state.model?.kind : ''"
          @change="builtin(($event.target as HTMLSelectElement).value)"
        >
          <option value="" disabled>切换内置 7 套经典实验</option>
          <option v-for="item in experimentPlugins" :key="item.id" :value="item.id">
            {{ item.name }}
          </option>
        </select>
      </div>
    </header>
    <p class="boundary">
      {{
        state.mode === 'ai'
          ? 'AI 动态原理仿真仅供现象观察与定性探究，不替代真实实验测定数据'
          : '内置确定性模型：结果依赖所列公式、参数与适用假设，不能代替真实测量。'
      }}
    </p>
    <div v-if="runtimeError && state.mode === 'ai'" role="alert" class="alert">
      <b>沙盒运行报错</b>
      <p>{{ runtimeError }}</p>
      <button :disabled="repairing || busy" @click="repair">
        {{ repairing ? '正在修复…' : '一键 AI 自动修复' }}</button
      ><button @click="builtin('formula')">改用内置公式画图器兜底</button>
    </div>
    <section v-if="busy" class="progress-stage" aria-live="polite">
      <span class="spinner" />
      <h2>{{ status }}</h2>
      <p>
        已生成 <b>{{ received.toLocaleString() }}</b> 个字符
      </p>
      <small>理解实验原理 → 构建方程与动画 → 完善交互界面</small
      ><button @click="back">取消并返回</button>
    </section>
    <section v-else-if="error && state.mode === 'ai'" class="progress-stage">
      <h2>暂未生成实验画面</h2>
      <p role="alert">{{ error }}</p>
      <div>
        <button class="primary" :disabled="repairing" @click="generate">重试生成</button
        ><button @click="builtin('formula')">改用内置公式画图器兜底</button>
      </div>
    </section>
    <AiSimulationSandbox
      v-else-if="state.mode === 'ai' && state.result"
      :html="state.result.html"
      @error="runtimeError = $event"
      @records="records = $event"
    />
    <ExperimentWorkspace
      v-else-if="state.mode === 'builtin' && state.model"
      :current="state.model"
      :snapshots="state.snapshots"
      @change="setModel"
      @restore="setModel"
      @snapshot="snapshot"
      @import="back"
      @news="back"
      @ask="
        (q) => {
          state.draft.simulationGoal = q
          back()
        }
      "
    />
    <details v-if="!busy && state.mode === 'ai' && state.result" class="extension">
      <summary>实验原理摘要与继续微调</summary>
      <MarkdownContent :content="state.result.summary" /><small
        >模型：{{ state.result.usedModel }} ·
        {{ state.result.reasoningMode }}。记录为模拟数据，非实测数据。</small
      >
      <form @submit.prevent="adjust">
        <label
          >继续微调本次模拟<input
            v-model="feedback"
            maxlength="1200"
            placeholder="例如：把背景改成深色，并加上阻尼开关" /></label
        ><button :disabled="repairing">按修改意见重新生成</button>
      </form>
    </details>
    <el-dialog v-model="showCode" title="单文件仿真代码" width="min(980px,94vw)" append-to-body
      ><p>代码在隔离沙盒运行；下载后请保留安全策略。</p>
      <textarea
        :value="state.result?.html"
        readonly
        rows="20"
        aria-label="仿真 HTML 代码"
      /><template #footer
        ><button @click="copyCode">复制代码</button
        ><button @click="exportHtml">下载 HTML</button></template
      ></el-dialog
    >
  </main>
</template>
<style scoped>
.practice-lab {
  color: var(--text-primary);
  padding: 10px 0 28px;
}
.lab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
.title,
.tools {
  display: flex;
  align-items: center;
  gap: 7px;
  flex-wrap: wrap;
}
.title strong {
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 15px;
}
.title small {
  font-size: 11px;
  color: var(--text-secondary);
}
button,
select {
  border: 1px solid var(--border-default);
  border-radius: 7px;
  background: var(--surface-white);
  color: inherit;
  padding: 8px 10px;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: default;
}
.boundary {
  margin: 10px 0;
  padding: 8px 12px;
  font-size: 12px;
  background: var(--surface-accent);
  border-radius: 7px;
  color: var(--text-secondary);
}
.progress-stage {
  min-height: max(620px, calc(100dvh - 210px));
  border: 1px solid var(--border-default);
  border-radius: 12px;
  background: var(--surface-white);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  text-align: center;
  padding: 24px;
}
.progress-stage p {
  max-width: 680px;
  line-height: 1.8;
  overflow-wrap: anywhere;
}
.progress-stage h2 {
  font-size: 22px;
}
.progress-stage b {
  font-variant-numeric: tabular-nums;
}
.progress-stage small {
  color: var(--text-secondary);
}
.spinner {
  height: 36px;
  width: 36px;
  border: 3px solid var(--border-default);
  border-top-color: var(--brand-primary);
  border-radius: 50%;
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
.alert {
  padding: 14px;
  background: var(--surface-subtle);
  border-left: 4px solid #c7544c;
  margin: 12px 0;
  border-radius: 8px;
}
.alert p {
  overflow-wrap: anywhere;
  font-size: 13px;
}
.extension {
  margin-top: 16px;
  padding: 14px;
  border: 1px solid var(--border-default);
  border-radius: 10px;
  background: var(--surface-white);
}
summary {
  cursor: pointer;
}
input,
textarea {
  width: 100%;
  padding: 10px;
  background: var(--surface-subtle);
  border: 1px solid var(--border-default);
  border-radius: 7px;
  color: var(--text-primary);
  font: inherit;
}
form {
  display: flex;
  align-items: end;
  gap: 10px;
  margin-top: 18px;
}
form label {
  flex: 1;
  font-size: 13px;
}
form input {
  margin-top: 8px;
}
.primary {
  background: var(--brand-primary);
  color: white;
}
@media (max-width: 650px) {
  form {
    flex-direction: column;
    align-items: stretch;
  }
  .title strong {
    max-width: 190px;
  }
  .lab-toolbar {
    gap: 9px;
  }
}
</style>
