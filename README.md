# My College · Argus 学生工作台

学生登录后从「我的大学」进入课程学习、专业实践、校园机会、项目记录、校园办事和升学就业。每个板块保存自己的对话和材料。可以在项目中确认个人贡献，再在实习或升学任务里选择引用的经历。

## 获取与构建

| 目录 | 内容 |
| --- | --- |
| [Argus-frontend](Argus-frontend/) | Vue 工作台、知识库、小组与登录界面 |
| [Argus-backend](Argus-backend/) | Java 21 / Spring Boot 服务、AI 对话、文件入库与权限 |
| [sql](sql/) | PostgreSQL 初始化与工作台迁移 |
| [tests](tests/) | 跨服务验收与浏览器回归 |
| [docs](docs/) · [demos](demos/student-workspace/) | 产品交接与早期静态原型 |

基础代码源自 [原 Argus 工程的固定版本](https://github.com/Bruce-computer/University-Student-Professional-Learning-Platform/tree/d609ad042ab0ea4c7da626f30df754ef3d1d1e4f)。本仓库直接维护完整源码。

```sh
git clone https://github.com/MrJiang675/My-College.git
cd My-College
cd Argus-frontend
npm ci --legacy-peer-deps
npm run build
```

需要 Python 3.9+、Node 22.12+、Java 21。正式前端从站点根路径进入，学生登录后默认进入 `/app/college`。发布静态产物时配置 SPA 回退到 index.html，并将同源 `/api/` 反代给运行本版代码的 Argus 后端。

## 隔离环境运行

沿用 Argus 的 PostgreSQL、pgvector、文档存储、索引与模型设置。首次初始化的本地 `argus_test` 数据库在装好上游所需扩展后，先应用 `sql/schema.sql`，再应用本仓库新增的 `sql/college.sql`。已有 Argus 数据库只需新增表迁移。以目标数据库对应的角色运行 SQL。

```sh
psql -h localhost -d argus_test -U postgres -v ON_ERROR_STOP=1 -f sql/college.sql
cd Argus-backend
./mvnw clean test
./mvnw spring-boot:run -Dspring-boot.run.profiles=test
```

test profile 使用本地 argus_test。将测试数据库密码和模型密钥分别注入 ARGUS_TEST_DB_PASSWORD 和 ZHIPUAI_API_KEY；存储与索引参数见上游 application-test.yml。运行前端开发服务器时，在另一终端进入 Argus-frontend 并执行 npm run dev。开发服务器默认代理到本机 10001。

## 体验与验收

- 专业实践从空白问题开始。试用 RC 示例，默认 R=10 kΩ、C=100 μF、V=5 V，时间常数 1 s。发送“把电阻翻倍”，观察时间常数变为 2 s。支持力学模型、TXT/MD 指导书、曲线对比和实验快照。两种示例与明确的电阻翻倍可离线计算，自由提问调用助手。
- 学习助手可选自己的资料库，用后端基于资料的问答返回文档名和原文片段。知识库文档管理、原 AI 助手与小组保留原入口。直接上传文本的任务可以引用文本进行解释。
- 校园机会读取清华大学计算机系公开通知栏目，可查看来源、引用正文。附件或其他院系的认定规则需另外提供。学校、学院、专业与年级作为对话上下文。
- 工作台 API 的 GET/PUT /api/college/workspace 从登录态取得用户 ID，使用 revision 防止并发覆盖。AI 不执行模型返回的程序。数值模型只接受已支持的类型和有效参数，采访核实只显示输入材料中能找到的被引原句。
- 浏览器回归测试需要 Python Playwright 和 Microsoft Edge。构建前端后运行 python tests/browser_workspace.py。该测试使用 API 替身验证交互。Java 测试覆盖用户归属和冲突返回；上线前仍需实测登录、真实 AI、资料库授权、两账户隔离以及跨设备恢复。

[产品交接文档](docs/handoff.md) · [独立静态 Demo](demos/student-workspace/index.html)
## 本地双账号集成验收

在专用测试实例监听 127.0.0.1:11001，并应用用户、refresh token 及 college_workspace 表后，可运行 python tests/live_workspace.py。脚本每次创建两个随机测试账号。覆盖未登录返回 401、两个独立登录会话读回同一份草稿、另一账号看不到草稿，以及两个会话并发首次保存或更新时旧版本返回 409。本轮已在 Spring Boot test profile 和 PostgreSQL 16.16 的隔离数据库上通过。

知识库使用 /api/qa/stream-ask 增量展示回答并保存引用片段。浏览器回归还覆盖长上下文追问：限制历史长度以完整发送学生本次问题，超长新问题保留在输入框并提示缩短。该 SSE 回归使用测试事件；真实模型推理和私有讲义检索仍需在配置模型与索引服务的环境验收。
## 真实课程资料与实验验收

本机工作台通过 15173 访问，Spring Boot 监听 15174。课程库上传入口位于文档管理：先建自己的小组，选择该组上传文档，等待 READY；然后在学习助手选择同一资料库。回答末尾可展开「核对资料原文」。本地独立 MinIO、Elasticsearch 8.15.3（IK）、PostgreSQL 16 + pgvector 分别绑定回环端口 19000、19200、55439。test profile 的 embedding-2 输出为 1024 维，新库使用 sql/schema.sql。已有向量库请先核对维数再迁移。

配置真实模型和上述本地服务后运行 python tests/live_course.py。它创建专用账号和课程组，用自编讲义验证入库、流式回答和引用，再检验自然语言生成与修改 RC 模型。短文本也可以直接附在「学习助手」的当前材料，无需建立课程组。直接材料模式只展示能在输入中逐字找到的引用。
