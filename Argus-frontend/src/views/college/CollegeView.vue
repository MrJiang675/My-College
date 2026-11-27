<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import http from '@/api/http'
import { createAssistantSession, streamAssistantMessage } from '@/api/assistant'
import { streamAskQuestion } from '@/api/qa'
import { fetchGroups, type GroupItem } from '@/api/group'
import { useAuthStore } from '@/stores/auth'
import { moduleGuides, type ModuleGuideKey } from '@/constants/moduleGuides'
import ModuleGuideHeader from '@/components/ModuleGuideHeader.vue'
import EmptyExampleCards from '@/components/EmptyExampleCards.vue'
import ExperimentWorkspace from './ExperimentWorkspace.vue'
import { skill, validModel, valueAt, localExperiment, type Model } from './models'

interface Entry { id: string; project: string; text: string; source: string }
interface Turn { q: string; a: string; citations: string[] }
interface Page {
  draft: string
  source: string
  sourceName: string
  turns: Turn[]
  session?: number
  group?: number
  model?: Model
  previous?: Model
  checks?: { quote: string; claim: string; verdict: string }[]
  selected?: string[]
}

const names: Record<string, string> = {
  learn: '学习助手',
  practice: '专业实践',
  opps: '校园机会',
  project: '我的项目',
  campus: '校园办事',
  career: '升学就业',
  experience: '我的经历',
}
const route = useRoute()
const auth = useAuthStore()
const mod = computed(() => String(route.params.module || 'home'))
const home = computed(() => !names[mod.value])
const guideKey = computed<ModuleGuideKey>(() => names[mod.value] ? mod.value as ModuleGuideKey : 'college')

const state = ref({
  school: '',
  dept: '',
  major: '',
  grade: '',
  last: '',
  track: '实习',
  pages: {} as Record<string, Page>,
  entries: [] as Entry[],
  snapshots: [] as { title: string; model: Model; result: number }[],
})
const currentKey = computed(() => mod.value === 'career' ? 'career-' + state.value.track : mod.value)
function newPage(): Page { return { draft: '', source: '', sourceName: '', turns: [] } }
const page = ref<Page>(newPage())
function syncPage() {
  const key = currentKey.value
  page.value = state.value.pages[key] ??= newPage()
}
syncPage()

const loaded = ref(false)
const loadError = ref('')
const busy = ref(false)
const status = ref('')
const warning = ref('')
const conflictHint = ref('')
const profile = ref(false)
const groups = ref<GroupItem[]>([])
const editing = ref(false)
const project = ref('')
const contribution = ref('')
const evidence = ref('')
const live = ref('')
const draftInput = ref<HTMLTextAreaElement | null>(null)

let revision = 0
let dirty = false
let saving = false
let conflict = false
let closed = false
let timer: ReturnType<typeof setTimeout> | undefined
const controller = new AbortController()

async function flush() {
  if (saving || !loaded.value || conflict) return
  saving = true
  try {
    while (dirty) {
      dirty = false
      const payload = JSON.stringify({ revision, state: state.value })
      const { data } = await http.put('/college/workspace', payload, {
        headers: { 'Content-Type': 'application/json' },
      })
      revision = data.data
      conflictHint.value = ''
      status.value = '已同步'
    }
  } catch (error) {
    dirty = true
    conflict = (error as { response?: { status: number } }).response?.status === 409
    if (conflict) {
      status.value = '保存冲突'
      conflictHint.value = '另一设备已修改这份工作台。请先复制当前未保存内容，再刷新页面查看最新版本。'
    } else {
      status.value = '同步失败，点击重试'
    }
  } finally {
    saving = false
  }
}

watch(state, () => {
  if (!loaded.value) return
  dirty = true
  status.value = '待同步'
  clearTimeout(timer)
  timer = setTimeout(flush, 400)
}, { deep: true, flush: 'sync' })

watch(currentKey, () => {
  editing.value = false
  warning.value = ''
  syncPage()
})

function leave(event: BeforeUnloadEvent) {
  if (dirty || saving) {
    event.preventDefault()
    event.returnValue = ''
  }
}

async function loadWorkspace() {
  loadError.value = ''
  try {
    const { data } = await http.get('/college/workspace')
    revision = data.data.revision
    Object.assign(state.value, JSON.parse(data.data.payload))
    syncPage()
    loaded.value = true
    profile.value = !state.value.major
  } catch {
    loadError.value = '工作台读取失败，请检查登录状态后重试。'
  }
  fetchGroups().then((result) => groups.value = [...result.ownedGroups, ...result.joinedGroups]).catch(() => {})
}

const overviewOpen = ref(false)
const OVERVIEW_STORAGE_KEY = 'argus.college.overview.dismissed'
function dismissOverview(permanent = false) {
  overviewOpen.value = false
  if (permanent) localStorage.setItem(OVERVIEW_STORAGE_KEY, '1')
}

function reloadWorkspacePage() {
  window.location.reload()
}

function openContextAssistant() {
  if (!home.value) assistantOpen.value = true
}

onMounted(async () => {
  window.addEventListener('beforeunload', leave)
  window.addEventListener('argus:open-context-assistant', openContextAssistant)
  await loadWorkspace()
  if (home.value && localStorage.getItem(OVERVIEW_STORAGE_KEY) !== '1') overviewOpen.value = true
})

onUnmounted(() => {
  closed = true
  controller.abort()
  clearTimeout(timer)
  void flush()
  window.removeEventListener('beforeunload', leave)
  window.removeEventListener('argus:open-context-assistant', openContextAssistant)
})

async function upload(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 96000) {
    warning.value = '请选择小于 96 KB 的文本，较大讲义可上传到知识库。'
    return
  }
  const target = page.value
  target.source = await file.text()
  target.sourceName = file.name
}

function fillExample(text: string) {
  page.value.draft = text
  nextTick(() => draftInput.value?.focus())
}

function collect(text: string) {
  contribution.value = text
  project.value = ''
  evidence.value = ''
  editing.value = true
}

function archive() {
  if (!project.value.trim() || !contribution.value.trim()) return
  state.value.entries.unshift({
    id: crypto.randomUUID(),
    project: project.value.trim(),
    text: contribution.value.trim(),
    source: evidence.value.trim(),
  })
  editing.value = false
}

async function send() {
  const currentPage = page.value
  const question = currentPage.draft.trim()
  const module = mod.value
  if (!question || busy.value) return
  warning.value = ''
  busy.value = true
  state.value.last = module
  const turn: Turn = { q: question, a: '', citations: [] }
  try {
    const local = module === 'practice' && !currentPage.source ? localExperiment(question, currentPage.model) : undefined
    if (local) {
      turn.a = local.answer
      if (local.model) {
        currentPage.previous = currentPage.model
        currentPage.model = local.model
      }
    } else if (module === 'practice' || (module === 'learn' && !currentPage.group && currentPage.source.trim())) {
      const kind = module === 'practice' ? 'practice' : 'reading'
      const current = module === 'practice' ? currentPage.model : currentPage.turns.at(-1)?.q
      const { data } = await http.post('/college/' + kind, {
        question,
        source: currentPage.source,
        current: current ?? null,
      }, { timeout: 90000, signal: controller.signal })
      const action = data.data
      turn.a = action.answer
      if (module === 'practice') {
        if (validModel(action.model)) {
          currentPage.previous = currentPage.model
          currentPage.model = action.model
          currentPage.checks = []
        } else if (action.model) {
          throw Error('参数未通过校验，请核实单位。')
        }
        if (Array.isArray(action.checks)) {
          const original = currentPage.source || question
          currentPage.checks = action.checks.filter((item: { quote: string; claim: string; verdict: string }) =>
            typeof item?.quote === 'string' && item.quote.trim() && original.includes(item.quote) &&
            typeof item.claim === 'string' && typeof item.verdict === 'string',
          )
          if (currentPage.checks?.length) currentPage.model = undefined
        }
      } else {
        const quotes: string[] = Array.isArray(action.quotes)
          ? action.quotes.filter((item: unknown) => typeof item === 'string' && item.trim() && currentPage.source.includes(item))
          : []
        turn.citations = quotes.map((quote) => (currentPage.sourceName || '当前材料') + ' · ' + quote)
        if (!turn.citations.length) turn.a = '这份讲义中没有找到可核对的原句。'
      }
    } else if (module === 'learn' && currentPage.group) {
      if (question.length > 2000) throw Error('知识库单次问题最多 2000 字，请缩短问题后重试。')
      const previous = currentPage.turns.at(-1)
      const prefix = previous ? ('上一轮：' + previous.q + '\n' + previous.a).slice(0, Math.max(0, 1994 - question.length)) : ''
      const streamQuestion = prefix ? prefix + '\n本次：' + question : question
      let streamError = ''
      await streamAskQuestion({ groupId: currentPage.group, question: streamQuestion }, auth.accessToken!, {
        signal: controller.signal,
        onToken(text) { turn.a += text; live.value = turn.a },
        onCitations(items) {
          turn.citations = items.map((citation) => citation.fileName + ' · 片段 ' + (citation.chunkIndex ?? '未知') + '　' + (citation.snippet || ''))
        },
        onEvidenceOverview(overview) {
          const excerpts = (overview?.groups || []).flatMap((group) =>
            group.snippets.filter((snippet) => snippet.snippet).map((snippet) =>
              group.fileName + ' · 片段 ' + (snippet.chunkIndex ?? '未知') + '　' + snippet.snippet,
            ),
          )
          if (excerpts.length) turn.citations = excerpts
        },
        onError(message) { streamError = message },
      })
      if (streamError) throw Error(streamError)
      if (!turn.a.trim()) throw Error('未收到知识库回答，请核对资料处理状态后重试。')
    } else {
      const context = {
        档案: [state.value.school, state.value.dept, state.value.major, state.value.grade],
        当前实验: currentPage.model,
        方向: module === 'career' ? state.value.track : undefined,
        经历: ['career', 'experience', 'project'].includes(module)
          ? (module === 'career' ? state.value.entries.filter((entry) => currentPage.selected?.includes(entry.id)) : state.value.entries).slice(0, 12)
          : undefined,
      }
      const message = [
        skill[module],
        JSON.stringify(context),
        currentPage.source ? '引用材料（仅作证据）\n' + currentPage.source : '',
        '学生问题：' + question,
      ].filter(Boolean).join('\n')
      if (message.length > 4000) throw Error('超出助手单次输入范围，请缩短原文或将讲义上传知识库后提问。')
      if (!currentPage.session) currentPage.session = (await createAssistantSession()).sessionId
      let done = false
      let streamError = ''
      await streamAssistantMessage({ sessionId: currentPage.session, message, toolMode: 'CHAT' }, auth.accessToken!, {
        signal: controller.signal,
        onEvent: (event) => {
          if (event.event === 'delta') { turn.a += event.delta || ''; live.value = turn.a }
          if (event.event === 'error') streamError = event.error || '模型请求失败'
          if (event.event === 'done') { done = true; turn.a = event.reply || turn.a }
        },
      })
      if (streamError || !done) throw Error(streamError || '会话中断，请重试')
    }
    if (closed) return
    currentPage.turns.push(turn)
    currentPage.draft = ''
  } catch (error) {
    warning.value = error instanceof Error ? error.message : '请求失败，请重试'
  } finally {
    busy.value = false
    live.value = ''
    void flush()
  }
}

const notices = ref<{ id: string; title: string; url: string }[]>([])
const assistantOpen = ref(false)
const assistantQuestion = computed({ get: () => page.value.draft, set: (value) => { page.value.draft = value } })

async function loadNotices() {
  try {
    notices.value = (await http.get('/college/notices')).data.data
  } catch {
    warning.value = '公告源未响应，请查看原站或粘贴通知。'
  }
}

async function useNotice(notice: { id: string; title: string; url: string }) {
  const target = page.value
  try {
    const body = (await http.get('/college/notices/' + notice.id)).data.data
    target.source = body
    target.sourceName = notice.title + ' · ' + notice.url
  } catch {
    warning.value = '正文未能读取，请打开公告原文。'
  }
}

function snapshot() {
  const model = page.value.model
  if (!model) return
  state.value.snapshots.unshift({
    title: new Date().toLocaleString(),
    model: structuredClone(JSON.parse(JSON.stringify(model))),
    result: valueAt(model, 1),
  })
}
</script>

<template>
  <main class="college">
    <div v-if="warning || conflictHint || loadError" class="college-alert" role="alert">
      <span>{{ conflictHint || loadError || warning }}</span>
      <button v-if="loadError" type="button" @click="loadWorkspace">重试</button>
      <button v-else-if="conflictHint" type="button" @click="reloadWorkspacePage">刷新页面</button>
    </div>

    <div v-if="!loaded" class="college-loading" aria-live="polite">
      <span class="college-spinner" />
      <p>{{ loadError ? '暂时无法读取工作台' : '正在加载你的工作台…' }}</p>
    </div>

    <template v-else>
      <ModuleGuideHeader :guide-key="guideKey" eyebrow="Argus · 学生工作台">
        <template #actions>
          <button v-if="!home" type="button" class="btn-secondary" @click="assistantOpen = !assistantOpen">
            问助手
          </button>
          <button type="button" class="btn-secondary" @click="profile = !profile">
            {{ state.major || '填写专业' }} · 校园档案
          </button>
          <button v-if="home" type="button" class="btn-secondary" @click="overviewOpen = true">30 秒看懂</button>
        </template>
      </ModuleGuideHeader>

      <form v-if="profile" class="profile-card panel" @submit.prevent="profile = false; flush()">
        <div class="panel-heading">
          <div>
            <h2>校园档案</h2>
            <p>填写后，助手会在核对资格与要求时优先按你的情况提醒；其他专业功能仍可随时使用。</p>
          </div>
        </div>
        <div class="profile-grid">
          <label>学校<input v-model="state.school" required placeholder="你的学校"></label>
          <label>学院<input v-model="state.dept" placeholder="所在学院"></label>
          <label>专业<input v-model="state.major" required placeholder="所学专业"></label>
          <label>年级<input v-model="state.grade" placeholder="如：大三"></label>
        </div>
        <button class="btn-primary" type="submit">保存档案</button>
      </form>

      <template v-if="home">
        <section class="home-hero">
          <div>
            <span class="home-hero__eyebrow">今天也从一个小问题开始</span>
            <h2>{{ state.school || '你的校园' }}<span v-if="state.major"> · {{ state.major }}</span></h2>
            <p>学习、实践、校园事务和个人成长，都在这里接着做。</p>
          </div>
          <RouterLink v-if="state.last && names[state.last]" :to="'/app/college/' + state.last" class="resume-link">
            <small>接着上次做</small>
            <strong>{{ names[state.last] }}</strong>
            <span>继续 →</span>
          </RouterLink>
          <div v-else class="resume-link resume-link--empty">
            <small>还没有进行中的任务</small>
            <strong>从下面选一个入口</strong>
          </div>
        </section>

        <div class="home-grid">
          <section class="task-section" aria-label="工作台功能">
            <div class="section-heading">
              <div>
                <h2>去哪里？</h2>
                <p>按当前任务选择，不必一次做完。</p>
              </div>
            </div>
            <div class="task-grid">
              <RouterLink v-for="(name, key) in names" :key="key" :to="'/app/college/' + key" class="task-card">
                <span class="task-card__index">{{ String(Object.keys(names).indexOf(key) + 1).padStart(2, '0') }}</span>
                <strong>{{ name }}</strong>
                <small>{{ moduleGuides[key as ModuleGuideKey].subtitle }}</small>
                <b aria-hidden="true">↗</b>
              </RouterLink>
            </div>
          </section>
          <aside class="home-aside">
            <div class="profile-summary panel">
              <span class="profile-summary__icon">⌂</span>
              <h2>校园档案</h2>
              <dl>
                <div><dt>学校</dt><dd>{{ state.school || '待填写' }}</dd></div>
                <div><dt>学院</dt><dd>{{ state.dept || '待填写' }}</dd></div>
                <div><dt>专业</dt><dd>{{ state.major || '待填写' }}</dd></div>
                <div><dt>年级</dt><dd>{{ state.grade || '待填写' }}</dd></div>
              </dl>
              <button type="button" class="btn-secondary" @click="profile = true">完善档案</button>
            </div>
            <RouterLink to="/app/documents" class="shortcut-card">
              <span>课程资料</span>
              <strong>先把讲义入库</strong>
              <small>READY 后即可用于知识库问答 →</small>
            </RouterLink>
          </aside>
        </div>
      </template>

      <template v-else>
        <div class="career-tabs" v-if="mod === 'career'" role="tablist" aria-label="申请方向">
          <button
            v-for="track in ['实习', '升学']"
            :key="track"
            type="button"
            role="tab"
            :aria-selected="state.track === track"
            @click="state.track = track"
          >
            {{ track }}申请
          </button>
        </div>

        <details v-if="mod === 'opps'" class="source-panel panel">
          <summary>
            <span>公共来源 · 清华大学计算机系通知公告</span>
            <small>其他院系通知可直接粘贴</small>
          </summary>
          <div class="source-panel__actions">
            <button type="button" class="btn-secondary" @click="loadNotices">读取院系公告</button>
            <a href="https://www.cs.tsinghua.edu.cn/index/tzgg.htm" target="_blank" rel="noopener">查看原站 ↗</a>
          </div>
          <ul v-if="notices.length" class="notice-list">
            <li v-for="notice in notices" :key="notice.id">
              <a :href="notice.url" target="_blank" rel="noopener">{{ notice.title }}</a>
              <button type="button" @click="useNotice(notice)">引用正文</button>
            </li>
          </ul>
        </details>

        <div class="module-workspace" :class="{ 'is-practice': mod === 'practice' }">
          <div class="module-main">
            <section class="composer-card panel">
              <form @submit.prevent="send">
                <label v-if="mod === 'learn'" class="field">
                  <span>回答依据</span>
                  <select v-model="page.group">
                    <option :value="undefined">当前材料 / 课程辅导</option>
                    <option v-for="group in groups" :value="group.groupId" :key="group.groupId">{{ group.groupName }} · 知识库</option>
                  </select>
                  <small>{{ page.group ? '正在使用所选课程小组内已入库的文档。' : '正在使用你粘贴的短材料，引用会核对原句。' }}</small>
                </label>

                <details class="material-input">
                  <summary>
                    <span>{{ page.sourceName || '附上材料或粘贴原文' }}</span>
                    <small>TXT / MD，最大 96 KB</small>
                  </summary>
                  <input type="file" accept=".txt,.md" @change="upload">
                  <textarea v-model="page.source" aria-label="材料原文" placeholder="可核对的讲义、通知、采访记录或岗位要求"></textarea>
                  <RouterLink to="/app/documents">较大讲义请到文档管理入库 →</RouterLink>
                </details>

                <label class="field">
                  <span>说说你想做什么</span>
                  <textarea
                    ref="draftInput"
                    v-model="page.draft"
                    required
                    maxlength="3500"
                    aria-label="给助手的任务"
                    placeholder="输入问题、实验条件或通知原文…"
                  ></textarea>
                </label>
                <div class="composer-actions">
                  <small>{{ page.draft.length }} / 3500</small>
                  <button class="btn-primary" type="submit" :disabled="busy">
                    {{ busy ? '正在处理…' : '发送 →' }}
                  </button>
                </div>
              </form>
              <EmptyExampleCards
                v-if="!page.turns.length && !page.source && !page.draft"
                :guide-key="guideKey"
                :disabled="busy"
                @select="fillExample"
              />
            </section>

            <section v-if="mod === 'practice' && page.checks?.length" class="check-card panel" aria-labelledby="check-title">
              <div class="panel-heading">
                <div>
                  <span class="section-kicker">逐项对照</span>
                  <h2 id="check-title">采访核实</h2>
                </div>
              </div>
              <div class="check-table">
                <div class="check-table__head"><span>采访原话</span><span>报道表述</span><span>核实意见</span></div>
                <div v-for="row in page.checks" :key="row.quote + row.claim" class="check-row">
                  <blockquote>{{ row.quote }}</blockquote>
                  <strong>{{ row.claim }}</strong>
                  <p>{{ row.verdict }}</p>
                </div>
              </div>
            </section>

            <section v-if="mod === 'practice' && page.model" class="experiment-panel">
              <ExperimentWorkspace :current="page.model" :previous="page.previous" @snapshot="snapshot" />
            </section>

            <p v-if="busy" class="streaming-status" aria-live="polite">
              <span class="college-spinner" />{{ mod === 'practice' ? '正在理解实验条件…' : live || '正在整理回答…' }}
            </p>

            <section v-if="page.turns.length" class="turns" aria-label="对话记录">
              <article v-for="(turn, index) in [...page.turns].reverse()" :key="page.turns.length - index" class="turn panel">
                <div class="turn__question"><span>你</span><strong>{{ turn.q }}</strong></div>
                <div class="turn__answer"><span>Argus</span><p>{{ turn.a }}</p></div>
                <details v-if="turn.citations.length" class="citation-details">
                  <summary>
                    核对资料原文
                    <el-tooltip content="展开查看答案出自哪份讲义的哪一段，区分原文依据与待确认内容。" placement="top">
                      <span class="hint-dot" aria-hidden="true">?</span>
                    </el-tooltip>
                  </summary>
                  <p v-for="citation in turn.citations" :key="citation">{{ citation }}</p>
                </details>
                <el-tooltip content="经本人核对后收入我的经历，可在升学就业中直接勾选使用。" placement="top">
                  <button v-if="mod === 'project'" type="button" class="btn-secondary" @click="collect(turn.a)">编辑并收录经历</button>
                </el-tooltip>
              </article>
            </section>
          </div>

          <aside class="module-aside">
            <section v-if="['opps', 'campus'].includes(mod)" class="context-card panel">
              <span class="section-kicker">核对提醒</span>
              <h2>{{ mod === 'opps' ? '报名与综测' : '办事清单' }}</h2>
              <dl>
                <div><dt>对象</dt><dd>以通知原句为准</dd></div>
                <div><dt>材料</dt><dd>核对是否缺少附件</dd></div>
                <div><dt>时间</dt><dd>确认截止日期</dd></div>
                <div><dt>地点</dt><dd>确认办理渠道</dd></div>
              </dl>
              <el-tooltip content="通知原文未写明该项，请以学校或学院当期最新通知为准。" placement="left">
                <span class="status-badge status-badge--warning">需核实</span>
              </el-tooltip>
              <p v-if="mod === 'opps'">综测加分还需对照学院认定目录，缺少文件时请找老师核实。</p>
            </section>

            <section v-if="mod === 'project'" class="context-card panel">
              <span class="section-kicker">收录闭环</span>
              <h2>项目 → 经历</h2>
              <ol>
                <li>口语描述进展</li>
                <li>修改整理内容并附成果位置</li>
                <li>本人确认后收录</li>
              </ol>
              <button type="button" class="btn-secondary" @click="collect(page.draft)">记录个人贡献</button>
              <RouterLink to="/app/groups">协作小组 →</RouterLink>
            </section>

            <form v-if="editing" class="archive-card panel" @submit.prevent="archive">
              <h2>确认收录经历</h2>
              <label>项目名称<input v-model="project" required></label>
              <label>我的贡献<textarea v-model="contribution" required></textarea></label>
              <label>成果位置<input v-model="evidence" placeholder="仓库、报告或演示链接"></label>
              <button class="btn-primary" type="submit">确认收录</button>
            </form>

            <section v-if="mod === 'career' && state.entries.length" class="entry-picker panel">
              <h2>选择本次申请的经历</h2>
              <label v-for="item in state.entries" :key="item.id">
                <input
                  type="checkbox"
                  :checked="page.selected?.includes(item.id)"
                  @change="page.selected = page.selected?.includes(item.id) ? page.selected.filter((id) => id !== item.id) : [...(page.selected || []), item.id]"
                >
                <span><strong>{{ item.project }}</strong><small>{{ item.text }}</small></span>
              </label>
              <RouterLink v-if="!state.entries.length" to="/app/college/project">先到我的项目记录经历 →</RouterLink>
            </section>

            <section v-if="['experience', 'project'].includes(mod) && state.entries.length" class="entry-list panel">
              <h2>我的经历</h2>
              <article v-for="entry in state.entries" :key="entry.id">
                <strong>{{ entry.project }}</strong>
                <p>{{ entry.text }}</p>
                <small v-if="entry.source">{{ entry.source }}</small>
              </article>
            </section>

            <section v-if="mod === 'practice' && state.snapshots.length" class="snapshot-list panel">
              <h2>实验记录</h2>
              <button
                v-for="(snap, index) in state.snapshots"
                :key="index"
                type="button"
                @click="page.previous = page.model; page.model = snap.model"
              >
                <span>{{ snap.title }}</span>
                <strong>{{ snap.model.kind === 'rc' ? 'RC 充电' : '悬臂梁静载' }} · 恢复参数</strong>
              </button>
            </section>
          </aside>
        </div>
      </template>

      <aside v-if="assistantOpen && !home" class="drawer" aria-label="当前任务问助手">
        <div class="drawer__header">
          <div>
            <span class="section-kicker">上下文助手</span>
            <h2>{{ names[mod] }} · 问助手</h2>
          </div>
          <button type="button" aria-label="收起助手" @click="assistantOpen = false">×</button>
        </div>
        <p class="drawer__context">{{ page.sourceName || page.model?.kind || '当前任务' }}</p>
        <div v-for="(turn, index) in page.turns.slice(-2)" :key="index" class="drawer__turn">
          <strong>{{ turn.q }}</strong>
          <p>{{ turn.a }}</p>
        </div>
        <form @submit.prevent="send">
          <textarea required v-model="assistantQuestion" placeholder="继续追问当前任务"></textarea>
          <button class="btn-primary" type="submit" :disabled="busy">追问</button>
        </form>
      </aside>

      <button class="sync-status" type="button" :class="{ 'is-conflict': conflictHint }" @click="flush" aria-live="polite">
        <span class="sync-status__dot" />{{ status || '已同步' }}
      </button>
    </template>

    <el-dialog v-model="overviewOpen" title="30 秒看懂 Argus" width="560px" append-to-body>
      <div class="overview">
        <p class="overview__intro">不用一次学会所有功能，沿着一条路径就能开始。</p>
        <ol>
          <li><span>1</span><div><strong>先完善校园档案</strong><p>学校、学院、专业和年级会让提醒更贴合你。</p></div></li>
          <li><span>2</span><div><strong>学习或实践时直接问</strong><p>贴讲义、输入实验条件，或粘贴校园通知。</p></div></li>
          <li><span>3</span><div><strong>记得核对依据</strong><p>展开引用、查看实验读数，并处理“需核实”项。</p></div></li>
          <li><span>4</span><div><strong>把成果沉淀成经历</strong><p>项目贡献经本人确认后，可在实习或升学申请中复用。</p></div></li>
        </ol>
      </div>
      <template #footer>
        <button type="button" class="btn-secondary" @click="dismissOverview(true)">不再提示</button>
        <button type="button" class="btn-primary" @click="dismissOverview(false)">开始使用</button>
      </template>
    </el-dialog>
  </main>
</template>

<style scoped>
.college {
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
  color: var(--text-primary);
}
.college p { white-space: pre-wrap; overflow-wrap: anywhere; }
.panel {
  border: 1px solid var(--border-default);
  border-radius: var(--radius-md);
  background: var(--surface-white);
  box-shadow: var(--shadow-xs);
}
.btn-primary, .btn-secondary {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 38px;
  padding: 8px 15px;
  border-radius: var(--radius-sm);
  font-size: 0.84rem;
  font-weight: 700;
  transition: all 0.18s ease;
}
.btn-primary { background: var(--brand-primary); color: #fff; }
.btn-primary:hover:not(:disabled) { background: var(--brand-primary-dark); }
.btn-secondary { border: 1px solid var(--border-default); background: var(--surface-white); color: var(--text-secondary); }
.btn-secondary:hover { border-color: var(--brand-primary); background: var(--surface-accent); color: var(--brand-primary-dark); }
.btn-primary:disabled { opacity: 0.55; }
.section-kicker { display: block; margin-bottom: 4px; color: var(--brand-primary); font-size: 0.7rem; font-weight: 700; letter-spacing: 0.1em; }
.panel-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
.panel-heading h2, .context-card h2, .profile-summary h2, .archive-card h2, .entry-picker h2, .entry-list h2, .snapshot-list h2 { margin: 0; font-size: 1rem; }
.panel-heading p { margin: 5px 0 0; color: var(--text-secondary); font-size: 0.82rem; }
.college-alert { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 16px; padding: 12px 15px; border: 1px solid rgba(245, 158, 11, 0.25); border-radius: var(--radius-sm); background: var(--status-warning-bg); color: var(--status-warning); font-size: 0.84rem; }
.college-alert button { flex-shrink: 0; padding: 5px 10px; border: 1px solid currentColor; border-radius: 6px; background: transparent; color: inherit; }
.college-loading { display: grid; place-items: center; min-height: 320px; color: var(--text-secondary); }
.college-spinner { display: inline-block; width: 18px; height: 18px; border: 2px solid var(--border-default); border-top-color: var(--brand-primary); border-radius: 50%; animation: spin 0.7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.profile-card { margin-bottom: 24px; padding: 22px; }
.profile-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin-bottom: 16px; }
.profile-grid label, .field, .archive-card label { display: flex; flex-direction: column; gap: 6px; color: var(--text-secondary); font-size: 0.8rem; font-weight: 600; }
.college input, .college textarea, .college select {
  width: 100%;
  min-width: 0;
  padding: 10px 12px;
  border: 1px solid var(--border-default);
  border-radius: var(--radius-sm);
  background: var(--surface-white);
  color: var(--text-primary);
  font: inherit;
  font-size: 0.86rem;
  font-weight: 400;
  transition: border-color 0.18s ease, box-shadow 0.18s ease;
}
.college input:focus, .college textarea:focus, .college select:focus { border-color: var(--brand-primary); box-shadow: var(--focus-ring); outline: none; }
.college textarea { min-height: 92px; resize: vertical; line-height: 1.65; }
.home-hero { position: relative; display: flex; align-items: center; justify-content: space-between; gap: 24px; min-height: 180px; margin-bottom: 24px; padding: 30px 34px; overflow: hidden; border-radius: var(--radius-lg); background: linear-gradient(120deg, rgba(74, 144, 217, 0.96), rgba(58, 123, 200, 0.86)), url('./campus.jpg') center/cover; color: #fff; box-shadow: 0 12px 30px rgba(74, 144, 217, 0.16); }
.home-hero::after { content: ''; position: absolute; width: 260px; height: 260px; right: -60px; top: -120px; border: 1px solid rgba(255,255,255,.16); border-radius: 50%; }
.home-hero__eyebrow { font-size: 0.75rem; font-weight: 600; letter-spacing: 0.08em; opacity: 0.85; }
.home-hero h2 { margin: 8px 0; font-size: clamp(1.55rem, 3vw, 2.25rem); }
.home-hero h2 span { font-size: 0.72em; font-weight: 500; opacity: 0.9; }
.home-hero p { margin: 0; font-size: 0.88rem; opacity: 0.88; }
.resume-link { position: relative; z-index: 1; display: flex; flex-direction: column; gap: 4px; min-width: 210px; padding: 17px 19px; border: 1px solid rgba(255,255,255,.28); border-radius: var(--radius-md); background: rgba(255,255,255,.12); color: #fff; backdrop-filter: blur(8px); }
.resume-link small { font-size: 0.72rem; opacity: 0.8; }
.resume-link strong { font-size: 1rem; }
.resume-link span { margin-top: 6px; font-size: 0.78rem; font-weight: 700; }
.resume-link--empty { opacity: 0.85; }
.home-grid { display: grid; grid-template-columns: minmax(0, 1fr) 280px; gap: 22px; align-items: start; }
.section-heading { margin-bottom: 14px; }
.section-heading h2 { margin: 0; font-size: 1.08rem; }
.section-heading p { margin: 4px 0 0; color: var(--text-secondary); font-size: 0.82rem; }
.task-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 12px; }
.task-card { position: relative; display: flex; flex-direction: column; min-height: 132px; padding: 18px; border: 1px solid var(--border-default); border-radius: var(--radius-md); background: var(--surface-white); transition: all 0.18s ease; }
.task-card:hover { border-color: rgba(74,144,217,.45); box-shadow: var(--shadow-md); transform: translateY(-2px); }
.task-card__index { color: var(--brand-primary); font-family: 'JetBrains Mono', monospace; font-size: 0.7rem; }
.task-card strong { margin: 13px 0 5px; font-size: 0.98rem; }
.task-card small { color: var(--text-secondary); font-size: 0.76rem; line-height: 1.5; }
.task-card b { position: absolute; top: 16px; right: 16px; color: var(--brand-primary); }
.home-aside { display: grid; gap: 14px; }
.profile-summary { padding: 20px; }
.profile-summary__icon { display: grid; place-items: center; width: 38px; height: 38px; margin-bottom: 12px; border-radius: 10px; background: var(--surface-accent); color: var(--brand-primary); font-size: 1.2rem; }
.profile-summary dl { display: grid; gap: 9px; margin: 16px 0; }
.profile-summary dl div { display: flex; justify-content: space-between; gap: 10px; font-size: 0.78rem; }
.profile-summary dt { color: var(--text-muted); }
.profile-summary dd { margin: 0; color: var(--text-secondary); text-align: right; }
.shortcut-card { display: flex; flex-direction: column; gap: 5px; padding: 18px; border-radius: var(--radius-md); background: #eaf3fd; color: var(--brand-primary-dark); }
.shortcut-card span { font-size: 0.72rem; }
.shortcut-card strong { font-size: 0.92rem; }
.shortcut-card small { color: var(--text-secondary); font-size: 0.73rem; }
.career-tabs { display: inline-flex; gap: 4px; margin-bottom: 18px; padding: 4px; border-radius: var(--radius-sm); background: var(--surface-muted); }
.career-tabs button { padding: 8px 20px; border-radius: 6px; background: transparent; color: var(--text-secondary); font-size: 0.84rem; font-weight: 600; }
.career-tabs button[aria-selected=true] { background: var(--surface-white); color: var(--brand-primary-dark); box-shadow: var(--shadow-xs); }
.source-panel { margin-bottom: 18px; padding: 16px 18px; }
.source-panel summary { display: flex; justify-content: space-between; gap: 12px; cursor: pointer; font-size: 0.86rem; font-weight: 700; list-style: none; }
.source-panel summary small { color: var(--text-muted); font-size: 0.75rem; font-weight: 400; }
.source-panel__actions { display: flex; align-items: center; gap: 14px; margin-top: 14px; }
.source-panel__actions a { color: var(--brand-primary); font-size: 0.8rem; }
.notice-list { display: grid; gap: 8px; margin-top: 14px; }
.notice-list li { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding-top: 9px; border-top: 1px solid var(--border-subtle); font-size: 0.8rem; }
.notice-list a { min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.notice-list button { padding: 5px 9px; border-radius: 6px; background: var(--surface-accent); color: var(--brand-primary-dark); }
.module-workspace { display: grid; grid-template-columns: minmax(0, 1fr); gap: 18px; align-items: start; }
.module-workspace.is-practice { grid-template-columns: minmax(0, 1fr) minmax(360px, 0.9fr); }
.module-main, .module-aside { display: grid; gap: 16px; min-width: 0; }
.composer-card { padding: 22px; }
.composer-card form { display: grid; gap: 16px; }
.field small { color: var(--text-muted); font-size: 0.74rem; font-weight: 400; }
.material-input { padding: 12px 14px; border: 1px solid var(--border-default); border-radius: var(--radius-sm); background: var(--surface-subtle); }
.material-input summary { display: flex; justify-content: space-between; gap: 12px; cursor: pointer; color: var(--text-secondary); font-size: 0.8rem; list-style: none; }
.material-input summary small { color: var(--text-muted); font-size: 0.72rem; }
.material-input[open] summary { margin-bottom: 12px; }
.material-input input[type=file] { padding: 7px; font-size: 0.76rem; }
.material-input a { display: inline-block; color: var(--brand-primary); font-size: 0.76rem; }
.composer-actions { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.composer-actions small { color: var(--text-muted); font-size: 0.72rem; }
.streaming-status { display: flex; align-items: center; gap: 9px; padding: 12px 14px; border-radius: var(--radius-sm); background: var(--surface-accent); color: var(--brand-primary-dark); font-size: 0.82rem; }
.turns { display: grid; gap: 14px; }
.turn { padding: 20px; }
.turn__question, .turn__answer { display: grid; grid-template-columns: 48px minmax(0, 1fr); gap: 12px; }
.turn__question > span, .turn__answer > span { display: grid; place-items: center; width: 42px; height: 28px; border-radius: 7px; background: var(--surface-muted); color: var(--text-secondary); font-size: 0.72rem; font-weight: 700; }
.turn__answer { margin-top: 16px; padding-top: 16px; border-top: 1px solid var(--border-subtle); }
.turn__answer > span { background: var(--surface-accent); color: var(--brand-primary-dark); }
.turn__question strong, .turn__answer p { font-size: 0.88rem; line-height: 1.75; }
.turn__answer p { margin: 0; }
.citation-details { margin: 16px 0 0 60px; padding: 11px 13px; border-radius: var(--radius-sm); background: var(--surface-subtle); font-size: 0.8rem; }
.citation-details summary { display: flex; align-items: center; gap: 7px; cursor: pointer; color: var(--brand-primary-dark); font-weight: 700; list-style: none; }
.citation-details p { margin-top: 9px; color: var(--text-secondary); }
.hint-dot { display: inline-grid; place-items: center; width: 15px; height: 15px; border: 1px solid var(--text-muted); border-radius: 50%; color: var(--text-muted); font-size: 0.65rem; }
.turn > .btn-secondary { margin: 14px 0 0 60px; }
.context-card, .archive-card, .entry-picker, .entry-list, .snapshot-list { padding: 20px; }
.context-card dl { display: grid; gap: 9px; margin: 16px 0; }
.context-card dl div { display: flex; justify-content: space-between; gap: 10px; font-size: 0.78rem; }
.context-card dt { color: var(--text-muted); }
.context-card dd { margin: 0; color: var(--text-secondary); text-align: right; }
.context-card p, .context-card ol { margin: 12px 0; color: var(--text-secondary); font-size: 0.78rem; line-height: 1.65; }
.context-card ol { padding-left: 18px; list-style: decimal; }
.context-card > a { display: block; margin-top: 12px; color: var(--brand-primary); font-size: 0.8rem; }
.archive-card { display: grid; gap: 12px; }
.entry-picker { display: grid; gap: 10px; }
.entry-picker label { display: flex; align-items: flex-start; gap: 9px; padding: 10px; border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); }
.entry-picker input { width: 16px; margin-top: 3px; accent-color: var(--brand-primary); }
.entry-picker label span { display: grid; gap: 3px; min-width: 0; }
.entry-picker strong { font-size: 0.8rem; }
.entry-picker small { color: var(--text-secondary); font-size: 0.74rem; line-height: 1.5; }
.entry-list article { padding: 12px 0; border-top: 1px solid var(--border-subtle); }
.entry-list article:first-of-type { margin-top: 12px; }
.entry-list p { margin: 5px 0; color: var(--text-secondary); font-size: 0.8rem; }
.entry-list small { color: var(--text-muted); font-size: 0.72rem; }
.snapshot-list { display: grid; gap: 9px; }
.snapshot-list button { display: grid; gap: 3px; padding: 10px; border: 1px solid var(--border-subtle); border-radius: var(--radius-sm); background: var(--surface-subtle); text-align: left; }
.snapshot-list button span { color: var(--text-muted); font-size: 0.68rem; }
.snapshot-list button strong { color: var(--text-secondary); font-size: 0.76rem; }
.check-card { padding: 20px; }
.check-table { display: grid; border: 1px solid var(--border-default); border-radius: var(--radius-sm); overflow: hidden; }
.check-table__head, .check-row { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); }
.check-table__head { background: var(--surface-subtle); color: var(--text-muted); font-size: 0.72rem; font-weight: 700; }
.check-table__head span, .check-row > * { padding: 12px; border-right: 1px solid var(--border-default); }
.check-table__head span:last-child, .check-row > *:last-child { border-right: 0; }
.check-row { border-top: 1px solid var(--border-default); }
.check-row blockquote { margin: 0; color: var(--brand-primary-dark); background: var(--surface-accent); font-size: 0.78rem; line-height: 1.6; }
.check-row strong { color: var(--text-secondary); font-size: 0.78rem; line-height: 1.6; }
.check-row p { margin: 0; color: var(--text-secondary); font-size: 0.78rem; line-height: 1.6; }
.drawer { position: fixed; z-index: 45; top: 64px; right: 0; bottom: 0; width: min(420px, 95vw); padding: 22px; overflow-y: auto; border-left: 1px solid var(--border-default); background: var(--surface-white); box-shadow: -12px 0 36px rgba(30, 41, 59, 0.12); }
.drawer__header { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.drawer__header h2 { margin: 0; font-size: 1.05rem; }
.drawer__header button { width: 32px; height: 32px; border-radius: 8px; background: var(--surface-muted); color: var(--text-secondary); font-size: 1.2rem; }
.drawer__context { margin: 12px 0 18px; padding: 9px 11px; border-radius: var(--radius-sm); background: var(--surface-accent); color: var(--brand-primary-dark); font-size: 0.76rem; }
.drawer__turn { margin-bottom: 14px; padding: 12px; border-radius: var(--radius-sm); background: var(--surface-subtle); }
.drawer__turn strong { font-size: 0.8rem; }
.drawer__turn p { margin: 7px 0 0; color: var(--text-secondary); font-size: 0.78rem; }
.drawer form { display: grid; gap: 10px; }
.sync-status { position: fixed; right: 24px; bottom: 20px; z-index: 20; display: inline-flex; align-items: center; gap: 7px; padding: 8px 12px; border: 1px solid var(--border-default); border-radius: 999px; background: rgba(255,255,255,.94); color: var(--text-secondary); box-shadow: var(--shadow-sm); font-size: 0.72rem; }
.sync-status__dot { width: 6px; height: 6px; border-radius: 50%; background: var(--status-ready); }
.sync-status.is-conflict .sync-status__dot { background: var(--status-warning); }
.overview__intro { margin: 0 0 18px; color: var(--text-secondary); font-size: 0.88rem; }
.overview ol { display: grid; gap: 15px; margin: 0; padding: 0; list-style: none; }
.overview li { display: flex; align-items: flex-start; gap: 12px; }
.overview li > span { display: grid; place-items: center; width: 28px; height: 28px; flex-shrink: 0; border-radius: 8px; background: var(--surface-accent); color: var(--brand-primary); font-size: 0.78rem; font-weight: 700; }
.overview strong { font-size: 0.88rem; }
.overview p { margin: 3px 0 0; color: var(--text-secondary); font-size: 0.8rem; }
.college :deep(svg) { max-height: 260px; }
.college :deep(input[type=range]) { width: 100%; accent-color: var(--brand-primary); }
@media (max-width: 1100px) {
  .home-grid { grid-template-columns: minmax(0, 1fr); }
  .home-aside { grid-template-columns: 1fr 1fr; }
  .module-workspace.is-practice { grid-template-columns: minmax(0, 1fr); }
  .task-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
}
@media (max-width: 760px) {
  .profile-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .home-hero { align-items: flex-start; flex-direction: column; padding: 24px; }
  .resume-link { width: 100%; min-width: 0; }
  .home-aside { grid-template-columns: minmax(0, 1fr); }
  .source-panel summary { flex-direction: column; gap: 4px; }
  .check-table__head { display: none; }
  .check-row { grid-template-columns: minmax(0, 1fr); }
  .check-row > * { border-right: 0; border-bottom: 1px solid var(--border-default); }
  .check-row > *:last-child { border-bottom: 0; }
}
@media (max-width: 520px) {
  .profile-grid, .task-grid { grid-template-columns: minmax(0, 1fr); }
  .composer-card, .profile-card { padding: 16px; }
  .material-input summary { flex-direction: column; gap: 4px; }
  .citation-details, .turn > .btn-secondary { margin-left: 0; }
  .turn__question, .turn__answer { grid-template-columns: 38px minmax(0, 1fr); gap: 9px; }
  .turn__question > span, .turn__answer > span { width: 36px; }
  .sync-status { right: 12px; bottom: 12px; }
}
</style>
