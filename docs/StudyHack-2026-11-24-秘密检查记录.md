# StudyHack · 2026-11-24 秘密检查记录

> 检查范围：`Argus-replica` 当前工作树的源码、配置、SQL、部署模板、前端源码和文档。未访问外部账号，未轮换任何真实凭据。

## 1. 结论

- **未发现当前明文真实密码、API Key、JWT、私钥或带账号密码的 URL。**
- 命中项均为开发/测试占位值、环境变量占位语法或历史脱敏说明；它们不是有效凭据。
- 新增 `application-prod.yml` 的数据库、MinIO、Elasticsearch、JWT 和模型凭据均不提供明文默认值；新增环境变量模板只包含变量名、空值和明确标注的非秘密默认策略。
- 无法从仓库证明外部账号中的历史凭据是否已经轮换，因此第 4 节仅列建议，不在本次执行。

## 2. 检查方法与边界

检查时间：2026-11-24。

检查模式包括：

- 私钥头、常见云访问 Key、模型 Key 前缀和 URL 内嵌凭据。
- 密码、密钥、令牌、API Key 的硬编码赋值。
- JWT 三段式令牌与 BCrypt 密码哈希。
- 人工复核所有命中项，区分真实值、占位值、环境变量注入和普通变量名。

排除项：`node_modules/`、`target/`、`dist/`、日志、二进制图片和压缩包。未对图片做 OCR，未扫描外部运行时环境、shell 历史、平台 Secret 管理或 GitHub 历史中的真实值。

## 3. 命中项与处置

| 文件 / 位置 | 发现 | 是否需要轮换 | 处置 |
|---|---|---|---|
| `Argus-backend/src/main/resources/application-dev.yml` | 数据库密码、MinIO、JWT、模型 Key 和 dev 管理员密码使用明显占位默认值 | 否；占位值不是真实凭据 | 保留 dev 行为；prod 不继承这些秘密默认值 |
| `Argus-backend/src/main/resources/application-test.yml` | 测试数据库密码与 MinIO 凭据要求环境注入；JWT 使用随机测试值 | 否 | 仅测试 profile；不得作为生产配置 |
| `Argus-backend/deploy/env/*.example`、`server-*.compose.yml` | 示例密码、MinIO 凭据和 JWT 使用占位值 | 否 | 部署前必须由受控环境覆盖；Compose 仍不是本次生产 JAR 路线 |
| `Argus-backend/src/main/java/com/argus/rag/auth/config/DevAdminInitializer.java` | dev 管理员密码有占位兜底，且 dev 启动会刷新密码 | 否；若有人曾把占位值用作真实密码，则应轮换 | `@Profile("dev")` 保持不变；prod 条件测试证明不会注册 |
| `README.md`、`README_EN.md`、`docs/启动流程与配置加载说明.md`、`docs/V2.0-项目文档.md`、`docs/development-log.md` | 示例命令和历史脱敏记录包含占位值或占位语法 | 否 | 不把占位文本当作真实秘密；不改写历史事实 |
| `Argus-frontend/.env.example`、前端源码 | 只有 API 地址与普通密码/令牌变量名，没有真实凭据 | 否 | 本次只修改上传大小提示/校验，未修改 API 地址或环境文件 |
| 新增 `application-prod.yml`、`deploy/application-prod.env.example` | 无真实值；生产敏感项无明文默认值 | 否 | 作为生产配置与模板纳入本次改动；prod 不再要求 DashScope Key |
| 新增 `deploy/local-run.env.example`、`start-backend.ps1` | 只有占位符和变量名；JWT 由脚本运行时生成 | 否 | 可纳入提交；脚本不输出环境变量值 |
| 本机 `deploy/local-run.env` | 当前只含占位符；后续由用户填写真实值 | 若填写后泄露则按对应凭据处理 | 已加入根目录和后端 `.gitignore`；不得上传 GitHub 或放入提交 |
| 用户提供的 Sites Origin、服务器公网 IP | 属于公开访问信息，不是密码、密钥或令牌 | 否 | 只写入状态/缺口文档，不写入秘密变量 |

## 4. 建议的外部轮换清单（本次未执行）

只有对应凭据曾经真实使用、泄露或无法确认保管状态时才轮换；动作需由账号/服务器所有者执行。

| 凭据 | 建议轮换条件 | 影响与注意事项 |
|---|---|---|
| PostgreSQL 密码 | 曾写入公开仓库、截图或多人可见文档；当前保管状态不明 | 同步更新后端受控环境变量；安排短暂维护窗口并验证连接 |
| MinIO Access/Secret Key | 曾公开或与不需要存储权限的人员共享 | 创建新凭据后更新环境变量，验证上传/下载，再禁用旧凭据 |
| JWT 签名密钥 | 曾使用默认值、弱值或可能泄露 | 使用强随机值；轮换会使现有 access/refresh token 失效，需安排重新登录 |
| 智谱模型 Key | 曾公开、额度异常或无法确认是否仅本人持有 | 在模型平台创建新 Key，更新环境变量并验证最小调用 |
| DashScope Key | 曾公开或仍被不需要的人员持有 | prod/test 已停用 DashScope 自动配置；如 dev 仍使用，再由账号所有者确认是否轮换 |
| You.com Key（如曾启用） | 曾公开或已不再需要 | 默认保持关闭；轮换后验证联网检索 |
| dev 管理员密码 | 占位值曾被实际使用，或 dev 环境曾暴露到不可信网络 | 立即停止暴露 dev；不要将该账号提升为生产管理员 |

## 5. 遗留与复核

- 生产真实环境变量、平台 Secret 和外部账号状态不在仓库内，必须由服务器/账号所有者确认。
- GitHub 同步前还会对最终暂存 diff 再做一次秘密检查；`logs/`、`需要的东西/`、构建产物和本地产物不进入提交。
- 本记录是有限范围秘密检查，不等同于完整安全审计或外部账号轮换证明。
