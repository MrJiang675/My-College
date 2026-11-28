# 工作台全量体验重构：实现与验收记录

日期：2026-11-28。分支：`codex/material-package-dynamic-lab`。基线：`main/c39a3e5`。

本轮按附件 Step 1 → Step 4 执行。最新要求明确升级专业实践，因此本轮解除上一轮报告中的实践冻结；此前报告仅描述其当时交付，不能作为本轮范围限制。

## 1. 已实现

### Step 1：安全 Markdown 和分模块输入

- 统一 `MarkdownContent` 用于工作台普通/流式回复、通知结论、申请打磨、面试亮点/建议/追问及情境助教。
- 使用已有 marked 的 GFM/换行；先转义输入 HTML，再对白名单 DOM 标签与链接协议检查。清除内联事件和其他属性，不加载回复中的远程图片。原始 HTML 按文字显示，不能执行脚本。
- 六模块专用输入元数据；通知和升学就业由专属流程承接，其余使用统一元数据驱动的输入区。
- 深浅主题复用现有 CSS 变量，没有更换视觉体系。

### Step 2：七套模型和通用公式

- 本地 TS 内置 RC、悬臂梁、正态分布、阻尼振动、抛体运动、最大功率传输、二项分布；无数据库种子或 DDL。
- 模型适用条件、参数滑块、确定性曲线和 HUD。阻尼正负包络分开绘制，避免连接成斜线；功率/效率明确量纲不同，标出负载等于内阻的最大功率点。
- `formula` 联合协议：表达式、x 变量/区间/轴标题与最多 8 个参数。递归下降语法处理加减乘除、右结合幂、一元符号、白名单函数和常量，无 eval/new Function。
- 输入限制 500 字符/256 token/32 层括号，401 点采样拒绝非实数、奇点与过大数值。公式对比仅允许相同表达式、变量和区间。重置恢复当前公式初始参数，不擅自更换公式。
- 后端协议增加七种 kind/formula，明确默认参数和假设披露；已返回内置 kind 的缺失数组值可确定性补齐，用户给定的数值保留。上传实验文本并要求模拟后，收到有效模型即挂载并滚动实验台。
- AI 负责识别关系与参数，浏览器负责验证和计算；不宣称任意一句话均能得到可信物理仿真。没有明确关系或参数冲突时仍需澄清。

### Step 3：通知到提交材料包

- 首块公共来源提供任意公开 HTTP/HTTPS 链接抓取与清华计算机系公告入口；紧接行动概览，显示完成进度、材料/待补/待确认数量和 JSON 备份。
- 三个大步骤视图：综合分析/资格/机会 → 理清单 → 去办事。原文逐字分段展示、引用回跳、附件引用告警和未挂靠时间线索。
- OCR 引用用 NFKC、空白与标点归一化核对，保留原文作证。学院名称/简称差异保守黄灯核实；明确年级不符仍红灯，不把模糊匹配当作已证实资格。
- AI 与规则候选要求可逐条加入/拒绝/全部加入，分电子、纸质、系统填报；可手工新增、修改格式/份数/来源、备注、关联和解绑文件。
- **学生提交材料仅保存在浏览器内存 Map**，不发送给提取接口，不进工作台 JSON。不做内容合格或签字真实性判断，要求本人逐项确认。通知/JD 等分析来源仍可使用既有文本提取上传接口，界面明确区别。
- 文件名/扩展名核对、规范打包名称；不允许靠改扩展名把 CSV 冒充 PDF。支持明确文件名和“学号_姓名_材料.pdf”模板比对，遇原文“命名”要求时还需本人勾选核实；不会声称能自动理解所有学校命名规则。
- 真结构 PDF、OOXML XLSX、CSV、TXT 示例，其中申请书故意为 CSV；“修复”仅替换明确标记的示例，真实材料要求自行从原软件正确导出。
- 所有文件关联、格式、内容、签字（若要求）、份数确认完成才可 ZIP。ZIP 是纯浏览器 STORE + UTF-8 + CRC-32，自动加 `提交前核对清单.md`，不代表已提交或学校审核通过。
- 本地关联上限 80 文件/100MB，单文件 25MB；ZIP 上限 100 文件/100MB。刷新/切模块后需重新关联文件。JSON 只备份文本要求和分析；导入会清除文件关联、勾选与日期确认，防止误报就绪。
- 话术可编辑复制；待核实时间可填写完整北京时间、确认后解锁单条/批量 ICS；保留办理地址与已有本人经历生成报名陈述接口。

### 安全抓取接口

`POST /api/college/notices/fetch-url`，登录后 `{ "url": "https://公开官网/通知" }`，响应数据含 `url/title/publishedAt/body`。

- 仅 HTTP/HTTPS 默认端口，无用户凭据或 fragment；解析全部 DNS 地址，任何私网、回环、链路本地、保留/测试网段都拒绝。IPv6 只接受排除保留/过渡网段后的全球单播。
- TCP 连接直接使用校验过的 IP，HTTPS 仍按原域名执行 SNI 和证书主机名校验，避免预检 DNS 与实际连接发生重绑定。每次跳转重新校验并固定地址，最多三次跳转。
- 总请求上限 8 秒、并发最多 2；头部和正文有界，正文 2MB，最终文本最多 30000 字。前端单轮分析仍限制 16000 字并提示截断。UTF-8/GBK/GB2312 charset，剥离脚本/导航，按段落输出。禁止抓取/登录/超时给复制正文或贴图引导。
- 不继承本机代理去绕过地址规则，不放开私网地址来“修好”演示。拒绝非 HTML/纯文本和非 identity 编码；不执行网页脚本，不支持动态渲染/内网登录/自动下载附件。

### Step 4：升学就业渐进流程

- 首屏先确认申请类型和目标，空目标即时红字；快捷项只填入，点击确认才进入下一步。
- 第二步上传/粘贴要求、选择本人经历；缺 JD/简章或经历即时提示，点击继续会突出缺口。也可明确选择仅看通用建议。
- 第三步展示证据矩阵、Markdown AI 对话、陈述草稿编辑导出、模拟面试。矩阵为原文关键词辅助匹配，证据强度由本人核实，不能冒充语义评审。
- `careerStep/genericAdvice` 及目标按“实习/升学”分开保存在既有工作台；再次进入恢复进度。跳转面试前等待保存成功，面试复用已选经历和目标。

## 2. 验证证据

|阶段|前端 `npm run build`（含 vue-tsc）|后端 `mvnw.cmd -o -s <本机临时 Maven settings> clean test`|
|---|---|---|
|Step 1|通过|72 项通过|
|Step 2|通过|74 项通过|
|Step 3|通过|78 项通过|
|Step 4/最终|通过|78 项通过，并成功 package|

最终前端有现存主包大于 500kB 的 Vite 优化提示，无构建 error；未升级依赖版本。

可复现命令（正常 Maven 配置下不需要本机离线参数）：

```powershell
cd Argus-backend
.\mvnw.cmd clean test
cd ..\Argus-frontend
npm run build
cd ..
# Node 24 原生读取 TS；不安装额外测试依赖
node --test sql/tests/experiment-regression.mjs sql/tests/seven-experiments.mjs sql/tests/material-package.mjs sql/tests/ics-export.mjs
# 已有 Playwright Python 和 Edge，Vite 位于 15173；接口夹具测试
python sql/tests/seven_upgrades_browser.py
# 可选：隔离 argus_test 后端 15174，真实供应商调用
python sql/tests/seven_upgrades_live.py
```

- Node 10 项全部通过：旧 RC/梁/正态回归、七套模型与二项概率和、公式优先级/注入/奇点、ZIP CRC/重复路径/OOXML、扩展名伪装修复阻断、ICS。
- PDF 另用本机 PyMuPDF 打开验证一页和示例文本；XLSX 用 Python zipfile 和 XML 解析核查所有组成文件及关系路径。未安装 openpyxl，没有声称用 Excel 实机验收。
- 浏览器夹具验收通过：公开来源引用、原文/附件、候选清单、故意错格式→替换→人工确认→ZIP、缺年份→确认→ICS、JSON 导入恢复后阻断就绪、上传实验文本→formula→滑块/基准→七模板、Markdown 标题/粗体/表格与 XSS、职业三步缺口提醒/恢复/目标经历带入面试、390px 深色无横溢与无运行时异常。**夹具不冒充真实 AI 或真实网页抓取**。
- 隔离 `argus_test` 真实服务：未登录抓取 401；127.0.0.1/10.0.0.1/192.168.1.1/::1/file:// 均 400；真实 AI 分别返回 `damped` 和 `formula`，不是预制回答。

## 3. 必须保留的外部复验缺口

本机 DNS 将 `www.cs.tsinghua.edu.cn` / `example.com` 分别解析到 `198.18.0.92` / `198.18.0.93`（测试网段），新抓取接口按规则拒绝，返回友好 400。独立 Python 网络访问清华为 200，说明本机有网络路由/代理差异；**这不等于通过新接口抓取成功**。部署环境必须用正常公网 DNS 再测公开网站成功、GBK 页面和真实重定向链，不为适应本机虚拟地址取消 SSRF 防护。

此前云视觉曾遇供应商 429/1305；本轮未改模型供应商或视图提取实现，不宣称彻底消除此限流。浏览器上传验收采用文本提取夹具，真实模型验收从已提取的关系文本开始。

## 4. 对接和回退

- 无新增环境变量、无新依赖、无数据库迁移；保留现有鉴权刷新、文档知识库、协作小组、上传解析与 ES 降级配置。未改 compose、模型凭据或部署文件，未执行公网部署。
- 先部署配套后端再发布前端；旧通知后端缺结构时前端降级黄灯，不生成确定资格。
- `Model` 新增四个 kind 与 formula；旧 rc/beam/normal JSON 兼容。旧版前端不理解新 kind，回退时需前后端一并回退，并先导出工作台备份。
- 新通知元数据可持久化，不包含材料 bytes/File/Base64；分享项目 JSON 前注意其中可能有本人原文/档案信息。
- 后续任何扩展都不能把学生提交文件送给 AI 或持久化到服务器。本机文件“格式符合”只指扩展名/命名检查，内容、盖章、学校口径由本人核实。
- 当前仅本地分支；没有自动推送或合并。

## 5. 文件清单

- `Argus-backend/src/main/java/com/argus/rag/college/NoticeAnalysisSupport.java`
- `Argus-backend/src/main/java/com/argus/rag/college/NoticeController.java`
- `Argus-backend/src/main/java/com/argus/rag/college/PracticeModelProtocol.java`
- `Argus-backend/src/main/java/com/argus/rag/college/PublicNoticeFetcher.java`
- `Argus-backend/src/main/java/com/argus/rag/college/TaskController.java`
- `Argus-backend/src/main/resources/prompts/skills/campus_notice.st`
- `Argus-backend/src/main/resources/prompts/skills/practice.st`
- `Argus-backend/src/test/java/com/argus/rag/college/NoticeAnalysisSupportTest.java`
- `Argus-backend/src/test/java/com/argus/rag/college/PracticeModelProtocolTest.java`
- `Argus-backend/src/test/java/com/argus/rag/college/PublicNoticeFetcherTest.java`
- `Argus-frontend/src/components/ContextualAssistantDrawer.vue`
- `Argus-frontend/src/components/MarkdownContent.vue`
- `Argus-frontend/src/constants/moduleInputConfig.ts`
- `Argus-frontend/src/constants/noticeScenarios.ts`
- `Argus-frontend/src/types/notice.ts`
- `Argus-frontend/src/utils/demoMaterialGenerator.ts`
- `Argus-frontend/src/utils/formulaEvaluator.ts`
- `Argus-frontend/src/utils/noticeMaterials.ts`
- `Argus-frontend/src/utils/safeMarkdown.ts`
- `Argus-frontend/src/utils/zipPackager.ts`
- `Argus-frontend/src/views/college/CollegeView.vue`
- `Argus-frontend/src/views/college/ExperimentWorkspace.vue`
- `Argus-frontend/src/views/college/MockInterviewView.vue`
- `Argus-frontend/src/views/college/components/CareerWorkflow.vue`
- `Argus-frontend/src/views/college/components/NoticeActionDashboard.vue`
- `Argus-frontend/src/views/college/components/OpportunitiesPanel.vue`
- `Argus-frontend/src/views/college/models.ts`
- `Argus-frontend/src/views/college/useCollegeWorkspace.ts`
- `docs/CHANGELOG-handoff.md`
- `docs/learning-notes.md`
- `docs/upgrade-seven-experience-report.md`
- `sql/tests/material-package.mjs`
- `sql/tests/seven-experiments.mjs`
- `sql/tests/seven_upgrades_browser.py`
- `sql/tests/seven_upgrades_live.py`
