<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import MarkdownContent from '@/components/MarkdownContent.vue'
import MaterialFileUploader from '@/components/MaterialFileUploader.vue'
import CareerMatrix from './CareerMatrix.vue'
import CareerDraftPanel from './CareerDraftPanel.vue'
import type { StudentExperience } from '@/types/notice'
type CareerPage = {
  targetRole?: string
  careerStep?: number
  genericAdvice?: boolean
  source: string
  sourceName: string
  selected?: string[]
  draft: string
  applicationDraft?: string
  requirementStatuses?: Record<string, string>
  turns: { q: string; a: string; citations: string[] }[]
}
const props = defineProps<{
  page: CareerPage
  track: string
  entries: StudentExperience[]
  busy: boolean
  live: string
  save: () => Promise<void>
}>()
const emit = defineEmits<{ send: []; sample: [] }>()
const router = useRouter(),
  error = ref(''),
  attempted = ref(false),
  uploader = ref<InstanceType<typeof MaterialFileUploader>>()
const step = computed(() => props.page.careerStep || 1)
watch(
  () => props.page,
  (p) => {
    if (!p.careerStep) p.careerStep = !p.targetRole?.trim() ? 1 : p.turns.length ? 3 : 2
  },
  { immediate: true },
)

const missingRequirements = computed(() => !props.page.source.trim())
const missingEntries = computed(
  () => !props.entries.some((e) => props.page.selected?.includes(e.id)),
)
watch(
  () => props.page,
  () => {
    error.value = ''
    attempted.value = false
  },
)
function go(n: number) {
  props.page.careerStep = n
  nextTick(() =>
    document
      .getElementById('career-step-' + n)
      ?.scrollIntoView({ behavior: 'smooth', block: 'start' }),
  )
}
function goal() {
  if (!props.page.targetRole?.trim()) {
    error.value = '请先填写目标岗位 / 院校方向'
    return
  }
  error.value = ''
  go(2)
}
function prepare() {
  attempted.value = true
  if (missingRequirements.value || missingEntries.value) {
    error.value = '请补齐高亮内容，或明确选择只看通用准备建议'
    return
  }
  error.value = ''
  props.page.genericAdvice = false
  go(3)
}
function generic() {
  props.page.genericAdvice = true
  error.value = ''
  go(3)
}
function requirementSample() {
  props.page.source =
    props.track === '升学'
      ? '示例夏令营简章：提交本科成绩单，说明本人科研贡献，提供可复核的实验记录与项目报告。'
      : '示例岗位 JD：能够清洗数据，设计实验对照，使用报告解释结果，提供可核验的项目证明。'
  props.page.sourceName = '内置示例要求（请替换为实际原文）'
}
async function sample() {
  emit('sample')
  await nextTick()
  props.page.selected = props.entries.filter((e) => e.id.startsWith('sample-')).map((e) => e.id)
}
function select(id: string) {
  props.page.selected = props.page.selected?.includes(id)
    ? props.page.selected.filter((v) => v !== id)
    : [...(props.page.selected || []), id]
}
function send() {
  if (!props.page.targetRole?.trim()) {
    go(1)
    error.value = '请先确认目标'
    return
  }
  if (!props.page.genericAdvice && (missingRequirements.value || missingEntries.value)) {
    go(2)
    error.value = '要求或经历已变化，请补齐后继续'
    return
  }
  if (!props.page.draft.trim())
    props.page.draft = props.page.genericAdvice
      ? '仅按当前目标给通用准备建议；目前缺少逐项对标材料，不要作出已证明能力的结论。'
      : '结合当前申请要求与所选真实经历，逐项核对证据、差距与准备行动，再帮我打磨申请陈述。'
  emit('send')
}
async function interview() {
  error.value = ''
  try {
    await props.save()
    await router.push({ path: '/app/college/mock-interview', query: { track: props.track } })
  } catch {
    error.value = '当前工作尚未保存，请先同步后再进入面试'
  }
}
</script>
<template>
  <section class="career-flow" aria-label="升学就业申请流程">
    <nav class="steps">
      <button :aria-current="step === 1 ? 'step' : undefined" @click="go(1)">1 设定目标</button
      ><button
        :disabled="!page.targetRole?.trim()"
        :aria-current="step === 2 ? 'step' : undefined"
        @click="go(2)"
      >
        2 要求与经历</button
      ><button
        :disabled="
          !page.targetRole?.trim() ||
          (!page.genericAdvice && (missingRequirements || missingEntries))
        "
        :aria-current="step === 3 ? 'step' : undefined"
        @click="go(3)"
      >
        3 对标与申请
      </button>
    </nav>
    <section v-if="step !== 1" class="summary panel">
      <b>目标已确认 · {{ track }}申请 / {{ page.targetRole }}</b
      ><button @click="go(1)">修改目标</button>
    </section>
    <section v-if="step === 1" id="career-step-1" class="panel body">
      <h2>先确定你想申请什么</h2>
      <p>当前类型：{{ track }}申请。可在上方切换实习或升学，各自保留进度。</p>
      <label
        >目标岗位 / 院校方向<input
          v-model="page.targetRole"
          maxlength="200"
          aria-label="目标岗位 / 院校方向"
          placeholder="例如：后端开发暑期实习"
      /></label>
      <p v-if="error" class="red">{{ error }}</p>
      <div class="quick">
        <button
          v-for="s in ['后端开发暑期实习', '数据分析实习', '数学/计算机保研夏令营']"
          :key="s"
          @click="page.targetRole = s"
        >
          {{ s }}
        </button>
      </div>
      <button class="primary" @click="goal">确认目标，进入下一步</button>
    </section>
    <section v-if="step === 3" class="summary panel">
      <b>{{
        page.genericAdvice
          ? '已选择仅看通用建议'
          : `要求已提供 · ${page.selected?.length || 0} 项经历已选择`
      }}</b
      ><button @click="go(2)">修改要求与经历</button>
    </section>
    <section v-if="step === 2" id="career-step-2" class="panel body">
      <h2>补齐目标要求和个人证据</h2>
      <div class="prepare-grid">
        <div :class="{ highlight: attempted && missingRequirements }">
          <h3>目标岗位 JD / 保研简章 / 简历</h3>
          <p>推荐上传：招聘 JD 截图 · 夏令营简章 · 个人简历 PDF</p>
          <div @paste="uploader?.paste($event)">
            <MaterialFileUploader
              ref="uploader"
              @extracted="
                (text, name) => {
                  page.source = text.slice(0, 16000)
                  page.sourceName = name
                }
              "
            /><textarea
              v-model="page.source"
              rows="7"
              maxlength="16000"
              aria-label="目标要求原文"
              placeholder="上传文件、Ctrl+V 粘贴截图，或粘贴目标要求原文"
            />
          </div>
          <small>{{ page.sourceName }}</small
          ><button @click="requirementSample">载入示例要求</button>
          <p v-if="missingRequirements" class="yellow">
            还缺少目标岗位 JD / 院校简章，请上传文件或粘贴原文，否则无法生成逐项能力对标。
          </p>
        </div>
        <div :class="{ highlight: attempted && missingEntries }">
          <h3>选择个人经历</h3>
          <button @click="sample">一键载入典型成长示例</button>
          <p>示例仅用于体验，请替换成本人真实成果。</p>
          <CareerDraftPanel
            :entries="entries"
            :selected="page.selected"
            @select="select"
          /><RouterLink to="/app/groups?tab=experience">去协作小组录入项目 →</RouterLink>
          <p v-if="missingEntries" class="yellow">
            还未勾选任何个人经历，请至少勾选 1 项（可点击“载入典型示例”快速体验）。
          </p>
        </div>
      </div>
      <p v-if="error" class="red">{{ error }}</p>
      <button class="primary" @click="prepare">材料已备齐，进入能力对标</button
      ><button @click="generic">仅按目标岗位先看通用准备建议</button>
    </section>
    <section v-if="step === 3" id="career-step-3" class="body panel">
      <h2>从证据到申请行动</h2>
      <p v-if="page.genericAdvice" class="yellow">
        当前仅提供通用准备建议，不代表已经完成逐项能力对标。
      </p>
      <CareerMatrix
        v-else
        v-model:draft="page.applicationDraft"
        v-model:statuses="page.requirementStatuses"
        :requirements="page.source"
        :entries="entries"
        :selected="page.selected"
      />
      <form @submit.prevent="send">
        <label
          >想怎样打磨申请陈述？<textarea
            v-model="page.draft"
            maxlength="3500"
            aria-label="申请打磨任务"
            placeholder="帮我区分已证明能力、证据偏弱与缺失佐证，或改写一段真实经历"
          /></label
        ><button class="primary" :disabled="busy">
          {{ busy ? '正在整理…' : '开始 AI 对标与打磨' }}
        </button>
      </form>
      <MarkdownContent v-if="busy" :content="live || '正在核对证据…'" />
      <article v-for="(turn, i) in [...page.turns].reverse()" :key="i">
        <b>{{ turn.q }}</b
        ><MarkdownContent :content="turn.a" />
        <details v-if="turn.citations.length">
          <summary>核对原文</summary>
          <p v-for="q in turn.citations" :key="q">{{ q }}</p>
        </details>
        <button @click="page.applicationDraft = turn.a">编辑为申请陈述草稿</button>
      </article>
      <template v-if="page.genericAdvice"
        ><label>申请草稿<textarea v-model="page.applicationDraft" rows="6" /></label></template
      ><button class="primary" @click="interview">基于当前目标与经历开始模拟面试</button>
      <p v-if="error" class="red">{{ error }}</p>
    </section>
  </section>
</template>
<style scoped>
.career-flow section {
  scroll-margin-top: 88px;
}
.career-flow {
  display: grid;
  gap: 16px;
  color: var(--text-primary);
}
.steps {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}
.steps button {
  flex: 1;
  padding: 18px;
  font-size: 16px;
}
.steps [aria-current],
.primary {
  background: var(--brand-primary);
  color: white;
}
.body,
.summary {
  border: 1px solid var(--border-default);
  border-radius: 14px;
  padding: 22px;
  background: var(--surface-white);
}
.summary {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
button {
  border: 1px solid var(--border-default);
  background: var(--surface-subtle);
  color: inherit;
  border-radius: 8px;
  padding: 10px 14px;
  margin: 4px;
  cursor: pointer;
}
button:disabled {
  opacity: 0.5;
  cursor: default;
}
input,
textarea {
  width: 100%;
  padding: 11px;
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--surface-subtle);
  color: inherit;
}
label {
  display: block;
  margin: 12px 0;
}
.prepare-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 22px;
}
.yellow {
  border-left: 4px solid #be9642;
  padding: 12px;
  background: var(--surface-subtle);
}
.red {
  color: #c64b47;
}
.highlight {
  outline: 2px solid #c64b47;
  padding: 10px;
  border-radius: 8px;
}
article {
  padding: 16px 0;
  border-bottom: 1px solid var(--border-default);
}
p {
  line-height: 1.7;
}
h2 {
  font-size: 22px;
}
h3 {
  font-size: 17px;
}
@media (max-width: 800px) {
  .prepare-grid {
    grid-template-columns: 1fr;
  }
  .steps button {
    min-width: 130px;
  }
}
</style>
