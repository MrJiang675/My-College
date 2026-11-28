<script setup lang="ts">
import { computed, nextTick, ref, shallowReactive, watch } from 'vue'
import MarkdownContent from '@/components/MarkdownContent.vue'
import MaterialFileUploader from '@/components/MaterialFileUploader.vue'
import http, { extractApiError } from '@/api/http'
import { exportCalendar, type CalendarDeadline } from '@/utils/icsExport'
import { makeZip, downloadBlob, safeFileName } from '@/utils/zipPackager'
import { demoFile, generateDemoMaterials } from '@/utils/demoMaterialGenerator'
import {
  newRequirement,
  extensions,
  extension,
  blockers,
  matchesEvidence,
  type MaterialRequirement,
} from '@/utils/noticeMaterials'
import type { CampusProfile, NoticeAnalysis, StudentExperience } from '@/types/notice'
import { noticeExample, noticeScenarios } from '@/constants/noticeScenarios'
type Saved = NoticeAnalysis & { contextKey?: string }
const props = defineProps<{
  source: string
  profile: CampusProfile
  enabled: boolean
  analysis?: Saved
  entries: StudentExperience[]
}>()
const emit = defineEmits<{
  load: [source: string, name: string]
  'update:analysis': [value: Saved]
  profile: [college: string, grade: string]
  statement: [value: string]
  adapt: [enabled: boolean]
}>()
const materials = defineModel<MaterialRequirement[]>('materials', { default: () => [] })
const view = ref(1),
  busy = ref(false),
  message = ref(''),
  focus = ref(''),
  extra = ref(''),
  script = ref(''),
  draft = ref(''),
  selected = ref<string[]>([]),
  rejected = ref<string[]>([]),
  expanded = ref(false)
const files = shallowReactive(new Map<string, File>()),
  dates = ref<Record<number, string>>({}),
  confirmed = ref<Record<number, CalendarDeadline>>({})
const uploader = ref<InstanceType<typeof MaterialFileUploader>>(),
  importInput = ref<HTMLInputElement>()
const contextKey = computed(() => JSON.stringify([props.source, props.profile, props.enabled]))
const demo = computed(() => noticeExample(props.source, props.profile, props.enabled))
const current = computed(() => {
  const a =
    demo.value || (props.analysis?.contextKey === contextKey.value ? props.analysis : undefined)
  if (!a) return undefined
  const evidence = (Array.isArray(a.eligibility?.evidences) ? a.eligibility.evidences : []).filter(
    (q) => typeof q === 'string' && matchesEvidence(props.source, q),
  )
  return {
    ...a,
    answer: typeof a.answer === 'string' ? a.answer : '【需核实】分析未完成',
    checklist: (Array.isArray(a.checklist) ? a.checklist : []).filter(
      (c) =>
        c &&
        typeof c.item === 'string' &&
        typeof c.evidenceQuote === 'string' &&
        matchesEvidence(props.source, c.evidenceQuote),
    ),
    eligibility: {
      status:
        evidence.length && ['GREEN', 'YELLOW', 'RED'].includes(a.eligibility?.status)
          ? a.eligibility.status
          : 'YELLOW',
      summary: a.eligibility?.summary || '缺少可核对的资格结构，请核实原文与档案。',
      evidences: evidence,
    },
    deadlines: (Array.isArray(a.deadlines) ? a.deadlines : [])
      .filter((d) => d && typeof d.title === 'string')
      .map((d) => ({ ...d, needsVerify: d.needsVerify !== false })),
    missingDocuments: (Array.isArray(a.missingDocuments) ? a.missingDocuments : []).filter(
      (m) => typeof m === 'string',
    ),
  }
})
const paragraphs = computed(() => props.source.split(/\n/).filter((p) => p.trim()))
const attachments = computed(() =>
  paragraphs.value
    .map((text, i) => ({ text, i }))
    .filter((p) => /见附件|详见附件|附件[0-9一二三四]|另附表格/.test(p.text)),
)
const timeClues = computed(() =>
  paragraphs.value
    .map((text, i) => ({ text, i }))
    .filter(
      (p) =>
        /时间|截止|公示|另行通知/.test(p.text) &&
        !(current.value?.deadlines || []).some(
          (d) => d.evidenceQuote && matchesEvidence(p.text, d.evidenceQuote),
        ),
    ),
)
const candidates = computed(() => {
  const ai = (current.value?.checklist || []).map((c) =>
    newRequirement(c.item, c.formatNote, c.evidenceQuote),
  )
  const rules = paragraphs.value
    .filter((p) => /提交|报送|填写|纸质|电子版/.test(p) && /表|申请书|成绩单|证明|材料/.test(p))
    .filter((p) => !ai.some((r) => matchesEvidence(p, r.quote)))
    .slice(0, 8)
    .map((p) => newRequirement(p.slice(0, 50), p, p))
  return [...ai, ...rules]
    .map((r) => ({
      ...r,
      id: r.name + '|' + r.quote,
      deadline:
        (current.value?.deadlines || []).find(
          (d) => d.evidenceQuote && matchesEvidence(r.quote, d.evidenceQuote),
        )?.dateTime || '',
    }))
    .filter(
      (r) =>
        !rejected.value.includes(r.id) &&
        !materials.value.some((m) => m.name === r.name && m.quote === r.quote),
    )
})
const ready = computed(() => materials.value.filter((r) => !blockers(r, files).length).length)
const missing = computed(
  () =>
    materials.value.filter((r) => r.kind === '电子材料' && (!r.fileId || !files.has(r.fileId)))
      .length,
)
const pending = computed(() => materials.value.filter((r) => blockers(r, files).length > 0).length)
const calendarItems = computed(() =>
  (current.value?.deadlines || []).map((d, i) => confirmed.value[i] || d),
)
watch(
  () => props.source,
  () => {
    files.clear()
    materials.value = []
    rejected.value = []
    confirmed.value = {}
    view.value = 1
    draft.value = ''
    message.value = ''
    selected.value = []
  },
)
watch(
  () => current.value?.teacherInquiryScripts,
  (v) => (script.value = (v || []).join('\n\n')),
  { immediate: true },
)
function go(step: number) {
  view.value = step
  nextTick(() =>
    document
      .getElementById('notice-step-' + step)
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
  )
}
async function jump(quote: string) {
  expanded.value = true
  await nextTick()
  const index = paragraphs.value.findIndex((p) => matchesEvidence(p, quote))
  document
    .getElementById('notice-paragraph-' + index)
    ?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}
function add(r: MaterialRequirement) {
  materials.value.push({ ...r, id: crypto.randomUUID() })
}
function addAll() {
  candidates.value.forEach(add)
}
async function analyze() {
  if (!props.source.trim() || busy.value) return
  busy.value = true
  message.value = ''
  const key = contextKey.value
  try {
    const { data } = await http.post(
      '/college/notice',
      {
        question: extra.value.trim() || '请综合核查资格、机会、原文依据、材料与全部时间节点。',
        source: props.source,
        current: null,
        campusProfile: props.profile,
        enableProfileAdaptation: props.enabled,
        moduleCode: 'CAMPUS_NOTICE',
      },
      { timeout: 45000 },
    )
    if (key !== contextKey.value) {
      message.value = '原文或档案已变化，请重新分析'
      return
    }
    emit('update:analysis', { ...data.data, contextKey: key })
  } catch (e) {
    message.value = extractApiError(e)
  } finally {
    busy.value = false
  }
}
function receive(list: FileList | File[] | null) {
  if (!list) return
  for (const f of Array.from(list)) {
    if (
      files.size >= 80 ||
      f.size > 25 * 1024 * 1024 ||
      [...files.values()].reduce((s, x) => s + x.size, 0) + f.size > 100 * 1024 * 1024
    ) {
      message.value = '本地材料上限 80 文件/100MB，单文件不超过 25MB，请拆分。'
      break
    }
    const id = crypto.randomUUID()
    files.set(id, f)
    const available = materials.value.filter((r) => r.kind === '电子材料' && !r.fileId)
    const words = (s: string) => s.replace(/示例|故意格式错误|[_\s.]/g, '')
    const matched = available.find(
      (r) =>
        words(f.name).includes(words(r.name)) ||
        words(r.name).includes(words(f.name.replace(/\.[^.]+$/, ''))),
    )
    const fallback = available.filter((r) => extensions(r).includes(extension(f.name)))
    const r = matched || (fallback.length === 1 ? fallback[0] : undefined)
    if (r) {
      r.fileId = id
      r.outputName = f.name
      r.contentChecked = false
      r.signatureChecked = false
    }
  }
}
function drop(e: DragEvent) {
  receive(e.dataTransfer?.files || null)
}
function bind(r: MaterialRequirement, id: string) {
  r.fileId = id || undefined
  r.outputName = files.get(id)?.name || ''
  r.contentChecked = false
  r.signatureChecked = false
  r.namingChecked = false
}
async function examples() {
  if (!materials.value.length) {
    materials.value = [
      newRequirement('示例成绩单', 'PDF'),
      newRequirement('示例报名表', 'XLSX'),
      newRequirement('示例申请书', 'PDF'),
      newRequirement('示例说明', 'TXT'),
    ]
  }
  const demoFiles = await generateDemoMaterials()
  receive(demoFiles)
  message.value =
    '已生成真实 PDF/XLSX/CSV/TXT 示例。申请书故意使用 CSV，请替换为 PDF；内容、签字与份数仍需亲自确认。'
}
async function repair(r: MaterialRequirement) {
  if (!r.name.startsWith('示例')) {
    message.value = '真实材料请用原始软件导出正确格式后重新关联，不会自动伪造文件。'
    return
  }
  const ext = extensions(r)[0] || 'pdf'
  if (!['pdf', 'xlsx', 'csv', 'txt'].includes(ext)) {
    message.value = '该格式请自行准备'
    return
  }
  const f = await demoFile(ext, r.name),
    id = crypto.randomUUID()
  files.set(id, f)
  bind(r, id)
  message.value = '已替换为真实结构的示例文件；不代表学校可接受的个人材料。'
}
function backup() {
  downloadBlob(
    new Blob(
      [
        JSON.stringify(
          {
            version: 1,
            source: props.source,
            analysis: props.analysis,
            materials: materials.value,
            confirmed: confirmed.value,
          },
          null,
          2,
        ),
      ],
      { type: 'application/json' },
    ),
    'Argus-通知材料项目.json',
  )
}
async function restore(e: Event) {
  try {
    const f = (e.target as HTMLInputElement).files?.[0]
    if (!f || f.size > 1024 * 1024) throw Error('项目 JSON 须小于 1MB')
    const x = JSON.parse(await f.text())
    if (
      x.version !== 1 ||
      typeof x.source !== 'string' ||
      x.source.length > 16000 ||
      !Array.isArray(x.materials) ||
      x.materials.length > 60
    )
      throw Error('不支持的项目结构')
    emit('load', x.source, '导入的项目备份')
    await nextTick()
    materials.value = x.materials.map((v: Partial<MaterialRequirement>) => ({
      ...newRequirement(
        String(v.name || '材料').slice(0, 120),
        String(v.format || '需核实').slice(0, 500),
        String(v.quote || '').slice(0, 2000),
      ),
      kind: ['电子材料', '纸质材料', '系统填报'].includes(v.kind || '') ? v.kind! : '电子材料',
      copies: Number.isInteger(v.copies) && v.copies! > 0 ? v.copies! : 0,
      notes: String(v.notes || '').slice(0, 1000),
    }))
    if (x.analysis && typeof x.analysis.answer === 'string')
      emit('update:analysis', { ...x.analysis, contextKey: contextKey.value })
    files.clear()
    confirmed.value = {}
    message.value = '已恢复文本要求。出于核对安全，文件、勾选与确认日期需要重新关联/确认。'
  } catch (e) {
    message.value = extractApiError(e)
  }
}
async function pack() {
  try {
    if (!materials.value.length || pending.value) throw Error('请先修复全部阻塞项')
    const entries = materials.value
      .filter((r) => r.kind === '电子材料')
      .map((r) => ({ name: safeFileName(r.outputName), data: files.get(r.fileId!)! }))
    const checklist =
      '# 提交前核对清单\n\n仅表示本人完成清单操作，不代表学校审核通过或已提交。\n\n' +
      materials.value
        .map(
          (r) =>
            `- [x] ${r.name} · ${r.kind} · ${r.copies} 份/次\n  - 原文：${r.quote}\n  - 备注：${r.notes}`,
        )
        .join('\n')
    downloadBlob(
      await makeZip([...entries, { name: '提交前核对清单.md', data: checklist }]),
      'Argus-提交材料.zip',
    )
    message.value = '已在本机打包，请按原通知自行提交。'
  } catch (e) {
    message.value = extractApiError(e)
  }
}
function confirmDate(i: number) {
  const d = dates.value[i]
  if (!d || !Number.isFinite(Date.parse(d))) {
    message.value = '请先向老师核实并选择完整日期时间'
    return
  }
  confirmed.value[i] = {
    ...current.value!.deadlines[i]!,
    dateTime: (d.length === 16 ? d + ':00' : d) + '+08:00',
    needsVerify: false,
  }
  message.value = '已按本人核实的北京时间确认，可导出日程。'
}
function calendar(index?: number) {
  try {
    const items =
      index === undefined
        ? calendarItems.value.filter((d) => !d.needsVerify)
        : [calendarItems.value[index]!]
    if (!items.length) throw Error('暂无已确认时间')
    exportCalendar(items)
  } catch (e) {
    message.value = extractApiError(e)
  }
}
async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    message.value = '已复制'
  } catch {
    message.value = '请手动选择文字复制'
  }
}
async function statement() {
  if (busy.value || !selected.value.length) return
  busy.value = true
  const key = contextKey.value
  try {
    const { data } = await http.post(
      '/college/notice-application',
      { source: props.source, selectedExperienceIds: selected.value, campusProfile: props.profile },
      { timeout: 45000 },
    )
    if (key === contextKey.value) {
      draft.value = data.data.draft
      emit('statement', draft.value)
    }
  } catch (e) {
    message.value = extractApiError(e)
  } finally {
    busy.value = false
  }
}
function fix(r: MaterialRequirement) {
  focus.value = r.id
  go(2)
  nextTick(() =>
    document
      .getElementById('material-' + r.id)
      ?.scrollIntoView({ behavior: 'smooth', block: 'center' }),
  )
}
</script>
<template>
  <section class="notice-workflow" aria-label="通知防坑行动台">
    <header class="overview panel">
      <div>
        <h2>从群通知到下一步行动</h2>
        <label class="check"
          ><input
            type="checkbox"
            :checked="enabled"
            @change="emit('adapt', ($event.target as HTMLInputElement).checked)"
          />结合校园档案 · {{ profile.school || '学校未填' }} /
          {{ profile.college || '学院未填' }}</label
        >
        <p>进度 {{ ready }}/{{ materials.length }} 清单操作完成</p>
        <progress :value="ready" :max="Math.max(materials.length, 1)" />
      </div>
      <div class="counts">
        材料 {{ materials.length }} · 待补文件 {{ missing }} · 待你确认 {{ pending }}
      </div>
      <div>
        <button @click="importInput?.click()">导入项目 JSON</button
        ><button @click="backup">导出项目 JSON</button
        ><input ref="importInput" hidden type="file" accept=".json" @change="restore" />
      </div>
      <details>
        <summary>能力边界说明</summary>
        <p>
          能帮你提取、逐条核对与打包；看不到缺失附件，不能代替学校认定，也不自动提交。提交文件只留在当前浏览器内存，刷新需重新选择。通知原文本身可提交
          AI 分析。
        </p>
      </details>
    </header>
    <nav class="steps" aria-label="通知办理步骤">
      <button :aria-current="view === 1 ? 'step' : undefined" @click="go(1)">
        ① 综合分析 · ② 核资格 · ③ 看机会</button
      ><button :aria-current="view === 2 ? 'step' : undefined" @click="go(2)">④ 理清单</button
      ><button :aria-current="view === 3 ? 'step' : undefined" @click="go(3)">⑤ 去办事</button>
    </nav>
    <section v-show="view === 1" id="notice-step-1" class="panel body">
      <h2>读通知，对要求</h2>
      <h3>把通知粘进来或上传截图 / 附件</h3>
      <p>保留原来的换行，时间与材料对得更准。</p>
      <div class="row">
        <button
          v-for="s in noticeScenarios"
          :key="s.id"
          @click="emit('load', s.source, s.title + '（内置演示）')"
        >
          载入{{ s.title }}样例</button
        ><button @click="emit('load', '', '')">清空通知</button>
      </div>
      <div @paste="uploader?.paste($event)">
        <MaterialFileUploader
          ref="uploader"
          @extracted="(text, name) => emit('load', text.slice(0, 16000), name)"
        /><textarea
          :value="source"
          aria-label="通知原文"
          rows="7"
          maxlength="16000"
          placeholder="粘贴通知，或上传通知截图/PDF/Word。提交材料请到下一步本地关联。"
          @input="emit('load', ($event.target as HTMLTextAreaElement).value, '粘贴的通知')"
        />
      </div>
      <div class="row">
        <label
          >学院<input
            :value="profile.college"
            @change="
              emit('profile', ($event.target as HTMLInputElement).value, profile.grade)
            " /></label
        ><label
          >年级<select
            :value="profile.grade"
            @change="emit('profile', profile.college, ($event.target as HTMLSelectElement).value)"
          >
            <option value="">待填写</option>
            <option v-for="g in ['大一', '大二', '大三', '大四', '研究生']" :key="g">
              {{ g }}
            </option>
          </select></label
        >
      </div>
      <label
        >补充你的关注点（选填）<input
          v-model="extra"
          maxlength="3500"
          placeholder="留空即完整分析" /></label
      ><button class="primary" :disabled="busy || !source.trim()" @click="analyze">
        {{ busy ? '正在分析…' : '开始综合分析并提取要求' }}
      </button>
      <details :open="expanded" @toggle="expanded = ($event.target as HTMLDetailsElement).open">
        <summary>通知原文（逐字保留，{{ paragraphs.length }} 段）</summary>
        <p v-for="(p, i) in paragraphs" :id="'notice-paragraph-' + i" :key="i" class="original">
          <b>第 {{ i + 1 }} 段</b> {{ p }}
        </p>
      </details>
      <div v-for="a in attachments" :key="a.i" class="red">
        原文提到附件，当前文本不能证明附件已完整提供；请另行索取并补入清单：<button
          @click="jump(a.text)"
        >
          「{{ a.text }}」· 第 {{ a.i + 1 }} 段
        </button>
      </div>
      <div v-for="t in timeClues" :key="t.i" class="blue">
        未挂靠时间线索：<button @click="jump(t.text)">{{ t.text }} · 第 {{ t.i + 1 }} 段</button>
      </div>
      <template v-if="current"
        ><small v-if="demo">当前为内置演示结果；上传自己的通知后由真实模型分析。</small>
        <div class="conclusions">
          <article :class="current.eligibility.status">
            <h3>核资格 · {{ current.eligibility.status }}</h3>
            <MarkdownContent :content="current.eligibility.summary" /><button
              v-for="q in current.eligibility.evidences"
              :key="q"
              @click="jump(q)"
            >
              原文：{{ q }}
            </button>
          </article>
          <article>
            <h3>看机会</h3>
            <dl>
              <dt>级别</dt>
              <dd>{{ current.opportunity?.level || '原文未明示，需核实' }}</dd>
              <dt>面向对象</dt>
              <dd>{{ current.opportunity?.audience || current.eligibility.summary }}</dd>
              <dt>预期收获</dt>
              <dd>{{ current.opportunity?.benefit || '以实际活动内容为准，需核实' }}</dd>
              <dt>综测认定</dt>
              <dd>{{ current.opportunity?.credit || '缺本院认定目录，需核实' }}</dd>
            </dl>
            <p>活动级别、面向对象与收获仅以原文为准；综测加分若缺本院认定目录，均【需核实】。</p>
            <ul>
              <li v-for="m in current.missingDocuments" :key="m">{{ m }}</li>
            </ul>
          </article>
          <article>
            <h3>综合分析</h3>
            <MarkdownContent :content="current.answer" />
          </article></div
      ></template>
      <h3>先看它找到了什么</h3>
      <button :disabled="!candidates.length" @click="addAll">全部加入清单</button>
      <article v-for="r in candidates" :key="r.id">
        <h3>{{ r.name }}</h3>
        <span>{{ r.kind }} · {{ r.deadline || '时间待确认' }}</span>
        <details>
          <summary>看细节与原文</summary>
          <p>{{ r.format }}</p>
          <button @click="jump(r.quote)">{{ r.quote }}</button>
        </details>
        <button @click="add(r)">加入清单</button
        ><button @click="rejected.push(r.id)">不要这条</button>
      </article>
      <button v-if="materials.length" class="primary" @click="go(2)">
        下一步：进入「理清单」备齐并核对材料 →
      </button>
    </section>
    <section v-show="view === 2" id="notice-step-2" class="panel body">
      <h2>一项一项过</h2>
      <p>
        文件只在你这台电脑的浏览器内存中比对文件名与扩展名，不上传服务器；内容、签字仍需本人确认。
      </p>
      <div class="drop" @dragover.prevent @drop.prevent="drop">
        <label
          >一次拖入或选择多个本地提交文件<input
            type="file"
            multiple
            aria-label="本地提交文件"
            @change="receive(($event.target as HTMLInputElement).files)"
        /></label>
      </div>
      <button @click="examples">生成示例材料文件（含故意格式错误）</button
      ><button @click="materials.push(newRequirement())">+ 手工补加遗漏项</button>
      <div v-for="kind in ['电子材料', '纸质材料', '系统填报']" :key="kind">
        <h3>{{ kind }}</h3>
        <article
          v-for="r in materials.filter((r) => r.kind === kind)"
          :id="'material-' + r.id"
          :key="r.id"
          :class="{ focused: focus === r.id }"
        >
          <label>材料名称<input v-model="r.name" /></label
          ><label
            >类型<select v-model="r.kind">
              <option>电子材料</option>
              <option>纸质材料</option>
              <option>系统填报</option>
            </select></label
          >
          <p :class="blockers(r, files).length ? 'red' : 'green'">
            {{ blockers(r, files).join('；') || '已对上 · 清单操作完成' }}
          </p>
          <details :open="focus === r.id">
            <summary>编辑要求、文件与人工核对</summary>
            <label>格式与命名要求<input v-model="r.format" /></label
            ><label
              >份数 / 提交次数（未说明先核实）<input
                v-model.number="r.copies"
                type="number"
                min="0"
                max="100"
            /></label>
            <p>通知原文：{{ r.quote || '手工补项，请自行补记来源' }}</p>
            <label>原文依据<textarea v-model="r.quote" /></label
            ><template v-if="r.kind === '电子材料'"
              ><label
                >手动关联本地文件<select
                  :value="r.fileId || ''"
                  @change="bind(r, ($event.target as HTMLSelectElement).value)"
                >
                  <option value="">解绑 / 尚未关联</option>
                  <option v-for="[id, file] in files" :key="id" :value="id">{{ file.name }}</option>
                </select></label
              ><label>打包文件名（不能修改真实扩展名）<input v-model="r.outputName" /></label
              ><label v-if="/命名/.test(r.format)" class="check"
                ><input
                  v-model="r.namingChecked"
                  type="checkbox"
                />已按原通知核实文件命名规则</label
              ><button @click="repair(r)">修复 / 替换示例为正确格式</button></template
            ><label class="check"
              ><input v-model="r.contentChecked" type="checkbox" />已核对文件内容确实满足要求</label
            ><label class="check"
              ><input v-model="r.signatureRequired" type="checkbox" />这项要求签字 / 盖章</label
            ><label v-if="r.signatureRequired" class="check"
              ><input v-model="r.signatureChecked" type="checkbox" />已完成本人及导师签字 /
              盖章</label
            ><label class="check"
              ><input v-model="r.copiesChecked" type="checkbox" />已核实并确认提交份数 / 次数</label
            ><label>个人备注<textarea v-model="r.notes" /></label
            ><button @click="materials = materials.filter((m) => m.id !== r.id)">移除此项</button>
          </details>
        </article>
      </div>
      <button class="primary" @click="go(3)">下一步：进入「去办事」临交体检与打包落地 →</button>
    </section>
    <section v-show="view === 3" id="notice-step-3" class="panel body">
      <h2>临交前的样子 · {{ ready }}/{{ materials.length }}</h2>
      <p>
        文件对上了、你也逐项勾完了，才叫“清单操作完成”。它不表示学校审核通过，也不表示已经提交。
      </p>
      <p v-if="!materials.length" class="red">还没有清单，请先提取要求或手工补项。</p>
      <template v-for="r in materials" :key="r.id"
        ><button v-for="b in blockers(r, files)" :key="b" class="red block" @click="fix(r)">
          {{ r.name }}：{{ b }} → 去修复
        </button></template
      ><button class="primary" :disabled="!materials.length || pending > 0" @click="pack">
        纯前端一键打包提交材料（ZIP）</button
      ><button @click="backup">导出项目备份 (.json)</button>
      <article>
        <h3>缺口核实与问老师话术</h3>
        <ul>
          <li v-for="m in current?.missingDocuments || []" :key="m">{{ m }}</li>
          <li v-for="a in attachments" :key="a.i">请核实附件：{{ a.text }}</li>
        </ul>
        <textarea v-model="script" aria-label="问老师话术" rows="4" /><button @click="copy(script)">
          一键复制问老师话术
        </button>
      </article>
      <article>
        <h3>关键时间轴 · 北京时间</h3>
        <button @click="calendar()">导出已确认日历 (.ics)</button>
        <div v-for="(d, i) in calendarItems" :key="i">
          <b>{{ d.title }}</b>
          <p>{{ d.dateTime || '日期未明确' }} · {{ d.location || '地点需核实' }}</p>
          <p>{{ d.description }}</p>
          <template v-if="d.needsVerify"
            ><label
              >核实后填写日期时间<input
                v-model="dates[i]"
                type="datetime-local"
                :aria-label="'确认日期-' + i" /></label
            ><button @click="confirmDate(i)">确认日期</button></template
          ><button :disabled="d.needsVerify" @click="calendar(i)">导出此节点</button>
        </div>
      </article>
      <article>
        <h3>办理入口 / 提交地点</h3>
        <p v-for="d in current?.deadlines || []" :key="d.title">
          {{ d.location || '原文未说明，需向老师核实' }}
        </p>
        <p v-for="p in paragraphs.filter((p) => /https?:|办公室|提交至|报送至/.test(p))" :key="p">
          {{ p }}
        </p>
      </article>
      <article v-if="current && current.eligibility.status !== 'RED'">
        <h3>用自己的经历写报名陈述</h3>
        <label v-for="e in entries" :key="e.id" class="check"
          ><input v-model="selected" type="checkbox" :value="e.id" />{{ e.project }}</label
        ><RouterLink v-if="!entries.length" to="/app/groups?tab=experience"
          >前往记录本人经历 →</RouterLink
        ><button :disabled="busy || !selected.length" @click="statement">
          带入「我的经历」生成报名陈述</button
        ><textarea v-if="draft" v-model="draft" rows="6" /><button
          v-if="draft"
          @click="copy(draft)"
        >
          复制陈述
        </button>
      </article>
    </section>
    <p v-if="message" role="status">{{ message }}</p>
  </section>
</template>
<style scoped>
.notice-workflow section {
  scroll-margin-top: 88px;
}
.notice-workflow {
  display: grid;
  gap: 18px;
  color: var(--text-primary);
}
.overview,
.body {
  border: 1px solid var(--border-default);
  border-radius: 14px;
  padding: 22px;
  background: var(--surface-white);
}
h2 {
  font-size: 21px;
}
h3 {
  font-size: 17px;
  margin: 10px 0;
}
.row,
.steps {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.row label {
  flex: 1;
  min-width: 130px;
}
.steps button {
  flex: 1;
  font-size: 16px;
  padding: 18px;
}
.steps [aria-current],
.primary {
  background: var(--brand-primary);
  color: white;
}
button {
  padding: 9px 13px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--surface-subtle);
  color: inherit;
  cursor: pointer;
  margin: 4px;
}
button:disabled {
  opacity: 0.5;
  cursor: default;
}
label {
  display: block;
  margin: 10px 0;
}
input:not([type='checkbox']),
textarea,
select {
  width: 100%;
  padding: 10px;
  border: 1px solid var(--border-default);
  border-radius: 7px;
  background: var(--surface-subtle);
  color: inherit;
}
textarea {
  resize: vertical;
  min-height: 80px;
}
input[type='checkbox'] {
  width: auto;
  margin-right: 8px;
}
.check {
  display: flex;
  align-items: center;
}
.original {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  padding: 10px;
}
.red,
.RED {
  background: var(--status-danger-bg, #fce9e9);
  border-left: 4px solid #c04444;
  padding: 12px;
  color: var(--text-primary);
}
.blue,
.YELLOW {
  background: var(--surface-subtle);
  border-left: 4px solid #668eb3;
  padding: 12px;
}
.green,
.GREEN {
  border-left: 4px solid #488168;
  padding: 12px;
}
.block {
  display: block;
  text-align: left;
  width: 100%;
}
.drop {
  border: 2px dashed var(--border-default);
  padding: 24px;
  border-radius: 12px;
}
article {
  border: 1px solid var(--border-default);
  padding: 16px;
  margin: 14px 0;
  border-radius: 10px;
}
.focused {
  outline: 2px solid var(--brand-primary);
}
.conclusions {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 14px;
}
progress {
  width: 100%;
  accent-color: var(--brand-primary);
}
p {
  line-height: 1.7;
  overflow-wrap: anywhere;
}
summary {
  cursor: pointer;
  padding: 8px;
}
.counts {
  font-size: 17px;
  margin: 12px 0;
}
</style>
