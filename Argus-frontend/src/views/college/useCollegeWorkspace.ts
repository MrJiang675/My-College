import { demoSamples, type DemoSample, type DemoPreset } from '@/constants/demoSamples'
import { presetMode, useDemoPresetReplay } from '@/composables/useDemoPresetReplay'
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, useRoute } from 'vue-router'
import http, { extractApiError } from '@/api/http'
import { createAssistantSession, streamAssistantMessage } from '@/api/assistant'
import { streamAskQuestion } from '@/api/qa'
import { fetchGroups, type GroupItem } from '@/api/group'
import { useAuthStore } from '@/stores/auth'
import { moduleGuides, type ModuleGuideKey } from '@/constants/moduleGuides'
import {
  defaultModel,
  experimentPlugins,
  skill,
  validModel,
  valueAt,
  localExperiment,
  type Model,
} from './models'
export function useCollegeWorkspace(props: { moduleOverride?: string; previewMode?: boolean }) {
  interface Entry {
    id: string
    project: string
    text: string
    source: string
    type?: string
    team?: string
    tags?: string[]
    groupId?: number
  }
  interface Turn {
    q: string
    a: string
    citations: string[]
    badge?: string
  }
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
    learningMode?: string
    publishedAt?: string
    applicationDraft?: string
    requirementStatuses?: Record<string, string>
    sampleId?: string
    sampleOriginal?: string
  }

  const names: Record<string, string> = {
    project: '我的项目',
    learn: '学习助手',
    practice: '专业实践',
    opps: '校园机会与办事',
    career: '升学就业',
    experience: '我的经历',
  }

  const route = useRoute()
  const auth = useAuthStore()
  const mod = computed(() => props.moduleOverride || String(route.params.module || 'home'))
  const home = computed(() => !names[mod.value] && mod.value !== 'project')
  const guideKey = computed<ModuleGuideKey>(() =>
    names[mod.value] || mod.value === 'project' ? (mod.value as ModuleGuideKey) : 'college',
  )

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
  const adaptProfile = ref(true)
  const moduleCodes: Record<string, string> = {
    learn: 'LEARNING',
    practice: 'PRACTICE',
    opps: 'CAMPUS_NOTICE',
    project: 'GROUP_PROJECTS',
    experience: 'EXPERIENCE',
    career: 'CAREER',
  }
  const currentKey = computed(() =>
    mod.value === 'career'
      ? 'career-' + state.value.track
      : mod.value === 'opps' && route.query.view === 'affairs'
        ? 'campus'
        : mod.value,
  )
  function newPage(): Page {
    return { draft: '', source: '', sourceName: '', turns: [] }
  }
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
  const team = ref(''),
    entryType = ref('课程设计'),
    entryGroup = ref<number>()
  const live = ref('')
  const draftInput = ref<HTMLTextAreaElement | null>(null)
  const materialInput = ref<HTMLInputElement | null>(null)

  let revision = 0
  let dirty = false
  let saving = false
  let conflict = false
  let closed = false
  let timer: ReturnType<typeof setTimeout> | undefined
  const controller = new AbortController()

  async function flush() {
    if (props.previewMode) {
      sessionStorage.setItem('argus-preview-workspace', JSON.stringify(state.value))
      dirty = false
      status.value = '预览已保存在本窗口'
      return
    }
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
        conflictHint.value =
          '另一设备已修改这份工作台。请先复制当前未保存内容，再刷新页面查看最新版本。'
      } else {
        status.value = '同步失败，点击重试'
      }
    } finally {
      saving = false
    }
  }

  watch(
    state,
    () => {
      if (!loaded.value) return
      window.dispatchEvent(
        new CustomEvent('argus:workspace-progress', {
          detail: {
            school: state.value.school,
            major: state.value.major,
            grade: state.value.grade,
            entries: state.value.entries.length,
            complete:
              [state.value.school, state.value.dept, state.value.major, state.value.grade].filter(
                Boolean,
              ).length * 25,
          },
        }),
      )
      dirty = true
      status.value = '待同步'
      clearTimeout(timer)
      timer = setTimeout(flush, 400)
    },
    { deep: true, flush: 'sync' },
  )

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
    if (props.previewMode) {
      try {
        Object.assign(
          state.value,
          JSON.parse(sessionStorage.getItem('argus-preview-workspace') || '{}'),
        )
      } catch {
        /* Fresh preview */
      }
      syncPage()
      loaded.value = true
      return
    }
    loadError.value = ''
    try {
      const { data } = await http.get('/college/workspace')
      revision = data.data.revision
      Object.assign(state.value, JSON.parse(data.data.payload))
      syncPage()
      loaded.value = true
      profile.value = !state.value.major || route.query.profile === '1'
    } catch {
      loadError.value = '工作台读取失败，请检查登录状态后重试。'
    }
    fetchGroups()
      .then((result) => (groups.value = [...result.ownedGroups, ...result.joinedGroups]))
      .catch(() => {})
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
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    if (!file) return
    if (!/\.(txt|md)$/i.test(file.name)) {
      warning.value = '当前材料仅支持 TXT 或 MD 文件。'
      input.value = ''
      return
    }
    if (file.size > 2 * 1024 * 1024) {
      warning.value = '内联材料最多 2 MB；较大讲义请使用下方“上传到知识库”。'
      input.value = ''
      return
    }
    try {
      const text = await file.text()
      if (text.length > 16000) {
        warning.value = '内联材料最多 16000 字；较大讲义请使用下方“上传到知识库”。'
        input.value = ''
        return
      }
      page.value.source = text
      page.value.sourceName = file.name
      warning.value = ''
    } catch {
      warning.value = '文件读取失败，请重试或直接粘贴原文。'
    } finally {
      input.value = ''
    }
  }

  function loadInterviewExample() {
 page.value.source='受访学生甲说：“这周我等班车比上周久。”受访学生乙说：“我今天等了约五分钟。”'
 page.value.sourceName='采访核实内置示例'
 page.value.model=undefined
 page.value.checks=[
  {quote:'这周我等班车比上周久。',claim:'一名受访学生表示本周个人候车时间增加。',verdict:'表述保留个人范围，可核对原句。'},
  {quote:'我今天等了约五分钟。',claim:'全校候车时间普遍延长。',verdict:'需补充多时段班次记录和更广泛的调查。'}
 ]
}
function removeMaterial() {
    page.value.source = ''
    page.value.sourceName = ''
    warning.value = ''
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

  function loadGrowthSample() {
    const sampleIds = ['sample-design', 'sample-contest']
    if (state.value.entries.some((e) => sampleIds.includes(e.id))) return
    state.value.entries.push(
      {
        id: sampleIds[0]!,
        project: '环境数据采集课程设计（示例）',
        text: '本人负责传感器标定、误差记录与测试脚本。',
        source: '示例报告：实际使用请附成果位置。',
        type: '课程设计',
        team: '三人团队完成系统集成',
        tags: ['实验', '数据处理'],
      },
      {
        id: sampleIds[1]!,
        project: '校园出行调查竞赛（示例）',
        text: '本人负责问卷清洗和图表呈现，调研范围为两个院系。',
        source: '缺少公开获奖证明，需补充。',
        type: '竞赛',
        team: '团队完成调研报告',
        tags: ['调研', '表达'],
      },
    )
    const career = (state.value.pages['career-实习'] ??= newPage())
    career.source =
      '岗位要求：能够清洗数据，设计实验对照，并用报告解释结果。请提供可核验的项目证明。'
    career.sourceName = '数据分析助理岗位（内置示例）'
    career.selected = sampleIds
  }
  async function resetDemoWorkspace() {
    if (!window.confirm('重置当前体验空间的草稿、快照和经历？')) return
    state.value.pages = {}
    state.value.entries = []
    state.value.snapshots = []
    state.value.last = ''
    syncPage()
    await flush()
  }
  function clearGrowthSample() {
    state.value.entries = state.value.entries.filter(
      (e) => !['sample-design', 'sample-contest'].includes(e.id),
    )
    for (const p of Object.values(state.value.pages))
      p.selected = p.selected?.filter((id) => !id.startsWith('sample-'))
    const career = state.value.pages['career-实习']
    if (career?.sourceName === '数据分析助理岗位（内置示例）') {
      career.source = ''
      career.sourceName = ''
    }
  }
  function extractProjectDraft() {
    const text = page.value.source.trim() || page.value.draft.trim()
    if (!text) {
      warning.value = '请先粘贴群聊汇报或进展记录。'
      return
    }
    collect(text)
    project.value = (text.match(/项目(?:名称)?[：:]([^。\n]+)/)?.[1] || '待确认项目').trim()
    contribution.value = (
      text.match(/(?:本人|我)(?:负责|完成)[：:]?([^。\n]+)/)?.[0] || text
    ).trim()
    evidence.value = text.match(/https?:\/\/[^\s]+/)?.[0] || ''
  }
  function archive() {
    if (!project.value.trim() || !contribution.value.trim()) return
    state.value.entries.unshift({
      id: crypto.randomUUID(),
      project: project.value.trim(),
      text: contribution.value.trim(),
      source: evidence.value.trim(),
      type: entryType.value,
      team: team.value.trim(),
      tags: [],
      groupId: entryGroup.value,
    })
    editing.value = false
    status.value = '已收录，可在我的经历查看'
  }

  const replayEngine = useDemoPresetReplay()
  async function loadDemoSample(sample: DemoSample, file: File, preset?: DemoPreset) {
    const text = await file.text()
    page.value.source = text
    page.value.sourceName = sample.fileName
    page.value.publishedAt = sample.id === 'notice' ? '2026-11-27（内置示例）' : undefined
    page.value.sampleId = sample.id
    page.value.sampleOriginal = text
    if (preset) {
      page.value.draft = preset.question
      await send()
    }
  }
  function matchingPreset(current: Page, question: string) {
    if (current.source !== current.sampleOriginal) return undefined
    return demoSamples
      .find((s) => s.id === current.sampleId)
      ?.presets.find((p) => p.question.trim() === question.trim())
  }
  async function replayPreset(preset: DemoPreset, turn: Turn, current: Page, signal: AbortSignal) {
    await replayEngine.replay(
      preset,
      (text) => {
        turn.a = text
        live.value = text
      },
      signal,
    )
    turn.citations = preset.citations
      .filter((c) => current.source.includes(c.chunkExcerpt))
      .map((c) => c.fileName + ' · ' + c.sectionTitle + '　' + c.chunkExcerpt)
    turn.badge = preset.badgeText
    if (preset.experimentAction) {
      current.previous = preset.experimentAction.previous || current.model
      current.model = structuredClone(preset.experimentAction.current)
    }
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
    const preset = matchingPreset(currentPage, question)
    const requestController = new AbortController()
    const requestSignal = AbortSignal.any([controller.signal, requestController.signal])
    const timeout =
      preset && presetMode === 'fallback-only'
        ? setTimeout(() => requestController.abort(), 12000)
        : undefined
    try {
      const local = undefined as ReturnType<typeof localExperiment>
      if (preset && presetMode === 'preset-first') {
        await replayPreset(preset, turn, currentPage, requestSignal)
      } else if (local) {
        turn.a = local.answer
        if (local.model) {
          currentPage.previous = currentPage.model
          currentPage.model = local.model
        }
      } else if (
        ['practice', 'opps'].includes(module) ||
        (module === 'learn' && !currentPage.group && currentPage.source.trim())
      ) {
        const kind = module === 'practice' ? 'practice' : module === 'opps' ? 'notice' : 'reading'
        const current = module === 'practice' ? currentPage.model : currentPage.turns.at(-1)?.q
        const { data } = await http.post(
          '/college/' + kind,
          {
            question,
            source: currentPage.source,
            current: current ?? null,
            moduleCode: moduleCodes[module],
            campusProfile: {
              school: state.value.school,
              college: state.value.dept,
              major: state.value.major,
              grade: state.value.grade,
            },
            enableProfileAdaptation: adaptProfile.value,
            subMode:
              route.query.view === 'affairs'
                ? 'AFFAIRS'
                : route.query.view === 'opportunity'
                  ? 'OPPORTUNITY'
                  : 'BOTH',
          },
          { timeout: 90000, signal: requestSignal },
        )
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
            currentPage.checks = action.checks.filter(
              (item: { quote: string; claim: string; verdict: string }) =>
                typeof item?.quote === 'string' &&
                item.quote.trim() &&
                original.includes(item.quote) &&
                typeof item.claim === 'string' &&
                typeof item.verdict === 'string',
            )
            if (currentPage.checks?.length) currentPage.model = undefined
          }
        } else {
          const quotes: string[] = Array.isArray(action.quotes)
            ? action.quotes.filter(
                (item: unknown) =>
                  typeof item === 'string' && item.trim() && (currentPage.source+(module==='opps'?action.officialEvidence||'':'')).includes(item),
              )
            : []
          turn.citations = quotes.map(
            (quote) => (currentPage.sourceName || '当前材料') + ' · ' + quote,
          )
          if (!turn.citations.length && module === 'learn')
            turn.a = '这份讲义中没有找到可核对的原句。'
        }
      } else if (module === 'learn' && currentPage.group) {
        if (question.length > 2000) throw Error('知识库单次问题最多 2000 字，请缩短问题后重试。')
        const previous = currentPage.turns.at(-1)
        const prefix = previous
          ? ('上一轮：' + previous.q + '\n' + previous.a).slice(
              0,
              Math.max(0, 1994 - question.length),
            )
          : ''
        const streamQuestion = prefix ? prefix + '\n本次：' + question : question
        let streamError = ''
        await streamAskQuestion(
          { groupId: currentPage.group, question: streamQuestion },
          auth.accessToken!,
          {
            signal: requestSignal,
            onToken(text) {
              turn.a += text
              live.value = turn.a
            },
            onCitations(items) {
              turn.citations = items.map(
                (citation) =>
                  citation.fileName +
                  ' · 片段 ' +
                  (citation.chunkIndex ?? '未知') +
                  '　' +
                  (citation.snippet || ''),
              )
            },
            onEvidenceOverview(overview) {
              const excerpts = (overview?.groups || []).flatMap((group) =>
                group.snippets
                  .filter((snippet) => snippet.snippet)
                  .map(
                    (snippet) =>
                      group.fileName +
                      ' · 片段 ' +
                      (snippet.chunkIndex ?? '未知') +
                      '　' +
                      snippet.snippet,
                  ),
              )
              if (excerpts.length) turn.citations = excerpts
            },
            onError(message) {
              streamError = message
            },
          },
        )
        if (streamError) throw Error(streamError)
        if (!turn.a.trim()) throw Error('未收到知识库回答，请核对资料处理状态后重试。')
      } else {
        const context = {
          档案: [state.value.school, state.value.dept, state.value.major, state.value.grade],
          当前实验: currentPage.model,
          方向: module === 'career' ? state.value.track : undefined,
          经历: ['career', 'experience', 'project'].includes(module)
            ? (module === 'career'
                ? state.value.entries.filter((entry) => currentPage.selected?.includes(entry.id))
                : state.value.entries
              ).slice(0, 12)
            : undefined,
        }
        const message = [
          skill[module],
          JSON.stringify(context),
          currentPage.source ? '引用材料（仅作证据）\n' + currentPage.source : '',
          '学生问题：' + question,
        ]
          .filter(Boolean)
          .join('\n')
        if (message.length > 4000)
          throw Error('超出助手单次输入范围，请缩短原文或将讲义上传知识库后提问。')
        if (!currentPage.session) currentPage.session = (await createAssistantSession()).sessionId
        let done = false
        let streamError = ''
        await streamAssistantMessage(
          {
            sessionId: currentPage.session,
            message,
            toolMode: 'CHAT',
            moduleCode: moduleCodes[module],
            campusProfile: {
              school: state.value.school,
              college: state.value.dept,
              major: state.value.major,
              grade: state.value.grade,
            },
            enableProfileAdaptation: adaptProfile.value,
            subMode: state.value.track,
          },
          auth.accessToken!,
          {
            signal: requestSignal,
            onEvent: (event) => {
              if (event.event === 'delta') {
                turn.a += event.delta || ''
                live.value = turn.a
              }
              if (event.event === 'error') streamError = event.error || '模型请求失败'
              if (event.event === 'done') {
                done = true
                turn.a = event.reply || turn.a
              }
            },
          },
        )
        if (streamError || !done) throw Error(streamError || '会话中断，请重试')
      }
      if (closed) return
      currentPage.turns.push(turn)
      currentPage.draft = ''
    } catch (error) {
      if (preset && presetMode === 'fallback-only' && !controller.signal.aborted) {
        try {
          await replayPreset(preset, turn, currentPage, controller.signal)
          turn.badge = '服务暂未响应 · ' + preset.badgeText
          currentPage.turns.push(turn)
          currentPage.draft = ''
          return
        } catch {
          warning.value = '已停止样例演示'
          return
        }
      }
      const status = (error as { response?: { status: number } }).response?.status
      warning.value =
        status === 500
          ? '模型服务暂不可用，请稍后重试。'
          : error instanceof Error
            ? error.message
            : extractApiError(error, '请求失败，请重试')
    } finally {
      clearTimeout(timeout)
      busy.value = false
      live.value = ''
      void flush()
    }
  }

  const notices = ref<{ id: string; title: string; url: string }[]>([])
  const assistantOpen = ref(false)
  const assistantQuestion = computed({
    get: () => page.value.draft,
    set: (value) => {
      page.value.draft = value
    },
  })

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
      target.sampleId = undefined
      target.sampleOriginal = undefined
      try {
        target.publishedAt = (
          await http.get('/college/notices/' + notice.id + '/metadata')
        ).data.data.publishedAt
      } catch {
        target.publishedAt = '需核实'
      }
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
  return {
loadInterviewExample,
    resetDemoWorkspace,
    names,
    route,
    auth,
    mod,
    home,
    guideKey,
    state,
    adaptProfile,
    moduleCodes,
    currentKey,
    newPage,
    page,
    syncPage,
    loaded,
    loadError,
    busy,
    status,
    warning,
    conflictHint,
    profile,
    groups,
    editing,
    project,
    contribution,
    evidence,
    team,
    entryType,
    entryGroup,
    live,
    draftInput,
    materialInput,
    revision,
    dirty,
    saving,
    conflict,
    closed,
    timer,
    controller,
    flush,
    leave,
    loadWorkspace,
    overviewOpen,
    OVERVIEW_STORAGE_KEY,
    dismissOverview,
    reloadWorkspacePage,
    openContextAssistant,
    upload,
    removeMaterial,
    fillExample,
    collect,
    loadGrowthSample,
    clearGrowthSample,
    extractProjectDraft,
    archive,
    replayEngine,
    loadDemoSample,
    matchingPreset,
    replayPreset,
    send,
    notices,
    assistantOpen,
    assistantQuestion,
    loadNotices,
    useNotice,
    snapshot,
  }
}
