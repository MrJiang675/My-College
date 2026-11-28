<script setup lang="ts">
const uploader=ref<InstanceType<typeof MaterialFileUploader>>()
import {ref,computed} from 'vue'
import MarkdownContent from '@/components/MarkdownContent.vue'
import {moduleInputConfig} from '@/constants/moduleInputConfig'
function loadNoticeScenario(source:string,name:string){page.value.source=source;page.value.sourceName=name;page.value.sampleId=undefined;page.value.sampleOriginal=undefined}
function switchNoticeProfile(college:string,grade:string){state.value.dept=college;state.value.grade=grade}
function saveNoticeStatement(text:string){const key='career-'+state.value.track;state.value.pages[key]??=newPage();state.value.pages[key].applicationDraft=text}
function exportWorkspace(){const url=URL.createObjectURL(new Blob([JSON.stringify(state.value,null,2)],{type:'application/json'}));const a=document.createElement('a');a.href=url;a.download='Argus-工作台备份.json';a.click();URL.revokeObjectURL(url)}
function clearHistory(){if(window.confirm('清空当前页对话？请先导出需要保留的内容。')){page.value.turns=[];flush()}}
function openGlobalAssistant(){if(props.previewMode){warning.value='当前为样例预览，请登录真实工作台后使用助教。';return}window.dispatchEvent(new CustomEvent('argus:show-assistant'))}
function removeEntry(id:string){if(window.confirm('删除这项经历？'))state.value.entries=state.value.entries.filter(e=>e.id!==id)}
function applyMaterial(text:string,name:string){page.value.source=text.slice(0,16000);page.value.sourceName=name;warning.value=text.length>16000?'本轮材料已截取前 16000 字，请拆分继续提问。':''}

import MaterialFileUploader from '@/components/MaterialFileUploader.vue'
import LearningContextPanel from './components/LearningContextPanel.vue'
import { moduleGuides, type ModuleGuideKey } from '@/constants/moduleGuides'
import { experimentPlugins, defaultModel, pluginFor } from './models'
import CareerWorkflow from './components/CareerWorkflow.vue'
import DemoSampleBar from '@/components/DemoSampleBar.vue'
import NoticeActionDashboard from './components/NoticeActionDashboard.vue'
import NoticeEvidencePanel from './components/NoticeEvidencePanel.vue'
import OpportunitiesPanel from './components/OpportunitiesPanel.vue'
import WorkspaceOverview from './components/WorkspaceOverview.vue'
import CampusAffairsPanel from './components/CampusAffairsPanel.vue'
import ProjectsExperiencePanel from './components/ProjectsExperiencePanel.vue'
import CampusProfileCard from './components/CampusProfileCard.vue'
import ModuleGuideHeader from '@/components/ModuleGuideHeader.vue'
import EmptyExampleCards from '@/components/EmptyExampleCards.vue'
import ExperimentWorkspace from './ExperimentWorkspace.vue'
import { useCollegeWorkspace } from './useCollegeWorkspace'
const inputConfig=computed(()=>moduleInputConfig[mod.value]||moduleInputConfig.experience!)
const props = defineProps<{ moduleOverride?: string; previewMode?: boolean;projectExample?:boolean }>()
const {
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
  saveBeforeInterview,
} = useCollegeWorkspace(props)
</script>

<template>
  <main class="college" :class="{'is-notice-module':mod==='opps'}">
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
          <button class="btn-secondary" @click="exportWorkspace">导出工作台备份</button>
          <button v-if="!home" class="btn-secondary" @click="clearHistory">清理本页对话</button>

          <button
            v-if="auth.currentUser?.userCode.startsWith('demo_guest_') || props.previewMode"
            type="button"
            class="btn-secondary"
            @click="resetDemoWorkspace"
          >
            重置演示数据
          </button>
          <button
            v-if="!home"
            type="button"
            class="btn-secondary"
            @click="openGlobalAssistant"
          >
            问助手
          </button>
          <button type="button" class="btn-secondary" @click="profile = !profile">
            {{ state.major || '填写专业' }} · 校园档案
          </button>
          <button v-if="home" type="button" class="btn-secondary" @click="overviewOpen = true">
            30 秒看懂
          </button>
        </template>
      </ModuleGuideHeader>

      <CampusProfileCard
        :visible="profile"
        :state="state"
        @save="profile = false; flush()"
      />

      <template v-if="home">
        <section class="home-hero">
          <div>
            <span class="home-hero__eyebrow">今天也从一个小问题开始</span>
            <h2>
              {{ state.school || '你的校园' }}<span v-if="state.major"> · {{ state.major }}</span>
            </h2>
            <p>学习、实践、校园事务和个人成长，都在这里接着做。</p>
          </div>
          <RouterLink
            v-if="state.last && names[state.last]"
            :to="'/app/college/' + state.last"
            class="resume-link"
          >
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
              <RouterLink
                v-for="(name, key) in Object.fromEntries(
                  Object.entries(names).filter(([key]) => key !== 'project'),
                )"
                :key="key"
                :to="'/app/college/' + key"
                class="task-card"
              >
                <span class="task-card__index">{{
                  String(Object.keys(names).indexOf(key) + 1).padStart(2, '0')
                }}</span>
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
                <div>
                  <dt>学校</dt>
                  <dd>{{ state.school || '待填写' }}</dd>
                </div>
                <div>
                  <dt>学院</dt>
                  <dd>{{ state.dept || '待填写' }}</dd>
                </div>
                <div>
                  <dt>专业</dt>
                  <dd>{{ state.major || '待填写' }}</dd>
                </div>
                <div>
                  <dt>年级</dt>
                  <dd>{{ state.grade || '待填写' }}</dd>
                </div>
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

        <OpportunitiesPanel :mod="mod" :notices="notices" @load="loadNotices" @use="useNotice" @fetched="loadNoticeScenario" />
        <NoticeActionDashboard v-if="mod==='opps'" v-model:materials="page.noticeMaterials" :source="page.source" :profile="{school:state.school,college:state.dept,major:state.major,grade:state.grade}" :enabled="adaptProfile" v-model:analysis="page.noticeAnalysis" :entries="state.entries" @load="loadNoticeScenario" @profile="switchNoticeProfile" @statement="saveNoticeStatement" @adapt="adaptProfile=$event" />

        <section v-if="mod === 'practice' && !page.model" class="panel" style="padding: 16px">
          <h2>已注册确定性计算模型</h2>
          <button
            v-for="item in experimentPlugins"
            :key="item.id"
            type="button"
            class="btn-secondary"
            @click="page.model = defaultModel(item.id)"
          >
            {{ item.name }} · {{ item.discipline }}
          </button>
          <p>你也可以描述任意专业的任务，助手将核对是否具备数值模型。</p>
        </section>
        <section
          v-if="['experience', 'project'].includes(mod)"
          class="panel"
          style="padding: 16px"
        >
          <button type="button" class="btn-secondary" @click="loadGrowthSample">
            载入典型学生成长示例</button
          ><button type="button" class="btn-secondary" @click="clearGrowthSample">
            清空示例经历
          </button>
          <p>示例记录带标识，收录前请替换为本人真实成果。</p>
        </section>
        <label v-if="mod!=='opps'" class="profile-adaptation"
          ><input v-model="adaptProfile" type="checkbox" />结合校园档案 ·
          {{ state.school || '学校未填' }} / {{ state.major || '跨专业任务' }} /
          {{ state.grade || '年级未填' }}</label
        >
        <DemoSampleBar v-if="!['opps','career'].includes(mod)" :module="mod" @load="loadDemoSample" />
        <button
          v-if="replayEngine.replaying.value"
          type="button"
          class="btn-secondary"
          @click="replayEngine.stop"
        >
          停止样例演示
        </button>

        <CareerWorkflow v-if="mod==='career'" :page="page" :track="state.track" :entries="state.entries" :busy="busy" :live="live" :save="saveBeforeInterview" @send="send" @sample="loadGrowthSample" />
        <div v-if="!['opps','career'].includes(mod)" class="module-workspace" :class="{ 'is-practice': mod === 'practice', 'is-notice':mod==='opps' }">
          <div class="module-main">
            <section v-if="mod === 'learn'" class="panel" style="padding: 10px">
              <button
                v-for="mode in ['短材料精读', '课程知识库问答', '概念自测出题']"
                :key="mode"
                type="button"
                class="btn-secondary"
                @click="
                  page.learningMode = mode;
                  mode === '概念自测出题' &&
                    (page.draft = '请依据当前课程材料出一道自测题，等我回答后再讲解。')
                "
              >
                {{ mode }}</button
              ><small> 当前模式 · {{ page.learningMode || '短材料精读' }}</small>
            </section>
            <section class="composer-card panel">
              <form @submit.prevent="send">
                <label v-if="mod === 'learn'" class="field">
                  <span>回答依据</span>
                  <select v-model="page.group">
                    <option :value="undefined">当前材料 / 课程辅导</option>
                    <option v-for="group in groups" :value="group.groupId" :key="group.groupId">
                      {{ group.groupName }} · 知识库
                    </option>
                  </select>
                  <small>{{
                    page.group
                      ? '正在使用所选课程小组内已入库的文档。'
                      : '正在使用你粘贴的短材料，引用会核对原句。'
                  }}</small>
                </label>

                <section class="material-input" aria-labelledby="material-title" @paste="uploader?.paste($event)">
                  <div class="material-input__header">
                    <div>
                      <strong id="material-title">{{inputConfig.materialTitle}}</strong>
                      <small>{{inputConfig.materialSubtitle}}</small>
                    </div>
                    <button
                      v-if="page.sourceName"
                      type="button"
                      class="material-input__remove"
                      @click="removeMaterial"
                    >
                      移除
                    </button>
                  </div>
                  <div class="upload-tags"><small v-for="tag in inputConfig.recommendedUploads" :key="tag">{{tag}}</small></div>
                  <MaterialFileUploader ref="uploader" @extracted="applyMaterial" />
                  <small v-if="page.sourceName">{{page.sourceName}}</small>
                  <textarea
                    v-model="page.source"
                    aria-label="材料原文"
                    :placeholder="inputConfig.materialPlaceholder"
                  ></textarea>
                  <div class="material-input__footer">
                    <small>内联短材料用于逐句核对；大讲义请上传知识库</small>
                    <RouterLink to="/app/documents">上传到知识库 →</RouterLink>
                  </div>
                </section>

                <label class="field">
                  <span>{{inputConfig.taskLabel}}</span>
                  <textarea
                    ref="draftInput"
                    v-model="page.draft"
                    :required="mod!=='opps'"
                    maxlength="3500"
                    aria-label="给助手的任务"
                    :placeholder="inputConfig.taskPlaceholder"
                  ></textarea>
                </label>
                <div class="upload-tags"><button v-for="action in inputConfig.quickActions" :key="action" type="button" class="btn-secondary" @click="fillExample(action)">{{action}}</button></div>
                <div class="composer-actions">
                  <small>{{ page.draft.length }} / 3500</small>
                  <button class="btn-primary" type="submit" :disabled="busy">
                    {{ busy ? '正在处理…' : inputConfig.submitLabel }}
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

            <section
              v-if="mod === 'practice' && page.checks?.length"
              class="check-card panel"
              aria-labelledby="check-title"
            >
              <div class="panel-heading">
                <div>
                  <span class="section-kicker">逐项对照</span>
                  <h2 id="check-title">采访核实</h2>
                </div>
              </div>
              <div class="check-table">
                <div class="check-table__head">
                  <span>采访原话</span><span>报道表述</span><span>核实意见</span>
                </div>
                <div v-for="row in page.checks" :key="row.quote + row.claim" class="check-row">
                  <blockquote>{{ row.quote }}</blockquote>
                  <strong>{{ row.claim }}</strong>
                  <p>{{ row.verdict }}</p>
                </div>
              </div>
            </section>

            <section v-if="mod === 'practice' && page.model" class="experiment-panel">
              <ExperimentWorkspace @news="loadInterviewExample"
                :current="page.model"
                :previous="page.previous"
                :snapshots="state.snapshots"
                @snapshot="snapshot"
                @change="
                  (model) => {
                    page.previous = page.model
                    page.model = model
                  }
                "
                @restore="
                  (model) => {
                    page.previous = page.model
                    page.model = model
                  }
                "
                @import="uploader?.open()"
                @ask="
                  (q) => {
                    page.draft = q
                    openGlobalAssistant()
                  }
                "
              />
            </section>

            <div v-if="busy" class="streaming-status" aria-live="polite"><MarkdownContent :content="live||'正在整理回答…'" /></div>

            <section v-if="page.turns.length" class="turns" aria-label="对话记录">
              <article
                v-for="(turn, index) in [...page.turns].reverse()"
                :key="page.turns.length - index"
                class="turn panel"
              >
                <small v-if="turn.badge" class="status-badge">{{ turn.badge }}</small>
                <div class="turn__question">
                  <span>你</span><strong>{{ turn.q }}</strong>
                </div>
                <button
                  v-if="mod === 'career'"
                  type="button"
                  class="btn-secondary"
                  @click="page.applicationDraft = turn.a"
                >
                  编辑为申请陈述草稿
                </button>
                <div class="turn__answer">
                  <span>Argus</span>
                  <MarkdownContent :content="turn.a" />
                </div>
                <details v-if="turn.citations.length" class="citation-details">
                  <summary>
                    核对资料原文
                    <el-tooltip
                      content="展开查看答案出自哪份讲义的哪一段，区分原文依据与待确认内容。"
                      placement="top"
                    >
                      <span class="hint-dot" aria-hidden="true">?</span>
                    </el-tooltip>
                  </summary>
                  <p v-for="citation in turn.citations" :key="citation">{{ citation }}</p>
                </details>
                <el-tooltip
                  content="经本人核对后收入我的经历，可在升学就业中直接勾选使用。"
                  placement="top"
                >
                  <button
                    v-if="mod === 'project'"
                    type="button"
                    class="btn-secondary"
                    @click="collect(turn.a)"
                  >
                    编辑并收录经历
                  </button>
                </el-tooltip>
              </article>
            </section>
          </div>

          <aside class="module-aside">
            <LearningContextPanel
              v-if="mod === 'learn'"
              :source="page.source"
              :name="page.sourceName"
              :group="page.group"
            />



            <section v-if="mod === 'project'" class="context-card panel">
              <span class="section-kicker">收录闭环</span>
              <h2>项目 → 经历</h2><label>当前项目状态<select :value="page.projectStatus||'ONGOING'" @change="page.projectStatus=($event.target as HTMLSelectElement).value as 'ONGOING'|'DONE'"><option value="ONGOING">进行中</option><option value="DONE">已完成</option></select></label>
              <label
                >粘贴零散记录<textarea
                  v-model="page.source"
                  placeholder="群聊汇报、实验报告中的本人进展"
                ></textarea></label
              ><button type="button" class="btn-secondary" @click="extractProjectDraft">
                整理为项目草稿
              </button>
              <ol>
                <li>口语描述进展</li>
                <li>修改整理内容并附成果位置</li>
                <li>本人确认后收录</li>
              </ol>
              <button type="button" class="btn-secondary" @click="collect(page.draft)">
                记录个人贡献
              </button>
              <RouterLink to="/app/groups">协作小组 →</RouterLink>
            </section>

            <form v-if="editing" class="archive-card panel" @submit.prevent="archive">
              <h2>确认收录经历</h2>
              <label
                >项目归属<select v-model="entryGroup">
                  <option :value="undefined">个人专属项目 / 课程组</option>
                  <option v-for="group in groups" :key="group.groupId" :value="group.groupId">
                    {{ group.groupName }}
                  </option>
                </select></label
              >
              <label
                >类型<select v-model="entryType">
                  <option>课程设计</option>
                  <option>竞赛</option>
                  <option>科研</option>
                  <option>实践</option>
                </select></label
              >
              <label>团队背景<input v-model="team" placeholder="团队整体做了什么" /></label>
              <label>项目名称<input v-model="project" required /></label>
              <label>我的贡献<textarea v-model="contribution" required></textarea></label>
              <label>成果位置<input v-model="evidence" placeholder="仓库、报告或演示链接" /></label>
              <button class="btn-primary" type="submit">确认收录至我的经历</button
              ><RouterLink to="/app/college/experience">查看我的经历 →</RouterLink>
            </form>

            <ProjectsExperiencePanel :mod="mod" :entries="state.entries" @remove="removeEntry" />

            <section
              v-if="mod === 'practice' && state.snapshots.length"
              class="snapshot-list panel"
            >
              <h2>实验记录</h2>
              <button
                v-for="(snap, index) in state.snapshots"
                :key="index"
                type="button"
                @click="
                  page.previous = page.model;
                  page.model = snap.model
                "
              >
                <span>{{ snap.title }}</span>
                <strong
                  >{{ pluginFor(snap.model).name }} · 恢复参数</strong
                >
              </button>
            </section>
          </aside>
        </div>
      </template>

      <button
        v-if="!home"
        class="floating-assistant"
        type="button"
        @click="openGlobalAssistant"
      >
        Argus · {{ moduleCodes[mod] }} 助教
      </button>


      <button
        class="sync-status"
        type="button"
        :class="{ 'is-conflict': conflictHint }"
        @click="flush"
        aria-live="polite"
      >
        <span class="sync-status__dot" />{{ status || '已同步' }}
      </button>
    </template>

    <WorkspaceOverview v-model="overviewOpen" @dismiss="dismissOverview" />
  </main>
</template>

<style scoped src="./college.css"></style>

<style scoped>
.module-workspace.is-notice{grid-template-columns:minmax(0,.9fr) minmax(0,1.1fr)}
.is-notice-module .profile-adaptation{display:flex;align-items:center;gap:8px;margin:12px 0}.is-notice-module .profile-adaptation input{width:auto}
@media(max-width:1100px){.module-workspace.is-notice{grid-template-columns:minmax(0,1fr)}}
</style>

<style scoped>.upload-tags{display:flex;flex-wrap:wrap;gap:6px;margin:8px 0}.upload-tags small{padding:4px 8px;border-radius:99px;background:var(--accent-soft);color:var(--text-secondary)}</style>
