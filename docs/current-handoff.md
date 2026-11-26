# Argus 学生工作台｜负责人接手指南

交接日期：2026-11-26。适用于产品负责人、前后端开发者和部署负责人。

## 先找到正确的项目

- 正式仓库：[MrJiang675/My-College](https://github.com/MrJiang675/My-College)。本次核对的远端 main 为 efaba81（PR #3）。普通克隆即可取得完整 Java、Vue 和 SQL 源码。
- 本机维护目录：D:\Users\jiang\Documents\ChatGPT\qoder赛\My-College。后文所有文件路径均相对这个目录。交接材料基于已合入 main 的完整源码布局。
- 本机正式工作台：[打开首页](http://127.0.0.1:15173/)。本次检查首页返回 200，未登录访问 /api/auth/me 返回 401。注册或登录后从 /app/college 开始。该地址只在运行服务的电脑上可用。
- [独立 Demo](../demos/student-workspace/index.html)用于回看设计。D:\Argus-replica 是早期副本。日常修改请进入本仓库根目录下的 Argus-frontend、Argus-backend 和 sql。
- [总体介绍](project-introduction.md)用于报名和答辩；[功能说明](features.md)用于逐板块讲解；[产品定稿](handoff.md)记录设计决策及预期验收。三份说明和本指南均放在仓库 docs 目录，可随源码一并交付。

## 产品已经做了什么

| 学生入口 | 当前交付 | 接手时要记住的边界 |
| --- | --- | --- |
| 我的大学 | 校园档案、多个任务入口、继续上次任务。工作台按登录账号保存，旧版本写入会返回 409 | 档案影响任务上下文，学生可跨专业提问 |
| 学习助手 | 当前材料可直接问；已入库的课程资料可按小组检索。流式回答后展开文件名和原文片段 | 短材料入口接受 TXT/Markdown；长期课程库走文档管理 |
| 专业实践 | 空白对话入口，根据指导书生成 RC 充电或悬臂梁静载实验，支持对话改参、曲线比较、快照恢复。采访核实可对照引文 | 可计算模型限于已实现的两类。新闻核实与数值仿真是不同的结果形式 |
| 校园机会、校园办事 | 引用公开公告或粘贴原文，核对对象、材料、截止日和认定条件 | 自动接入清华大学计算机系一个公开栏目；其他院校通知和综测目录需另提供 |
| 我的项目、我的经历 | 编辑本人贡献、项目名、成果位置，确认后收录 | 团队成果与个人贡献由学生确认 |
| 升学就业 | 实习和升学分别保存草稿，勾选已有经历对照申请要求 | 助手生成申请准备材料，提交由学生完成 |
| 知识库、AI 助手、协作小组 | 资料上传处理、组内权限、会话管理、流式聊天、邀请和加入申请 | 一人可自行建课程组，现有团队也可以共享材料 |

## 按任务找源码

| 要修改什么 | 从哪里开始 |
| --- | --- |
| 参赛首页、工作台页面、侧栏与登录后落点 | [首页](../Argus-frontend/src/views/college/Landing.vue)、[工作台](../Argus-frontend/src/views/college/CollegeView.vue)、[布局](../Argus-frontend/src/layouts/DefaultLayout.vue)、[路由](../Argus-frontend/src/router/index.ts)、[登录状态](../Argus-frontend/src/stores/auth.ts) |
| 工作台内各板块的对话、资料上传输入、草稿、经历、助手抽屉 | CollegeView.vue 统一处理模块切换与结果呈现；[models.ts](../Argus-frontend/src/views/college/models.ts) 管理模型参数、计算和模块提示；[Experiment.vue](../Argus-frontend/src/views/college/Experiment.vue) 绘图、读数和快照 |
| 后端工作台保存 | [college 包](../Argus-backend/src/main/java/com/argus/rag/college/) 内 WorkspaceController.java、Workspace.java、mapper/WorkspaceMapper.java。GET/PUT /api/college/workspace 从登录态取用户 ID，以 revision 检查冲突 |
| 真实模型解析和公告 | 同一 college 包内 TaskController.java 提供 POST /api/college/practice 和 /api/college/reading；NoticeController.java 提供公告列表与正文 |
| 文档入库、模型检索与引用 | [Java 包根目录](../Argus-backend/src/main/java/com/argus/rag/) 下 document 接收文件，ingestion 处理入库，engine 对接存储与检索，qa 生成课程回答，assistant 处理一般 AI 会话 |
| 登录、权限、小组、管理 | 同一 Java 包根目录的 auth、user、group、metrics。前端对应页面在 [views](../Argus-frontend/src/views/)，请求封装在 [api](../Argus-frontend/src/api/) |
| 数据库 | [schema.sql](../sql/schema.sql) 初始化基础表和 1024 维向量；[college.sql](../sql/college.sql) 新增 college_workspace。对已有数据库应用增量迁移前要核对现有向量维数 |
| 运行配置与部署 | [后端配置](../Argus-backend/src/main/resources/application-test.yml)、[Vite 代理](../Argus-frontend/vite.config.ts)、[deploy](../Argus-backend/deploy/) 下的 nginx.conf、两个 compose 文件、env 示例及 Elasticsearch IK 镜像构建文件。按实际部署方式选用 |
| 自动化检查 | [Java 测试](../Argus-backend/src/test/java/com/argus/rag/college/WorkspaceControllerTest.java)、[浏览器回归](../tests/browser_workspace.py)、[真实账号与数据库](../tests/live_workspace.py)、[真实课程与实验](../tests/live_course.py) |

## 当前本机环境

| 服务 | 本机入口 | 用途 |
| --- | --- | --- |
| Vue/Vite | 127.0.0.1:15173 | 浏览器访问入口；/api 代理到后端 |
| Spring Boot，test profile | 127.0.0.1:15174 | 业务接口、智谱模型、资料权限 |
| college-vector16 | 127.0.0.1:55439，库名 argus_test | PostgreSQL 16 + pgvector，账号、文档元数据、向量、工作台 |
| college-minio | 127.0.0.1:19000 | 原始文档，当前桶 college-documents |
| college-es | 127.0.0.1:19200 | Elasticsearch 8.15.3，IK 中文索引 |

交接时上述端口均在监听，三个容器均在运行。历史上的 11001、55438 用于早期验收。当前运行进程启动于仓库整理之前，可能依赖本地 build 或临时 JAR；完成根目录构建并切换进程前，请保留这些文件。

## 新电脑如何构建和启动

1. 从 main 克隆正式仓库，安装 Java 21、Node 22.12+ 和 npm。前端在 Argus-frontend 中执行 `npm ci --legacy-peer-deps`、`npm run build`，部署产物为 dist/。后端在 Argus-backend 中执行 `./mvnw clean package`（Windows 用 .\mvnw.cmd），JAR 在 target/。
2. 准备隔离的 PostgreSQL + pgvector、MinIO 与 Elasticsearch + IK。新库安装 vector 扩展后依次执行 sql/schema.sql、sql/college.sql。已有实例先确认库名、角色及备份。数据库、对象存储和索引端口只供后端访问。
3. 通过运行环境提供配置。模型使用 ZHIPUAI_API_KEY；数据库使用 SPRING_DATASOURCE_URL、SPRING_DATASOURCE_USERNAME、SPRING_DATASOURCE_PASSWORD（也可按 test 配置提供 ARGUS_TEST_DB_PASSWORD）；登录使用 ARGUS_TEST_JWT_SECRET。MinIO 使用 MINIO_ENDPOINT、MINIO_BUCKET、MINIO_ACCESS_KEY、MINIO_SECRET_KEY；索引使用 ES_HOST、ES_PORT、ES_SCHEME。密钥值不进入仓库或交接文档。test 配置中部分存储地址有远端默认值，本地启动要显式设成本机服务。
4. 复用上表环境时，数据库 URL 为 jdbc:postgresql://127.0.0.1:55439/argus_test，MinIO endpoint 为 http://127.0.0.1:19000，ES 为 http / 127.0.0.1 / 19200。后端设 SPRING_PROFILES_ACTIVE=test、SERVER_PORT=15174，在 Argus-backend 执行 Maven Wrapper 的 spring-boot:run。前端设 VITE_DEV_PROXY_TARGET=http://127.0.0.1:15174，在 Argus-frontend 执行 `npm run dev -- --host 127.0.0.1 --port 15173`。已运行的服务无需重复启动。
5. 打开首页注册并登录。先填校园档案，刷新确认保存，再按下面两条路径核验。构建入口及其他默认端口见 [README](../README.md)。

## 验收记录与复测

- **构建与界面**：整理后的源码已通过后端 Maven 测试（27 项）、Vue 类型检查、Vite 生产构建及浏览器回归。tests/browser_workspace.py 依赖 Python Playwright 和 Edge，使用替身 API 检查参数修改、刷新恢复、引用及手机宽度。
- **真实课程链路**：建课程小组 → 文档管理上传自编讲义 → 状态 READY → 学习助手选择同一小组 → 提问并展开引用。已验证上传、真实模型流式回答与对应文档片段。来源片段从 SSE 的 evidence-overview 读取，单独的 citations 事件可能没有 snippet。
- **真实实验链路**：上传初始电压为零、R=12 kΩ、C=50 μF、直流阶跃 6 V 的指导书 → 生成 RC 实验 → 要求只将电阻翻倍。验收得到 12→24 kΩ，电容、电源保持不变，对应时间常数 0.6→1.2 s。tests/live_course.py 默认通过 15173 访问，运行会创建测试账号、课程组和文件，并调用真实模型。
- **数据隔离**：已在独立登录会话验证同账号恢复、另一账号看不到草稿、未登录返回 401、过期 revision 返回 409。tests/live_workspace.py 默认连早期隔离实例 11001；复测须核对脚本 BASE 与目标测试实例。以上是本机验收记录，本次编写交接文档只重新核对了 Git、监听端口、容器、首页响应和匿名鉴权。

## 下一任负责人先做什么

1. 优先关闭公网交付缺口。新版代码已在 GitHub，当前本机地址仅供本机浏览器使用。确认发布目标、域名证书、生产库迁移、模型与存储配置，再发布同一版本的前端和后端。
2. 部署后从校外设备验证 HTTPS、登录与刷新 Cookie、资料权限、SSE 逐段输出及文件上传。若前后端跨站部署，核对实际 Origin、凭据 CORS 和 SameSite=None + Secure；按既定服务器上传限制核对 Nginx 和后端配置。
3. 跟踪同步 /api/qa/ask 偶发的结构化输出格式错误。学生工作台已验收路径使用 /api/qa/stream-ask。新增公告源或实验模型要单独记录来源和验收条件。
4. 交付材料可用「总体介绍 + 板块说明」面向评委，用本指南面向开发与运维。统计参赛完成度时区分本机已验收与公网已验收。
