# 五份提示词改造与验收报告

2026-11-27，分支 codex/review-workspace-upgrade。

## 交付对应关系

| 顺序 | 提示词 | 当前实现 |
| --- | --- | --- |
| 1 | 评审隔离、残留清理与组件拆分 | 每次一键体验创建 UUID 用户与独立工作台；正常 JWT/Refresh Cookie；限流每实例每分钟 30 次；演示重置；旧品牌配置归一；授权学习提示；板块子组件及状态协调 composable |
| 2 | 实验插件化、冷启动与防幻觉 | RC、悬臂梁、正态分布插件；总体密度/均值抽样分布、区间与分位读数；示例成长档案和岗位；零散记录整理；通知原句核查与缺文件提示 |
| 3 | 预制样例、双轨演示与大文件拦截 | 三份 UTF-8 材料；统一元数据与逐字引文；预设标识、可停止流式回放；preset-first/fallback-only/off；自定义问题走 API；默认 10 MB 上传、2 MB 文本读取及后端兜底；分片最多两次重试 |
| 4 | 2C2G、ES 降级与模型容错 | 容器限额、低内存 JVM、Linux 启动脚本；ES 开关、异常降级；单线程/8 队列入库；失败状态及 8 分钟扫描；工具 15 秒、Agent 45 秒限制；解析和查询规划回退 |
| 5 | 导航、高校适配与交互工作台 | 五个学习与成长入口、项目迁入小组、旧路由跳转；服务端 Skill 和档案可选字段；官网偏好搜索；实验模式/HUD/参数 Inspector；学习、经历、申请矩阵及情境助教 |

## 用户入口与路由

- 首页进入登录，使用「评委一键体验（独立空间，免注册）」可分配自己的空间。每次调用产生新账号，已有体验账号不能被免密码用户名入口复用。
- 正式本机入口为 http://127.0.0.1:15173/；无后端预览为 /preview。预览使用窗口内保存，界面明确标记演示模式。
- 01 学习助手仍为 /app/college/learn；02 专业实践为 /app/college/practice。
- 03 校园机会与办事为 /app/college/opps，支持综合、机会、办事视角。旧 /app/college/campus 跳转到 /app/college/opps?view=affairs，已有办事草稿沿用原键。
- 04 我的经历为 /app/college/experience；05 升学就业为 /app/college/career。
- 旧 /app/college/project 跳转到 /app/groups?tab=projects。小组工作区提供「我的项目」「小组资料与知识库」「成员与协作管理」三个视图。项目可以归属个人或具体小组，确认收录后可直达经历。
- 资源与协作保留文档管理、知识库问答、独立 AI 助手及小组。管理员菜单及守卫沿用 ADMIN 权限限制。

## 前端职责

CollegeView.vue 负责工作区布局，useCollegeWorkspace.ts 协调按账号保存、模块对话与结果。共享样式位于 college.css。

板块组件位于 src/views/college/components：

- CampusProfileCard.vue
- OpportunitiesPanel.vue
- CampusAffairsPanel.vue
- NoticeEvidencePanel.vue
- ProjectsExperiencePanel.vue
- CareerDraftPanel.vue
- CareerMatrix.vue
- LearningContextPanel.vue
- WorkspaceOverview.vue

ExperimentWorkspace.vue 负责模式、画布、参数、观察值、基准与历史快照；models.ts 声明注册模型与计算。GroupWorkspace.vue 复用原小组管理和文档管理页面。

## 后端适配

新增 ModuleSkillPromptRegistry 与 prompts/skills 下 learning、practice、campus_notice、group_projects、experience、career、general 七份模板。AssistantChatRequest 与 TaskController 的请求增加可选 moduleCode、campusProfile、enableProfileAdaptation、subMode，旧构造方式和请求字段继续可用。档案关闭时按跨专业任务处理，空字段安全显示未填写。

校园通知按学校与学院构造官网检索词，已知学校使用官网域名偏好，接受 edu.cn 来源。未配置搜索凭据、无结果或搜索异常时依赖用户材料。发布时间无法取得时显示需核实。搜索摘要不能代替学院内部认定文件，前端保留原文核查面板。

模型格式异常会显示需核实的原文或可重试的业务提示。数值计算由已注册公式执行，支持范围外的实验进入方法与测量讨论。

## 样例与自定义问题

public/samples 中包含课程讲义、RC 指导书、竞赛通知。demoSamples.ts 统一保存问题、答案和引文。预制引用已经逐字核对样例。

- preset-first 只对当前样例的原文和完全一致的预设问题回放。
- 编辑了问题或材料后进入真实 API。
- fallback-only 先调用服务，匹配预设的问题在失败或 12 秒超时后回放，显示降级标识。
- off 完全交由后端。回放过程中可停止。
- 默认上传上限 10 MB，可通过 VITE_MAX_UPLOAD_SIZE_MB 与 ARGUS_MAX_UPLOAD_SIZE_MB 分别配置前后端；两端需同步。
- 内联 TXT/MD 读取限制 2 MB，发送模型的单次材料继续限制 16000 字。超限在读取或上传前拦截，提示保留在页面内。

## 2C2G 内存账本

| 默认限额 | MiB |
| --- | --- |
| Java 容器 | 600 |
| PostgreSQL | 320 |
| MinIO | 192 |
| 默认合计，ES 关闭 | 1112 |
| ES 可选容器 | 420 |
| 启用 ES 后合计 | 1532 |

1532 MiB 小于 1.5 GiB。A 节点默认 512 MiB，带 ES 为 932 MiB；B 节点远程模型模式为 600 MiB。local-model 与 es-tools 是独立可选 profile。默认后端改用 JRE 运行预先打包的 JAR，避免运行期 Maven 编译占用。

容器限额是配置预算，Ubuntu、Docker、Nginx 和进程原生内存仍需实际监控。服务器若已有约 1.21 GiB 占用，应先排查来源；仅设置这些限额无法证明物理峰值一定低于 2 GiB。本次未对远端服务器施加压力测试。deploy/README.md 给出进程检查、独立 2 GiB Swap 文件、swappiness=20 和回退步骤，由部署负责人按服务器状态执行。

## 新增配置

- ARGUS_DEMO_GUEST_ENABLED，dev/prod 默认 true，其他环境默认关闭；限制是每个后端实例 30 次/分钟。
- ARGUS_ELASTICSEARCH_ENABLED，通用默认 true，prod 默认 false。Compose 默认不启用 es profile。
- ARGUS_MAX_UPLOAD_SIZE_MB=10、ARGUS_MAX_REQUEST_SIZE_MB=12。
- VITE_MAX_UPLOAD_SIZE_MB=10、VITE_DEMO_PRESET_MODE=preset-first。
- BACKEND_MEMORY_LIMIT=600m、POSTGRES_MEMORY_LIMIT=320m、MINIO_MEMORY_LIMIT=192m、ES_MEMORY_LIMIT=420m。
- JAVA_TOOL_OPTIONS 默认使用 Xms192m、Xmx384m、MaxMetaspaceSize160m、UseSerialGC。
- document.ingestion.processing-timeout-minutes 默认 8；scan-interval-ms 默认 60000。

体验账号通过前缀标记，当前会保存在测试库。正式公开演示需要运营方制定保留周期；本次未自动批量删除用户记录。

## 自动化与人工验收

后端 Maven Wrapper clean test 与打包、前端 npm run build 均执行成功。新增回归覆盖 ES 关闭、ES 断连后的向量证据、入库保留 READY、JSON 容错、超限上传、串行队列、可选上下文及体验开关/限流。

sql/tests/upgrade_acceptance.py 验证独立浏览器、档案保存恢复、统计模型、预设与自定义 API 分流、大文件零上传请求、项目收录与旧路由、深浅主题、宽窄屏和无后端预览。还用 10 个并发请求检查独立账号与工作台保存。

sql/tests/experiment-regression.mjs 校验原 RC/悬臂梁数值及统计参数；sample-citations.mjs 校验引用。fallback-only 实测匹配预设等待 12 秒后回放，自定义问题失败时不会追加预设答案。

## 30 秒演示路径

1. 一键体验，进入自己的空间。
2. 学习助手载入中值定理样例，点击预设问题，展开逐字引用。
3. 专业实践载入 RC 指导书，观察 0.6 秒时间常数，再对比 24 kΩ 的 1.2 秒。
4. 切换正态分布，改变样本量观察抽样分布与区间。
5. 校园通知载入样例，看见缺少本院综测目录的核实提醒。
6. 小组项目载入成长档案，确认个人贡献；到升学就业勾选经历、编辑并导出申请草稿。

## 作品贴技术简介

Argus 以声明式参数表和纯计算函数注册实验。AI 负责理解指导书、追问条件和解释差异，数值由注册模型计算。RC、悬臂梁和正态分布共享调参、曲线、基准对比与快照，新增学科可沿同一接口扩展。

## 当前运行与交付范围

本机数据库端口曾被 Windows 保留，现使用 15439，并复用原 college-vector16 数据卷。后端 15174，前端 15173。运行实例采用 test profile 和独立 argus_test，ES 开关关闭时仍可使用向量检索。另一个 15175 端口仅用于 fallback-only 回归。

代码、配置和运维操作文档在仓库交付。公网发布、生产容量和证书验收仍按服务器负责人实际部署结果确认。

## 最终验证记录

- Maven Wrapper `clean test package`：50 项测试，0 失败、0 错误，编译与打包成功。
- `npm run build`：TypeScript 与 Vite 均成功。默认构建产物约 2.11 MiB。已有超过 500 kB 的 chunk 警告，不影响构建。
- Node 模型及逐字引文回归：3 项通过。
- 两份 Compose `config --quiet`：通过。
- 浏览器端到端：独立账号、档案恢复、统计快照、预设/自定义分流、超限文件零上传、旧路由、项目到经历到申请、深浅色、1440/390 屏宽、断开 API 的公开预览均通过。
- 构建产物浏览器回归：以 `VITE_API_BASE_URL=https://api.example.test/api` 构建，用拦截的 API fixture 验证保存、恢复、长问题、流式引用与窄屏；不访问此示例域名。默认构建不注入该地址。
- 真实隔离库联调：上传、入库 READY、AI 流式回答、来源引用、指导书解读和参数追问通过。
- fallback-only：匹配预设 12 秒超时降级，自定义问题失败不会伪装成预设答案。

安全检查同步移除了 dev/test 与本地运行模板中的既有公网 IP 默认值，改为回环地址，实际环境通过变量注入。没有修改远端数据库或部署服务器。

## 修改与新增文件清单

以下为本次 Git 暂存差异（R 表示重命名，D 为清理的旧文件）：

```text
A	.Argus 学生工作台.qoder.site
D	.Argus 数据洞察中心.qoder.site
M	Argus-backend/deploy/README.md
M	Argus-backend/deploy/application-prod.env.example
M	Argus-backend/deploy/env/server-a.env.example
M	Argus-backend/deploy/env/server-b.env.example
M	Argus-backend/deploy/local-run.env.example
M	Argus-backend/deploy/nginx.conf
M	Argus-backend/deploy/server-a.compose.yml
M	Argus-backend/deploy/server-b.compose.yml
M	Argus-backend/deploy/start-backend.ps1
A	Argus-backend/deploy/start-backend.sh
M	Argus-backend/src/main/java/com/argus/rag/assistant/agent/AssistantAgentFacade.java
M	Argus-backend/src/main/java/com/argus/rag/assistant/agent/AssistantKnowledgeBaseTool.java
M	Argus-backend/src/main/java/com/argus/rag/assistant/agent/AssistantKnowledgeBaseToolResultHolder.java
M	Argus-backend/src/main/java/com/argus/rag/assistant/agent/AssistantReactAgentFactory.java
M	Argus-backend/src/main/java/com/argus/rag/assistant/model/dto/chat/AssistantChatRequest.java
M	Argus-backend/src/main/java/com/argus/rag/assistant/service/AssistantService.java
M	Argus-backend/src/main/java/com/argus/rag/assistant/support/config/AssistantPromptContextBuilder.java
M	Argus-backend/src/main/java/com/argus/rag/auth/controller/AuthController.java
M	Argus-backend/src/main/java/com/argus/rag/auth/security/JwtAuthenticationFilter.java
M	Argus-backend/src/main/java/com/argus/rag/auth/service/AuthService.java
A	Argus-backend/src/main/java/com/argus/rag/college/CampusProfile.java
A	Argus-backend/src/main/java/com/argus/rag/college/ModuleContext.java
A	Argus-backend/src/main/java/com/argus/rag/college/ModuleSkillPromptRegistry.java
M	Argus-backend/src/main/java/com/argus/rag/college/NoticeController.java
M	Argus-backend/src/main/java/com/argus/rag/college/TaskController.java
M	Argus-backend/src/main/java/com/argus/rag/document/service/DocumentIngestionAsyncListener.java
M	Argus-backend/src/main/java/com/argus/rag/document/service/DocumentUploadService.java
A	Argus-backend/src/main/java/com/argus/rag/document/service/IngestionExecutorConfiguration.java
M	Argus-backend/src/main/java/com/argus/rag/document/service/StaleProcessingDocumentRecoveryRunner.java
M	Argus-backend/src/main/java/com/argus/rag/engine/elasticsearch/ElasticsearchChunkIndexService.java
M	Argus-backend/src/main/java/com/argus/rag/ingestion/mapper/IngestionJobMapper.java
M	Argus-backend/src/main/java/com/argus/rag/ingestion/service/DocumentIngestionAsyncService.java
M	Argus-backend/src/main/java/com/argus/rag/qa/config/QueryPlanningConfiguration.java
M	Argus-backend/src/main/java/com/argus/rag/qa/rag/HybridChunkRetrievalService.java
M	Argus-backend/src/main/java/com/argus/rag/qa/service/QaChatService.java
M	Argus-backend/src/main/java/com/argus/rag/qa/service/QueryPlanningService.java
M	Argus-backend/src/main/java/com/argus/rag/qa/support/QaAnswerParser.java
M	Argus-backend/src/main/resources/application-dev.yml
M	Argus-backend/src/main/resources/application-prod.yml
M	Argus-backend/src/main/resources/application-test.yml
M	Argus-backend/src/main/resources/application.yml
M	Argus-backend/src/main/resources/prompts/query-planning/user.st
A	Argus-backend/src/main/resources/prompts/skills/campus_notice.st
A	Argus-backend/src/main/resources/prompts/skills/career.st
A	Argus-backend/src/main/resources/prompts/skills/experience.st
A	Argus-backend/src/main/resources/prompts/skills/general.st
A	Argus-backend/src/main/resources/prompts/skills/group_projects.st
A	Argus-backend/src/main/resources/prompts/skills/learning.st
A	Argus-backend/src/main/resources/prompts/skills/practice.st
A	Argus-backend/src/test/java/com/argus/rag/auth/controller/DemoGuestControllerTest.java
A	Argus-backend/src/test/java/com/argus/rag/college/CampusAdaptationTest.java
A	Argus-backend/src/test/java/com/argus/rag/college/ModuleSkillPromptRegistryTest.java
M	Argus-backend/src/test/java/com/argus/rag/document/service/DocumentUploadServiceTest.java
A	Argus-backend/src/test/java/com/argus/rag/document/service/IngestionQueueTest.java
A	Argus-backend/src/test/java/com/argus/rag/document/service/IngestionWithoutElasticsearchTest.java
A	Argus-backend/src/test/java/com/argus/rag/engine/elasticsearch/OptionalElasticsearchTest.java
M	Argus-backend/src/test/java/com/argus/rag/qa/rag/HybridChunkRetrievalServiceTest.java
A	Argus-backend/src/test/java/com/argus/rag/qa/support/QaAnswerParserResilienceTest.java
D	Argus-frontend/.Argus 数据洞察中心.qoder.site
M	Argus-frontend/.env.example
D	Argus-frontend/_test_marked.mjs
A	Argus-frontend/public/samples/sample-competition-notice.txt
A	Argus-frontend/public/samples/sample-course-notes.md
A	Argus-frontend/public/samples/sample-rc-experiment-guide.md
M	Argus-frontend/src/api/document.ts
A	Argus-frontend/src/components/DemoGuestButton.vue
A	Argus-frontend/src/components/DemoSampleBar.vue
M	Argus-frontend/src/components/LoginModal.vue
M	Argus-frontend/src/composables/useChunkedUpload.ts
A	Argus-frontend/src/composables/useDemoPresetReplay.ts
A	Argus-frontend/src/constants/demoSamples.ts
M	Argus-frontend/src/constants/moduleGuides.ts
A	Argus-frontend/src/constants/uploadLimits.ts
M	Argus-frontend/src/layouts/DefaultLayout.vue
M	Argus-frontend/src/router/index.ts
M	Argus-frontend/src/types/assistant.ts
M	Argus-frontend/src/views/HomeView.vue
M	Argus-frontend/src/views/LoginView.vue
M	Argus-frontend/src/views/college/CampusLanding.vue
M	Argus-frontend/src/views/college/CollegeView.vue
M	Argus-frontend/src/views/college/ExperimentWorkspace.vue
A	Argus-frontend/src/views/college/PreviewWorkspace.vue
A	Argus-frontend/src/views/college/college.css
A	Argus-frontend/src/views/college/components/CampusAffairsPanel.vue
A	Argus-frontend/src/views/college/components/CampusProfileCard.vue
A	Argus-frontend/src/views/college/components/CareerDraftPanel.vue
A	Argus-frontend/src/views/college/components/CareerMatrix.vue
A	Argus-frontend/src/views/college/components/LearningContextPanel.vue
A	Argus-frontend/src/views/college/components/NoticeEvidencePanel.vue
A	Argus-frontend/src/views/college/components/OpportunitiesPanel.vue
A	Argus-frontend/src/views/college/components/ProjectsExperiencePanel.vue
A	Argus-frontend/src/views/college/components/WorkspaceOverview.vue
M	Argus-frontend/src/views/college/models.ts
A	Argus-frontend/src/views/college/useCollegeWorkspace.ts
M	Argus-frontend/src/views/documents/DocumentListView.vue
M	Argus-frontend/src/views/documents/components/UploadDialog.vue
A	Argus-frontend/src/views/groups/GroupWorkspace.vue
M	Argus-frontend/src/views/groups/GroupsPage.vue
A	docs/upgrade-five-prompts-progress.md
A	docs/upgrade-five-prompts-report.md
M	sql/tests/browser_workspace.py
A	sql/tests/experiment-regression.mjs
M	sql/tests/live_workspace.py
A	sql/tests/sample-citations.mjs
A	sql/tests/upgrade_acceptance.py
```
