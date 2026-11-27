# 登录流程极简化：可行性评估与实施方案

> 评估日期：2026-11-26  
> 本轮状态：只读评估，未修改任何源码、配置、数据库脚本或构建产物。  
> 目标：评委从首页进入后，只输入一个合法用户名即可创建/登录普通账号并进入 `/app/groups`；管理员仍必须使用密码；注册接口和注册入口移除。

## 1. 可行性结论

**可行，且可以在不改数据库结构、不引入新依赖、不改 JWT / refresh token / Cookie 会话机制的前提下完成。** 主要风险不是技术实现，而是安全边界：普通用户名一旦可以免密登录，就等于成为登录凭证；同时公开接口会被无限自动开户，因此该方案应明确限定为答辩/评审演示用法，不能直接当作生产认证方案。

当前代码还有两个与提示词描述不一致、实施前必须注意的事实：

1. `DevAdminInitializer` 类内默认用户名是 `admin`，但 `application-dev.yml` 实际默认值是 `argus_admin`；环境变量 `ARGUS_RAG_DEV_ADMIN_USERNAME` 还可以再次覆盖。管理员保护必须按数据库中的 `systemRole=ADMIN` 判断，不能只硬编码 `admin`。
2. 现有普通注册用户也有真实 BCrypt 密码。若免密路径允许所有 `USER` 角色账号登录，这些旧账号的密码也会被绕过；这是演示体验与安全之间的核心取舍，见文末问题 1。

## 2. 推荐方案

### 2.1 总体策略

采用以下组合：

- **陌生用户名自动开户**：不存在的合法用户名自动创建 `USER` 账号并直接登录。
- **混合登录**：密码字段改为可选；普通用户允许空密码登录，管理员必须提供正确密码。
- **按角色保护管理员**：不仅保护保留用户名，任何数据库中的 `ADMIN` 账号都必须校验密码。
- **保留现有会话链路**：登录成功后仍吊销旧 refresh token、签发新 access/refresh token、下发 httpOnly Cookie；`refresh`、`logout`、`me` 和前端 401 刷新重试逻辑不改。
- **保留首页弹窗**：首页继续使用精简后的 `LoginModal`，避免额外路由和布局改动；`/login` 独立页同步精简。
- **免密账号显式标识**：在用户信息响应中新增 `passwordEnabled`，用于设置页隐藏/禁用改密表单，不需要新增数据库列。

### 2.2 登录接口定义

建议保留现有字段名 `loginId`，避免无必要地破坏 API 兼容；UI 对用户只称“用户名”。

#### 请求

`POST /api/auth/login`

普通体验账号：

```json
{
  "loginId": "demo_01"
}
```

管理员或密码账号：

```json
{
  "loginId": "argus_admin",
  "password": "真实密码"
}
```

字段语义：

- `loginId`：必填，去除首尾空格。
  - 空密码路径：只接受用户名，必须符合 `^[A-Za-z0-9_-]+$`，最长 64。
  - 带密码路径：继续支持用户名或邮箱，最长 128，便于保留现有管理员邮箱登录能力。
- `password`：可选；`null`、缺省或空白字符串都视为未提供密码。
- 带密码时继续执行现有长度与 BCrypt 72 字节上限校验。

#### 响应

保持原有 `accessToken + currentUser` 结构，仅在 `currentUser` 中增加一个字段：

```json
{
  "success": true,
  "data": {
    "accessToken": "JWT",
    "currentUser": {
      "userId": 12,
      "userCode": "demo_01",
      "displayName": "demo_01",
      "systemRole": "USER",
      "mustChangePassword": false,
      "passwordEnabled": false
    }
  },
  "message": "操作成功"
}
```

Refresh token 仍只通过 httpOnly Cookie 下发，不进入响应体。`passwordEnabled` 是附加展示字段，不进入 JWT claims，因此不改变会话机制。

### 2.3 后端登录与自动开户流程

建议在同一个事务中完成：

1. **归一化与校验**
   - 校验 `loginId` 非空、长度合法。
   - 判断是否提供非空密码；提供时校验密码长度。
   - 空密码路径先按用户名规则校验，并执行保留名检查。
2. **查询已有用户**
   - 沿用 `selectByLoginIdForUpdate`，按用户名或邮箱查询并加行锁。
   - 空密码路径因为已限制为用户名，不会用邮箱绕过用户名规则。
3. **已有用户判定**
   - 账号为 `DISABLED`：拒绝登录。
   - `systemRole=ADMIN`：必须提供密码且 BCrypt 校验通过，否则拒绝。
   - `systemRole=USER` 且未提供密码：允许登录。
   - `systemRole=USER` 且提供密码：要求账号 `passwordEnabled=true` 且密码正确；免密账号提供任意密码应失败。
4. **陌生用户名处理**
   - 带密码查询不到用户：返回“账号或密码错误”，不自动开户。
   - 空密码查询不到用户：执行保留名保护后自动创建普通用户。
5. **自动开户字段**
   - `userCode = username`
   - `username = username`
   - `displayName = username`
   - `email = username + "." + UUID + "@demo.local"`，避免与已有邮箱冲突，同时保证不超过 128 字符
   - `passwordHash = "{no-password}"`，作为应用层“不可用密码”标记
   - `systemRole = USER`
   - `status = ACTIVE`
   - `mustChangePassword = false`
6. **签发会话**
   - 沿用现有逻辑：吊销该用户旧 refresh token、签发新 access/refresh token、更新 `lastLoginAt`、返回当前用户信息。

#### 为什么推荐 `{no-password}` 而不是随机 BCrypt 哈希

两种方式都能满足 `password_hash NOT NULL`，但随机 BCrypt 哈希无法与真实密码账号区分。若要使用随机哈希，同时又要让设置页可靠区分免密用户，就只能额外依赖合成邮箱后缀等脆弱约定，或新增数据库列。

推荐使用固定不可用标记：

- 不保存任何可猜测密码；
- 可稳定计算 `passwordEnabled=false`；
- 不需要数据库迁移；
- 所有密码校验路径必须先识别该标记，不能把标记交给 BCrypt 校验；
- 该标记只允许由后端自动开户逻辑写入，绝不能返回给前端。

如果评审明确要求“看起来必须是 BCrypt 哈希”，也可以改为随机 UUID 的 BCrypt 哈希，但需接受无法精确区分免密用户，或增加额外应用约定。

### 2.4 并发重名开户

现有“先查询、不存在则插入”在并发下会产生唯一约束冲突，不能只靠 `SELECT ... FOR UPDATE` 解决不存在的行。

推荐使用 PostgreSQL 已有的冲突忽略能力，不引入新依赖：

1. 先查询并加锁；
2. 不存在时执行 `INSERT ... ON CONFLICT DO NOTHING`；
3. 再按用户名查询并加锁；
4. 若仍查询不到，返回“该用户名暂不可用，请重试”；
5. 若查到用户，继续按状态和角色执行登录判定。

这样两个请求同时使用同一个新用户名时，数据库只创建一行，另一个请求读取已创建行后继续登录，不会把唯一约束异常暴露成 500。合成邮箱使用 UUID 可避免 email 唯一约束成为额外冲突源。

不建议在同一个 Spring 事务中捕获 `DuplicateKeyException` 后直接重新查询：PostgreSQL 事务在唯一约束冲突后可能已经中止，简单重查不一定可用。

### 2.5 管理员保护细节

管理员保护必须同时满足以下条件：

- **角色是最终依据**：任何 `ADMIN` 角色账号，空密码一律拒绝，无论用户名是什么。
- **保留名是补充防线**：`admin`、`root`、`null`、`undefined`、`system` 继续禁止自动开户，比较时不区分大小写；建议把当前默认别名 `argus_admin` 也加入保留名。
- **密码路径才允许邮箱登录**：空密码路径只接受用户名；管理员邮箱必须配合密码使用。
- **未知账号带密码不开户**：防止通过密码字段意外触发自动注册。
- **错误信息**：管理员空密码可提示“管理员账号需要密码登录”；密码错误沿用“账号或密码错误”；禁用账号沿用“账号已被禁用”。
- **开发管理员初始化器不改**：`DevAdminInitializer` 继续负责创建/刷新管理员账号和密码。

当前实际管理员用户名以启动环境为准。代码只能确认默认值是 `argus_admin`，不能确认评测 jar 是否通过环境变量覆盖过。

### 2.6 免密用户改密

推荐新增 `passwordEnabled` 并贯穿以下链路：

- 登录/刷新响应中的 `currentUser.passwordEnabled`
- `/api/auth/me` 返回的 `passwordEnabled`
- 前端 `CurrentUserProfile` 类型
- 设置页根据该字段决定是否显示改密表单

自动开户用户 `passwordEnabled=false`：

- 设置页显示“体验账号无需设置密码”，隐藏/禁用当前密码、新密码、确认密码表单。
- 后端 `/api/account/change-password` 也显式拒绝，不能只依赖前端隐藏。
- `mustChangePassword=false`，不会被强制跳到设置页改密。

管理员和已有密码用户 `passwordEnabled=true`：设置页和改密接口保持现状。

### 2.7 前端交互

#### `/login` 与首页 `LoginModal`

默认模式只保留一个用户名输入框：

- 标题：登录 / 欢迎回来
- 字段：用户名
- 占位提示：例如 `demo_01`
- 按钮：进入系统
- 校验：只校验用户名非空；格式错误由后端统一返回

提供折叠入口“使用密码登录”：

- 展开后显示密码框；
- 密码可选字段在普通模式不发送；
- 管理员验收时可展开密码框，并快捷填入当前管理员用户名；
- 管理员无密码提交失败时，提示点击“使用密码登录”。

不建议默认把管理员用户名作为真实输入值预填，否则评委直接点击会撞上“管理员需要密码”，反而阻塞普通体验路径。更推荐把管理员用户名放在 placeholder 或快捷按钮中。

#### 首页

- 删除导航栏“免费试用”按钮。
- 删除底部 CTA 区“免费试用”按钮。
- 保留“登录”“开始使用”“进入工作台”“查看演示”等现有入口；它们继续打开精简后的登录弹窗。
- 清理仅属于被删除按钮的 CSS，避免留下死样式。

#### 注册清理

- 删除注册 tab、注册表单、注册成功自动登录逻辑。
- 删除“还没有账号？返回首页注册”页脚。
- 删除 `RegisterPayload`、`register()`。
- 删除无后端实现的 `resetPasswordByIdentity()` 及其类型。
- 后端删除 `/api/auth/register` 后，该路径在无 Authorization 请求下应返回 404。

## 3. 方案对比

### 决策 1：陌生用户名

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| A. 仅预置账号 | 实现最简单；不会公开自动开户 | 评委必须记住账号；输入任意用户名会失败；与“无注册体验”目标不符 | 不推荐 |
| B. 自动开户 | 完全符合“输入用户名即进入”；无需口头告知账号；旧用户名再次输入可复用 | 普通用户名成为凭证；可被无限开户；需处理并发和保留名 | **推荐** |

### 决策 2：管理员保护

| 方案 | 优点 | 缺点 | 结论 |
|---|---|---|---|
| 完全免密，包括管理员 | 交互最少 | 管理员权限裸奔，不可接受 | 拒绝 |
| 混合登录，按角色保护 | 普通体验路径短；管理员仍可密码登录；未来新增管理员同样受保护 | 需要两种前端模式；普通用户名免密有安全取舍 | **推荐** |
| 管理员改名/独立入口 | 能降低误操作 | 改名不能替代密码；独立入口增加开发和维护成本 | 可作为辅助，不作为主方案 |

## 4. 风险与边界情况清单

1. **普通用户名即凭证**：任何人知道普通用户名即可登录。这是演示方案的固有风险，必须在验收说明中明确。
2. **旧普通账号密码被绕过**：如果允许所有 `USER` 空密码登录，旧注册用户的密码也会失效。需要用户确认是否接受。
3. **公开自动开户**：没有现成限流机制，可能产生大量垃圾账号。当前不建议为演示引入复杂限流；生产环境必须重新设计认证。
4. **并发重名**：必须使用数据库冲突处理，不能只靠先查后插。
5. **保留名大小写**：`Admin`、`ROOT` 等也应阻止自动开户；保留名检查不区分大小写。
6. **实际管理员别名**：默认是 `argus_admin`，不是提示词中的 `admin`；环境变量可能覆盖。管理员保护按角色判断，UI 快捷填入需确认实际值。
7. **用户名大小写**：PostgreSQL 当前等值查询区分大小写，`Demo` 与 `demo` 是不同用户名；不要向前端承诺“不区分大小写”。
8. **禁用账号**：免密路径也必须检查 `status`，不能给禁用用户重新签发会话。
9. **未知账号带密码**：应拒绝且不创建用户，保持“带密码只登录已有账号”的语义。
10. **免密账号提交密码**：应失败，不能把 `{no-password}` 交给 BCrypt 或误判为密码正确。
11. **设置页**：仅隐藏前端表单不够，后端也要拒绝免密账号改密。
12. **Token 过期**：access token 过期但 refresh cookie 有效时，仍由现有 401 刷新机制恢复；refresh 也失效时回到登录页，重新输入同一用户名即可进入原账号。
13. **注册接口 404**：删除 Controller 方法和过滤器白名单；当前项目只依赖 `spring-security-crypto`，没有完整 SecurityFilterChain，无 Authorization 的未知路径会落到 404。
14. **两套登录 UI**：`LoginView.vue` 和 `LoginModal.vue` 必须同步修改，不能只改首页弹窗。
15. **当前 jar 不受影响**：不重新构建、不替换运行时产物前，评测中的旧 jar 行为不变。

## 5. 改动文件清单

### 后端

| 文件 | 计划改动 |
|---|---|
| `auth/controller/AuthController.java` | 删除 `/register` 接口及 `RegisterRequest` 引用；登录改为接收可选密码；更新类注释 |
| `auth/service/AuthService.java` | 重构登录分支；新增自动开户；保留令牌逻辑；删除注册逻辑及仅供注册使用的校验/依赖；增加保留名和免密标记判断 |
| `auth/model/dto/LoginRequest.java` | `password` 改为可选，更新字段说明 |
| `auth/model/dto/RegisterRequest.java` | 删除 |
| `auth/security/JwtAuthenticationFilter.java` | 删除 `REGISTER_PATH` 白名单 |
| `auth/model/vo/CurrentUserProfileResponse.java` | 新增 `passwordEnabled` |
| `auth/CurrentUserService.java` | `CurrentUser` 增加 `passwordEnabled`，从数据库查询结果传入 |
| `user/service/UserQueryService.java` | `UserRecord` 增加并计算 `passwordEnabled` |
| `user/service/AccountService.java` | 免密账号显式拒绝改密；密码账号流程保持原样 |
| `user/mapper/UserMapper.java` | 新增自动开户的冲突安全插入方法 |
| `resources/mappers/UserMapper.xml` | 实现 `INSERT ... ON CONFLICT DO NOTHING` |
| `user/model/entity/User.java` | 可选：更新 `passwordHash` 注释，说明可能保存不可用标记 |
| `auth/service/PasswordPolicyValidator.java` | 保留供改密使用；更新“注册和改密共用”的过时注释 |
| `auth/config/DevAdminInitializer.java` | 不改 |
| `sql/schema.sql` | 不改 |
| `src/test/.../auth/...` | 建议新增登录分支、保留名、免密改密和注册接口移除相关测试；并发场景最好用 PostgreSQL 集成测试验证 |

### 前端

| 文件 | 计划改动 |
|---|---|
| `src/api/auth.ts` | `LoginPayload.password` 改为可选；新增 `passwordEnabled`；删除注册类型/函数和重置密码死代码；更新登录标识说明 |
| `src/stores/auth.ts` | 登录请求允许省略密码；会话、刷新、登出、路由落位逻辑不改 |
| `src/views/LoginView.vue` | 默认仅用户名；增加折叠密码模式；删除注册页脚；更新文案 |
| `src/components/LoginModal.vue` | 删除注册 tab/表单/逻辑及对应样式；默认仅用户名；增加折叠密码模式 |
| `src/views/HomeView.vue` | 删除两处“免费试用”按钮及仅被其使用的样式；其余入口保持 |
| `src/views/settings/SettingsView.vue` | 根据 `passwordEnabled` 隐藏免密用户改密表单并显示说明 |
| `src/api/http.ts` | 不改 |
| `src/router/index.ts` | 不改 |
| `src/components/AccountPasswordForm.vue` | 当前无引用，不在本次范围，避免无关清理 |

## 6. 工作量估计

- 后端认证重构、自动开户、并发处理、`passwordEnabled` 链路：约 0.5–1 个工作日。
- 前端两套登录 UI、首页清理、设置页适配：约 0.5 个工作日。
- 编译、类型检查、联调和边界验证：约 0.5–1 个工作日。

**核心实现约 1 个工作日；完整验证约 1.5–2 个工作日。** 不需要数据库迁移、依赖升级或基础设施调整。

## 7. 实施后验收标准草案

### 后端

- [ ] `POST /api/auth/login {"loginId":"demo_001"}` 返回 200，用户角色为 `USER`，`passwordEnabled=false`，`mustChangePassword=false`，并下发 refresh Cookie。
- [ ] 自动创建的用户具有合成邮箱、不可用密码标记、`ACTIVE` 状态，数据库只新增一行。
- [ ] 同一用户名再次登录返回同一 `userId`，不重复开户。
- [ ] 两个请求同时使用同一个新用户名时，最终只存在一个用户，不出现 500。
- [ ] 空用户名、非法字符、超长用户名、保留用户名均被拒绝。
- [ ] 当前管理员用户名不带密码登录被拒绝，且不签发令牌。
- [ ] 当前管理员用户名带正确密码可登录并进入 `/app/admin/users`；错误密码被拒绝。
- [ ] 任何 `ADMIN` 角色账号都不能通过空密码登录，不仅限于 `admin` 或 `argus_admin`。
- [ ] 禁用用户不能通过免密路径登录。
- [ ] 不存在的用户名带密码登录不会创建用户。
- [ ] 免密用户调用改密接口被明确拒绝；密码用户改密流程保持可用。
- [ ] `POST /api/auth/register` 返回 404。
- [ ] `/api/auth/refresh`、`/logout`、`/me` 的原行为保持；`/me` 额外返回 `passwordEnabled`。
- [ ] access token 过期后的 401 刷新重试仍正常工作。

### 前端

- [ ] 首页导航栏和底部 CTA 均不再出现“免费试用”字样。
- [ ] 首页点击登录后，弹窗默认只有一个用户名输入框，无注册 tab。
- [ ] 输入合法陌生用户名直接进入 `/app/groups`。
- [ ] `/login` 独立页同样只需用户名即可进入。
- [ ] 展开“使用密码登录”后可输入管理员密码；管理员无密码提交有明确提示。
- [ ] 页面不再出现注册入口、注册表单或“返回首页注册”链接。
- [ ] 免密用户设置页不显示不可用的改密表单；管理员设置页仍显示改密表单。
- [ ] 刷新页面、access token 过期、退出后重新输入用户名等场景符合预期。
- [ ] `npm run build` 的类型检查与构建通过；后端 `./mvnw clean compile` 通过。

## 8. 需要用户拍板的问题

1. **旧普通用户是否也允许免密登录？**
   - 推荐：允许所有 `USER` 角色账号空密码登录，保证评委重复输入旧用户名也能顺畅进入。
   - 更严格：仅允许 `passwordEnabled=false` 的自动开户账号空密码登录；旧注册用户必须输入密码。

2. **评测环境的实际管理员用户名是什么？**
   - 代码默认是 `argus_admin`，但可能被 `ARGUS_RAG_DEV_ADMIN_USERNAME` 覆盖。
   - 推荐：界面用“管理员登录”快捷按钮填入该值，不默认预填，避免阻塞普通用户体验。

3. **是否接受 `{no-password}` 作为免密账号的数据库兼容值？**
   - 推荐：接受，并新增 `passwordEnabled` 字段；这是不改表结构时最稳定、可维护的方案。
   - 备选：随机 BCrypt 哈希，但设置页无法精确区分免密用户。

4. **首页入口是否保留弹窗？**
   - 推荐：保留 `LoginModal`，只精简为登录模式，改动最小。
   - 备选：所有首页按钮统一跳 `/login`，删除 `LoginModal`，UI 更统一但改动更大。

如果回复“按推荐方案执行”，即代表接受以上四项推荐默认值。
