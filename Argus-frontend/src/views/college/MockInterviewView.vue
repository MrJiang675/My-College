<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import http, { extractApiError } from '@/api/http'
import { useRoute } from 'vue-router'
const route = useRoute()
import MarkdownContent from '@/components/MarkdownContent.vue'
import MaterialFileUploader from '@/components/MaterialFileUploader.vue'
type Question = {
  id: string
  index: number
  category: string
  question: string
  targetWeakness?: string
  focusPoint: string
}
type Feedback = {
  score: number
  strengths: string
  weaknesses: string
  followUpHint?: string
  improvedAnswerSample: string
}
type Report = {
  overallScore: number
  summaryLevel: string
  dimensions: { name: string; score: number }[]
  questionFeedbacks: Feedback[]
  actionableAdvice: string[]
}
const step = ref(1),
  resume = ref(''),
  target = ref(''),
  type = ref('实习技术/业务面'),
  busy = ref(false),
  error = ref(''),
  index = ref(0)
const profile = ref({ school: '', college: '', major: '', grade: '' }),
  entries = ref<{ id: string; project: string; text: string; source: string; team?: string }[]>([])
const questions = ref<Question[]>([]),
  answers = ref<string[]>(Array(5).fill('')),
  report = ref<Report>()
const current = computed(() => questions.value[index.value]),
  answered = computed(() => answers.value.filter((a) => a.trim()).length)
const selectedIds = ref<string[]>([])
const selectedEntries = computed(() =>
  entries.value.filter((e) => selectedIds.value.includes(e.id)),
)
let controller: AbortController | undefined
onMounted(async () => {
  try {
    const state = JSON.parse((await http.get('/college/workspace')).data.data.payload)
    entries.value = state.entries || []
    const career = state.pages?.['career-' + String(route.query.track || state.track || '实习')]
    selectedIds.value = career?.selected || []
    target.value = career?.targetRole || ''
    if (selectedIds.value.length) importEntries()
    profile.value = {
      school: state.school || '',
      college: state.dept || '',
      major: state.major || '',
      grade: state.grade || '',
    }
  } catch (e) {
    error.value = extractApiError(e, '档案暂未读取，可直接粘贴简历。')
  }
})
onUnmounted(() => controller?.abort())
function importEntries() {
  if (!selectedIds.value.length) selectedIds.value = entries.value.map((e) => e.id)
  resume.value = selectedEntries.value
    .map(
      (e) =>
        `${e.project}\n个人贡献：${e.text}\n团队成果：${e.team || '未填写'}\n证明位置：${e.source || '待补充'}`,
    )
    .join('\n\n')
  if (!resume.value) error.value = '还没有经历，请先录入项目或直接粘贴简历。'
}
function loadResumeExample() {
  resume.value =
    '【内置简历示例，需替换本人事实】信息工程学院大三，拟申请数据分析实习。团队完成校园出行调查，我参与数据处理，声称效率明显提升但未记录基线。团队获奖但本人工作与团队成果尚未拆清，公开证明链接缺失。'
  target.value = '数据分析实习'
  selectedIds.value = []
}
function extracted(text: string) {
  resume.value = text
}
function requestBody() {
  return {
    resumeText: resume.value,
    targetRole: target.value,
    interviewType: type.value,
    campusProfile: profile.value,
    selectedExperiences: selectedEntries.value,
  }
}
async function generate() {
  if (busy.value) return
  if (selectedIds.value.length > 12) {
    error.value = '每轮最多选择12项经历'
    return
  }
  busy.value = true
  error.value = ''
  controller = new AbortController()
  try {
    const { data } = await http.post('/college/interview/generate', requestBody(), {
      timeout: 55000,
      signal: controller.signal,
    })
    questions.value = data.data.questions
    answers.value = Array(5).fill('')
    index.value = 0
    report.value = undefined
    step.value = 2
  } catch (e) {
    error.value = extractApiError(e)
  } finally {
    busy.value = false
  }
}
async function evaluate() {
  if (busy.value || !answered.value) return
  if (
    answered.value < 5 &&
    !window.confirm(`目前回答了 ${answered.value}/5 题，未回答题按 0 分处理，确认提交？`)
  )
    return
  busy.value = true
  error.value = ''
  controller = new AbortController()
  try {
    const { data } = await http.post(
      '/college/interview/evaluate',
      {
        ...requestBody(),
        answers: questions.value.map((q, i) => ({
          question: q.question,
          category: q.category,
          targetWeakness: q.targetWeakness,
          userAnswer: answers.value[i] || '',
        })),
      },
      { timeout: 55000, signal: controller.signal },
    )
    report.value = data.data
    step.value = 3
  } catch (e) {
    error.value = extractApiError(e)
  } finally {
    busy.value = false
  }
}
function exportReport() {
  if (!report.value) return
  const r = report.value
  const text =
    `# Argus 模拟面试复盘\n\n目标：${target.value || '未填写'} · ${type.value}\n\n练习评分：${r.overallScore}/100（${r.summaryLevel}，非录取预测）\n\n` +
    r.dimensions.map((d) => `- ${d.name}：${d.score}`).join('\n') +
    '\n\n' +
    questions.value
      .map((q, i) => {
        const f = r.questionFeedbacks[i]!
        return `## ${i + 1}. ${q.question}\n\n我的回答：${answers.value[i] || '未作答'}\n\n分数：${f.score}\n\n亮点：${f.strengths}\n\n改进点：${f.weaknesses}\n\n简历薄弱点：${q.targetWeakness || '需核实'}\n\n示范：${f.improvedAnswerSample}\n\n二轮追问：${f.followUpHint || '请补充个人贡献证据。'}\n`
      })
      .join('\n') +
    '\n## 行动建议\n' +
    r.actionableAdvice.map((a) => '- ' + a).join('\n')
  const url = URL.createObjectURL(new Blob([text], { type: 'text/markdown;charset=utf-8' })),
    link = document.createElement('a')
  link.href = url
  link.download = 'Argus-模拟面试复盘.md'
  link.click()
  URL.revokeObjectURL(url)
}
</script>
<template>
  <main class="interview">
    <RouterLink to="/app/college/career">← 返回升学就业</RouterLink>
    <header>
      <small>Argus · 面试练习室</small>
      <h1>从你的经历出发，练习真实追问</h1>
      <p>导入简历 → 五题作答 → 逐题复盘。评分用于练习，不是录取预测。</p>
    </header>
    <nav aria-label="模拟面试步骤">
      <span :class="{ active: step === 1 }">01 导入与目标</span
      ><span :class="{ active: step === 2 }">02 五题作答</span
      ><span :class="{ active: step === 3 }">03 评分复盘</span>
    </nav>
    <p v-if="error" role="alert" class="error">{{ error }}</p>
    <section v-if="step === 1" class="interview-card">
      <button :disabled="busy" @click="importEntries">一键导入「我的经历」</button>
      <button :disabled="busy" @click="loadResumeExample">载入典型大学生简历示例</button>
      <fieldset v-if="entries.length">
        <legend>选择本轮深挖经历（最多12项）</legend>
        <label v-for="entry in entries" :key="entry.id"
          ><input v-model="selectedIds" type="checkbox" :value="entry.id" :disabled="busy" />{{
            entry.project
          }}
          · 证明{{ entry.source ? '已填写' : '待补充' }}</label
        >
      </fieldset>
      <MaterialFileUploader :disabled="busy" @extracted="extracted" />
      <label
        >简历 / 经历文字<textarea
          v-model="resume"
          :disabled="busy"
          maxlength="30000"
          rows="10"
          placeholder="粘贴简历，或上传文件提取文字后核对。"
        /></label
      ><small>{{ resume.length }} / 30000 字</small>
      <div class="targets">
        <label
          >面试场景<select v-model="type" :disabled="busy">
            <option>实习技术/业务面</option>
            <option>保研/夏令营面试</option>
            <option>秋招/校招综合面</option>
          </select></label
        ><label
          >目标岗位 / 专业方向<input
            v-model="target"
            :disabled="busy"
            maxlength="200"
            placeholder="例如：电气工程复试、数据分析实习"
        /></label>
      </div>
      <button class="primary" :disabled="busy || !resume.trim()" @click="generate">
        {{ busy ? '正在生成…' : '生成 5 道面试题' }}
      </button>
    </section>
    <section v-else-if="step === 2 && current" class="interview-card">
      <strong>第 {{ index + 1 }}/5 题 · 已作答 {{ answered }} 题</strong
      ><progress :value="answered" max="5" />
      <div class="question-nav">
        <button
          v-for="(q, i) in questions"
          :key="q.id"
          :aria-pressed="i === index"
          @click="index = i"
        >
          {{ i + 1 }} {{ answers[i]?.trim() ? '✓' : '' }}
        </button>
      </div>
      <small>{{ current.category }} · {{ current.focusPoint }}</small>
      <h2>{{ current.question }}</h2>
      <details>
        <summary>面试官追问意图（命中简历哪处薄弱点）</summary>
        <MarkdownContent :content="current.targetWeakness || '请核对个人行动、结果与证明是否说清。'" />
      </details>
      <label
        >我的回答<textarea
          v-model="answers[index]"
          :disabled="busy"
          maxlength="5000"
          rows="9"
          placeholder="建议用背景、个人行动、结果与证据作答。"
        /></label
      ><small>{{ answers[index]?.length || 0 }} / 5000 字 · 建议思考与作答 2–3 分钟</small>
      <div class="question-nav">
        <button :disabled="index === 0" @click="index--">上一题</button
        ><button :disabled="index === 4" @click="index++">下一题</button
        ><button :disabled="busy || !answered" class="primary" @click="evaluate">
          {{ busy ? '正在评分…' : '提交答卷，生成 AI 评分与建议' }}
        </button>
      </div>
      <button :disabled="busy" @click="step = 1">返回修改简历</button>
    </section>
    <section v-else-if="report" class="interview-card">
      <div class="score">
        <b>{{ report.overallScore }}<small>/100</small></b>
        <h2>{{ report.summaryLevel }}</h2>
        <p>参考评分，面试表现仍需真人反馈。</p>
      </div>
      <div class="dimensions">
        <label v-for="d in report.dimensions" :key="d.name"
          >{{ d.name }} · {{ d.score }}<progress :value="d.score" max="100"
        /></label>
      </div>
      <article v-for="(q, i) in questions" :key="q.id" class="feedback">
        <h3>{{ i + 1 }}. {{ q.question }}</h3>
        <p>你的回答：{{ answers[i] || '未作答' }}</p>
        <p>评分：{{ report.questionFeedbacks[i]?.score }} / 100</p>
        <strong>亮点</strong><MarkdownContent :content="report.questionFeedbacks[i]?.strengths" />
        <strong>改进点</strong><MarkdownContent :content="report.questionFeedbacks[i]?.weaknesses" />
        <strong>示范优化回答</strong><MarkdownContent :content="report.questionFeedbacks[i]?.improvedAnswerSample" />
        <strong>潜在二轮追问</strong><MarkdownContent :content="report.questionFeedbacks[i]?.followUpHint || '如果请你出示个人贡献的原始证据，你会提供什么？'" />
      </article>
      <h3>接下来可以做</h3>
      <ul>
        <li v-for="advice in report.actionableAdvice" :key="advice"><MarkdownContent :content="advice" /></li>
      </ul>
      <div class="question-nav">
        <button class="primary" @click="exportReport">导出复盘 Markdown</button
        ><RouterLink to="/app/college/career">回到升学就业优化陈述</RouterLink
        ><button @click="step = 1">再来一轮模拟面试</button>
      </div>
    </section>
  </main>
</template>
<style scoped>
.interview {
  max-width: 960px;
  margin: auto;
  color: var(--text-primary);
}
header {
  margin: 24px 0;
}
h1 {
  font-size: clamp(24px, 4vw, 34px);
}
p,
small {
  color: var(--text-secondary);
  line-height: 1.8;
}
nav,
.question-nav,
.targets {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin: 16px 0;
}
nav span {
  padding: 10px 14px;
  color: var(--text-secondary);
  border-bottom: 2px solid var(--border-default);
}
nav .active {
  color: var(--accent-primary);
  border-color: var(--accent-primary);
}
.interview-card {
  padding: clamp(16px, 3vw, 32px);
  background: var(--bg-surface);
  border: 1px solid var(--border-default);
  border-radius: 20px;
  display: grid;
  gap: 16px;
}
button,
input,
select,
textarea {
  border: 1px solid var(--border-default);
  border-radius: 10px;
  padding: 11px 14px;
  background: var(--bg-input);
  color: var(--text-primary);
  font: inherit;
  min-width: 0;
}
button {
  cursor: pointer;
}
button:disabled {
  opacity: 0.6;
  cursor: default;
}
button.primary,
button[aria-pressed='true'] {
  background: var(--brand-primary);
  color: #fff;
}
label {
  display: grid;
  gap: 8px;
  flex: 1;
}
textarea {
  width: 100%;
  resize: vertical;
}
progress {
  width: 100%;
  height: 10px;
  accent-color: var(--brand-primary);
}
.dimensions {
  display: grid;
  gap: 20px;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
}
.score b {
  font-size: 64px;
}
.score small {
  font-size: 18px;
}
.feedback {
  border-top: 1px solid var(--border-default);
  padding-top: 20px;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.error {
  color: var(--status-error);
}
ul {
  padding-left: 20px;
}
</style>
