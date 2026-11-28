# 第三方对接变更记录

> **当前部署口径（2026-11-26）**：正式演示使用 Qoder Sites 前端 + `https://106.14.125.122/api`。发布根目录 `Argus-replica`，静态目录 `Argus-frontend/dist`，启用 SPA，更新现有站点，不新建站点；根目录 `.Argus 数据洞察中心.qoder.site` 为准，前端目录内描述文件为旧快照。下文 11/23—11/25 的域名、证书和服务器同源前端安排均为历史记录，不作为当前操作步骤。

## [2026-11-26] 部署路线更正为 Sites + IP API

- 用户明确当前正式演示不再使用 DuckDNS 域名直连或 DNS-01/HTTP-01 交接方案，改为 Qoder Sites + `https://106.14.125.122/api`。
- 发布固定为：根目录 `Argus-replica`、静态目录 `Argus-frontend/dist`、启用 SPA、更新现有站点、不新建站点。
- 根目录 `.Argus 数据洞察中心.qoder.site` 是当前发布依据；`Argus-frontend/.Argus 数据洞察中心.qoder.site` 为旧快照，两个描述文件均保留且不手改。
- 前端构建使用被 Git 忽略的 `.env.production.local` 注入 `VITE_API_BASE_URL=https://106.14.125.122/api`；只改环境变量不重新构建仍会得到旧 dist。2026-11-26 19:46 的本地 dist 已静态确认包含该地址，但 Sites 远端更新未验收。
- 当前后端 JAR 为打包后重命名的 `Argus-backend/target/Argus-backend-0.0.1-SNAPSHOT1126.jar`，102,951,711 字节，SHA-256 `c7754c533e9badb06b8f8864269baedd1e51d673598faf27dcff770457db47eb`；服务器启动未验收。
- 旧域名/DNS/证书交接改为历史归档；现行操作清单和教程同步更新。本次只改 docs，未修改 README、环境文件、站点描述文件或凭据。
- 上传边界：不得上传 `.env`、`deploy/local-run.env`、`target`、`node_modules` 或任何凭据。

## [2026-11-25] 组员甲 25 日下午投入与 G0 保底链路

- 新情况：用户确认 Qoder Sites 已发布；一名组员 11 月 25 日下午可投入，正在执行域名、DNS 与证书交接。
- 计划调整：G0 争取 11 月 25 晚完成，11 月 26 白天最终复核、晚上仅作缓冲；服务器操作仍由用户负责，组员甲不接触 SSH/Nginx/JAR/数据库。
- 最小体验范围：基础保底链路固定为“登录 → 群组列表 → 创建小组 → 刷新可见 → 重新登录”，只依赖 PostgreSQL 与认证；QA 冲刺在模型 Key、ES 和样板资料就绪时追加。
- 当时优先项：配置前端 `VITE_API_BASE_URL=https://<API_DOMAIN>/api`、精确 Sites Origin 的 CORS、跨站 `SameSite=None; Secure`、Nginx 443 与服务器 JAR 启动；上传、分片、课程功能和自动 401 刷新不先改。
- 已发布产物核查：本地 Sites 发布描述文件解码后的 index 与本地 `dist/index.html` 引用同一组资源；对应 `dist/assets/http-CnshRjXp.js` 的 Axios `baseURL` 为空，并在请求发出前抛“仅前端预览”错误。因此 11/25 记录的发布产物不能调用后端，当时计划待真实 API 地址确定后重建并重新发布；该域名方案现已归档。
- 文档同步：更新当时的计划、待办、域名/证书交接、外部前置缺口表和公网入口教程；为 11/24 的 QA/上传候选与生产整改交接补上范围说明。旧公网教程曾采用“已发布 Sites 跨站访问 API 域名”和独立 ACME webroot；现行教程已改为 Sites + 固定 IP API。
- 状态：仅更新文档并核查本地产物；未修改源码、未注册域名、未修改 DNS/证书、未登录或修改服务器，也未把未验证项标记为完成。

## [2026-11-25] 域名、DNS 与证书协作边界

- 改动动机：用户确认服务器由本人操作，外网同学只协助域名、DNS 和证书，不接触 SSH、Nginx、JAR、防火墙或数据库。
- 新增交接：[handoff-2026-11-25-域名DNS与证书协作.md](./handoff-2026-11-25-域名DNS与证书协作.md)。要求同学把根域名 A 记录指向 `106.14.125.122`，首选 DNS-01 在自己电脑上签发证书，并交回安全交付的 `fullchain.pem`、`privkey.pem`、续期方案和完整公网入口傻瓜教程。
- 完整教程模板：[公网入口傻瓜教程-待同学填写](./公网入口傻瓜教程-待同学填写.md)。覆盖证书落地、前端 `/api` 构建、Nginx 80/443 与 `/api/` 反代、防火墙、后端 prod 回环启动、验收与回退；同学填写实际值，用户本人执行服务器命令。
- 证书依据：Certbot 官方说明 DNS 插件可在非 Web 服务器机器上签发；Let’s Encrypt 官方说明 DNS-01 不要求公网 Web 服务器，但需要 DNS API 自动化。HTTP-01 仅作为服务器 80 端口就绪后的备选。
- 状态：仅编写交接与同步文档；未注册域名、未修改 DNS、未申请证书、未登录或修改服务器，也未生成或交付任何真实私钥。

## [2026-11-25] 本机一键启动与 Origin 尾斜杠兼容

- 改动动机：用户手动启动旧 JAR 时误用默认 dev profile，且 PostgreSQL 密码为占位/错误值，启动在恢复任务访问数据库时退出。
- 涉及文件：新增 `Argus-backend/deploy/local-run.env.example`、`start-backend.ps1`；本机 `local-run.env` 只含占位符且被 Git 忽略；更新 `.gitignore`、CORS 配置/测试和交接文档。
- 当前启动方式：编辑 `Argus-backend/deploy/local-run.env`，至少填写真实 `DB_PASSWORD`，运行 `start-backend.ps1 -ValidateOnly` 校验后再运行 `start-backend.ps1`。脚本固定 prod、自动生成 JWT 密钥；MinIO/智谱 Key 留占位时会警告但允许先启动。
- 测试影响：数据库密码错误会阻止启动；MinIO 凭据影响上传/对象存储；智谱 Key 影响文档处理、向量化和 QA；JWT 自动生成会使后端重启后的登录态失效。
- CORS 修复：配置 Origin 的尾部斜杠会归一化，确保用户提供的 `https://argus-insights-6ymqxlsdfpp.qoder.website/` 能匹配浏览器不带斜杠的 Origin。
- 验证：后端 31 项测试通过；Java 21 重新打包，JAR 102,937,021 字节，SHA-256 `df38e9d6842aa38daccbac24cdc0544a0540c919799dc62ad0a1bf6e0755465d`。真实数据库、MinIO、模型和公网入口仍未验收。

## [2026-11-24] 生产配置、准确 Origin 与 10MB 直传边界

- 改动动机：提供独立非 dev 配置，消除通配 Origin + 凭据组合，并统一已选择的 ≤10MB 直传候选限制。
- 涉及文件：`application-prod.yml`、`application-prod.env.example`、`CorsConfiguration.java`、`UploadDialog.vue`、`nginx.conf`、`server-b.compose.yml` 及 StudyHack 交接文档。
- 当前对接要求：`ARGUS_RAG_CORS_ALLOWED_ORIGINS=https://argus-insights-6ymqxlsdfpp.qoder.website/`；跨站 Cookie 使用 `None + Secure`；前端/后端文件上限 10MB，Nginx 请求包络 12MB；prod/test 不要求 `DASHSCOPE_API_KEY`。
- 服务器证据：`106.14.125.122` 的 5432/9200/9000/9001 可达，ES 为 yellow；80/443/10001 不可达，真实 CORS、HTTPS 和上传仍未验收。

## [2026-11-23] 配置跨域来源与 Nginx 前端入口

- 改动动机：开发演示需要从不同来源调用带 refresh cookie 的接口；生产前端需要统一的静态页面、API 与文件上传入口。
- 涉及文件：后端 application.yml、CorsConfiguration.java、deploy/nginx.conf，以及 docs/learning-notes.md 和本记录。
- 改动内容摘要：API 按可配置的来源模式处理带凭据请求；Nginx 在 80 端口提供前端文件，将 /api/ 原路径转发至本机 10001 端口。
- 对接注意事项：CORS 默认全放开仅用于开发或内网演示。生产建议设置 ARGUS_RAG_CORS_ALLOWED_ORIGINS=http://106.14.125.122，多来源用逗号分隔。将 deploy/nginx.conf 作为站点配置由 Nginx 的 http 块引入，按服务器实际目录调整 root，使其指向含 index.html 的 dist。前端构建使用 VITE_API_BASE_URL=/api 以经由同源代理。Nginx 请求体上限为 110m；当前 dev 后端的单文件上限为 50MB，整次 multipart 请求为 100MB。

## [2026-11-23] 修复小组页面日期回退格式的类型检查
- 改动动机：vue-tsc 在三个页签的日期回退逻辑中报告 TS2532，导致生产构建失败。
- 涉及文件：InvitationsTab.vue、JoinedGroupsTab.vue、OwnedGroupsTab.vue、docs/learning-notes.md 和本记录。
- 改动内容摘要：直接移除日期文本中从首个空格或 T 开始的后缀，消除连续数组索引；有效日期和空值分支沿用原行为。
- 对接注意事项：本地生产构建通过，dist 共 35 个文件、1,806,961 字节。依赖版本和前后端接口均沿用原配置。

## [2026-11-23] 支持托管前端通过 HTTPS 跨站刷新会话
- 改动动机：托管前端需要携带 refresh cookie 并持续接收 AI 流式响应。
- 涉及文件：CorsConfiguration、AuthProperties、AuthCookieSupport、application.yml、application-dev.yml、deploy/nginx.conf、CrossDomainConfigurationTest、HybridChunkRetrievalServiceTest 及本目录的 https-setup.md、learning-notes.md、本记录。
- 改动内容摘要：API 预检允许请求方法与请求头并缓存一小时；刷新与清除 Cookie 共用 SameSite 配置；Nginx 保留 SSE 即时转发并增加读超时和 443 模板。同步已有检索测试的 mock 参数以执行测试套件。
- 对接注意事项：ARGUS_RAG_CORS_ALLOWED_ORIGINS 生产填入实际前端 HTTPS 来源；跨站设置 ARGUS_RAG_AUTH_REFRESH_COOKIE_SAME_SITE=None、ARGUS_RAG_AUTH_REFRESH_COOKIE_SECURE=true。本地 HTTP 默认 Lax/false。Nginx 处理 TLS、SPA、110m 上传及 API 转发，启用 443 前先配置 DNS、安全组和有效证书，操作与回退见 https-setup.md。

## [2026-11-23] 增加本地隔离数据库 profile
- 改动动机：在本机检验登录与跨域 cookie，避免 dev 初始化器更新共享数据库。
- 涉及文件：Argus-backend/src/main/resources/application-test.yml、docs/https-setup.md、本记录。
- 改动内容摘要：test 绑定回环地址，数据源固定为 argus_test；沿用智谱模型及上传限制，使用独立 cookie、存储桶和索引。
- 对接注意事项：只启用 test。数据库密码通过 ARGUS_TEST_DB_PASSWORD 注入，模型及对象存储凭据均由后端运行环境提供；启动命令和已验证结果见 https-setup.md。

## [2026-11-28] 工作台九项核心体验与功能升级
- 改动动机：修复过期会话与保存失败的恢复路径，整合导航，补齐图片/短文档提取、模拟面试与情境助教，并同步提升上传能力。
- 涉及文件：前端 api/http 与 SSE API、DefaultLayout/router、MaterialFileUploader、ContextualAssistantDrawer、MockInterviewView、UnifiedQaAssistantView、工作台与主题样式；后端 CollegeFileExtractController、MockInterviewController、WorkspaceController、NoticeController、文件上传与解析器；application 配置、deploy 模板与本次测试。完整清单见 upgrade-nine-prompts-report.md。
- 改动内容摘要：REST 与 SSE 共用单次刷新重试；工作台 800ms 防抖与最多两次自动重试、备份/清理恢复；树形侧栏；经历并入项目，问答双 Tab；云端 OCR 与短文档提取；五题面试及导出；50 MB/3 MB 分片与磁盘合并；深色主题及醒目切换。
- 对接注意事项：知识库默认 50 MiB，multipart 60 MiB，Nginx 60m；短材料文档 15 MiB、图片 5 MiB。新增 ARGUS_VISION_MODEL、ARGUS_VISION_MAX_IMAGE_MB、ARGUS_VISION_TIMEOUT_SECONDS，沿用后端 ZHIPUAI_API_KEY。旧路由保留跳转。没有数据库迁移，没有部署公网服务器；云端模型可用性和容量验收以本次报告为准。
