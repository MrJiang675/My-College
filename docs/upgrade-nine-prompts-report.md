# Argus 工作台九项优化验收报告

日期：2026-11-28。分支：`codex/workspace-nine-upgrades`，基于上一轮 `ab730ff`。

## 结论与验收边界

九项代码改造已实现，后端 `clean test package` 63 项测试全部通过，前端 `npm run build` 成功，dist 约 2.14 MiB。真实隔离库完成文字提取、五题模拟面试生成/评分和 50 MiB 文件分片入库。图片提取已通过本地模拟供应商协议测试和浏览器流程；真实 `glm-4.6v-flash` 返回供应商 HTTP 429、错误码 1305（模型访问量过大），本次真实 OCR 不计为通过。没有伪造模型结果或切换到本地 OCR。

未部署公网服务、未操作生产数据库、未申请证书，没有数据库表结构迁移。本机实例仅使用 `test` + 独立 `argus_test`。50 MiB 样本为有效单页 PDF 追加空白填充，验证的是大文件传输与处理链路；不能替代复杂 PDF 和真实 2C2G 服务器压力测试。

## 1. 稳定性：确定的代码缺陷与复现

在基线 `ab730ff` 的 `Argus-frontend/src/api/http.ts` 中只有请求拦截器，**没有 401 响应刷新拦截器**。`useCollegeWorkspace.ts` 的 GET/PUT/Task/Notice 都直接使用该客户端。用户仍在当前页时，过期 Token 会持续用于后续请求。`api/qa.ts` 与 `api/assistant.ts` 的 Fetch SSE 同样没有握手 401 刷新重试，且未携带跨站 refresh cookie 所需的 `credentials: include`。

现修复位置：

- `Argus-frontend/src/api/http.ts:69`：REST 401 自动续期并仅重试一次，排除登录/刷新/登出接口，尊重取消信号。
- `Argus-frontend/src/api/http.ts:88`：SSE 握手 401 刷新重试，携带 cookie；流已经开始后不重放。
- `Argus-frontend/src/api/sessionRefresh.ts:3`：并发请求共用一个刷新 Promise，迟到的旧请求复用新 Token；刷新失败清理登录态。
- `useCollegeWorkspace.ts:148` 起：保留本地交互和 dirty 状态，800ms 防抖、非冲突失败最多重试两次、同步按钮可继续手动重试。
- `useCollegeWorkspace.ts:151`：接近 100 万字符时提前提示导出备份/清理，不把页面锁在 loading。没有静默删除用户材料或对话。
- `WorkspaceController.java:36`：递归拒绝 data:image Base64 持久化，保留既有大小和 revision 乐观锁。
- `NoticeController.java:29`、`:39`：8 秒 HTTP 请求超时；缓存刷新改为可立即失败的信号量，避免多个请求在 synchronized 抓取期间串行等待。搜索客户端超时同步为 8 秒。

浏览器测试强制两个并发 REST 请求过期，验证只进行一次 refresh、两个原请求均恢复；同时验证 SSE 过期握手恢复。所有工作台 AI 状态仍在 finally 中释放。

**证据限制**：未获得生产故障日志，不能将用户描述的所有“只有工作台失效”现象都归因于同一原因。当前仓库中资源模块也共用此客户端，所以“资源模块完全不受影响”的生产版本差异不能凭空确认。上述缺陷是本次已确认并可控复现的失效原因。

## 2. 九项交付对照

| 项目 | 实现与验证 |
| --- | --- |
| 1 稳定性 | REST/SSE 续期、有限重试、备份/清理恢复、大报文预检、禁止 Base64、抓取并发控制 |
| 2 常驻树形侧栏 | 学生工作台默认展开，可折叠；竖线/缩进/编号/选中条；窄屏变化与跳转自动收起；无悬浮子菜单 |
| 3 经历并入项目 | 侧栏移除独立经历；项目右上方“我的经历 (N)”打开内嵌完整管理视图；筛选、编辑贡献/证明、删除、示例与申请引用保留 |
| 4 智能问答 | 统一入口、知识库/助手双模式；KeepAlive 保留各自状态；旧助手会话 ID 继续携带和恢复 |
| 5 文件提取 | MaterialFileUploader；5 MiB 图片、15 MiB 文档；压缩 JPEG、截图粘贴、缩略图/取消；PDF/DOCX/TXT/MD 解析和 30000 字截断标识 |
| 6 模拟面试 | 独立页面；经历导入/文件提取、场景与目标、五题导航、逐题作答、报告/四维进度、Markdown 导出；不足五题作答需确认 |
| 7 50 MB 上传 | 3 MiB 传输分片，业务 50 MiB，multipart/Nginx 60 MiB；磁盘合并、完整 SHA-256、解析限额、单线程入库保留 |
| 8 情境助教 | 当前路由/Skill/档案，携带内容开关；三张快捷提问卡、流式 Markdown、复制、清空、完整问答入口、Esc/遮罩关闭 |
| 9 深色主题 | 语义变量、原生与 Element Plus 控件、消息/筛选/上传背景；浅色/深色分段按钮常驻顶部；390px 与桌面测试及截图检查 |

## 3. 导航与路由

工作台树：我的大学 → 01 学习助手 → 02 专业实践 → 03 校园机会与办事 → 04 我的项目 → 05 升学就业。资源与协作：文档管理、智能问答、协作小组；管理员菜单及权限守卫保留。

| 旧地址或入口 | 当前目的地 |
| --- | --- |
| /app/college/experience | /app/groups?tab=experience |
| /app/college/project | /app/groups?tab=projects |
| /app/college/campus | /app/college/opps?view=affairs |
| /app/qa（保留 query） | /app/intelligent?mode=knowledge |
| /app/assistant | /app/intelligent?mode=assistant |
| /app/assistant/:sessionId | /app/intelligent?mode=assistant&sessionId=原值 |
| 升学就业“模拟面试” | /app/college/mock-interview |
| /college/mock-interview | /app/college/mock-interview（别名兼容） |

我的经历保留同一份后端工作台数据，没有复制出第二个经历库。项目确认收录与升学就业勾选同一 entries 数组。

## 4. 接口与配置

所有新接口沿用 JWT 与 CurrentUserService，需要有效登录：

| 接口 | 输入 | 输出 |
| --- | --- | --- |
| POST /api/college/files/extract-text | multipart file | extractedText/fileType/fileName/charCount/truncated/model |
| POST /api/college/interview/generate | resumeText、可选 targetRole/interviewType/campusProfile | questions 数组，恰好五项 |
| POST /api/college/interview/evaluate | 同上 + 五个 question/category/userAnswer | overallScore/summaryLevel、四维 dimensions、五个 questionFeedbacks、三条 actionableAdvice |

模拟面试真实联调发现 glm-4-flash 会把单项 schema 错误重复嵌套；已改为按接口给出完整五项结构，并只对面试调用设置 JSON 对象模式、4096 输出 token、temperature=0.2。校验失败返回中文业务错误。总分使用五题 AI 分数算术平均；未答题由服务端强制记 0 分，避免未答被补全。85/70 分分别对应优秀/良好，其他待强化。分数是练习反馈，不预测录取。

新增视觉变量：

- ARGUS_VISION_MODEL，默认 glm-4.6v-flash。
- ARGUS_VISION_MAX_IMAGE_MB，默认 5，服务端硬上限 5。
- ARGUS_VISION_TIMEOUT_SECONDS，默认 30，最大 60；连接 8 秒。
- 沿用后端 ZHIPUAI_API_KEY、spring.ai.zhipuai.base-url，不向浏览器下发凭据。

上传两端同步：VITE_MAX_UPLOAD_SIZE_MB=50、ARGUS_MAX_UPLOAD_SIZE_MB=50、ARGUS_MAX_REQUEST_SIZE_MB=60。Nginx client_max_body_size 60m；保留原 SSE/透传头配置。工作台单轮材料目前 16000 字；提取超过该量时前端明确截取并提示拆分，面试简历支持 30000 字。

视觉请求采用 RestClient/OpenAI 兼容 image_url JSON，图片仅在上限内 Base64 编码后发送，不记日志、不入 workspace。WebP 在浏览器上传路径转 JPEG。文字解析不安装 OCR，扫描 PDF 没有文字时建议上传页面截图，不自动将整份 PDF 栅格化。

协议参考：[智谱官方视觉工具实现](https://github.com/zai-org/GLM-V/blob/main/skills/glmv-caption/scripts/glmv_caption.py)。实际模型可用性以供应商响应为准。

## 5. 低内存链路

- multipart file-size-threshold=0B，接收体落磁盘。
- 直传与分片均使用 8 KiB 缓冲计算哈希和传输。
- 3 MiB 分片无法直接依赖要求中间 part 至少 5 MiB 的 S3 compose；改为临时磁盘合并、校验全文件 SHA-256 和声明大小后流式上传。
- StoredObjectDocumentReader 先缓存到临时文件，finally 删除；PDFBox 使用临时文件 scratch，最多 150 页/200000 字。
- DOCX 使用 StAX 流式提取正文/表格文字，禁用 DTD/外部实体，正文解压 XML 限制 16 MiB；TXT/MD 严格编码流式解码，保留 UTF-8/UTF-16 BOM/GB18030 兼容，输出约 200000 字。
- 单线程入库、8 个排队任务和 ES 开关未放宽；重型 PDF/DOCX 提取与入库还共享解析信号量，忙时明确提示重试。
- 控制输出不等于能证明任意损坏/恶意 PDF 都不耗尽资源；未执行 2G 服务器复杂文件压力测试。

## 6. 验证命令与结果

```powershell
# JDK 21；本机用缓存镜像设置与离线依赖运行
cd Argus-backend
./mvnw.cmd -o -s "$env:TEMP/argus-maven-settings.xml" clean test package
# 63 tests / 0 failures / 0 errors；BUILD SUCCESS

cd ../Argus-frontend
npm run build
# vue-tsc + Vite 通过，dist 约 2.14 MiB；保留既有大 chunk 警告

cd ..
node --test sql/tests/experiment-regression.mjs sql/tests/sample-citations.mjs
# 3 项通过
python sql/tests/nine_upgrades_browser.py
# Vite 15173；API fixture，仅 UI/续期行为回归；7 组检查通过
python sql/tests/nine_upgrades_live.py --storage-only
# 真后端 / 真 MinIO / 独立 argus_test，50 MiB 分片入库 READY
python sql/tests/nine_upgrades_live.py
# 真五题生成与评分、50 MiB 入库通过；OCR 供应商 429/1305，所以整组按 PARTIAL 非 0 退出
```

新增测试覆盖文件提取协议/截断/超限/扫描 PDF、面试结构与评分边界/未答题计零、Unicode 与 DOCX/PDF 限额、磁盘合并及哈希、禁止 Base64 保存。浏览器强制 Token 过期、图片超限、上下文携带、经历编辑、完整面试导出、双模式路由、深浅主题与 1440/390 屏宽。

本次没有隐藏首次失败：15 MiB 超限旧测试已按新阈值调整为 51 MiB；真实面试的 JSON 结构问题经过输出模式与独立 schema 修复后再验通过。运行日志检查未发现 data:image Base64。

## 7. 深色主题重点文件

- `Argus-frontend/src/views/HomeView.vue`
- `Argus-frontend/src/views/admin/MetricsView.vue`
- `Argus-frontend/src/views/qa/components/QaComposer.vue`
- `Argus-frontend/src/views/qa/components/QaMessage.vue`
- `Argus-frontend/src/views/qa/components/QaSidebar.vue`
- `Argus-frontend/src/views/qa/components/QaTranscript.vue`
- `Argus-frontend/src/views/documents/components/DocFiltersBar.vue`
- `Argus-frontend/src/views/documents/components/RecentFailuresPanel.vue`
- `Argus-frontend/src/views/documents/components/UploadDialog.vue`
- `Argus-frontend/src/views/assistant/components/AssistantComposer.vue`
- `Argus-frontend/src/views/assistant/components/AssistantMessage.vue`
- `Argus-frontend/src/views/assistant/components/AssistantSidebar.vue`
- `Argus-frontend/src/views/assistant/components/AssistantTranscript.vue`
- `src/assets/main.css`：语义别名、原生/Element Plus 表单与禁用状态。
- `src/layouts/DefaultLayout.vue`、`ThemeToggle.vue`：树形导航、常驻双态开关、手机顶栏布局。
- `CollegeView.vue/college.css`、新抽屉、模拟面试、统一问答容器：均使用主题变量。

## 8. 本机入口与回退

本机前端 http://127.0.0.1:15173/，后端 127.0.0.1:15174。Vite 仅开发代理到该端口；真实云端 key 只在后端进程环境。测试 PostgreSQL 15439/argus_test、MinIO 19000；ES 关闭。可点击一键体验创建自己的普通用户空间。

代码在独立分支，主分支未改。回退使用上一版制品和配置，无需执行数据库降级；新旧 entries/pages 结构兼容。若回退到 10 MiB，需要同步降低前后端/Nginx 阈值并留意此前上传的大文件。已有个人材料不应批量删除。

## 9. 完整变更清单

以下清单由本次 Git 差异生成（不含旧缓存目录 tests/upstream/__pycache__）：

```text
M	Argus-backend/deploy/README.md
M	Argus-backend/deploy/application-prod.env.example
M	Argus-backend/deploy/env/server-a.env.example
M	Argus-backend/deploy/env/server-b.env.example
M	Argus-backend/deploy/nginx.conf
M	Argus-backend/deploy/server-b.compose.yml
A	Argus-backend/src/main/java/com/argus/rag/college/CollegeFileExtractController.java
A	Argus-backend/src/main/java/com/argus/rag/college/MockInterviewController.java
M	Argus-backend/src/main/java/com/argus/rag/college/NoticeController.java
M	Argus-backend/src/main/java/com/argus/rag/college/WorkspaceController.java
M	Argus-backend/src/main/java/com/argus/rag/document/service/DocumentPreviewService.java
M	Argus-backend/src/main/java/com/argus/rag/document/service/DocumentUploadService.java
M	Argus-backend/src/main/java/com/argus/rag/engine/youcom/YoucomSearchService.java
A	Argus-backend/src/main/java/com/argus/rag/ingestion/service/pipeline/parser/BoundedTextWriter.java
M	Argus-backend/src/main/java/com/argus/rag/ingestion/service/pipeline/parser/DocxDocumentParser.java
A	Argus-backend/src/main/java/com/argus/rag/ingestion/service/pipeline/parser/ParseBudget.java
M	Argus-backend/src/main/java/com/argus/rag/ingestion/service/pipeline/parser/PdfDocumentParser.java
M	Argus-backend/src/main/java/com/argus/rag/ingestion/service/pipeline/parser/TextDecodingSupport.java
M	Argus-backend/src/main/java/com/argus/rag/ingestion/service/pipeline/reader/StoredObjectDocumentReader.java
M	Argus-backend/src/main/resources/application-dev.yml
M	Argus-backend/src/main/resources/application-prod.yml
M	Argus-backend/src/main/resources/application-test.yml
M	Argus-backend/src/main/resources/application.yml
A	Argus-backend/src/main/resources/prompts/skills/mock_interview.st
A	Argus-backend/src/test/java/com/argus/rag/college/BoundedParserTest.java
A	Argus-backend/src/test/java/com/argus/rag/college/CollegeFileExtractControllerTest.java
A	Argus-backend/src/test/java/com/argus/rag/college/MockInterviewControllerTest.java
M	Argus-backend/src/test/java/com/argus/rag/college/WorkspaceControllerTest.java
M	Argus-backend/src/test/java/com/argus/rag/document/service/DocumentUploadServiceTest.java
M	Argus-frontend/.env.example
M	Argus-frontend/src/api/assistant.ts
M	Argus-frontend/src/api/http.ts
M	Argus-frontend/src/api/qa.ts
A	Argus-frontend/src/api/sessionRefresh.ts
M	Argus-frontend/src/assets/main.css
A	Argus-frontend/src/components/ContextualAssistantDrawer.vue
A	Argus-frontend/src/components/MaterialFileUploader.vue
M	Argus-frontend/src/components/ThemeToggle.vue
M	Argus-frontend/src/composables/useChunkedUpload.ts
M	Argus-frontend/src/constants/uploadLimits.ts
M	Argus-frontend/src/layouts/DefaultLayout.vue
M	Argus-frontend/src/router/index.ts
M	Argus-frontend/src/views/HomeView.vue
M	Argus-frontend/src/views/admin/MetricsView.vue
M	Argus-frontend/src/views/assistant/AssistantView.vue
M	Argus-frontend/src/views/assistant/components/AssistantComposer.vue
M	Argus-frontend/src/views/assistant/components/AssistantMessage.vue
M	Argus-frontend/src/views/assistant/components/AssistantSidebar.vue
M	Argus-frontend/src/views/assistant/components/AssistantTranscript.vue
M	Argus-frontend/src/views/college/CollegeView.vue
A	Argus-frontend/src/views/college/MockInterviewView.vue
M	Argus-frontend/src/views/college/college.css
M	Argus-frontend/src/views/college/components/ProjectsExperiencePanel.vue
M	Argus-frontend/src/views/college/useCollegeWorkspace.ts
M	Argus-frontend/src/views/documents/components/DocFiltersBar.vue
M	Argus-frontend/src/views/documents/components/RecentFailuresPanel.vue
M	Argus-frontend/src/views/documents/components/UploadDialog.vue
M	Argus-frontend/src/views/groups/GroupWorkspace.vue
A	Argus-frontend/src/views/qa/UnifiedQaAssistantView.vue
M	Argus-frontend/src/views/qa/components/QaComposer.vue
M	Argus-frontend/src/views/qa/components/QaMessage.vue
M	Argus-frontend/src/views/qa/components/QaSidebar.vue
M	Argus-frontend/src/views/qa/components/QaTranscript.vue
M	docs/CHANGELOG-handoff.md
A	docs/upgrade-nine-prompts-report.md
A	sql/tests/nine_upgrades_browser.py
A	sql/tests/nine_upgrades_live.py
M	sql/tests/upgrade_acceptance.py
```
