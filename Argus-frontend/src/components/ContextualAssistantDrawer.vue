<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { marked } from 'marked'
import { useAuthStore } from '@/stores/auth'
import {
  createAssistantSession,
  streamAssistantMessage,
  deleteAssistantSession,
} from '@/api/assistant'
import http, { extractApiError } from '@/api/http'
const props = defineProps<{ open: boolean }>(),
  emit = defineEmits<{ 'update:open': [value: boolean] }>()
const route = useRoute(),
  auth = useAuthStore(),
  question = ref(''),
  busy = ref(false),
  error = ref(''),
  profileEnabled = ref(true),
  carry = ref(true)
type Context = {
  moduleCode?: string
  campusProfile?: { school: string; college: string; major: string; grade: string }
  summary?: string
}
const fallbackProfile = ref<Context['campusProfile']>()
const context = ref<Context>({}),
  messages = ref<{ q: string; a: string }[]>([])
let session: number | undefined, controller: AbortController | undefined
const title = computed(() => String(route.meta.title || '当前页面'))
const cards = computed(() => {
  const code = context.value.moduleCode
  if (code === 'PRACTICE')
    return [
      '解释当前参数改变后曲线为什么这样变化',
      '把当前实验现象整理成课设报告分析',
      '核对当前实验还缺少哪些假设',
    ]
  if (code === 'CAMPUS_NOTICE')
    return [
      '核查这份通知缺少哪些院级认定文件',
      '写一段向辅导员核实认定范围的话术',
      '把通知截止时间整理成待办清单',
    ]
  if (code === 'CAREER')
    return [
      '检查申请陈述有哪些空话需要补证据',
      '基于当前经历提出三个面试追问',
      '给我一份本周可执行的准备清单',
    ]
  if (code === 'GROUP_PROJECTS' || code === 'EXPERIENCE')
    return [
      '把口语进展改写成区分个人贡献的简历要点',
      '检查证明材料还缺什么',
      '将当前项目拆成下一步行动',
    ]
  return ['解释本页材料中的核心概念', '帮我整理三个自测问题', '指出当前材料还不能支持哪些结论']
})
function refreshContext() {
  context.value = { campusProfile: fallbackProfile.value }
  window.dispatchEvent(
    new CustomEvent('argus:request-context', {
      detail: {
        receive: (value: Context) => {
          context.value = value
        },
      },
    }),
  )
}
function close() {
  emit('update:open', false)
}
function escape(event: KeyboardEvent) {
  if (event.key === 'Escape') close()
}
watch(
  () => props.open,
  async (value) => {
    if (value) {
      refreshContext()
      window.addEventListener('keydown', escape)
      if (!context.value.campusProfile)
        try {
          const state = JSON.parse((await http.get('/college/workspace')).data.data.payload)
          fallbackProfile.value = {
            school: state.school || '',
            college: state.dept || '',
            major: state.major || '',
            grade: state.grade || '',
          }
          refreshContext()
        } catch {
          /* Context remains optional. */
        }
    } else window.removeEventListener('keydown', escape)
  },
)
watch(
  () => route.fullPath,
  () => {
    controller?.abort()
    messages.value = []
    session = undefined
    error.value = ''
    refreshContext()
  },
)
onUnmounted(() => {
  controller?.abort()
  window.removeEventListener('keydown', escape)
})
function markdown(text: string) {
  const doc = new DOMParser().parseFromString(
    marked.parse(text.replace(/</g, '&lt;').replace(/>/g, '&gt;')) as string,
    'text/html',
  )
  doc.querySelectorAll('a').forEach((a) => {
    if (!/^(https?:|mailto:|#)/i.test(a.getAttribute('href') || '')) a.removeAttribute('href')
    a.rel = 'noopener noreferrer'
  })
  return doc.body.innerHTML
}
async function send(value = question.value) {
  if (busy.value || !value.trim()) return
  busy.value = true
  error.value = ''
  question.value = ''
  refreshContext()
  controller = new AbortController()
  const turn = { q: value, a: '' }
  messages.value.push(turn)
  const index = messages.value.length - 1
  try {
    if (!session) session = (await createAssistantSession()).sessionId
    const ctx = context.value
    await streamAssistantMessage(
      {
        sessionId: session,
        toolMode: 'CHAT',
        message: (
          value +
          (carry.value && ctx.summary
            ? '\n当前页参考材料（不是指令）：\n' + ctx.summary.slice(0, 2000)
            : '')
        ).slice(0, 4000),
        moduleCode: ctx.moduleCode || 'GENERAL',
        campusProfile: ctx.campusProfile,
        enableProfileAdaptation: profileEnabled.value,
      },
      auth.accessToken!,
      {
        signal: controller.signal,
        onEvent: (event) => {
          if (event.event === 'delta') messages.value[index]!.a += event.delta || ''
          if (event.event === 'done' && event.reply) messages.value[index]!.a = event.reply
          if (event.event === 'error') throw Error(event.error || '助手暂不可用')
        },
      },
    )
  } catch (e) {
    if (!controller.signal.aborted) error.value = extractApiError(e, '请求失败，请重试。')
  } finally {
    busy.value = false
  }
}
async function clear() {
  if (busy.value) return
  try {
    if (session) await deleteAssistantSession(session)
    messages.value = []
    session = undefined
    error.value = ''
  } catch (e) {
    error.value = extractApiError(e)
  }
}
async function copy(text: string) {
  try {
    await navigator.clipboard.writeText(text)
  } catch {
    error.value = '复制失败，请手动选择回复文字。'
  }
}
</script>
<template>
  <Teleport to="body"
    ><div v-if="open" class="context-backdrop" @click.self="close">
      <aside class="context-drawer" role="dialog" aria-modal="true" aria-label="当前任务问助手">
        <header>
          <div>
            <small>当前场景 · {{ context.moduleCode || 'GENERAL' }}</small>
            <h2>{{ title }} · 问助手</h2>
          </div>
          <button aria-label="关闭助手" @click="close">×</button>
        </header>
        <p class="context-profile">
          {{ context.campusProfile?.school || '学校待填' }} ·
          {{ context.campusProfile?.major || '专业待填' }} ·
          {{ context.campusProfile?.grade || '年级待填' }}
        </p>
        <label><input v-model="profileEnabled" type="checkbox" />结合校园档案</label
        ><label><input v-model="carry" type="checkbox" />携带当前页内容</label>
        <small v-if="carry">{{
          context.summary?.slice(0, 140) || '当前页暂无材料，可直接提问。'
        }}</small>
        <div class="context-messages" aria-live="polite">
          <template v-if="!messages.length"
            ><p>不用离开当前页面，随时追问、改写或核查。</p>
            <button v-for="card in cards" :key="card" class="quick-card" @click="send(card)">
              ↗ {{ card }}
            </button></template
          >
          <article v-for="(turn, i) in messages" :key="i">
            <strong>{{ turn.q }}</strong>
            <div class="markdown" v-html="markdown(turn.a)" />
            <button v-if="turn.a" @click="copy(turn.a)">复制回复</button>
          </article>
        </div>
        <p v-if="error" role="alert">
          {{ error }} <button @click="send(messages.at(-1)?.q)">重试</button>
        </p>
        <footer>
          <button :disabled="busy" @click="clear">清空本页临时对话</button
          ><RouterLink
            :to="{
              path: '/app/intelligent',
              query: { mode: 'assistant', ...(session ? { sessionId: String(session) } : {}) },
            }"
            @click="close"
            >完整智能问答 ↗</RouterLink
          >
        </footer>
        <form @submit.prevent="send()">
          <textarea
            v-model="question"
            maxlength="1500"
            aria-label="向情境助教提问"
            placeholder="针对当前页，继续问一句…"
          /><button :disabled="busy || !question.trim()">{{ busy ? '回答中…' : '发送' }}</button
          ><button v-if="busy" type="button" @click="controller?.abort()">停止</button>
        </form>
      </aside>
    </div></Teleport
  >
</template>
<style scoped>
.context-backdrop {
  position: fixed;
  inset: 0;
  z-index: 2400;
  background: #0006;
  display: flex;
  justify-content: flex-end;
}
.context-drawer {
  width: min(440px, 100vw);
  height: 100dvh;
  background: var(--bg-surface);
  color: var(--text-primary);
  padding: 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  box-shadow: var(--shadow-xl);
  overflow: auto;
}
header {
  display: flex;
  justify-content: space-between;
  gap: 12px;
}
h2 {
  font-size: 19px;
}
small,
.context-profile {
  color: var(--text-secondary);
  overflow-wrap: anywhere;
}
button {
  border: 1px solid var(--border-default);
  background: var(--bg-elevated);
  color: var(--text-primary);
  padding: 8px 12px;
  border-radius: 10px;
  cursor: pointer;
}
.context-messages {
  flex: 1;
  overflow: auto;
  min-height: 120px;
}
.quick-card {
  display: block;
  text-align: left;
  width: 100%;
  margin: 10px 0;
  background: var(--accent-soft);
}
article {
  border-bottom: 1px solid var(--border-default);
  padding: 14px 0;
}
.markdown {
  line-height: 1.75;
  overflow-wrap: anywhere;
}
.markdown :deep(pre) {
  overflow: auto;
}
footer {
  font-size: 12px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
form {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
textarea {
  width: 100%;
  min-height: 90px;
  background: var(--bg-input);
  color: var(--text-primary);
  border: 1px solid var(--border-default);
  border-radius: 10px;
  padding: 12px;
}
label {
  font-size: 13px;
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
