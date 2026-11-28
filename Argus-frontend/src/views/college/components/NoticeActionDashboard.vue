<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import http, { extractApiError } from '@/api/http'
import { exportCalendar } from '@/utils/icsExport'
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
}>()
const busy = ref(false),
  message = ref(''),
  script = ref(''),
  checked = ref<number[]>([]),
  draft = ref(''),
  selected = ref<string[]>([])
const contextKey = computed(() => JSON.stringify([props.source, props.profile, props.enabled]))
const demo = computed(() => noticeExample(props.source, props.profile, props.enabled))
const stale = computed(() => !demo.value && props.analysis?.contextKey !== contextKey.value)
function safeResult(value: NoticeAnalysis): NoticeAnalysis {
  const text = (value: unknown, fallback = '') => (typeof value === 'string' ? value : fallback)
  const texts = (value: unknown) =>
    Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : []
  const evidence = texts(value.eligibility?.evidences).filter(
    (q) => q.trim() && props.source.includes(q),
  )
  const status = value.eligibility?.status
  return {
    answer: text(value.answer, '【需核实】暂未取得完整结构化分析。'),
    eligibility: {
      status: evidence.length && ['GREEN', 'YELLOW', 'RED'].includes(status) ? status : 'YELLOW',
      summary: text(
        value.eligibility?.summary,
        '后端未提供完整资格结构，请核对原文和档案，或升级配套后端。',
      ),
      evidences: evidence,
    },
    missingDocuments: texts(value.missingDocuments),
    teacherInquiryScripts: texts(value.teacherInquiryScripts),
    deadlines: (Array.isArray(value.deadlines) ? value.deadlines : [])
      .filter((d) => d && typeof d === 'object')
      .map((d) => ({
        ...d,
        title: text(d.title, '待核实事项'),
        dateTime: text(d.dateTime),
        location: text(d.location),
        description: text(d.description),
        needsVerify: d.needsVerify !== false,
      })),
    checklist: (Array.isArray(value.checklist) ? value.checklist : []).filter(
      (c) =>
        c &&
        typeof c === 'object' &&
        typeof c.item === 'string' &&
        typeof c.evidenceQuote === 'string' &&
        props.source.includes(c.evidenceQuote),
    ),
  }
}
const current = computed(() => {
  const raw = demo.value || (!stale.value ? props.analysis : undefined)
  return raw ? safeResult(raw) : undefined
})
watch(current, () => {
  checked.value = []
})
const scripts = computed(() => current.value?.teacherInquiryScripts || [])
watch(scripts, (value) => (script.value = value.join('\n\n')), { immediate: true })
watch(
  () => props.source,
  () => {
    checked.value = []
    draft.value = ''
    message.value = ''
  },
)
async function analyze() {
  if (busy.value || !props.source.trim()) return
  busy.value = true
  message.value = ''
  const key = contextKey.value
  try {
    const { data } = await http.post(
      '/college/notice',
      {
        question: '请按我的学院与年级核查资格、缺失文件、问老师话术、全部截止时间及材料清单。',
        source: props.source,
        current: null,
        campusProfile: props.profile,
        enableProfileAdaptation: props.enabled,
        moduleCode: 'CAMPUS_NOTICE',
      },
      { timeout: 45000 },
    )
    emit('update:analysis', { ...data.data, contextKey: key })
  } catch (e) {
    message.value = extractApiError(e)
  } finally {
    busy.value = false
  }
}
async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
    message.value = '已复制，可直接发给辅导员/教务老师'
  } catch {
    message.value = '复制失败，请手动选择文本复制。'
  }
}
function calendar(index?: number) {
  try {
    const items =
      index === undefined
        ? current.value?.deadlines.filter((d) => !d.needsVerify)
        : [current.value!.deadlines[index]!]
    if (!items?.length) throw Error('还没有已确认的时间节点，请先核实日期。')
    exportCalendar(items)
    message.value = '日历已导出，含提前一天提醒。'
  } catch (e) {
    message.value = extractApiError(e)
  }
}
async function statement() {
  if (busy.value) return
  const entries = props.entries.filter((e) => selected.value.includes(e.id))
  if (!entries.length) {
    message.value = '先勾选本人已收录经历；没有经历可前往“我的项目”添加或载入示例。'
    return
  }
  busy.value = true
  message.value = ''
  const requestKey = contextKey.value
  try {
    const { data } = await http.post(
      '/college/notice-application',
      {
        source: props.source,
        selectedExperienceIds: entries.map((e) => e.id),
        campusProfile: props.profile,
      },
      { timeout: 45000 },
    )
    if (requestKey !== contextKey.value) {
      message.value = '通知或档案已变化，请按新内容重新生成。'
      return
    }
    draft.value = data.data.draft
    emit('statement', draft.value)
  } catch (e) {
    message.value = extractApiError(e)
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <section class="notice-action" aria-label="通知防坑行动台">
    <header>
      <div>
        <small>一图 / 一文 → 明确下一步</small>
        <h2>通知防坑行动台</h2>
      </div>
      <button :disabled="busy || !source.trim()" @click="analyze">
        {{ busy ? '正在核查…' : '按当前档案重新核查' }}
      </button>
    </header>
    <div class="notice-examples">
      <button
        v-for="scene in noticeScenarios"
        :key="scene.id"
        @click="emit('load', scene.source, scene.title + '（内置演示）')"
      >
        载入{{ scene.title }}样例
      </button>
    </div>
    <div class="quick-profile">
      <label
        >学院<input
          aria-label="学院"
          :value="profile.college"
          placeholder="示例：信息工程学院"
          @change="
            emit('profile', ($event.target as HTMLInputElement).value, profile.grade)
          " /></label
      ><label
        >年级<select
          aria-label="年级"
          :value="profile.grade"
          @change="emit('profile', profile.college, ($event.target as HTMLSelectElement).value)"
        >
          <option value="">请选择</option>
          <option v-for="grade in ['大一', '大二', '大三', '大四', '研究生']" :key="grade">
            {{ grade }}
          </option>
        </select></label
      >
    </div>
    <p v-if="demo" class="notice-label">内置样例规则演示 · 切换学院/年级即可比较，不代表真实校务</p>
    <p v-if="!current">
      {{
        source
          ? '材料或档案已变化，请重新核查；旧裁决不会沿用。'
          : '先粘贴通知、上传文件或 Ctrl+V 粘贴截图，也可载入上方样例。'
      }}
    </p>
    <template v-if="current">
      <article class="verdict" :class="current.eligibility.status">
        <b>{{
          { GREEN: '绿灯 · 明示范围符合', YELLOW: '黄灯 · 条件待核实', RED: '红灯 · 明示条件不符' }[
            current.eligibility.status
          ]
        }}</b>
        <p>{{ current.eligibility.summary }}</p>
        <details>
          <summary>展开资格原文依据</summary>
          <mark v-for="quote in current.eligibility.evidences" :key="quote">{{ quote }}</mark>
        </details>
      </article>
      <article class="missing">
        <h3>先补齐这些口径，再行动</h3>
        <ul>
          <li v-for="item in current.missingDocuments" :key="item">{{ item }}</li>
        </ul>
        <small>建议核实对象：学院辅导员 / 教务负责老师</small
        ><textarea v-model="script" aria-label="问老师话术" rows="4" /><button
          :disabled="!script.trim()"
          @click="copy(script)"
        >
          一键复制问老师话术
        </button>
      </article>
      <article>
        <h3>提交前材料包 · 已备齐 {{ checked.length }}/{{ current.checklist.length }} 项</h3>
        <label v-for="(item, i) in current.checklist" :key="i" class="check"
          ><input v-model="checked" type="checkbox" :value="i" /><span
            >{{ item.item }} · {{ item.required ? '必需' : '按需'
            }}<small>{{ item.formatNote }}</small>
            <details>
              <summary>核对原句</summary>
              {{ item.evidenceQuote }}
            </details></span
          ></label
        >
      </article>
      <article>
        <h3>关键时间轴</h3>
        <button @click="calendar()">一键导出日历 (.ics)</button>
        <p v-if="!current.deadlines.length">原文未提供可提取时间，请向老师核实，暂不能生成日程。</p>
        <div v-for="(deadline, i) in current.deadlines" :key="i" class="deadline">
          <strong>{{ deadline.title }}</strong>
          <p>{{ deadline.dateTime || '日期未明确' }} · {{ deadline.location || '地点需核实' }}</p>
          <small>{{
            deadline.needsVerify ? '日期/时间待核实，不自动导出' : '原文时间明确 · 提前一天提醒'
          }}</small
          ><button :disabled="deadline.needsVerify" @click="calendar(i)">导出此节点</button>
        </div>
      </article>
      <article v-if="current.eligibility.status !== 'RED'">
        <h3>用自己的经历，写报名陈述</h3>
        <label v-for="entry in entries" :key="entry.id" class="check"
          ><input v-model="selected" type="checkbox" :value="entry.id" />{{ entry.project }}</label
        ><RouterLink v-if="!entries.length" to="/app/groups?tab=experience"
          >还没有经历，前往添加或载入示例 →</RouterLink
        ><button :disabled="busy" @click="statement">带入「我的经历」生成报名陈述</button
        ><textarea v-if="draft" v-model="draft" aria-label="报名陈述草稿" rows="7" /><button
          v-if="draft"
          @click="copy(draft)"
        >
          复制报名陈述</button
        ><RouterLink v-if="draft" to="/app/college/career">前往升学就业继续打磨 →</RouterLink>
      </article>
    </template>
    <p v-if="message" role="status">{{ message }}</p>
  </section>
</template>
<style scoped>
.notice-action {
  display: grid;
  gap: 16px;
  background: var(--bg-surface);
  color: var(--text-primary);
  padding: 18px;
  border: 1px solid var(--border-default);
  border-radius: 16px;
}
header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}
h2 {
  font-size: 20px;
}
h3 {
  font-size: 15px;
  margin-bottom: 10px;
}
small {
  display: block;
  color: var(--text-secondary);
  line-height: 1.6;
}
article {
  padding: 16px;
  border: 1px solid var(--border-default);
  border-radius: 12px;
}
p {
  line-height: 1.7;
  overflow-wrap: anywhere;
}
button {
  border: 1px solid var(--border-default);
  border-radius: 8px;
  background: var(--accent-soft);
  color: var(--text-primary);
  padding: 9px 12px;
  cursor: pointer;
  font-size: 13px;
}
button:disabled {
  opacity: 0.6;
  cursor: default;
}
.notice-examples,
.quick-profile {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.quick-profile label {
  flex: 1;
  min-width: 100px;
  font-size: 13px;
}
input:not([type='checkbox']),
textarea,
select {
  width: 100%;
  padding: 9px;
  background: var(--bg-input);
  color: var(--text-primary);
  border: 1px solid var(--border-default);
  border-radius: 8px;
}
textarea {
  margin: 10px 0;
  resize: vertical;
}
.GREEN {
  border-left: 5px solid var(--status-ready);
  background: var(--status-ready-bg);
}
.YELLOW,
.missing {
  border-left: 5px solid var(--status-warning);
  background: var(--status-warning-bg);
}
.RED {
  border-left: 5px solid var(--status-error);
  background: var(--status-error-bg);
}
mark {
  display: block;
  background: var(--accent-soft);
  color: var(--text-primary);
  margin: 8px 0;
  padding: 8px;
}
.check {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin: 10px 0;
}
.check input {
  width: auto;
  flex: none;
}
.deadline {
  margin: 12px 0;
  padding: 10px;
  border-left: 2px solid var(--border-default);
}
ul {
  padding-left: 18px;
}
.notice-label {
  color: var(--status-warning);
  font-size: 12px;
}
a {
  display: block;
  margin: 8px 0;
}
</style>
