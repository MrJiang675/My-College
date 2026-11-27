# 任务：登录流程极简化 —— 先评估可行性，确认后再实施

> ⚠️ **本次只做评估，禁止修改任何代码。** 请先输出完整的可行性评估与方案设计，等我确认方案后，再进入实施阶段。如果你认为某些关键决策必须由我拍板，请在评估报告末尾列出问题清单向我提问，不要自行假设。

## 一、背景与目标

这是一个答辩/评审演示项目（前后端分离：`Argus-backend` Spring Boot + `Argus-frontend` Vue 3）。评委时间有限，现在的「注册 → 登录」流程摩擦太大，需要极简化：

1. **登录页只输入用户名即可进入系统**（不再要求密码，管理员账户可以在没输入时展示在输入框中方便评委验收管理员界面）；
2. **删除注册功能**（前端注册入口/表单 + 后端注册接口全部移除，相当于输入用户名后台自动注册个账号并且登录进去）；
3. **删除首页的「免费试用」入口**（首页导航栏和底部 CTA 区的两处「免费试用」按钮）。

核心诉求：评委打开首页 → 点登录 → 输入一个用户名 → 直接进入产品开始体验，全程无密码、无注册。

## 二、项目技术背景（速览）

- 后端：Java 21 + Spring Boot 3.5.0 + Spring MVC + MyBatis-Plus 3.5.15 + PostgreSQL；统一返回 `ApiResponse<T>`；JWT access token + httpOnly Cookie 中的 refresh token（表 `user_refresh_tokens`）
- 前端：Vue 3 + Vite + TypeScript + Pinia + Element Plus + Axios；`Argus-frontend/src/api/http.ts` 为 axios 实例（withCredentials）
- **前后端独立构建**：打包的 jar 只含后端，前端通过 `npm run dev` / `npm run build` 独立构建，两者需分别重新构建
- `application.yml` 当前激活 profile 为 `dev`：`DevAdminInitializer`（`@Profile("dev")`）会在启动时自动创建/刷新一个 admin 管理员账号
- 目录当前**不是 git 仓库**，不要执行任何 git 操作

## 三、现状代码地图（改动涉及面）

### 后端（Argus-backend/src/main/java/com/argus/rag/）

| 文件 | 现状 |
|---|---|
| `auth/controller/AuthController.java` | `/api/auth/{login,register,refresh,logout,me}`；`/register` 调 `authService.register` |
| `auth/service/AuthService.java` | `login(loginId, password)`：BCrypt 校验密码 + `selectByLoginIdForUpdate`（XML，用户名或邮箱匹配，行锁）+ 吊销旧 refresh token + 签发 token；`register(...)`：用户名/邮箱唯一校验、`RESERVED_USERNAMES = {admin, root, null, undefined, system}`、`PasswordPolicyValidator` 密码策略，创建 `USER` 角色 |
| `auth/model/dto/RegisterRequest.java` | 注册请求 DTO（username/email/displayName/password） |
| `auth/model/dto/LoginRequest.java` | 登录请求 DTO（loginId/password，均必填校验） |
| `auth/security/JwtAuthenticationFilter.java` | `shouldNotFilter` 白名单含 `REGISTER_PATH = /api/auth/register` |
| `auth/service/PasswordPolicyValidator.java` | 注册和修改密码共用的密码策略校验 |
| `auth/config/DevAdminInitializer.java` | `@Profile("dev")` 启动时播种 admin 账号（ADMIN 角色，带密码） |
| `user/service/AccountService.java` | `/api/account/change-password` 需验证当前密码 |
| `user/controller/AdminUserController.java` | 仅 list/get/改状态，**没有**管理员创建用户的接口 |

数据库 `sql/schema.sql`：`users` 表 `password_hash VARCHAR(256) NOT NULL`、`email VARCHAR(128) NOT NULL UNIQUE`、`username UNIQUE`。

### 前端（Argus-frontend/src/）

| 文件 | 现状 |
|---|---|
| `views/LoginView.vue` | `/login` 页：loginId + password 两个输入框；页脚「还没有账号？返回首页注册」 |
| `components/LoginModal.vue` | 首页弹窗，**登录/注册双模式**（tab 切换、注册表单、注册成功后自动登录） |
| `views/HomeView.vue` | 导航栏「免费试用」按钮（约 L116）和底部 CTA「免费试用」按钮（约 L242），均只做 `showLoginModal = true`；另有「登录」按钮和「查看演示」按钮 |
| `api/auth.ts` | `register()` / `RegisterPayload`；另有死代码 `resetPasswordByIdentity`（后端无对应接口） |
| `stores/auth.ts` | login/bootstrap/refresh；`mustChangePassword=true` 时强制跳 `/app/settings` 改密；按角色落位 ADMIN→`/app/admin/users`、USER→`/app/groups` |

## 四、需要你评估的关键设计决策

### 决策 1：陌生用户名如何处理（核心）
评委很可能随手输入任意用户名。两个方案请评估并给出推荐：
- **方案 A（仅预置账号）**：只有数据库中已存在的用户名能登录，陌生用户名报「用户不存在」。需要预置演示账号，评委仍需被口头告知账号名。
- **方案 B（自动开户，推荐倾向）**：输入不存在的合法用户名时，后端自动创建 `USER` 角色用户并直接登录成功（等价于隐式注册）。**注意**：`users.password_hash` 和 `email` 均 NOT NULL（email 还 UNIQUE），自动开户时需要写入兼容值（如随机 UUID 的 BCrypt 哈希、合成 email `username@demo.local`），不改动表结构；同时沿用/调整 `RESERVED_USERNAMES` 保护 admin 等保留名，用户名格式校验建议沿用 `USERNAME_PATTERN`（字母/数字/下划线/短横线）。

### 决策 2：admin 账号如何保护
自动开户 + 免密登录后，如果任何人输入 `admin` 也能进系统，管理员权限就裸奔了。请评估两种保护方式并推荐：
- **混合登录（推荐倾向）**：`LoginRequest.password` 改为可选。带密码 → 走原密码校验流程（admin 及后续手动建的用户用）；不带密码 → 免密路径（命中保留用户名时拒绝，要求输密码）。
- 或者其他你认为更稳妥的方案（如 admin 改名、独立管理端入口等）。

### 决策 3：连锁影响清单
- 免密用户（无密码）在「账号设置」页修改密码时 `AccountService` 要求验证当前密码，必然失败 → 前端是否需要隐藏/禁用该功能？如何区分免密用户和密码用户（是否需要后端在用户信息中加标识字段）？
- `mustChangePassword` 流程：自动开户用户应置 `false`，不受影响；admin 保持现状即可。请确认。
- 删除 `/api/auth/register` 后同步清理：后端 `RegisterRequest`、`AuthService.register`、过滤器白名单；前端注册 tab、`register()`/`RegisterPayload`、`resetPasswordByIdentity` 死代码、`LoginView.vue` 页脚「返回首页注册」链接。
- 登录页文案/字段：免密路径下「登录标识（用户名或邮箱）」应改为「用户名」；邮箱匹配逻辑是否仅保留给带密码的 admin 路径？
- 「免费试用」两处按钮删除后，首页是否保留「登录」按钮作为唯一入口（弹窗精简为仅登录模式），还是直接跳 `/login` 页？给出你的建议。

## 五、硬性约束

1. **不动会话机制**：`/api/auth/refresh`、`/logout`、`/me`、JWT 签发、refresh token httpOnly Cookie、前端 axios 401 刷新重试逻辑，全部保持原样。
2. **不改数据库表结构**：password_hash / email 的 NOT NULL 与 UNIQUE 约束在应用层兼容。
3. **admin 必须仍能用密码登录**，管理端功能（用户管理、改状态等）不能受影响。
4. 不引入新的第三方依赖；前端沿用现有 CSS 变量与 Element Plus 风格；后端遵循现有分层（Controller → Service → MyBatis-Plus Mapper，统一 `ApiResponse`，异常走 `BusinessException` 等）。
5. 现有已打包的 jar 正在用于测试，本次改动不影响它；实施完成后前后端分别重新构建（`./mvnw clean package -DskipTests` / `npm run build`）由我执行，你只需保证代码可编译。

## 六、期望产出（本次评估报告的格式）

1. **可行性结论**：一句话结论 + 主要风险点
2. **推荐方案**：含登录接口的请求/响应定义变化、自动开户（若采用方案 B）的完整流程、admin 保护细节、前端交互变化
3. **方案对比**：决策 1/2 各方案的优劣对比（简表即可）
4. **风险与边界情况清单**：并发重名开户（唯一约束冲突如何兜底）、保留用户名、用户名格式校验、免密用户改密入口、Token 过期后重新输入用户名等
5. **改动文件清单**：逐文件说明改什么（前端 + 后端）
6. **工作量估计** + **实施后的验收标准草案**（例如：陌生用户名输入即进入 /app/groups；输入 admin 无密码被拒；admin 带密码可登录；/api/auth/register 返回 404；首页无「免费试用」字样等）
7. **问题清单**：需要我拍板的决策点

请基于以上信息输出评估报告。**再次强调：本轮不要修改任何代码文件。**
