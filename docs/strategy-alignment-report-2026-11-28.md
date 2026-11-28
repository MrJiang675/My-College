# 战略对齐改造与验收报告

日期：2026-11-28。分支：`codex/notice-collaboration-interview`。基线：已合并九项优化的 `main/a57ec8f`。

## 交付范围与冻结证据

本轮集中在通知防坑行动闭环、校园组队与项目贡献、经历驱动模拟面试，配套首页引导。没有公网部署，没有数据库结构迁移，没有安装本地 OCR，没有调整文件上传、解析队列、ES 降级或 RAG 检索配置。

**02 专业实践零业务改动**：`models.ts`、`ExperimentWorkspace.vue`、`prompts/skills/practice.st` 与基线按规范化换行逐字比较一致；实践引导文案及 TaskController 中实践模型提取协议逐字一致。共享 CollegeView/useCollegeWorkspace 只新增通知、项目和升学面试条件逻辑；对实践保留原来的必填、发送及数值模型行为。既有 RC/悬臂梁/统计模型回归通过。

## A. 通知一图/一文防坑行动闭环

### 输入与裁决

- 复用上一轮 MaterialFileUploader 与 `/api/college/files/extract-text`，支持文字、截图粘贴及受支持文件，不新建 OCR 服务。
- 通知左右工作区：左侧核对原文与提问，右侧 NoticeActionDashboard 展示档案快切、资格、缺文件、问老师话术、材料与时间轴。左侧“核查通知”也会更新同一个仪表盘。
- 三种内置样例：竞赛招募、奖学金评选、材料报送。明确标识虚构校务演示；样例在本地按明示学院/年级比较红绿灯。修改材料后退出样例规则，使用真实接口。
- 真实分析保留 answer/quotes/officialEvidence/needsVerification 等旧字段，新增 eligibility、missingDocuments、teacherInquiryScripts、deadlines、checklist。
- 资格引用必须逐字出自本次原文；缺档案、关闭适配、无有效引用时为黄灯。原文明确的其他学院或年级限制可覆盖模型误给的绿灯。GREEN 只代表明示范围，不代表学院认可加分或最终报名批准。
- 未验证引用的材料项不进入确定清单；没有完整年月日、时间或对应原句的节点保持 needsVerify。时间按本轮中国校园场景核对 +08:00；其他时区不自动判定为已确认。
- 非法/局部 JSON 与纯文本回复在通知路径降级为需核实结果，不冒充完整裁决。其他模块协议保持。

### 问老师、材料与日历

话术可编辑并一键复制；材料逐项勾选显示进度；日历支持单条或全部已确认节点下载。没有具体日期时不补年份、不生成臆造日程。

`src/utils/icsExport.ts` 完全在浏览器生成 VCALENDAR/VEVENT、独立 UID/DTSTAMP、明确 UTC 时间或全天 DATE，以及提前一天的 VALARM。处理反斜线、逗号、分号、换行转义和 75 UTF-8 字节折行，拒绝无时区时间与不存在日期。参考 [RFC 5545](https://www.rfc-editor.org/rfc/rfc5545)。未在真实 iOS/Android 日历应用逐一实测导入，已验证生成格式与浏览器下载。

### 经历 → 报名陈述

绿/黄灯下可勾选已有经历，调用 `/api/college/notice-application` 生成报名理由、个人优势和证明待补草稿，支持复制并带入升学就业。

服务端按当前登录用户读取 college_workspace，只接受其中匹配的经历 ID。请求不接受客户端伪造他人的经历正文；不存在或属于其他账号的 ID 返回错误。团队成果与个人贡献在提示中严格区分，未知指标用待补充，不替学生声称资格获批。

## B. 协作小组与我的项目

- 新增 `/api/groups/collaboration`：成员数、共享文档数、READY 数、待处理申请；SQL 通过当前用户成员关系过滤小组，待处理申请只向组长统计。
- 不增加 DDL。创建小组沿用 description，以 `【竞赛组队】`、`【课程设计】`、`【科研合作】`、`【学习互助】` 前缀保存场景，兼容旧纯文本说明；原创建、邀请、成员、申请接口不变。
- 顶部总览、场景胶囊、小组卡片、快速创建、编码申请加入、招募/邀请文案复制。文案明确需要组长审批，不伪造已加入状态。
- 选中小组后进入项目进展与分工、共享资料库、成员分工与申请管理三个视图。资料和成员复用真实权限接口；问答入口携带当前组上下文。
- 项目群聊整理改用真实 `/api/college/project`，输出 projectName/teamOutcome/personalContribution/evidenceLocation/missingEvidence。本人身份未明确时个人贡献为空，需手动补全后才可收录。
- 项目草稿按当前账号与 groupId 分键保存，可标记进行中/已完成。统计“进行中协作项目（本人）”是本人有内容且未完成的分组草稿，不冒充全组共享项目数量。项目进展草稿仍是个人工作区，真正共享的是小组资料与成员协作。
- 一键收录保留 groupId、个人贡献与团队背景；同一 entries 同时供通知陈述、升学就业与模拟面试使用。
- 评委独立账号看到一个明确标记的只读协作场景：三种角色、项目原始记录与共享资料文本示例。示例成员与 READY 不混入真实统计，不创建假邀请或真实成员。用户可另建自己的真实小组。

## C. 经历证据驱动模拟面试

复用原 MockInterviewController、MockInterviewView 与上传提取能力，不新建模拟答辩。新 UI 只有实习技术/业务面、保研/夏令营面试、秋招/校招综合面；旧接口场景参数仍可兼容，但提示按面试处理。

- 生成请求可选新增 selectedExperiences；旧请求不传也可用。Answer 可选新增 targetWeakness，保留旧 Java 构造方式和 JSON 请求兼容。
- 升学就业保存目标岗位/院校方向和勾选经历；开始面试后从同一工作台读取，无需将简历正文放入 URL。
- 简历文字、已有经历勾选导入、文件/截图提取与一套标识清楚的简历示例并存。
- 五题包含 targetWeakness 与 focusPoint，优先追问“参与但行动不清”“提升但无基线”“团队成果没拆个人贡献”“目标能力缺口”。题目可折叠查看追问意图。
- 报告保留四维评分、逐题优缺点与示范回答，新增 followUpHint；Markdown 同步导出薄弱点和二轮追问，支持返回升学就业与再来一轮。
- 真实联调发现轻量模型会合并相近题目的反馈；加入明确 index 与禁止合并要求，首次不足五条时最多补全一次，仍由完整校验决定是否展示，总超时保持 45 秒、并发保持 1。不会复制一条反馈凑五条。

## 接口与发布兼容

| 接口 | 本轮变化 |
| --- | --- |
| POST /api/college/notice | 兼容旧字段，新增五类行动结构 |
| POST /api/college/notice-application | 新增；原文与当前账号经历 ID → 报名陈述 |
| POST /api/college/project | 兼容 answer/quotes，新增团队/个人结构 |
| GET /api/groups/collaboration | 新增；成员范围内的轻量聚合 |
| POST /api/college/interview/generate | 可选 selectedExperiences，题目增加 targetWeakness |
| POST /api/college/interview/evaluate | Answer 可选 targetWeakness，反馈增加 followUpHint |

没有新增凭据或必填环境变量。前后端可分步更新：先后端，旧前端继续使用兼容字段，再发布新前端。新前端建议与本轮后端配套使用；旧后端缺新接口会显示可读错误，旧通知只返回 answer/quotes 时会安全回落黄灯，不导致页面报错。回退制品不会要求回滚数据库结构；description 场景前缀对旧客户端只是普通文字。

## 验证记录

- Maven Wrapper `clean test package`：72 项测试，0 failures / 0 errors，成功打包。
- `npm run build`：vue-tsc + Vite 通过；保留已有大 chunk 警告。
- Node：日历三项、实践模型两项、样例引用一项，共六项通过。
- `strategy_browser.py`：通知红绿灯/复制/日历/陈述、组队筛选/邀请/项目收录、携带目标和经历的薄弱点面试/导出、深色与 1440/390px 布局。
- `nine_upgrades_browser.py`：上一轮七组稳定性/上传/导航回归通过，更新了本轮按钮名称兼容定位。
- 实际运行使用 test + 本机独立 argus_test（PostgreSQL 15439）、MinIO 19000、后端 15174，ES 关闭、JVM Xmx384m；没有访问共享库。
- 真实云端联调最终通过：成员范围小组统计/匿名拒绝、通知五类结构与逐字依据、原文日期提取、当前账号经历报名陈述、团队/个人项目草稿、五题 targetWeakness 与五条 followUpHint。`strategy_live.py` 最后一次以 0 退出。

已有视觉提取接口本轮直接复用，未声称前轮的供应商 429/1305 限流已解决；此轮核心链路以文字通知与已提取材料验证，不把内置样例标成真实 OCR 结果。

## 本机检查入口

http://127.0.0.1:15173/，使用一键体验。先打开校园机会与办事，载入竞赛样例，切换信息工程学院/大一与大二或大三；复制问老师话术、导出日历。随后到协作小组粘贴本人进展，确认收录经历，再从升学就业选定目标进入模拟面试。

运行测试：

```powershell
cd Argus-backend
./mvnw.cmd clean test
cd ../Argus-frontend
npm run build
cd ..
node --test sql/tests/ics-export.mjs sql/tests/experiment-regression.mjs sql/tests/sample-citations.mjs
python sql/tests/strategy_browser.py
python -u sql/tests/strategy_live.py
```

本机 Maven 复测使用缓存离线设置 `-o -s "$env:TEMP/argus-maven-settings.xml"`；Node 采用已有 Node 24 环境。浏览器脚本使用本地 Vite，API fixture 与真实 live 脚本明确分开。

## 完整文件清单

以下清单由提交前 Git 差异生成：

```text
M	Argus-backend/src/main/java/com/argus/rag/college/MockInterviewController.java
A	Argus-backend/src/main/java/com/argus/rag/college/NoticeAnalysisSupport.java
A	Argus-backend/src/main/java/com/argus/rag/college/NoticeApplicationController.java
M	Argus-backend/src/main/java/com/argus/rag/college/TaskController.java
A	Argus-backend/src/main/java/com/argus/rag/group/controller/GroupCollaborationController.java
A	Argus-backend/src/main/java/com/argus/rag/group/mapper/GroupCollaborationMapper.java
M	Argus-backend/src/main/resources/prompts/skills/campus_notice.st
M	Argus-backend/src/main/resources/prompts/skills/group_projects.st
M	Argus-backend/src/main/resources/prompts/skills/mock_interview.st
M	Argus-backend/src/test/java/com/argus/rag/college/MockInterviewControllerTest.java
A	Argus-backend/src/test/java/com/argus/rag/college/NoticeAnalysisSupportTest.java
A	Argus-backend/src/test/java/com/argus/rag/college/NoticeApplicationControllerTest.java
A	Argus-backend/src/test/java/com/argus/rag/college/StrategicTaskCompatibilityTest.java
A	Argus-backend/src/test/java/com/argus/rag/group/controller/GroupCollaborationControllerTest.java
M	Argus-frontend/src/constants/moduleGuides.ts
A	Argus-frontend/src/constants/noticeScenarios.ts
A	Argus-frontend/src/types/notice.ts
A	Argus-frontend/src/utils/icsExport.ts
M	Argus-frontend/src/views/HomeView.vue
M	Argus-frontend/src/views/college/CollegeView.vue
M	Argus-frontend/src/views/college/MockInterviewView.vue
A	Argus-frontend/src/views/college/components/NoticeActionDashboard.vue
M	Argus-frontend/src/views/college/useCollegeWorkspace.ts
A	Argus-frontend/src/views/groups/CollaborationOverview.vue
M	Argus-frontend/src/views/groups/GroupWorkspace.vue
M	Argus-frontend/src/views/groups/GroupsPage.vue
M	Argus-frontend/src/views/groups/components/modals/CreateGroupModal.vue
M	Argus-frontend/src/views/qa/UnifiedQaAssistantView.vue
M	docs/CHANGELOG-handoff.md
A	docs/strategy-alignment-report-2026-11-28.md
A	sql/tests/ics-export.mjs
M	sql/tests/nine_upgrades_browser.py
A	sql/tests/strategy_browser.py
A	sql/tests/strategy_live.py
M	sql/tests/upgrade_acceptance.py
```
