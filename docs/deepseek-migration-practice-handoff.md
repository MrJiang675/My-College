# DeepSeek 全站迁移与专业实践两页实验台交接

日期：2026-11-28。分支：`codex/practice-simulation-two-pages`。基线：`610cfe6`（前一轮 PR #11 的提交，尚不假设已合并 main）。

## 当前模型分工（红线）

|能力|实现|向量数据影响|
|---|---|---|
|普通助手、ReAct 工具调用、记忆摘要|默认 Primary ChatModel → OpenAI Starter → DeepSeek `deepseek-flash`|无|
|知识库问答、Query Planning|同一默认 ChatModel/ChatClient|检索和引用流程保留|
|工作台任务、通知/陈述、项目、模拟面试|移除智谱 Chat qualifier，使用 Primary；JSON 选项改 OpenAiChatOptions|无|
|图片文字/表格/公式提取|DeepSeek 原生 image_url + text，常规非思考|不保存原图|
|动态实验、修复|独立 DeepSeek WebClient SSE，fast/low 与 deep/high|无|
|文档切片与向量检索|**智谱 `embedding-2`，1024 维**|**原模型、表、pgvector 数据和索引保留，无迁移和重建**|

`spring.ai.model.chat=openai` / `embedding=zhipuai`，显式禁用智谱 chat、OpenAI embedding 与 DashScope chat/embedding。`primaryChatModel` 指向 `openAiChatModel`，`primaryEmbeddingModel` 指向已有智谱 embedding。业务 Java 中没有残留 `zhiPuAiChatModel` 或 `ZhiPuAiChatOptions`。

## 为什么额外增加 DeepSeekApiConfiguration

实测 Spring AI 1.1.2 在逐调用工具选项合并时会丢失默认 extraBody，仅写配置中的 thinking.disabled 不够。现在同步 RestClient 请求和异步 WebClient 请求在发送 JSON 前强制写入 `thinking.type=disabled`；两轮工具回传、流式请求均用本机 HTTP 模拟上游捕获请求体验证。该处理仅用于全站常规 ChatModel，独立仿真客户端保留 fast/low 与 deep/high。

连接和读取超时显式设置；OpenAI Chat 路径固定 `/chat/completions`，base 可以为官方根地址或带 `/v1` 的兼容前缀，单测验证不重复追加 `/v1`。没有改动 Spring MVC 技术栈，没有引入 Netty 服务或本地模型。

## 配置与部署变量

真实 Key 仅由运行环境注入。**DEEPSEEK_API_KEY 不替代 ZHIPUAI_API_KEY**，后者仍是向量入库/查询必需凭据。

|环境变量|默认值|用途|
|---|---|---|
|`DEEPSEEK_API_KEY`|空（OpenAI 自动配置占位 test-key）|所有 DeepSeek 请求的真实凭据；占位不是可用 Key|
|`DEEPSEEK_BASE_URL`|`https://api.deepseek.com`|OpenAI 兼容基础 URL|
|`DEEPSEEK_CHAT_MODEL`|`deepseek-flash`|对话、工作台、QA、Agent、监控兜底模型名|
|`DEEPSEEK_VISION_MODEL`|`deepseek-flash`|图文识别|
|`DEEPSEEK_SIMULATION_MODEL`|`deepseek-flash`|实验生成与修复|
|`DEEPSEEK_SIMULATION_THINKING_LEVEL`|`low`|快速仿真的默认推理档位；deep 使用 high|
|`DEEPSEEK_CONNECT_TIMEOUT_SECONDS`|`10`|连接超时，限制 1—30 秒|
|`DEEPSEEK_READ_TIMEOUT_SECONDS`|`90`|读取/空闲超时，限制 5—180 秒；仿真另有总时长上限 timeout+30 秒|
|`DEEPSEEK_SIMULATION_MAX_TOKENS`|`8192`|仿真输出预算，限制 1024—16000|
|`DEEPSEEK_SIMULATION_MAX_CONCURRENT`|`3`|仿真与修复共用许可，限制 1—3|

四套现有 env.example 已补齐。基础配置统一写在 application.yml，dev/prod/test 继承并明确使用生成/向量分离；没有改数据库、MinIO、compose、JWT 或 cookie 配置。

监控不再写死 GLM：QA 优先取 ChatResponse 元数据模型名，缺失时使用配置；Agent 现有记录接口不暴露 ChatResponse，使用配置名并保留估算标志。DeepSeek 默认价格为官方高峰缓存未命中估算（每千输入 0.002 元、输出 0.008 元），可通过 `argus.deepseek.pricing.input-per-1k/output-per-1k` 覆盖。没有把估算当供应商账单，也没有猜测缓存命中费用。

本机已按用户提供的附件将 Key 写入 **Windows 用户环境变量**；未复制进仓库、文档、截图或测试代码。运行进程读取环境变量后生效。共享服务器部署仍需负责人自行注入两个模型的环境凭据，本文档不含任何值。

## 新接口

### POST /api/college/practice/simulate-stream

认证用户，JSON 包含：experimentName（200 字）、simulationGoal（3000 字）、materialText（16000 字）、imageDataUrl（JPEG/PNG/WebP base64 data URL，最多 2.5MiB）、reasoningMode（fast/deep）、可选 campusProfile。四项内容至少一项非空。

图片在浏览器先压缩：宽最多 1400px、JPEG 0.85，额外限制原图 10MB、4000 万像素和最大高度 6000px；文本文件沿用 15MB 提取入口，并限制本轮 16000 字。服务端检查 data URL、长度、base64 与格式签名，不记录图片数据；DTO toString 脱敏。

SSE：
- status：stage/message；生成时定时心跳，便于断连取消。
- delta：receivedChars；仅统计进度，不逐段执行半成品 HTML，不向 UI 展示推理过程。
- done：title/summary/html/usedModel/reasoningMode；完成后才装载画布。
- error：message/fallbackSuggested；缺 Key、限流、截断、超时等明确引导兜底。

信号量在 `Flux.defer` 内获取，`AtomicBoolean + doFinally` 只释放一次，生成和修复共享并发限制；总长 120000 字符、每个 SSE 帧有界。上游提前断流或 finish_reason=length 均不把残缺 HTML 当成功。

### POST /api/college/practice/repair-simulation

认证用户；experimentName/simulationGoal、brokenHtml（120000 字符）、runtimeError（2500 字符）。返回 ApiResponse 包装的完整 HTML 和 fixSummary；复用同一条有超时、并发释放保证的 SSE 上游链路，没有额外阻塞线程等待模型。

### 沙盒安全与学术边界

- 服务端剥离代码围栏、截掉 HTML 文档后的额外解释、移除外部脚本/link/meta/base/嵌套框架，重新构造可信 head，把 CSP 放在所有生成脚本之前。
- 强制 CSP：default-src none、connect/frame/worker/object/base/form 禁止，script/style 仅 inline，图片仅 data/blob。
- 浏览器再次净化并注入可信 head；iframe **仅 sandbox=allow-scripts，绝无 allow-same-origin**，referrerpolicy=no-referrer。
- postMessage 只接受当前 iframe.contentWindow、origin=null、固定类型及有界数据；外部伪造消息不会触发修复/导出。onerror/unhandledrejection 限流回报。图片、HTML 和代码不写工作台 JSON/localStorage。
- 记录在浏览器表格和内存，最多 1000 行/16 列；外层按钮导出 CSV，并处理电子表格公式注入字符；HTML 可离线保存。
- 画布标明“AI 动态原理仿真，仅供现象观察与定性探究，不替代真实实验测定数据”。沙盒隔离不等于公式已证明正确；保留七套确定性实验和受限 formula 引擎作为替代。

## 前端两页

|路由|职责|
|---|---|
|`/app/college/practice`|760px 居中需求/上传卡，模式切换与三个填入示例；**不挂载画布、滑块、对话、历史列表**|
|`/app/college/practice/lab`|精简工具栏、流式进度、专属 iframe；修复、查看/复制代码、下载、记录导出、内置七模型切换与公式兜底|

提交用 router.push，浏览器返回和页内返回均能回到设置页。图片直接作为 DeepSeek 图文输入，不经过 OCR；PDF/DOCX/TXT/MD 使用原有正文提取。申请者档案作为可选专业背景传递。返回或取消会中止流；相同登录用户在路由间保留内存状态，刷新需要重新选择材料，切账号清空。

旧实践 state/snapshots 没有删除；旧 CollegeView 嵌入预览也转为清爽入口。内置 Model、models.ts 数值算法、ExperimentWorkspace 和数据库中的既有资料没有改动。实验页本轮新快照在内存保存最多 30 条，仍可用原实验台导出记录，不声称新沙盒已同步保存到数据库。

## 验收与真实联调

- Step 1：81 项后端单测通过，含 provider Bean 选择、URL、两轮 Tool Calling。
- Step 2：86 项后端单测通过，含未登录、空输入、缺 Key、原生图文、围栏/CSP/错误监听、取消/完成/异常/修复的许可释放。
- 最终：**88 项后端测试通过并 package**，另补非思考流式传输与读取超时后释放测试。`npm run build`（vue-tsc + Vite）通过，仅保留现有主包体积提示；Node 原有 10 项模型/ZIP/日历回归通过。
- 浏览器夹具：清爽首屏、文件提取与图片直传区分、路由返回/取消、SSE 401 单次续期、CSP 拒绝外网与不允许读取 parent DOM、拒绝伪造消息、运行报错→修复→热刷新、记录/HTML/代码导出、缺 Key 公式兜底、七模型、390px 深浅主题与无运行时异常。resize 后等待布局稳定再断言，未为测试修改布局检测阈值。
- 原七项升级的通知材料包/ZIP/ICS/JSON 导入、升学就业渐进/面试携带等浏览器流程回归通过。
- **真实 DeepSeek**：普通工作台任务与图片 OCR 成功；通知结构、本人经历陈述、项目贡献、五题面试及评分追问成功。
- **真实混合 RAG**：隔离 argus_test 中上传短文档 → 智谱 embedding 入库 READY → DeepSeek QA 流式回答含原文引用；DeepSeek ReAct 知识库工具流式调用也成功，没有 reasoning_content 协议错误。
- **真实实验**：初次 8192 token 触发 length 限制，错误提示如实显示，未渲染残缺 HTML。本机临时环境覆盖 16000 后返回约 11701 字符的完整光学实验；真实代码在应用 iframe 中滑块、画布、动画和记录桥接可运行。
- **真实修复与原理检查**：模型初稿错误地给空气入射玻璃显示全反射临界角；使用 repair-simulation 实际校正并复验：n1=1/n2=1.5 显示不存在，n2=0.75 时临界角约48.6°，入射60°显示全反射。提示词补充量纲与适用条件自查；这只是该样例边界核验，不宣称所有 AI 公式已经自动证明。

真实生成与修复的 HTML、JSON、截图只放本机临时目录，不提交为产品默认示例。生成场景的测试夹具和真实供应商测试明确分开。

复现命令：

```powershell
cd Argus-backend
.\mvnw.cmd clean test package
cd ..\Argus-frontend
npm run build
cd ..
node --test sql/tests/experiment-regression.mjs sql/tests/seven-experiments.mjs sql/tests/material-package.mjs sql/tests/ics-export.mjs
# Vite 15173；下两项用明确的 API 夹具，不要求云模型 Key
python sql/tests/deepseek_practice_browser.py
python sql/tests/seven_upgrades_browser.py
# 隔离 test 后端 15174，配置 DeepSeek + 智谱向量凭据后才运行
python sql/tests/deepseek_live.py
python sql/tests/deepseek_knowledge_live.py
python sql/tests/strategy_live.py
# 读取临时目录里上一步真实模型产物并在应用 iframe 回放
python sql/tests/deepseek_artifact_browser.py
```

本机 Maven 使用已有 JDK21、临时 central settings 与离线依赖缓存执行等价命令；未运行任何公网部署或修改共享数据库配置。

## 部署与回退

先部署配套后端，再发前端；使用上述环境变量与原 ZHIPUAI_API_KEY。按业务复杂度评估仿真预算，8192 不保证复杂实验完整，截断会报错；本机实测使用 16000，不把临时覆盖写死为生产默认。

前端生产变量仍是既有 HTTPS API 地址；保留已部署 Nginx 的 SSE 无缓冲和足够 read timeout，本轮没有改 compose/Nginx。回退以 `610cfe6` 前后端为一组，回退前导出工作台资料；向量模型没有变更，因此不应删除或重新生成已有向量。

## 文件清单

- `Argus-backend/deploy/application-prod.env.example`
- `Argus-backend/deploy/env/server-a.env.example`
- `Argus-backend/deploy/env/server-b.env.example`
- `Argus-backend/deploy/local-run.env.example`
- `Argus-backend/pom.xml`
- `Argus-backend/src/main/java/com/argus/rag/assistant/agent/AssistantReactAgentFactory.java`
- `Argus-backend/src/main/java/com/argus/rag/assistant/service/AssistantService.java`
- `Argus-backend/src/main/java/com/argus/rag/assistant/support/config/AssistantChatModelConfiguration.java`
- `Argus-backend/src/main/java/com/argus/rag/assistant/support/config/DeepSeekApiConfiguration.java`
- `Argus-backend/src/main/java/com/argus/rag/college/CollegeFileExtractController.java`
- `Argus-backend/src/main/java/com/argus/rag/college/DeepSeekProperties.java`
- `Argus-backend/src/main/java/com/argus/rag/college/DeepSeekSimulationController.java`
- `Argus-backend/src/main/java/com/argus/rag/college/DeepSeekSimulationService.java`
- `Argus-backend/src/main/java/com/argus/rag/college/MockInterviewController.java`
- `Argus-backend/src/main/java/com/argus/rag/college/NoticeApplicationController.java`
- `Argus-backend/src/main/java/com/argus/rag/college/SimulationHtmlSupport.java`
- `Argus-backend/src/main/java/com/argus/rag/college/TaskController.java`
- `Argus-backend/src/main/java/com/argus/rag/metrics/cost/LlmCostCalculatorImpl.java`
- `Argus-backend/src/main/java/com/argus/rag/qa/service/QaChatService.java`
- `Argus-backend/src/main/java/com/argus/rag/qa/service/QaService.java`
- `Argus-backend/src/main/resources/application-dev.yml`
- `Argus-backend/src/main/resources/application-prod.yml`
- `Argus-backend/src/main/resources/application-test.yml`
- `Argus-backend/src/main/resources/application.yml`
- `Argus-backend/src/main/resources/prompts/skills/practice_simulation.st`
- `Argus-backend/src/test/java/com/argus/rag/college/CollegeFileExtractControllerTest.java`
- `Argus-backend/src/test/java/com/argus/rag/college/DeepSeekBeanConfigurationTest.java`
- `Argus-backend/src/test/java/com/argus/rag/college/DeepSeekMigrationTest.java`
- `Argus-backend/src/test/java/com/argus/rag/college/DeepSeekSimulationControllerTest.java`
- `Argus-frontend/src/api/simulation.ts`
- `Argus-frontend/src/layouts/DefaultLayout.vue`
- `Argus-frontend/src/router/index.ts`
- `Argus-frontend/src/utils/simulationSandbox.ts`
- `Argus-frontend/src/views/college/CollegeView.vue`
- `Argus-frontend/src/views/college/components/AiSimulationSandbox.vue`
- `Argus-frontend/src/views/college/components/PracticeLabPage.vue`
- `Argus-frontend/src/views/college/components/PracticeSetupPage.vue`
- `Argus-frontend/src/views/college/useCollegeWorkspace.ts`
- `Argus-frontend/src/views/college/usePracticeSession.ts`
- `README.md`
- `docs/CHANGELOG-handoff.md`
- `docs/deepseek-migration-practice-handoff.md`
- `docs/learning-notes.md`
- `sql/tests/deepseek_artifact_browser.py`
- `sql/tests/deepseek_knowledge_live.py`
- `sql/tests/deepseek_live.py`
- `sql/tests/deepseek_practice_browser.py`
- `sql/tests/seven_upgrades_browser.py`
