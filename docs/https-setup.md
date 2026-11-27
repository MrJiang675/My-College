# Qoder Sites + IP API 发布与跨站登录清单

> 当前正式演示路线（2026-11-26 起）：Qoder Sites 托管前端，浏览器直接调用 `https://106.14.125.122/api`。DuckDNS、域名 A 记录、DNS-01/HTTP-01 证书交接和服务器同源托管前端均为历史备用方案，不再作为当前部署配置。
>
> 发布根目录：`Argus-replica`；静态目录：`Argus-frontend/dist`；启用 SPA；更新现有站点，不新建站点。
>
> 发布依据只认仓库根目录的 `.Argus 数据洞察中心.qoder.site`。`Argus-frontend/.Argus 数据洞察中心.qoder.site` 是旧快照，保留但不要手改或删除。

## 1. 当前配置表

| 项目 | 当前值 |
|---|---|
| 前端托管 | 现有 Qoder Sites 站点 |
| Sites Origin | `https://argus-insights-6ymqxlsdfpp.qoder.website`（无尾斜杠） |
| API Base URL | `https://106.14.125.122/api` |
| 发布根目录 | `Argus-replica` |
| 静态目录 | `Argus-frontend/dist` |
| SPA | 启用 |
| 发布动作 | 更新现有站点，不新建站点 |
| 当前发布描述文件 | `Argus-replica/.Argus 数据洞察中心.qoder.site` |
| 旧发布快照 | `Argus-frontend/.Argus 数据洞察中心.qoder.site`，不得手改或删除 |

## 2. 构建前端

在 `Argus-frontend` 中确认被 Git 忽略的 `.env.production.local` 包含：

```dotenv
VITE_API_BASE_URL=https://106.14.125.122/api
```

然后执行：

```sh
npm run type-check
npm run build
```

检查 `dist/index.html` 和打包后的 API 客户端，确认业务请求指向 `https://106.14.125.122/api`，不再指向 `localhost`、空地址或旧 API 域名。仅修改变量而不重新构建不会更新已发布产物。

**本地产物核查（2026-11-26）**：`Argus-frontend/dist` 的构建时间为 19:46；`assets/http-Bw8X26vZ.js` 与 `assets/index-zQ4CvKqB.js` 均包含 `https://106.14.125.122/api`。本次只做静态核查，未重新执行类型检查或构建；这些证据不证明现有 Sites 已更新，远端发布版本仍需单独验收。

## 3. 更新现有 Qoder Sites

1. 从仓库根目录 `Argus-replica` 打开发布流程。
2. 使用根目录 `.Argus 数据洞察中心.qoder.site` 作为当前发布依据。
3. 静态目录选择 `Argus-frontend/dist`。
4. 启用 SPA/history 路由回退。
5. 选择更新现有站点，不新建站点。
6. 发布后记录部署版本，并从非开发机打开 Sites 复验。

不要手改或删除两个 `.qoder.site` 描述文件；前端目录内的旧快照不代表当前发布状态。

## 4. 后端跨站配置

服务器 prod 环境至少确认：

```dotenv
ARGUS_RAG_CORS_ALLOWED_ORIGINS=https://argus-insights-6ymqxlsdfpp.qoder.website
ARGUS_RAG_AUTH_REFRESH_COOKIE_SAME_SITE=None
ARGUS_RAG_AUTH_REFRESH_COOKIE_SECURE=true
```

后端继续绑定 `127.0.0.1:10001`，由服务器 HTTPS 入口将 `/api/` 反代到该地址。Nginx 只承担 API 反代、SSE 关闭缓冲和 12MB 请求包络，不在服务器托管前端静态首页。

当前后端制品为 `Argus-backend/target/Argus-backend-0.0.1-SNAPSHOT1126.jar`，102,951,711 字节，SHA-256 `c7754c533e9badb06b8f8864269baedd1e51d673598faf27dcff770457db47eb`。该文件是打包后重命名的当前 JAR；JAR 存在不等于服务器已启动或健康检查通过。

## 5. 公网验收

```sh
curl -i https://106.14.125.122/api/auth/me
curl -i -X OPTIONS https://106.14.125.122/api/auth/login \
  -H 'Origin: https://argus-insights-6ymqxlsdfpp.qoder.website' \
  -H 'Access-Control-Request-Method: POST' \
  -H 'Access-Control-Request-Headers: content-type,authorization'
```

预期未登录 API 返回 401 JSON；预检返回 2xx，并回显准确 Origin 与 `Access-Control-Allow-Credentials: true`。浏览器中继续检查：

- Sites 页面不显示“仅前端预览”，深链接刷新可回退到 SPA。
- Network 中业务请求指向 `https://106.14.125.122/api/...`，无 Mixed Content。
- 登录、退出和手动 refresh 成功，Refresh Cookie 实际为 `HttpOnly; SameSite=None; Secure`。
- QA/Assistant 的 `text/event-stream` 分批到达，代理未缓冲整段响应。
- 两个账号替换对象 ID 被拒绝。

## 6. 回退

1. Sites 回退到上一版已发布静态产物，仍使用现有站点。
2. 后端恢复上一版 JAR 或 prod 环境变量后重启。
3. Nginx 只回退 `/api/` 反代和 HTTPS 配置，不恢复服务器前端 root。
4. 只撤销本次新增的防火墙规则，不删除原有规则。
5. 保留失败日志、Network 截图和构建/部署版本。

## 7. 提交与上传边界

不得上传或提交 `.env`（含 `.env.production.local`）、`deploy/local-run.env`、`target`、`node_modules` 或任何密码、Token、私钥、数据库/模型凭据。Sites 发布使用 `Argus-frontend/dist`；后端秘密只通过仓库外环境文件注入。

## 8. 历史方案入口

以下材料仅用于理解旧决策，不作为当前发布步骤：

- [域名、DNS 与证书协作交接（历史归档）](./handoff-2026-11-25-域名DNS与证书协作.md)
- [公网入口傻瓜教程（现行 IP API 版）](./公网入口傻瓜教程-待同学填写.md)

## 附：本机隔离库验收记录（历史）

2026-11-23 曾建立仅绑定 `127.0.0.1:5432` 的 `argus-test-postgres` 容器，数据卷为 `argus-test-pgdata`，数据库及账号均为 `argus_test`，并执行 `sql/schema.sql`。该记录只证明本机隔离环境，不代表当前 Sites + IP API 公网链路已通过。真实数据库/MinIO/ES/模型联调仍需单独验收。
