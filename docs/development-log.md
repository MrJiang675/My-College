# Argus 团队 Demo 复现与验证记录

## 记录范围与参考来源

**验收状态：按用户确认保留参考源码。最终后端编译通过，前端 npm install 成功，但 npm run build 因既有 TS2532 类型错误失败；本 Demo 尚未达到可直接发布状态。下文将已执行、通过、失败、跳过和未验证严格区分。**

本记录采用团队口径描述本次 Demo 复现，不逐阶段划分个人作者。参考来源为本机 Argus 仓库，其 origin 为 https://github.com/Bruce-computer/University-Student-Professional-Learning-Platform.git 。参考历史反映既有项目的演进，本次新增工作是按历史快照逐阶段还原、脱敏、构建验证和整理记录，不将复制过程表述为从零原创。

- 固定参考版本：`7ba359acb1a1fee8cbdc19bce8887c55fc78da58`。
- 历史范围：实际执行 `git log --reverse --oneline` 得到的全部 **52 个提交**，包含合并、文档和纯配置阶段，并非原任务示例列出的五个阶段。
- 参考目录：`C:/Users/Lenovo/IdeaProjects/GitHub/Argus`，保持只读；目标目录：`C:/Users/Lenovo/IdeaProjects/GitHub/Argus-replica`。目标未复制源 `.git`，也未导入源工作区暂存但未提交的文档与图片。
- 初次远端访问遇到非交互认证失败，因此经确认改用本地完整 Git 历史；未创建 `_reference/`，无该目录清理需求。
- 本次机器记录时间：2026-11-23T15:03:17.972Z（UTC）；下文各提交时间来自原始 Git 元数据，不是本次执行时刻。

## 项目与架构概览

根目录 `AGENTS.md` 原样保留。其描述偏向早期认证/用户架构，部分技术细节已落后于 HEAD，因此本节同时核对实际目录、POM、配置及前端依赖，不改写原有说明文件。

| 层次 | 实际技术与职责 |
| --- | --- |
| 后端 | Java 21、Spring Boot 3.5.0、Spring MVC、Maven Wrapper、MyBatis-Plus 3.5.15、PostgreSQL/pgvector、JWT/BCrypt；实际 POM 中 Lombok 为 1.18.38 |
| 检索与 AI | Elasticsearch 文本检索、pgvector 向量检索、Spring AI、智谱聊天/嵌入、可选 You.com 检索；当前配置聊天模型为 glm-4-flash、嵌入模型为 embedding-2、向量维度为 1024 |
| 对象存储与处理 | MinIO、文档上传/分片上传、解析、切片与异步入库；PDFBox、POI |
| 前端 | Vue 3、TypeScript、Vite 8、Element Plus、Pinia、Vue Router、Axios；构建包含 vue-tsc 类型检查和 Vite 打包 |
| 部署资产 | 两节点 Docker Compose、环境变量示例、Elasticsearch 镜像与部署说明；本次保留原结构，仅脱敏，不等于已部署 |

后端包根为 `com.argus.rag`，包含 `assistant`、`auth`、`common`、`document`、`engine`、`group`、`ingestion`、`metrics`、`qa`、`user` 等模块；`src/main/resources` 中保留配置、映射 XML、提示词与日志配置，测试、SQL 和部署文件一并复现。当前主配置激活 `dev`，后端监听 10001、接口使用 `/api`；原说明中的默认 `local` 不能作为 HEAD 的启动依据。

## 复现方法与构建环境

1. 逐提交执行 `git show <commit> --stat`，查看关键 diff；合并提交另对第一父提交核对差异，不把合并重复描述成独立功能开发。
2. 使用 `git ls-tree` 枚举该提交文件，`git cat-file --batch` 读取每个 Git blob 的原始字节，逐文件写入目标。只同步自己创建的参考文件；历史删除同步到目标，不修改源仓库。
3. 排除源 `.git`、`node_modules`、`target`；仅对批准的敏感配置默认值以及文档中的对应凭据做占位符替换，不重构、不补写业务源码、不更新版本、不格式化。
4. 本次阶段触发规则：与上一已复现快照相比，`pom.xml` 变化时执行后端编译；`package.json` 或 `package-lock.json` 变化时执行依赖安装和前端构建。未触发不代表该阶段通过编译，最终快照必须另行完整构建。
5. 每个快照同步后验证全部受管文件 SHA256；最终按固定 HEAD 原始 blob 独立逐文件比对。此处以提交内容为准，而非 Windows 工作树可能转换过换行的副本。
6. 早期编译错误保留真实日志并继续下一参考快照，不修改源码强行修通；最终错误须暂停验收并说明。

| 项目 | 实际检查结果 |
| --- | --- |
| JDK | Amazon Corretto 21.0.13；本次命令显式设置 JAVA_HOME，未使用机器默认的 JDK 24 |
| JAVA_HOME | C:/Users/Lenovo/.jdks/corretto-21.0.13 |
| Node.js / npm | v26.13.0 / 11.20.0；符合前端声明的 ^20.19.0 或 >=22.12.0 |
| Maven | 系统 Maven 3.9.16 可用；项目构建使用仓库自带 mvnw.cmd，其 Wrapper 指向 Maven 3.9.14 |
| 下载连接 | Maven Central 分发地址与 npm 镜像的 Vite 元数据预检均返回 HTTP 200 |
| npm 镜像 | https://registry.npmmirror.com |

构建命令主体如下，在各模块目录执行。环境设置采用 PowerShell 等价写法展示；实际执行由 Node 调用 Windows cmd.exe，显式传入 JAVA_HOME 与 MAVEN_OPTS，阶段12起将诊断语言设为英文以避免中文输出编码问题：

```powershell
$env:JAVA_HOME = 'C:\Users\Lenovo\.jdks\corretto-21.0.13'
.\mvnw.cmd clean compile

# 在 Argus-frontend 目录
npm install --no-audit --no-fund
npm run build
```

`--no-audit --no-fund` 只关闭安装时附加的审计和资助输出，不跳过生命周期脚本或构建步骤。最终前端删除的是本次自行生成的依赖目录，再重新安装，并未复制参考仓库的旧依赖。若 npm 重写锁文件元数据，记录后恢复参考锁文件原始字节，保证交付文件一致性。

阶段实际执行命令 11 次，其中退出码为 0 的 8 次、非零或超时 3 次；跳过项按各阶段说明列出，不能算作成功。编译和静态打包不等于数据库、对象存储或真实 AI 请求已连通。

最终安全复查另外发现 DevAdminInitializer.java 的 @Value 注解含管理员密码默认值。按用户批准的统一脱敏范围，仅替换这一配置默认值，不改控制流；随后重新按52阶段同步并校验全部脱敏快照，最后再次编译后端的结果为通过。下方历史构建日志来自首次复现轮次，额外的注解默认值替换没有逐阶段重跑编译；最终编译则基于补齐脱敏后的文件。

原任务的“前端完整构建成功”未达成：最终出现 TS2532 后已经暂停并告知用户，用户明确选择“保持原样，记录失败”，因此不补写类型修复、不把 Vite 单独产出的 dist 当作 npm run build 成功。

## 分阶段演进与本次验证

### 阶段 01：feat: Implement Admin User Management Service and User Query Service

- **提交**：`64eebca`；原始提交时间：2026-04-30T17:13:20+08:00。
- **阶段目标**：建立 Argus 后端初始工程，以及用户认证、账户维护和管理员查询/状态管理的基础实现。
- **涉及文件与模块**：`Argus-backend/pom.xml`、`Argus-backend/src/main/java/com/argus/rag/auth/service/AuthService.java`、`Argus-backend/src/main/java/com/argus/rag/auth/security/RefreshTokenService.java`、`Argus-backend/src/main/java/com/argus/rag/user/service/AdminUserService.java`、`Argus-backend/src/main/java/com/argus/rag/user/service/UserQueryService.java`、`sql/schema.sql`。
- **实际变化**：这是无父提交的根提交，新增 60 个文件、3594 行，内容不只包含标题所说的两个用户服务，还包括 Java 21/Spring Boot/MyBatis-Plus 工程、认证控制器、JWT 与刷新令牌、统一响应和异常处理。登录实现用户名或邮箱查询、密码校验、旧刷新令牌吊销和新令牌签发，管理员端实现用户列表、详情和状态修改，禁用用户时吊销刷新令牌。SQL 此时新增的是 users、user_refresh_tokens 两张表，并附带初始化和 V1 项目文档。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；这是初始代码快照，不能把所有新增内容描述成针对既有线上故障的修复。
- **参考阶段方案**：通过 AuthService、JWT/刷新令牌服务和 CurrentUserService 划分认证职责，使用 BCrypt、数据库登录行锁、角色/状态检查以及管理员服务复用 UserQueryService 的方式落下基础逻辑；此阶段没有构建或运行验证记录可据以声称功能通过。
- **代码依据**：history/01-64eebca.diff:1886-1962；Argus-backend/src/main/java/com/argus/rag/auth/service/AuthService.java#login/register/refresh；history/01-64eebca.diff:2968-2989；Argus-backend/src/main/java/com/argus/rag/user/service/AdminUserService.java#listUsers/getUser/updateUserStatus；history/01-64eebca.diff:3028-3056；Argus-backend/src/main/java/com/argus/rag/user/service/UserQueryService.java#listUsers/existsByUsername/existsByEmail

**本次验证结果**

- `mvnw.cmd clean compile`：通过，退出码 `0`，耗时 88 秒；原始日志索引 `01-backend.log`。

```text
[INFO] BUILD SUCCESS
```

> 【截图占位：此处插入阶段 1 backend 的真实成功输出截图，对应 01-backend.log；不得用其他阶段截图替代】

- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。
<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.gitignore                                         |  35 ++
 Argus-backend/.gitattributes                       |   2 +
 Argus-backend/.gitignore                           |  33 ++
 .../.mvn/wrapper/maven-wrapper.properties          |   3 +
 Argus-backend/mvnw                                 | 295 +++++++++++++++
 Argus-backend/mvnw.cmd                             | 189 ++++++++++
 Argus-backend/pom.xml                              | 112 ++++++
 .../com/argus/rag/ArgusBackendApplication.java     |  16 +
 .../com/argus/rag/auth/CurrentUserService.java     |  93 +++++
 .../argus/rag/auth/config/AuthConfiguration.java   |  52 +++
 .../com/argus/rag/auth/config/AuthProperties.java  |  55 +++
 .../argus/rag/auth/config/DevAdminInitializer.java |  87 +++++
 .../argus/rag/auth/controller/AuthController.java  | 109 ++++++
 .../rag/auth/mapper/UserRefreshTokenMapper.java    |   9 +
 .../com/argus/rag/auth/model/dto/LoginRequest.java |  17 +
 .../argus/rag/auth/model/dto/RegisterRequest.java  |  25 ++
 .../rag/auth/model/entity/UserRefreshToken.java    |  38 ++
 .../rag/auth/model/vo/AuthTokensResponse.java      |  19 +
 .../auth/model/vo/CurrentUserProfileResponse.java  |  26 ++
 .../argus/rag/auth/security/AuthCookieSupport.java |  50 +++
 .../rag/auth/security/JwtAccessTokenService.java   | 133 +++++++
 .../rag/auth/security/JwtAuthenticationFilter.java | 110 ++++++
 .../rag/auth/security/RefreshTokenService.java     | 162 ++++++++
 .../com/argus/rag/auth/service/AuthService.java    | 354 ++++++++++++++++++
 .../com/argus/rag/auth/service/PasswordHasher.java |  13 +
 .../argus/rag/auth/service/RefreshTokenRecord.java |  27 ++
 .../java/com/argus/rag/common/api/ApiResponse.java |  33 ++
 .../rag/common/config/OpenApiConfiguration.java    |  22 ++
 .../com/argus/rag/common/enums/DocumentStatus.java |  13 +
 .../rag/common/enums/GroupInvitationStatus.java    |  13 +
 .../rag/common/enums/GroupJoinRequestStatus.java   |  13 +
 .../java/com/argus/rag/common/enums/GroupRole.java |   9 +
 .../com/argus/rag/common/enums/GroupStatus.java    |   7 +
 .../argus/rag/common/enums/IngestionJobStatus.java |  15 +
 .../argus/rag/common/enums/IngestionJobType.java   |   7 +
 .../com/argus/rag/common/enums/SystemRole.java     |   9 +
 .../com/argus/rag/common/enums/UserStatus.java     |   9 +
 .../rag/common/exception/BusinessException.java    |  15 +
 .../rag/common/exception/ForbiddenException.java   |  11 +
 .../common/exception/GlobalExceptionHandler.java   |  60 +++
 .../common/exception/UnauthorizedException.java    |  11 +
 .../rag/user/controller/AccountController.java     |  36 ++
 .../rag/user/controller/AdminUserController.java   |  66 ++++
 .../java/com/argus/rag/user/mapper/UserMapper.java |  19 +
 .../rag/user/model/dto/ChangePasswordRequest.java  |  14 +
 .../user/model/dto/ResetUserPasswordRequest.java   |  12 +
 .../user/model/dto/UpdateUserStatusRequest.java    |  13 +
 .../java/com/argus/rag/user/model/entity/User.java |  55 +++
 .../rag/user/model/vo/AdminUserItemResponse.java   |  22 ++
 .../com/argus/rag/user/service/AccountService.java |  76 ++++
 .../argus/rag/user/service/AdminUserService.java   |  65 ++++
 .../argus/rag/user/service/UserQueryService.java   |  69 ++++
 .../src/main/resources/application-dev.yml         |  19 +
 .../src/main/resources/application-local.yml       |  20 +
 Argus-backend/src/main/resources/application.yml   |  16 +
 .../src/main/resources/mappers/UserMapper.xml      |  25 ++
 CLAUDE.md                                          |  95 +++++
 docs/V1.0-项目文档.md                              | 416 +++++++++++++++++++++
 docs/project-init.md                               | 178 +++++++++
 sql/schema.sql                                     |  67 ++++
 60 files changed, 3594 insertions(+)
```

</details>

### 阶段 02：优化登陆代码，添加日志框架

- **提交**：`14c01eb`；原始提交时间：2026-04-30T18:00:33+08:00。
- **阶段目标**：整理登录与密码校验逻辑，增加刷新令牌并发吊销判断及统一操作日志。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/auth/service/AuthService.java`、`Argus-backend/src/main/java/com/argus/rag/auth/service/PasswordPolicyValidator.java`、`Argus-backend/src/main/java/com/argus/rag/auth/mapper/UserRefreshTokenMapper.java`、`Argus-backend/src/main/java/com/argus/rag/common/log/LogAop.java`、`Argus-backend/src/main/resources/logback-spring.xml`。
- **实际变化**：20 个文件新增 300 行、删除 145 行，提取 PasswordPolicyValidator 供注册和改密共用，把当前用户信息直接装入 AuthTokens，减少控制器重复查询，并删除基于用户名和邮箱重置密码的服务方法及对应 SQL。刷新令牌新增 revoked_at 为空才更新的原子吊销方法，登录时间改为单字段更新，Mapper 扫描范围收窄到 mapper 包。新增 AOP 操作日志、按大小和时间滚动的 Logback 文件日志，以及不向前端直接返回堆栈的通用异常兜底。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；diff 明确增加并发重复使用令牌的判断，但不能据此声称发生过重放攻击。另一个可直接确认的实现事实是 LogAop 将方法入参 Arrays.toString 后记录，而登录、注册和改密已接入该日志，不能将其描述成已经脱敏的安全审计日志。
- **参考阶段方案**：用 revokeByIdIfActive 的受影响行数决定是否继续签发新令牌，复用密码长度、字母数字及 BCrypt 72 字节限制，返回已有 User 转换得到的当前用户视图；通过 OperationLog 切面和 Logback 统一记录耗时与异常。重放分支包含吊销全部令牌后抛出业务异常的代码，不据此宣称事务提交后的全部吊销效果已验证。
- **代码依据**：history/02-14c01eb.diff:184-186,396-415；Argus-backend/src/main/java/com/argus/rag/auth/mapper/UserRefreshTokenMapper.java#revokeByIdIfActive；Argus-backend/src/main/java/com/argus/rag/auth/service/AuthService.java#refresh；history/02-14c01eb.diff:429-497,539-568；Argus-backend/src/main/java/com/argus/rag/auth/service/AuthService.java#toCurrentUser/updateSuccessfulLogin；Argus-backend/src/main/java/com/argus/rag/auth/service/PasswordPolicyValidator.java#validate；history/02-14c01eb.diff:637-664,879-923；Argus-backend/src/main/java/com/argus/rag/common/log/LogAop.java#logAroundAdvice；Argus-backend/src/main/resources/logback-spring.xml#FILE/ERROR_FILE

**本次验证结果**

- `mvnw.cmd clean compile`：通过，退出码 `0`，耗时 14 秒；原始日志索引 `02-backend.log`。

```text
[INFO] BUILD SUCCESS
```

> 【截图占位：此处插入阶段 2 backend 的真实成功输出截图，对应 02-backend.log；不得用其他阶段截图替代】

- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。
<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
Argus-backend/pom.xml                              |  28 +++---
 .../com/argus/rag/ArgusBackendApplication.java     |   2 +-
 .../argus/rag/auth/config/DevAdminInitializer.java |   6 +-
 .../argus/rag/auth/controller/AuthController.java  |   8 +-
 .../rag/auth/mapper/UserRefreshTokenMapper.java    |   8 ++
 .../rag/auth/model/vo/AuthTokensResponse.java      |   5 +-
 .../rag/auth/security/JwtAuthenticationFilter.java |   5 +-
 .../rag/auth/security/RefreshTokenService.java     |  25 ++---
 .../com/argus/rag/auth/service/AuthService.java    | 101 +++++++--------------
 .../rag/auth/service/PasswordPolicyValidator.java  |  48 ++++++++++
 .../common/exception/GlobalExceptionHandler.java   |  10 ++
 .../main/java/com/argus/rag/common/log/LogAop.java |  58 ++++++++++++
 .../com/argus/rag/common/log/OperationLog.java     |  16 ++++
 .../rag/user/controller/AccountController.java     |   2 +
 .../rag/user/controller/AdminUserController.java   |   2 +
 .../java/com/argus/rag/user/mapper/UserMapper.java |   4 -
 .../com/argus/rag/user/service/AccountService.java |  34 ++-----
 .../argus/rag/user/service/AdminUserService.java   |   3 +
 .../src/main/resources/logback-spring.xml          |  71 +++++++++++++++
 .../src/main/resources/mappers/UserMapper.xml      |   9 --
 20 files changed, 300 insertions(+), 145 deletions(-)
```

</details>

### 阶段 03：refactor(auth): 优化当前用户认证逻辑，改用线程上下文存储用户信息

- **提交**：`7865314`；原始提交时间：2026-04-30T21:00:36+08:00。
- **阶段目标**：把当前用户读取从 Servlet request attribute 改为线程上下文，减少控制器的认证参数传递。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/auth/CurrentUserService.java`、`Argus-backend/src/main/java/com/argus/rag/auth/security/JwtAuthenticationFilter.java`、`Argus-backend/src/main/java/com/argus/rag/user/controller/AccountController.java`、`Argus-backend/src/main/java/com/argus/rag/user/controller/AdminUserController.java`、`Argus-backend/src/main/java/com/argus/rag/common/config/OpenApiConfiguration.java`。
- **实际变化**：6 个文件新增 65 行、删除 54 行，CurrentUserService 和相关控制器改用无 HttpServletRequest 参数的认证方法，过滤器改为设置 UserContext 并在 finally 中清理。Authorization 同时接受 Bearer 前缀和纯 token 文本，OpenAPI 新增 BearerAuth scheme。逐操作添加 SecurityRequirement 的 Bean 仍是注释，且本提交只是引用 UserContext，并未提交该类文件。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；可以确认阶段 3 的文件快照没有 UserContext.java，而过滤器和 CurrentUserService 已调用它，该文件直到阶段 6 才加入。不能将本阶段描述成依赖齐全、已经运行通过的认证重构。
- **参考阶段方案**：代码层面以 UserContext.get/set/clear 替换 request attribute，并同步调整登录用户、管理员及业务用户检查入口；应在复现记录中如实保留该依赖晚于调用方入库的事实，不提前补齐历史代码。
- **代码依据**：history/03-7865314.diff:32-76；Argus-backend/src/main/java/com/argus/rag/auth/CurrentUserService.java#getRequiredCurrentUser/requireSystemAdmin/requireBusinessUser；history/03-7865314.diff:108-155；Argus-backend/src/main/java/com/argus/rag/auth/security/JwtAuthenticationFilter.java#doFilterInternal；history/03-7865314.diff:184-208；Argus-backend/src/main/java/com/argus/rag/common/config/OpenApiConfiguration.java#SCHEME_NAME/applySecurityToAllOperations

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 3 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../com/argus/rag/auth/CurrentUserService.java     | 26 ++++++-----------
 .../argus/rag/auth/controller/AuthController.java  |  4 +--
 .../rag/auth/security/JwtAuthenticationFilter.java | 33 ++++++++++++----------
 .../rag/common/config/OpenApiConfiguration.java    | 27 ++++++++++++++++--
 .../rag/user/controller/AccountController.java     | 10 +++----
 .../rag/user/controller/AdminUserController.java   | 19 +++++--------
 6 files changed, 65 insertions(+), 54 deletions(-)
```

</details>

### 阶段 04：feat(groupmembership): 实现群组管理与加入申请功能

- **提交**：`ca5ba27`；原始提交时间：2026-05-06T22:59:57+08:00。
- **阶段目标**：增加群组创建、邀请、加入申请与成员权限管理。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/groupmembership/controller/GroupManagementController.java`、`Argus-backend/src/main/java/com/argus/rag/groupmembership/service/GroupManagementService.java`、`Argus-backend/src/main/java/com/argus/rag/groupmembership/service/GroupJoinRequestService.java`、`Argus-backend/src/main/java/com/argus/rag/groupmembership/service/GroupMembershipService.java`、`Argus-backend/src/main/resources/mappers/GroupMembershipMapper.xml`、`docs/V1.0-项目文档.md`。
- **实际变化**：17 个文件新增 2020 行、删除 171 行，新增群组管理、群组查询、邀请决策和加入申请控制器、服务及 Mapper/XML。创建群组时写入 OWNER 成员关系，邀请支持接受/拒绝/取消，加入申请支持提交/查询/审批，成员管理区分 OWNER 与普通成员并禁止 OWNER 直接退出。V1 文档大幅扩充，但群组实体及部分响应类型尚未在此提交加入，群组建表 SQL 也不是本阶段新增。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；diff 显示服务和 Mapper 引用群组实体、响应模型，清单显示这些相关模型到阶段 6 才加入，groups 等建表语句则在阶段 5 加入，不能把阶段 4 写成已独立完成数据库部署和联调。
- **参考阶段方案**：在服务内调用 requireBusinessUser、requireGroupOwner、requireGroupReadable，将成员关系写入与审批/邀请状态更新放在事务中，并通过 PENDING 条件更新及影响行数阻止已处理记录再次改变；实现层还检查重复邀请、重复申请和已有成员关系。
- **代码依据**：history/04-ca5ba27.diff:499-558,616-625；Argus-backend/src/main/java/com/argus/rag/groupmembership/service/GroupJoinRequestService.java#submitJoinRequest/approveJoinRequest/updateStatus；history/04-ca5ba27.diff:713-824,876-884；Argus-backend/src/main/java/com/argus/rag/groupmembership/service/GroupManagementService.java#createGroup/acceptInvitation/removeMember/leaveGroup/updateInvitationStatus；history/04-ca5ba27.diff:984-1009；Argus-backend/src/main/java/com/argus/rag/groupmembership/service/GroupMembershipService.java#listVisibleGroups/requireGroupReadable/requireGroupOwner

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 4 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../argus/rag/auth/controller/AuthController.java  |   5 +-
 .../controller/GroupJoinRequestController.java     |  70 ++
 .../controller/GroupManagementController.java      |  71 ++
 .../controller/GroupQueryController.java           |  29 +
 .../controller/InvitationDecisionController.java   |  45 ++
 .../mapper/GroupJoinRequestMapper.java             |  48 ++
 .../mapper/GroupMembershipMapper.java              |  66 ++
 .../model/dto/CreateGroupRequest.java              |  19 +
 .../model/dto/CreateInvitationRequest.java         |  15 +
 .../model/dto/CreateJoinRequestRequest.java        |  15 +
 .../service/GroupJoinRequestService.java           | 201 +++++
 .../service/GroupManagementService.java            | 274 +++++++
 .../service/GroupMembershipService.java            | 187 +++++
 .../rag/user/controller/AdminUserController.java   |   5 +-
 .../resources/mappers/GroupJoinRequestMapper.xml   | 123 ++++
 .../resources/mappers/GroupMembershipMapper.xml    | 202 +++++
 docs/V1.0-项目文档.md                              | 816 +++++++++++++++++----
 17 files changed, 2020 insertions(+), 171 deletions(-)
```

</details>

### 阶段 05：feat(rag): 添加文档切片及分片上传支持

- **提交**：`d9bc3bd`；原始提交时间：2026-05-09T14:58:34+08:00。
- **阶段目标**：引入文档上传管理、分片续传及异步文档摄入流水线，为知识检索准备持久化切片和索引。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/document/service/DocumentUploadService.java`、`Argus-backend/src/main/java/com/argus/rag/document/service/DocumentService.java`、`Argus-backend/src/main/java/com/argus/rag/document/service/DocumentIngestionAsyncService.java`、`Argus-backend/src/main/java/com/argus/rag/ingestion/service/EtlDocumentIngestionProcessor.java`、`Argus-backend/src/main/java/com/argus/rag/ingestion/transformer/StructureAwareChunkTransformer.java`、`Argus-backend/src/main/java/com/argus/rag/storage/service/MinioStorageService.java`、`sql/schema.sql`。
- **实际变化**：60 个文件新增 7565 行、删除 36 行，新增直接上传、分片初始化/上传/合并、状态查询、文档列表、预览、下载、删除和失败重处理代码，上传初始化支持同群组 READY 文档复用及既有会话续传。新增 TXT/Markdown/PDF/DOCX 解析、文本清洗、结构感知切片、切片落库、向量写入和 ES 索引，事务提交后异步启动摄入并更新 READY/FAILED 状态，同时扩展群组、文档、上传会话和切片等 SQL。认证前缀由 旧项目包名.auth 改为 rag.auth，撤掉此前 OpenAPI BearerAuth 组件，还新增了误放在 resources 下的启动类副本。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；重试、补偿、超时回收及失败状态分支是代码能力，不是已经发生上传故障的日志证据。下载接口已引用 DocumentDownloadVO，但该类直到阶段 6 才加入；同样不能据此虚构一次具体编译异常或模型调用成功。
- **参考阶段方案**：分片元数据使用 upsert、按会话归属和有效状态校验并调用对象存储 composeObject 合并；AFTER_COMMIT 异步事件驱动 ETL，以 @Retryable(maxAttempts=3) 和 recover 清理中间产物、记录失败状态，完成后同步 ES 并置 READY。这里 maxAttempts=3 是总尝试次数的代码值，不照抄注释为额外重试三次；所有凭据只保留配置结构说明。
- **代码依据**：history/05-d9bc3bd.diff:2506-2659；Argus-backend/src/main/java/com/argus/rag/document/service/DocumentUploadService.java#initUpload/uploadChunk/completeUpload；history/05-d9bc3bd.diff:1311-1313,1416-1453；Argus-backend/src/main/java/com/argus/rag/document/service/DocumentIngestionAsyncListener.java#handle；Argus-backend/src/main/java/com/argus/rag/document/service/DocumentIngestionAsyncService.java#ingestDocument/recover；history/05-d9bc3bd.diff:4736-4758；Argus-backend/src/main/java/com/argus/rag/ingestion/service/EtlDocumentIngestionProcessor.java#process

**本次验证结果**

- `mvnw.cmd clean compile`：未通过，退出码 `1`，耗时 29 秒；原始日志索引 `05-backend.log`。

本次编译输出包含 GroupJoinRequest、GroupMemberResponse 等缺失类型诊断；部分中文因终端编码显示异常，原始采集日志保留，下方不展示乱码行。

```text
[ERROR] -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
```

> 【截图占位：此处插入阶段 5 backend 的真实失败输出截图，对应 05-backend.log；不得用其他阶段截图替代】

- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。
<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
Argus-backend/pom.xml                              |  88 +++
 .../com/argus/rag/auth/config/AuthProperties.java  |  25 +-
 .../rag/common/config/OpenApiConfiguration.java    |   9 +-
 .../document/controller/DocumentController.java    | 268 +++++++
 .../argus/rag/document/mapper/DocumentMapper.java  |  89 +++
 .../document/mapper/DocumentUploadChunkMapper.java |  40 ++
 .../mapper/DocumentUploadSessionMapper.java        |  43 ++
 .../rag/document/model/dto/DocumentQuery.java      |  46 ++
 .../rag/document/model/dto/UploadChunkRequest.java |  25 +
 .../document/model/dto/UploadDocumentRequest.java  |  25 +
 .../rag/document/model/dto/UploadInitRequest.java  |  30 +
 .../rag/document/model/entity/DocumentEntity.java  |  88 +++
 .../model/entity/DocumentUploadChunkEntity.java    |  59 ++
 .../model/entity/DocumentUploadSessionEntity.java  |  89 +++
 .../rag/document/model/vo/DocumentListItemVO.java  |  58 ++
 .../rag/document/model/vo/DocumentPreviewVO.java   |  26 +
 .../rag/document/model/vo/UploadInitResponse.java  |  73 ++
 .../document/model/vo/UploadStatusResponse.java    |  24 +
 .../service/DocumentIngestionAsyncListener.java    |  47 ++
 .../service/DocumentIngestionAsyncService.java     | 228 ++++++
 .../service/DocumentIngestionRequestedEvent.java   |  18 +
 .../rag/document/service/DocumentService.java      | 793 +++++++++++++++++++++
 .../document/service/DocumentUploadService.java    | 614 ++++++++++++++++
 .../StaleProcessingDocumentRecoveryRunner.java     |  79 ++
 .../argus/rag/ingestion/chunk/ChunkService.java    | 296 ++++++++
 .../config/DocumentIngestionConfiguration.java     | 117 +++
 .../rag/ingestion/mapper/DocumentChunkMapper.java  |  75 ++
 .../rag/ingestion/mapper/IngestionJobMapper.java   |  93 +++
 .../model/entity/DocumentChunkEntity.java          |  43 ++
 .../ingestion/model/entity/IngestionJobEntity.java |  50 ++
 .../parser/factory/DocumentParserFactory.java      | 121 ++++
 .../ingestion/parser/strategy/DocumentParser.java  |  42 ++
 .../parser/strategy/DocxDocumentParser.java        |  74 ++
 .../parser/strategy/MdDocumentParser.java          |  88 +++
 .../parser/strategy/PdfDocumentParser.java         |  65 ++
 .../parser/strategy/TxtDocumentParser.java         |  62 ++
 .../parser/support/TextDecodingSupport.java        | 153 ++++
 .../reader/StoredObjectDocumentReader.java         | 144 ++++
 .../service/DocumentIngestionProcessor.java        |  18 +
 .../service/EtlDocumentIngestionProcessor.java     | 202 ++++++
 .../argus/rag/ingestion/service/package-info.java  |   4 +
 .../ingestion/transformer/ChunkingProperties.java  |  37 +
 .../StructureAwareChunkTransformer.java            | 336 +++++++++
 .../transformer/TextCleanupTransformer.java        | 185 +++++
 .../ingestion/vector/VectorIngestionService.java   | 208 ++++++
 .../ElasticsearchChunkIndexService.java            | 632 ++++++++++++++++
 .../vectorstore/PgVectorRetrievalAdapter.java      | 199 ++++++
 .../rag/storage/service/MinioStorageService.java   | 280 ++++++++
 .../service/MissingObjectStorageService.java       | 108 +++
 .../rag/storage/service/ObjectStorageService.java  |  78 ++
 .../main/resources/ArgusBackendApplication.java    |  16 +
 .../src/main/resources/application-dev.yml         |  70 +-
 .../src/main/resources/application-local.yml       |  68 +-
 Argus-backend/src/main/resources/application.yml   |   6 +-
 .../src/main/resources/document/DocumentMapper.xml | 131 ++++
 .../document/DocumentUploadChunkMapper.xml         |  67 ++
 .../document/DocumentUploadSessionMapper.xml       |  77 ++
 .../resources/ingestion/DocumentChunkMapper.xml    |  90 +++
 .../resources/ingestion/IngestionJobMapper.xml     |  97 +++
 sql/schema.sql                                     | 385 ++++++++++
 60 files changed, 7565 insertions(+), 36 deletions(-)
```

</details>

### 阶段 06：Refactor code structure for improved readability and maintainability

- **提交**：`17e4487`；原始提交时间：2026-05-09T15:07:36+08:00。
- **阶段目标**：补入已被前序代码引用的线程用户上下文、群组模型和文档下载响应，并整理 V2 文档。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/auth/security/UserContext.java`、`Argus-backend/src/main/java/com/argus/rag/document/model/vo/DocumentDownloadVO.java`、`Argus-backend/src/main/java/com/argus/rag/groupmembership/model/entity/Group.java`、`Argus-backend/src/main/java/com/argus/rag/groupmembership/model/entity/GroupMembership.java`、`Argus-backend/src/main/java/com/argus/rag/groupmembership/model/vo/MyJoinRequestResponse.java`、`docs/V2.0-项目文档.md`。
- **实际变化**：本提交虽使用泛化的 refactor 标题，实际是 10 个新文件共 1484 行，没有删除或移动记录。新增 ThreadLocal UserContext、封装 InputStream/文件名/类型/大小的 DocumentDownloadVO、4 个群组实体以及成员和申请的 3 个响应 record。另新增 1206 行 V2 项目文档，因此既不是纯文档提交，也不是新的文档摄入算法实现。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；前序阶段已经存在对本次新增类型的引用，历史快照可以确认定义滞后入库，但不能编造补文件前的具体错误文本。
- **参考阶段方案**：以 ThreadLocal 的 set/get/remove 实现用户上下文，补上表映射实体及下载/群组响应类型；V2 文档解释既有上传、ETL 和检索基础设施，不把文档新增等同于这些功能此时才开始实现。
- **代码依据**：history/06-17e4487.diff:4-39；Argus-backend/src/main/java/com/argus/rag/auth/security/UserContext.java#CURRENT_USER/set/get/clear；history/06-17e4487.diff:40-76；Argus-backend/src/main/java/com/argus/rag/document/model/vo/DocumentDownloadVO.java#inputStream/fileName/contentType/fileSize；history/06-17e4487.diff:77-335；Argus-backend/src/main/java/com/argus/rag/groupmembership/model/entity/Group.java#TableName；Argus-backend/src/main/java/com/argus/rag/groupmembership/model/vo/OwnerJoinRequestResponse.java#OwnerJoinRequestResponse

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 6 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../com/argus/rag/auth/security/UserContext.java   |   30 +
 .../rag/document/model/vo/DocumentDownloadVO.java  |   31 +
 .../rag/groupmembership/model/entity/Group.java    |   42 +
 .../model/entity/GroupInvitation.java              |   39 +
 .../model/entity/GroupJoinRequest.java             |   39 +
 .../model/entity/GroupMembership.java              |   33 +
 .../model/vo/GroupMemberResponse.java              |   16 +
 .../model/vo/MyJoinRequestResponse.java            |   24 +
 .../model/vo/OwnerJoinRequestResponse.java         |   24 +
 docs/V2.0-项目文档.md                              | 1206 ++++++++++++++++++++
 10 files changed, 1484 insertions(+)
```

</details>

### 阶段 07：feat(rag): 新增混合检索服务及知识问答请求响应模型

- **提交**：`67b4423`；原始提交时间：2026-05-09T17:18:36+08:00。
- **阶段目标**：加入查询规划、双通道融合检索和带证据引用的知识问答链路。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/qa/rag/HybridChunkRetrievalService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/service/QueryPlanningService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/service/QaChatService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/service/QaService.java`、`Argus-backend/src/main/resources/mappers/document/DocumentMapper.xml`、`Argus-backend/src/main/resources/prompts/qa/system.st`。
- **实际变化**：31 个文件新增 1440 行，加入 QA 请求/响应、查询规划、PGVector 与 ES 候选融合、相邻切片聚合和证据等级判断，以及预取证据复用、结构化答案和引用组装。规划支持 DIRECT/REWRITE/DECOMPOSE，最多保留 3 条查询，空证据直接拒答，结构化解析失败时另取原始内容尝试解析。5 个文档/摄入 XML 迁入 mappers 子目录，还提前加入助手摘要提示模板；但新 QA Java 文件的 package/import 仍使用 旧项目包名。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；源码路径为 com/argus/rag/qa，但新增文件声明旧包名并引用旧命名空间，QaService 还调用带 request 参数的 requireGroupReadable，这与已存在的群组服务签名不一致。阶段 8 的 diff 明确改正这些位置，不能把阶段 7 记作已完成运行联调。
- **参考阶段方案**：本阶段在代码中按查询规划逐条执行向量/关键词检索，以 RRF 排名和邻接窗口组织证据，再通过 ChatClient/检索 Advisor 生成答案并设置无证据、格式错误的返回分支；对命名空间和方法签名的修正应留在下一阶段叙述。
- **代码依据**：history/07-67b4423.diff:321-408,527-585；Argus-backend/src/main/java/com/argus/rag/qa/rag/HybridChunkRetrievalService.java#RRF_K/retrieve/buildClusters/evaluateEvidenceLevel；history/07-67b4423.diff:1042-1118；Argus-backend/src/main/java/com/argus/rag/qa/service/QaChatService.java#ask/getStructuredAnswer/parseFallbackAnswer；history/07-67b4423.diff:1139-1164,1209-1272；Argus-backend/src/main/java/com/argus/rag/qa/service/QaService.java#package/ask；Argus-backend/src/main/java/com/argus/rag/qa/service/QueryPlanningService.java#plan/validatePlan

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 7 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../rag/qa/config/QaChatClientConfiguration.java   |  62 +++
 .../rag/qa/config/QueryPlanningConfiguration.java  |  26 ++
 .../com/argus/rag/qa/controller/QaController.java  |  30 ++
 .../java/com/argus/rag/qa/model/EvidenceLevel.java |   8 +
 .../argus/rag/qa/model/KnowledgeAnswerOutput.java  |  12 +
 .../com/argus/rag/qa/model/QueryPlanResult.java    |  16 +
 .../com/argus/rag/qa/model/QueryPlanStrategy.java  |   7 +
 .../argus/rag/qa/model/dto/AskQuestionRequest.java |  33 ++
 .../argus/rag/qa/model/vo/AskQuestionResponse.java |  37 ++
 .../rag/qa/rag/HybridChunkRetrievalService.java    | 490 +++++++++++++++++++++
 .../rag/qa/rag/QuestionQueryRewriteService.java    |  57 +++
 .../rag/qa/rag/ReadyChunkDocumentRetriever.java    | 104 +++++
 .../argus/rag/qa/rag/RetrievedEvidenceBundle.java  |  21 +
 .../com/argus/rag/qa/service/QaChatService.java    | 142 ++++++
 .../java/com/argus/rag/qa/service/QaService.java   |  28 ++
 .../argus/rag/qa/service/QueryPlanningService.java | 113 +++++
 .../com/argus/rag/qa/service/package-info.java     |   4 +
 .../argus/rag/qa/support/CitationAssembler.java    |  81 ++++
 .../com/argus/rag/qa/support/QaAnswerParser.java   |  28 ++
 .../{ => mappers}/document/DocumentMapper.xml      |   0
 .../document/DocumentUploadChunkMapper.xml         |   0
 .../document/DocumentUploadSessionMapper.xml       |   0
 .../ingestion/DocumentChunkMapper.xml              |   0
 .../{ => mappers}/ingestion/IngestionJobMapper.xml |   0
 .../prompts/assistant/runtime-compact-summary.st   |  24 +
 .../prompts/assistant/session-compact-summary.st   |  20 +
 .../prompts/assistant/session-memory-update.st     |  19 +
 .../src/main/resources/prompts/qa/rag-context.st   |   8 +
 .../src/main/resources/prompts/qa/system.st        |  18 +
 .../src/main/resources/prompts/qa/user.st          |  19 +
 .../main/resources/prompts/query-planning/user.st  |  33 ++
 31 files changed, 1440 insertions(+)
```

</details>

### 阶段 08：feat(rag): 新增混合检索服务及知识问答请求响应模型

- **提交**：`812d932`；原始提交时间：2026-05-09T17:18:48+08:00。
- **阶段目标**：对齐 QA 模块命名空间和权限调用，整理 Mapper 扫描路径及 V2/V3 文档。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/qa/service/QaService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/rag/HybridChunkRetrievalService.java`、`Argus-backend/src/main/java/com/argus/rag/auth/config/AuthProperties.java`、`Argus-backend/src/main/resources/application.yml`、`docs/V2.0-设计决策.md`、`docs/V3.0-设计决策.md`、`docs/V3.0-项目文档.md`。
- **实际变化**：72 个文件新增 2615 行、删除 437 行，标题沿用阶段 7，但核心实际差异是把 QA 的 旧项目包名 package/import 改为 com.argus.rag，并把群组可读检查改为只传 groupId。Mapper 扫描收敛为 classpath*:/mappers/**/*.xml，JWT 默认 issuer 改为 argus-rag。大量其他源码变更为注释整理，文档拆出 V2 设计决策并新增 V3 设计及项目说明，不能再次宣称新建了一套混合检索算法。
- **问题与证据边界**：源码 diff 可以证明旧命名空间与权限方法参数不一致被修正；未见历史故障日志、不能推断实际报错或修正后启动成功。
- **参考阶段方案**：逐文件统一 QA 包名和导入，适配已有 requireGroupReadable(groupId)，配合上一阶段 XML 迁移移除多余扫描路径，并用 Javadoc 与独立设计文档解释现有检索链路。
- **代码依据**：history/08-812d932.diff:995-1014,1708-1761；Argus-backend/src/main/java/com/argus/rag/qa/rag/HybridChunkRetrievalService.java#package/import；Argus-backend/src/main/java/com/argus/rag/qa/service/QaService.java#ask；history/08-812d932.diff:8-13,2086-2097；Argus-backend/src/main/java/com/argus/rag/auth/config/AuthProperties.java#issuer；Argus-backend/src/main/resources/application.yml#mybatis-plus.mapper-locations；manifest.json#commits[stage=8].stat；docs/V2.0-设计决策.md#三阶段分片上传协议；docs/V3.0-设计决策.md#查询规划与混合检索设计

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 8 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../com/argus/rag/auth/config/AuthProperties.java  |   2 +-
 .../document/controller/DocumentController.java    |   2 +-
 .../argus/rag/document/mapper/DocumentMapper.java  |   2 +-
 .../document/mapper/DocumentUploadChunkMapper.java |   2 +-
 .../mapper/DocumentUploadSessionMapper.java        |   2 +-
 .../rag/document/model/dto/DocumentQuery.java      |   2 +-
 .../rag/document/model/dto/UploadChunkRequest.java |   2 +-
 .../document/model/dto/UploadDocumentRequest.java  |   2 +-
 .../rag/document/model/dto/UploadInitRequest.java  |   2 +-
 .../rag/document/model/entity/DocumentEntity.java  |   2 +-
 .../model/entity/DocumentUploadChunkEntity.java    |   2 +-
 .../model/entity/DocumentUploadSessionEntity.java  |   2 +-
 .../rag/document/model/vo/DocumentDownloadVO.java  |   2 +-
 .../rag/document/model/vo/DocumentListItemVO.java  |   2 +-
 .../rag/document/model/vo/DocumentPreviewVO.java   |   2 +-
 .../rag/document/model/vo/UploadInitResponse.java  |   2 +-
 .../document/model/vo/UploadStatusResponse.java    |   2 +-
 .../service/DocumentIngestionAsyncListener.java    |   2 +-
 .../service/DocumentIngestionAsyncService.java     |   2 +-
 .../service/DocumentIngestionRequestedEvent.java   |   2 +-
 .../rag/document/service/DocumentService.java      |   2 +-
 .../document/service/DocumentUploadService.java    |   2 +-
 .../StaleProcessingDocumentRecoveryRunner.java     |   2 +-
 .../argus/rag/ingestion/chunk/ChunkService.java    |   2 +-
 .../config/DocumentIngestionConfiguration.java     |   2 +-
 .../rag/ingestion/mapper/DocumentChunkMapper.java  |   2 +-
 .../rag/ingestion/mapper/IngestionJobMapper.java   |   2 +-
 .../model/entity/DocumentChunkEntity.java          |   2 +-
 .../ingestion/model/entity/IngestionJobEntity.java |   2 +-
 .../parser/factory/DocumentParserFactory.java      |   2 +-
 .../ingestion/parser/strategy/DocumentParser.java  |   2 +-
 .../parser/strategy/DocxDocumentParser.java        |   2 +-
 .../parser/strategy/MdDocumentParser.java          |   2 +-
 .../parser/strategy/PdfDocumentParser.java         |   2 +-
 .../parser/strategy/TxtDocumentParser.java         |   2 +-
 .../parser/support/TextDecodingSupport.java        |   2 +-
 .../reader/StoredObjectDocumentReader.java         |   2 +-
 .../service/DocumentIngestionProcessor.java        |   2 +-
 .../service/EtlDocumentIngestionProcessor.java     |   2 +-
 .../ingestion/transformer/ChunkingProperties.java  |   2 +-
 .../StructureAwareChunkTransformer.java            |   2 +-
 .../transformer/TextCleanupTransformer.java        |   2 +-
 .../ingestion/vector/VectorIngestionService.java   |   2 +-
 .../rag/qa/config/QaChatClientConfiguration.java   |  55 +-
 .../rag/qa/config/QueryPlanningConfiguration.java  |  23 +-
 .../com/argus/rag/qa/controller/QaController.java  |  30 +-
 .../java/com/argus/rag/qa/model/EvidenceLevel.java |  17 +-
 .../argus/rag/qa/model/KnowledgeAnswerOutput.java  |  14 +-
 .../com/argus/rag/qa/model/QueryPlanResult.java    |  19 +-
 .../com/argus/rag/qa/model/QueryPlanStrategy.java  |  15 +-
 .../argus/rag/qa/model/dto/AskQuestionRequest.java |  11 +-
 .../argus/rag/qa/model/vo/AskQuestionResponse.java |  40 +-
 .../rag/qa/rag/HybridChunkRetrievalService.java    | 127 ++-
 .../rag/qa/rag/QuestionQueryRewriteService.java    |  25 +-
 .../rag/qa/rag/ReadyChunkDocumentRetriever.java    |  50 +-
 .../argus/rag/qa/rag/RetrievedEvidenceBundle.java  |  19 +-
 .../com/argus/rag/qa/service/QaChatService.java    |  66 +-
 .../java/com/argus/rag/qa/service/QaService.java   |  34 +-
 .../argus/rag/qa/service/QueryPlanningService.java |  41 +-
 .../com/argus/rag/qa/service/package-info.java     |   8 +-
 .../argus/rag/qa/support/CitationAssembler.java    |  27 +-
 .../com/argus/rag/qa/support/QaAnswerParser.java   |  22 +-
 .../ElasticsearchChunkIndexService.java            |   2 +-
 .../vectorstore/PgVectorRetrievalAdapter.java      |   2 +-
 .../rag/storage/service/MinioStorageService.java   |   2 +-
 .../service/MissingObjectStorageService.java       |   2 +-
 .../rag/storage/service/ObjectStorageService.java  |   2 +-
 Argus-backend/src/main/resources/application.yml   |   2 +-
 docs/V2.0-设计决策.md                              | 524 +++++++++++++
 docs/V2.0-项目文档.md                              | 337 +-------
 docs/V3.0-设计决策.md                              | 862 +++++++++++++++++++++
 docs/V3.0-项目文档.md                              | 588 ++++++++++++++
 72 files changed, 2615 insertions(+), 437 deletions(-)
```

</details>

### 阶段 09：feat(rag): 新增混合检索服务及知识问答请求响应模型

- **提交**：`088901d`；原始提交时间：2026-05-09T18:37:29+08:00。
- **阶段目标**：移除被跟踪的本地环境配置，提供可通过环境变量填写的开发配置示例。
- **涉及文件与模块**：`Argus-backend/.gitignore`、`Argus-backend/pom.xml`、`Argus-backend/src/main/resources/application-dev.yml`、`Argus-backend/src/main/resources/application-local.yml`、`docs/V3.0-设计决策.md`。
- **实际变化**：5 个文件新增 58 行、删除 114 行，虽然继续使用混合检索的提交标题，实际没有修改 QA Java 业务代码。删除被跟踪的 application-local.yml，并在后端 .gitignore 中忽略同名配置，开发配置将数据库、模型密钥、对象存储、ES 和开发账号参数改为环境变量占位，端口改为 10001。POM 仅增加被注释的 Ollama 依赖示例，开发 YAML 也提供被注释的 Ollama 配置，同时调整 PGVector 配置并给设计文档补一个绘图工具链接。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；删除本地配置是可证实的版本管理变化，不代表真实环境凭据已撤销。基础 application.yml 的 active: local 此阶段未改，复现不能把删除后的仓库描述为无需外部配置即可启动。
- **参考阶段方案**：用环境变量和示例值替换开发配置中的环境专用值，并让本地配置不再跟踪；被注释的 Ollama 示例只记录为可选说明，不能写成已经切换或验证 Ollama。不得在记录中输出任何历史真实凭据。
- **代码依据**：history/09-088901d.diff:4-29,158-247；Argus-backend/.gitignore#application-local.yml；Argus-backend/pom.xml#被注释的 spring-ai-starter-model-ollama；Argus-backend/src/main/resources/application-local.yml#删除；history/09-088901d.diff:37-155；Argus-backend/src/main/resources/application-dev.yml#spring.datasource/spring.ai/storage.minio/elasticsearch；history/10-75d7b77.diff:8-10；Argus-backend/src/main/resources/application.yml#spring.profiles.active（紧接阶段仍为 local 的上下文）

**本次验证结果**

- `mvnw.cmd clean compile`：通过，退出码 `0`，耗时 15 秒；原始日志索引 `09-backend.log`。

```text
[INFO] BUILD SUCCESS
```

> 【截图占位：此处插入阶段 9 backend 的真实成功输出截图，对应 09-backend.log；不得用其他阶段截图替代】

- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。
<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
Argus-backend/.gitignore                           |  4 ++
 Argus-backend/pom.xml                              |  6 ++
 .../src/main/resources/application-dev.yml         | 76 ++++++++++++--------
 .../src/main/resources/application-local.yml       | 84 ----------------------
 docs/V3.0-设计决策.md                              |  2 +
 5 files changed, 58 insertions(+), 114 deletions(-)
```

</details>

### 阶段 10：feat(config): 添加 MyBatis-Plus 基本配置支持

- **提交**：`75d7b77`；原始提交时间：2026-05-09T18:47:05+08:00。
- **阶段目标**：为已有 MyBatis-Plus 配置补充用途和扫描规则说明。
- **涉及文件与模块**：`Argus-backend/src/main/resources/application.yml`。
- **实际变化**：仅 application.yml 新增 18 行，全部是注释、分隔线或空行。说明 mapper-locations、type-aliases-package、下划线转驼峰以及 EnumTypeHandler 的用途，原有配置键和值保持不变。标题称添加基本配置，但 diff 不支持新增配置能力或行为改变的说法。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；这是仅注释提交，没有证据表明它修复了 Mapper 扫描或枚举映射故障。
- **参考阶段方案**：通过 YAML 注释解释已有递归 XML 扫描和枚举名称存储约定；不描述成新增 MyBatis-Plus 接入，也不填入构建验证结果。
- **代码依据**：history/10-75d7b77.diff:8-36；Argus-backend/src/main/resources/application.yml#mybatis-plus.mapper-locations/type-aliases-package/configuration.map-underscore-to-camel-case/configuration.default-enum-type-handler

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 10 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
Argus-backend/src/main/resources/application.yml | 18 ++++++++++++++++++
 1 file changed, 18 insertions(+)
```

</details>

### 阶段 11：docs(argus): 编写 Argus 项目启动流程与配置加载详解文档

- **提交**：`76506c4`；原始提交时间：2026-05-09T19:23:59+08:00。
- **阶段目标**：整理项目启动顺序、配置加载和 Bean 注册机制的中文说明。
- **涉及文件与模块**：`docs/启动流程与配置加载说明.md`。
- **实际变化**：仅新增 837 行启动说明文档，按 prepareEnvironment、prepareContext、refreshContext、callRunners 和就绪事件梳理启动链。文档覆盖 profile 配置、Logback、MapperScan、异步机制、认证/摄入/QA 配置类、JWT 过滤器以及启动 Runner 和 Bean 清单。未修改源码、依赖或运行配置，是纯文档提交。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；文档中的启动示意和完成标志不等于真实执行日志，尤其其中仍讲解已在阶段 9 退出版本跟踪的 application-local.yml，不能视为本阶段恢复了该配置文件。
- **参考阶段方案**：文档层面把已有启动组件和配置关系按阶段解释清楚；代码层面无修复或新功能，不将文档范例改写为实际启动成功记录。
- **代码依据**：history/11-76506c4.diff:4-55；docs/启动流程与配置加载说明.md#一、启动入口/二、配置文件加载；history/11-76506c4.diff:352-517,572-741；docs/启动流程与配置加载说明.md#四、Configuration 类加载/六、ApplicationRunner 执行/七、应用就绪

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 11 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
docs/启动流程与配置加载说明.md | 837 +++++++++++++++++++++++++++++++++++++++++
 1 file changed, 837 insertions(+)
```

</details>

### 阶段 12：feat(assistant): 实现助手Agent及相关会话和聊天控制器

- **提交**：`91f7da4`；原始提交时间：2026-05-10T06:44:40+08:00。
- **阶段目标**：加入基于 ReactAgent 的助手会话、同步/SSE 聊天、知识库工具和短期记忆实现。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/assistant/agent/AssistantReactAgentFactory.java`、`Argus-backend/src/main/java/com/argus/rag/assistant/agent/AssistantKnowledgeBaseTool.java`、`Argus-backend/src/main/java/com/argus/rag/assistant/controller/AssistantChatController.java`、`Argus-backend/src/main/java/com/argus/rag/assistant/service/AssistantService.java`、`Argus-backend/src/main/java/com/argus/rag/assistant/memory/AssistantShortTermMemoryHook.java`、`Argus-backend/src/main/resources/mappers/assistant/AssistantSessionContextMapper.xml`。
- **实际变化**：52 个文件新增 4190 行、删除 5 行，新增助手会话管理、消息持久化、上下文查询和 CHAT/KB_SEARCH 两种模式，两者共用 ReactAgent，知识库模式挂载仅返回证据的工具并以结果持有器限制本轮重复检索。同步聊天保存消息并返回回复，SSE 使用线程池及 start/delta/done/error 事件；短期记忆 Hook 重组摘要、记忆、最近消息、工具观察和当前问题，相关 Mapper/XML 支持会话上下文维护。另为上传、ETL、双路检索和 QA 增加链路日志，本阶段没有新增助手建表 SQL。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；新增 SSE 入口直接在工作线程调用依赖 UserContext 的服务，没有把请求线程的 ThreadLocal 用户复制过去，阶段 18 才显式处理这一点。助手表定义在阶段 15 才加入 schema.sql，且 Hook 中虽有 shouldRuntimeCompact/runtimeCompact 方法，beforeModel 本身没有调用这两个方法，不能仅按设计说明声称该运行时截断已自动触发。
- **参考阶段方案**：以 AssistantService 编排参数、权限、消息与 Agent 调用，KB 工具返回证据/引用而不代替 Agent 生成最终答案；通过 MessagesModelHook 的 REPLACE 策略和当前问题去重重建上下文，并用数据库 Mapper 持久化会话信息。只记录这些代码应对，不将新增日志语句当成已经产生的运行结果。
- **代码依据**：history/12-91f7da4.diff:587-612,350-392；Argus-backend/src/main/java/com/argus/rag/assistant/agent/AssistantReactAgentFactory.java#createAgent；Argus-backend/src/main/java/com/argus/rag/assistant/agent/AssistantKnowledgeBaseTool.java#search；history/12-91f7da4.diff:659-725,3404-3477；Argus-backend/src/main/java/com/argus/rag/assistant/controller/AssistantChatController.java#streamChat；Argus-backend/src/main/java/com/argus/rag/assistant/service/AssistantService.java#streamChat；history/12-91f7da4.diff:1645-1694,1755-1799,1823-1828；Argus-backend/src/main/java/com/argus/rag/assistant/memory/AssistantShortTermMemoryHook.java#beforeModel/assembleBeforeModelMessages/shouldRuntimeCompact/runtimeCompact

**本次验证结果**

- `mvnw.cmd clean compile`：通过，退出码 `0`，耗时 20 秒；原始日志索引 `12-backend.log`。

```text
[INFO] BUILD SUCCESS
```

> 【截图占位：此处插入阶段 12 backend 的真实成功输出截图，对应 12-backend.log；不得用其他阶段截图替代】

- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。
<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
Argus-backend/pom.xml                              |  15 +
 .../rag/assistant/agent/AssistantAgentFacade.java  | 235 ++++++++++++++
 .../agent/AssistantKnowledgeBaseTool.java          | 172 ++++++++++
 .../AssistantKnowledgeBaseToolResultHolder.java    |  61 ++++
 .../agent/AssistantReactAgentFactory.java          |  79 +++++
 .../controller/AssistantChatController.java        | 146 +++++++++
 .../AssistantConversationController.java           |  43 +++
 .../controller/AssistantSessionController.java     | 110 +++++++
 .../assistant/mapper/AssistantMessageMapper.java   |  59 ++++
 .../mapper/AssistantSessionContextMapper.java      |  51 +++
 .../assistant/mapper/AssistantSessionMapper.java   |  85 +++++
 .../memory/AssistantMemoryPromptConfiguration.java |  57 ++++
 .../memory/AssistantMemorySummarizer.java          | 174 ++++++++++
 .../memory/AssistantSessionSummaryService.java     | 196 +++++++++++
 .../memory/AssistantShortTermMemoryHook.java       | 335 +++++++++++++++++++
 ...AssistantShortTermMemoryMaintenanceService.java | 221 +++++++++++++
 .../model/dto/chat/AssistantChatRequest.java       |  37 +++
 .../dto/message/AssistantMessageCreateDTO.java     |  32 ++
 .../dto/session/CreateAssistantSessionRequest.java |  18 ++
 .../dto/session/UpdateAssistantSessionRequest.java |  15 +
 .../model/entity/AssistantMessageEntity.java       |  50 +++
 .../entity/AssistantSessionContextEntity.java      |  65 ++++
 .../model/entity/AssistantSessionEntity.java       |  43 +++
 .../model/enums/AssistantMessageRole.java          |  20 ++
 .../model/enums/AssistantSessionStatus.java        |  20 ++
 .../assistant/model/enums/AssistantToolMode.java   |  16 +
 .../model/vo/chat/AssistantAgentResult.java        |  26 ++
 .../model/vo/chat/AssistantChatResponse.java       |  34 ++
 .../model/vo/chat/AssistantChatStreamEvent.java    | 124 +++++++
 .../AssistantConversationContextVO.java            |  18 ++
 .../model/vo/message/AssistantMessageVO.java       |  42 +++
 .../model/vo/session/AssistantSessionDetailVO.java |  28 ++
 .../vo/session/AssistantSessionListItemVO.java     |  19 ++
 .../vo/tool/KnowledgeBaseSearchToolResponse.java   |  68 ++++
 .../service/AssistantConversationService.java      | 358 +++++++++++++++++++++
 .../rag/assistant/service/AssistantService.java    | 334 +++++++++++++++++++
 .../assistant/service/AssistantSessionService.java | 239 ++++++++++++++
 .../service/AssistantStreamEventEmitter.java       |  17 +
 .../config/AssistantPromptContextBuilder.java      | 111 +++++++
 .../config/AssistantRunnableConfigFactory.java     |  43 +++
 .../service/DocumentIngestionAsyncListener.java    |   6 +
 .../service/DocumentIngestionAsyncService.java     |   4 +
 .../rag/document/service/DocumentService.java      |   4 +
 .../document/service/DocumentUploadService.java    |  28 +-
 .../rag/qa/rag/HybridChunkRetrievalService.java    |  22 ++
 .../com/argus/rag/qa/service/QaChatService.java    |  12 +
 .../argus/rag/qa/service/QueryPlanningService.java |   6 +-
 .../ElasticsearchChunkIndexService.java            |  10 +-
 .../vectorstore/PgVectorRetrievalAdapter.java      |  11 +-
 .../mappers/assistant/AssistantMessageMapper.xml   |  79 +++++
 .../assistant/AssistantSessionContextMapper.xml    | 109 +++++++
 .../mappers/assistant/AssistantSessionMapper.xml   |  88 +++++
 52 files changed, 4190 insertions(+), 5 deletions(-)
```

</details>

### 阶段 13：docs(assistant): 添加 V4.0 AI 助手模块设计决策文档

- **提交**：`ae80680`；原始提交时间：2026-05-10T10:21:23+08:00。
- **阶段目标**：补充 V4 助手模块的设计决策、接口和使用说明。
- **涉及文件与模块**：`docs/V4.0-设计决策.md`、`docs/V4.0-项目文档.md`。
- **实际变化**：仅新增两份文档共 1410 行，设计决策介绍 ReactAgent 统一入口、短期记忆、BEFORE_MODEL Hook、SSE 增量处理、单轮知识库工具调用和自动命名。项目文档列出助手目录、表结构、API、配置、权限和运行指导。虽然文档包含示例代码及建表说明，本提交未修改 Java 或 SQL 文件，不能归类为再次实现助手功能。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；文档中的设计解释不是验证证据。具体偏差是文档将 50000 token 的 runtimeCompact 描述成超阈值自动截断防线，但阶段 12 的 beforeModel 只组装后返回，没有接入该调用；文档中的助手表也尚未在 schema.sql 加入。
- **参考阶段方案**：只将其作为已有助手实现的说明资料，引用到实际符号时以源码调用关系为准；没有代码层面的新增应对，不把文档中的计划、机制解释或示例输出冒充已经执行的修复与验证。
- **代码依据**：history/13-ae80680.diff:225-365,369-638；docs/V4.0-设计决策.md#4.1 ReactAgent/4.2 短期记忆/运行时压缩/4.4 SSE/4.5 工具检索；history/13-ae80680.diff:936-1015,1032-1249；docs/V4.0-项目文档.md#四、数据库设计/六、API 接口文档/七、关键配置说明；history/12-91f7da4.diff:1645-1694,1783-1799；Argus-backend/src/main/java/com/argus/rag/assistant/memory/AssistantShortTermMemoryHook.java#beforeModel/runtimeCompact

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 13 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
docs/V4.0-设计决策.md | 683 +++++++++++++++++++++++++++++++++++++++++++++++
 docs/V4.0-项目文档.md | 727 ++++++++++++++++++++++++++++++++++++++++++++++++++
 2 files changed, 1410 insertions(+)
```

</details>

### 阶段 14：refactor(core): 完成 Argus-backend 模块大规模重构设计与代码迁移

- **提交**：`af92ee2`；原始提交时间：2026-05-10T14:08:19+08:00。
- **阶段目标**：重整模块边界，拆分文档服务并建立对外查询/检索门面。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/document/controller/DocumentController.java`、`Argus-backend/src/main/java/com/argus/rag/document/service/DocumentUploadService.java`、`Argus-backend/src/main/java/com/argus/rag/document/service/DocumentQueryService.java`、`Argus-backend/src/main/java/com/argus/rag/common/security/AuthenticatedUser.java`、`Argus-backend/src/main/java/com/argus/rag/common/security/UserContext.java`、`Argus-backend/src/main/java/com/argus/rag/qa/service/QaRetrievalService.java`、`Argus-backend/src/main/java/com/argus/rag/user/service/UserQueryService.java`、`Argus-backend/docs/superpowers/specs/2026-05-06-argus-module-refactoring-design.md`。
- **实际变化**：71 个文件新增 1566 行、删除 1150 行，删除原 DocumentService 并拆分查询、删除/重试、预览和下载服务，直接上传及合并后的持久化移入 DocumentUploadService，控制器改调对应职责服务。groupmembership 改为 group，ES/PGVector/对象存储组件移入 engine，解析/清洗/切片归入 ingestion.service.pipeline，异步摄入服务移入 ingestion；UserContext 和独立 AuthenticatedUser 移到 common.security。新增 QaRetrievalService 供助手调用，CurrentUserService 改经 UserQueryService.findById 查询用户，删除 resources 中的启动类副本，并同步更新架构文档。
- **问题与证据边界**：同提交设计文档明确列出原 DocumentService 797 行、多职责、Servlet 请求穿透服务层、资源目录误放 Java 以及跨模块直接依赖内部类等结构问题，diff 与这些问题对应；未见历史故障日志、不能推断实际报错。设计目标不能等同于全部落实：DocumentUploadService 的分片方法仍接收 HttpServletRequest，文件扩展到约 845 行，部分拆分服务仍通过线程上下文校验而非真正使用传入 userId。
- **参考阶段方案**：通过职责拆分、包迁移及引用/Mapper namespace 同步调整整理结构，用户查询和 QA 检索改走门面，公共认证数据不再以内嵌过滤器 record 暴露。记录为单体工程内部重构而非 Maven 多模块拆分，也不宣称已经彻底移除服务层 Servlet 依赖或满足所有文件不超过 300 行的设计目标。
- **代码依据**：history/14-af92ee2.diff:18-55；Argus-backend/docs/superpowers/specs/2026-05-06-argus-module-refactoring-design.md#1.1 当前问题/1.2 重构目标；history/14-af92ee2.diff:998-1253,2655-2970；Argus-backend/src/main/java/com/argus/rag/document/controller/DocumentController.java#uploadDocument/previewDocument；Argus-backend/src/main/java/com/argus/rag/document/service/DocumentUploadService.java#uploadDocument/finalizeUploadedDocument；history/14-af92ee2.diff:770-840,941-997,12153-12170；Argus-backend/src/main/java/com/argus/rag/auth/CurrentUserService.java#loadUserById；Argus-backend/src/main/java/com/argus/rag/user/service/UserQueryService.java#findById/UserRecord

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 14 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../2026-05-06-argus-module-refactoring-design.md  | 672 +++++++++++++++++
 .../agent/AssistantKnowledgeBaseTool.java          |  34 +-
 .../rag/assistant/service/AssistantService.java    |   2 +-
 .../com/argus/rag/auth/CurrentUserService.java     |  31 +-
 .../rag/auth/security/JwtAuthenticationFilter.java |  12 +-
 .../rag/common/config/OpenApiConfiguration.java    |  10 -
 .../rag/common/security/AuthenticatedUser.java     |  15 +
 .../rag/{auth => common}/security/UserContext.java |  10 +-
 .../document/controller/DocumentController.java    | 113 +--
 .../document/service/DocumentDeleteService.java    |  92 +++
 .../document/service/DocumentDownloadService.java  |  66 ++
 .../service/DocumentIngestionAsyncListener.java    |   3 +-
 .../document/service/DocumentPreviewService.java   |  65 ++
 .../rag/document/service/DocumentQueryService.java |  89 +++
 .../rag/document/service/DocumentService.java      | 797 ---------------------
 .../document/service/DocumentUploadService.java    | 245 ++++++-
 .../StaleProcessingDocumentRecoveryRunner.java     |   1 -
 .../ElasticsearchChunkIndexService.java            |   3 +-
 .../pgvector}/PgVectorRetrievalAdapter.java        |   3 +-
 .../storage}/MinioStorageService.java              |   3 +-
 .../storage}/MissingObjectStorageService.java      |   3 +-
 .../storage}/ObjectStorageService.java             |   2 +-
 .../controller/GroupJoinRequestController.java     |  10 +-
 .../controller/GroupManagementController.java      |  10 +-
 .../controller/GroupQueryController.java           |   4 +-
 .../controller/InvitationDecisionController.java   |   4 +-
 .../mapper/GroupJoinRequestMapper.java             |  10 +-
 .../mapper/GroupMembershipMapper.java              |   8 +-
 .../model/dto/CreateGroupRequest.java              |   2 +-
 .../model/dto/CreateInvitationRequest.java         |   2 +-
 .../model/dto/CreateJoinRequestRequest.java        |   2 +-
 .../model/entity/Group.java                        |   2 +-
 .../model/entity/GroupInvitation.java              |   2 +-
 .../model/entity/GroupJoinRequest.java             |   2 +-
 .../model/entity/GroupMembership.java              |   2 +-
 .../model/vo/GroupMemberResponse.java              |   2 +-
 .../model/vo/MyJoinRequestResponse.java            |   2 +-
 .../model/vo/OwnerJoinRequestResponse.java         |   2 +-
 .../service/GroupJoinRequestService.java           |  14 +-
 .../service/GroupManagementService.java            |  14 +-
 .../service/GroupMembershipService.java            |   6 +-
 .../config/DocumentIngestionConfiguration.java     |  12 +-
 .../service/DocumentIngestionAsyncService.java     |   5 +-
 .../service/EtlDocumentIngestionProcessor.java     |  13 +-
 .../{chunk => service/pipeline}/ChunkService.java  |   3 +-
 .../pipeline/parser}/DocumentParser.java           |   2 +-
 .../pipeline/parser}/DocumentParserFactory.java    |   4 +-
 .../pipeline/parser}/DocxDocumentParser.java       |   2 +-
 .../pipeline/parser}/MdDocumentParser.java         |   4 +-
 .../pipeline/parser}/PdfDocumentParser.java        |   2 +-
 .../pipeline/parser}/TextDecodingSupport.java      |   2 +-
 .../pipeline/parser}/TxtDocumentParser.java        |   4 +-
 .../reader/StoredObjectDocumentReader.java         |   8 +-
 .../pipeline}/transformer/ChunkingProperties.java  |   2 +-
 .../StructureAwareChunkTransformer.java            |   2 +-
 .../transformer/TextCleanupTransformer.java        |   2 +-
 .../ingestion/vector/VectorIngestionService.java   |   1 -
 .../rag/qa/rag/HybridChunkRetrievalService.java    |   7 +-
 .../argus/rag/qa/service/QaRetrievalService.java   |  37 +
 .../java/com/argus/rag/qa/service/QaService.java   |   2 +-
 .../argus/rag/user/service/UserQueryService.java   |  19 +
 .../main/resources/ArgusBackendApplication.java    |  16 -
 .../resources/mappers/GroupJoinRequestMapper.xml   |  10 +-
 .../resources/mappers/GroupMembershipMapper.xml    |   6 +-
 docs/V1.0-项目文档.md                              |   6 +-
 docs/V2.0-设计决策.md                              |  32 +-
 docs/V2.0-项目文档.md                              |  67 +-
 docs/V3.0-项目文档.md                              |  14 +-
 docs/V4.0-设计决策.md                              |  11 +-
 docs/V4.0-项目文档.md                              |  10 +-
 docs/启动流程与配置加载说明.md                     |  40 +-
 71 files changed, 1566 insertions(+), 1150 deletions(-)
```

</details>

### 阶段 15：feat(database): 添加 AI 助手模块数据表及索引

- **提交**：`905e954`；原始提交时间：2026-05-10T14:21:05+08:00。
- **阶段目标**：将助手会话、消息和短期上下文的建表语句加入版本化 SQL。
- **涉及文件与模块**：`sql/schema.sql`。
- **实际变化**：只修改 schema.sql，新增 91 行，定义 assistant_sessions、assistant_messages、assistant_session_contexts 三张表及字段注释。会话表关联用户，消息表关联会话并含 JSONB 结构化负载，上下文表一对一关联会话并保存记忆、摘要覆盖范围和 context_version。增加按用户/状态和会话/创建时间查询的两个索引，没有修改助手 Java 逻辑。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；只能确认阶段 12 引入助手持久化代码后，本阶段才把对应 DDL 加入 schema.sql，不能写成此前数据库必然缺表或已经实际执行建表成功。
- **参考阶段方案**：在 SQL 脚本中增加 IF NOT EXISTS 建表和索引、外键、默认值及 JSONB/版本字段，使仓库具备对应初始化定义；这是数据库脚本变化，不是仅文档，也不代表数据库实例已迁移。
- **代码依据**：history/15-905e954.diff:19-42；sql/schema.sql#assistant_sessions/idx_sessions_user_status；history/15-905e954.diff:46-71；sql/schema.sql#assistant_messages/structured_payload/idx_messages_session_created；history/15-905e954.diff:75-102；sql/schema.sql#assistant_session_contexts/context_version

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 15 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
sql/schema.sql | 91 ++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 1 file changed, 91 insertions(+)
```

</details>

### 阶段 16：fix(memory): 增加消息组装会话注释

- **提交**：`c94fd59`；原始提交时间：2026-05-10T14:24:24+08:00。
- **阶段目标**：为助手模型调用前的消息组装顺序补充逐步注释。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/assistant/memory/AssistantShortTermMemoryHook.java`。
- **实际变化**：仅一个 Java 文件新增 5 行注释，分别标出紧凑摘要、会话记忆、近期对话、运行时工具消息和当前问题五步。既有方法调用、条件和返回值均未改变。虽以 fix(memory) 开头，实际为仅注释提交，不是记忆行为修复。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；没有代码行为变化可以支持修复丢失消息、改变组装顺序或改善压缩效果等描述。
- **参考阶段方案**：在 assembleBeforeModelMessages 既有步骤前增加编号说明，以提高可读性；没有新增代码层面的故障应对。
- **代码依据**：history/16-c94fd59.diff:8-23；Argus-backend/src/main/java/com/argus/rag/assistant/memory/AssistantShortTermMemoryHook.java#assembleBeforeModelMessages/addSystemMemory/appendRecentMessages/appendRuntimeToolMessages

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 16 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../com/argus/rag/assistant/memory/AssistantShortTermMemoryHook.java | 5 +++++
 1 file changed, 5 insertions(+)
```

</details>

### 阶段 17：feat(group): 支持查询当前用户发出的所有群组邀请

- **提交**：`f4f575e`；原始提交时间：2026-05-12T10:27:04+08:00。
- **阶段目标**：增加已发邀请查询，并同步扩充群组展示、文档全文预览和切片批量写入实现。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/group/controller/GroupManagementController.java`、`Argus-backend/src/main/java/com/argus/rag/group/service/GroupMembershipService.java`、`Argus-backend/src/main/resources/mappers/GroupMembershipMapper.xml`、`Argus-backend/src/main/java/com/argus/rag/document/service/DocumentPreviewService.java`、`Argus-backend/src/main/java/com/argus/rag/ingestion/service/pipeline/ChunkService.java`、`Argus-backend/src/main/resources/mappers/ingestion/DocumentChunkMapper.xml`。
- **实际变化**：10 个文件新增 176 行、删除 48 行，新增 /invitations/my-sent 接口及按邀请人查询 SQL，群组视图增加描述、创建时间和待处理申请数，待处理邀请视图也扩充字段。文档预览从读取数据库中最多 200 字符的 previewText，改为从对象存储读取原文件并解析、拼接全部非空文本。切片保存改调用新 insertBatch XML，以 foreach 构造多行 INSERT、显式转换 metadata_json 为 jsonb，并为 PostgreSQL 参数上限预留 128 个参数余量。
- **问题与证据边界**：未见历史故障日志、不能推断实际报错；diff 证明预览原来会截断、批量写入路径发生替换，但没有具体上传失败日志。新增接口、服务和 XML 引用了 MySentInvitationResponse，而阶段 17、18 的完整文件清单均没有该类，必须记录为源码依赖缺项，不可宣称此阶段已独立通过编译或接口验证。
- **参考阶段方案**：通过 inviterUserId 条件及创建时间倒序提供已发邀请数据；预览复用 StoredObjectDocumentReader 和解析器工厂，仍保留群组可读和 READY 状态检查；切片持久化显式使用自定义批量 SQL 和分批大小计算。不要因标题只提邀请而遗漏另外两组功能变化，也不要提前补入缺失响应类。
- **代码依据**：history/17-f4f575e.diff:152-195,389-402；Argus-backend/src/main/java/com/argus/rag/group/controller/GroupManagementController.java#listMySentInvitations；Argus-backend/src/main/resources/mappers/GroupMembershipMapper.xml#selectSentInvitationsByInviterUserId；history/17-f4f575e.diff:57-132；Argus-backend/src/main/java/com/argus/rag/document/service/DocumentPreviewService.java#previewDocument/loadDocument；history/17-f4f575e.diff:321-343,417-444；Argus-backend/src/main/java/com/argus/rag/ingestion/service/pipeline/ChunkService.java#MAX_INSERT_BATCH_SIZE/persistChunkBatches；Argus-backend/src/main/resources/mappers/ingestion/DocumentChunkMapper.xml#insertBatch

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 17 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../document/controller/DocumentController.java    | 11 ++--
 .../document/service/DocumentPreviewService.java   | 67 ++++++++++++++++------
 .../controller/GroupManagementController.java      |  7 +++
 .../rag/group/mapper/GroupMembershipMapper.java    |  3 +
 .../rag/group/service/GroupManagementService.java  |  7 +++
 .../rag/group/service/GroupMembershipService.java  | 53 +++++++++++------
 .../rag/ingestion/mapper/DocumentChunkMapper.java  |  9 +++
 .../ingestion/service/pipeline/ChunkService.java   |  8 +--
 .../resources/mappers/GroupMembershipMapper.xml    | 28 ++++++++-
 .../mappers/ingestion/DocumentChunkMapper.xml      | 31 ++++++++++
 10 files changed, 176 insertions(+), 48 deletions(-)
```

</details>

### 阶段 18：fix(assistant): 解决流式对话多线程用户上下文传递问题

- **提交**：`4139743`；原始提交时间：2026-05-12T14:02:37+08:00。
- **阶段目标**：显式把请求线程的认证用户传入 SSE 工作线程，并在任务结束清理线程上下文。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/assistant/controller/AssistantChatController.java`、`.gitignore`。
- **实际变化**：2 个文件新增 14 行，SSE 入口先在请求线程取出 AuthenticatedUser，进入 sseExecutor 任务后重新设置 UserContext，并在 finally 调用 clear。业务流式调用和事件发送逻辑沿用原实现。根 .gitignore 同时新增三个工具目录规则及一条单独的 / 规则，不能把这些忽略规则描述成聊天功能。
- **问题与证据边界**：提交标题明确为流式对话多线程用户上下文传递问题，新增注释也明确说明普通 ThreadLocal 不会从请求线程自动传给 SSE 工作线程，旧入口没有该传递步骤；未见历史故障日志，不能推断具体 HTTP 状态、异常堆栈或受影响请求数量。
- **参考阶段方案**：仅在请求线程捕获认证快照，在 SSE 工作线程业务调用前恢复，使用 finally 清理，避免线程池任务结束后遗留用户上下文；这是明确的代码层面应对，不声称已经运行验证，也不扩大为修复 Agent 内部所有异步线程的上下文传递。
- **代码依据**：history/18-4139743.diff:23-58；Argus-backend/src/main/java/com/argus/rag/assistant/controller/AssistantChatController.java#streamChat/UserContext.get/UserContext.set/UserContext.clear；history/18-4139743.diff:4-18；.gitignore#新增 /.agent、/.claude、/.shared 和 / 规则；manifest.json#commits[stage=1..18].parents：阶段 1 为根提交，其余均为单父提交，本研究范围没有合并提交

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 18 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.gitignore                                                     |  4 ++++
 .../rag/assistant/controller/AssistantChatController.java      | 10 ++++++++++
 2 files changed, 14 insertions(+)
```

</details>

### 阶段 19：feat(account): 添加修改密码表单组件

- **提交**：`8e64498`；原始提交时间：2026-05-12T14:21:28+08:00。
- **阶段目标**：复现原项目首次集中纳入前端工程的阶段，并核对修改密码表单与既有接口的衔接。
- **涉及文件与模块**：`Argus-frontend/package.json`、`Argus-frontend/src/router/index.ts`、`Argus-frontend/src/components/AccountPasswordForm.vue`、`Argus-frontend/src/api/auth.ts`、`Argus-frontend/src/api/assistant.ts`、`Argus-frontend/src/views/qa/QaView.vue`、`Argus-backend/src/main/java/com/argus/rag/group/model/vo/MySentInvitationResponse.java`。
- **实际变化**：提交实际涉及79个文件、24001行新增和1行删除，集中加入Vue、TypeScript、Vite、Pinia前端、认证与业务API封装、群组/文档/助手/QA等页面，并非仅增加一个密码表单。AccountPasswordForm.handleSubmit校验必填项、新密码至少6位和两次输入一致，调用changePassword后清空字段并发出completed事件。后端另增MySentInvitationResponse记录类型，同时加入助手模块说明和后续升级计划。
- **问题与证据边界**：历史未记载运行异常；提交标题仅提修改密码表单，明显小于实际diff范围，不能据此把其余前端功能写成后续阶段才首次出现。
- **参考阶段方案**：按历史diff记录为前端集中接入，改密部分说明输入校验、接口调用与错误反馈；助手API中已有streamAssistantMessage，而此时QA API使用askQuestion，避免混淆两类流式能力的引入时间。
- **代码依据**：Argus-frontend/src/components/AccountPasswordForm.vue#handleSubmit；diff第7876-7937行。；Argus-frontend/src/api/auth.ts#changePassword；Argus-frontend/src/api/assistant.ts#streamAssistantMessage；Argus-frontend/src/api/qa.ts#askQuestion。；Argus-backend/src/main/java/com/argus/rag/group/model/vo/MySentInvitationResponse.java#MySentInvitationResponse。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- `npm install --no-audit --no-fund`：通过，退出码 `0`，耗时 47 秒；原始日志索引 `19-frontend-install.log`。

```text
added 348 packages in 47s
```

> 【截图占位：此处插入阶段 19 frontend-install 的真实成功输出截图，对应 19-frontend-install.log；不得用其他阶段截图替代】

- `npm run build`：未通过，退出码 `1`，耗时 7 秒；原始日志索引 `19-frontend-build.log`。

```text
dist/assets/group-CsBY75rl.js                        3.28 kB │ gzip:   1.05 kB
dist/assets/DefaultLayout-BuTSkSgr.js                8.72 kB │ gzip:   2.94 kB
dist/assets/HomeView-DRXYB9FC.js                    17.73 kB │ gzip:   5.81 kB
dist/assets/QaView-Bj7AKAEZ.js                      23.79 kB │ gzip:   8.13 kB
dist/assets/DocumentListView-CgkCbBqH.js            27.17 kB │ gzip:   8.58 kB
dist/assets/GroupsPage-y1Xcu5of.js                  32.01 kB │ gzip:   9.44 kB
dist/assets/AssistantView-C0Z7fnBy.js               33.48 kB │ gzip:  11.11 kB
dist/assets/DocumentPreviewModal-BHQl4XA2.js        46.35 kB │ gzip:  14.48 kB
dist/assets/http-LZZ_uZs3.js                        63.21 kB │ gzip:  24.81 kB
dist/assets/runtime-core.esm-bundler--k6fTV2a.js    64.18 kB │ gzip:  25.20 kB
dist/assets/index-C8AVj_x5.js                      916.05 kB │ gzip: 292.20 kB
✓ built in 1.14s
src/views/groups/components/InvitationsTab.vue(22,10): error TS2532: Object is possibly 'undefined'.
src/views/groups/components/InvitationsTab.vue(22,10): error TS2532: Object is possibly 'undefined'.
src/views/groups/components/JoinedGroupsTab.vue(22,10): error TS2532: Object is possibly 'undefined'.
src/views/groups/components/JoinedGroupsTab.vue(22,10): error TS2532: Object is possibly 'undefined'.
src/views/groups/components/OwnedGroupsTab.vue(20,10): error TS2532: Object is possibly 'undefined'.
src/views/groups/components/OwnedGroupsTab.vue(20,10): error TS2532: Object is possibly 'undefined'.
```

> 【截图占位：此处插入阶段 19 frontend-build 的真实失败输出截图，对应 19-frontend-build.log；不得用其他阶段截图替代】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.gitignore                                         |    3 +-
 .../group/model/vo/MySentInvitationResponse.java   |   26 +
 Argus-frontend/.editorconfig                       |    8 +
 Argus-frontend/.gitattributes                      |    1 +
 Argus-frontend/.gitignore                          |   39 +
 Argus-frontend/.oxlintrc.json                      |   10 +
 Argus-frontend/.prettierrc.json                    |    6 +
 Argus-frontend/README.md                           |   48 +
 Argus-frontend/env.d.ts                            |    1 +
 Argus-frontend/eslint.config.ts                    |   26 +
 Argus-frontend/index.html                          |   13 +
 Argus-frontend/package-lock.json                   | 5138 ++++++++++++++++++++
 Argus-frontend/package.json                        |   47 +
 Argus-frontend/public/favicon.ico                  |  Bin 0 -> 4286 bytes
 Argus-frontend/src/App.vue                         |   60 +
 Argus-frontend/src/api/admin-user.ts               |   91 +
 Argus-frontend/src/api/assistant.ts                |  245 +
 Argus-frontend/src/api/auth.ts                     |  215 +
 Argus-frontend/src/api/document.ts                 |  602 +++
 Argus-frontend/src/api/group.ts                    |  411 ++
 Argus-frontend/src/api/http.ts                     |   98 +
 Argus-frontend/src/api/qa.ts                       |   87 +
 Argus-frontend/src/assets/groups-page.css          |  390 ++
 Argus-frontend/src/assets/main.css                 |  160 +
 .../src/components/AccountPasswordForm.vue         |  191 +
 .../src/components/DocumentPreviewModal.vue        |  338 ++
 Argus-frontend/src/components/EmptyState.vue       |   51 +
 Argus-frontend/src/components/LoginModal.vue       |  320 ++
 .../src/components/layout/PageHeaderHero.vue       |   78 +
 Argus-frontend/src/layouts/DefaultLayout.vue       |  786 +++
 Argus-frontend/src/main.ts                         |   16 +
 Argus-frontend/src/router/index.ts                 |  119 +
 Argus-frontend/src/stores/app.ts                   |   87 +
 Argus-frontend/src/stores/auth.ts                  |  222 +
 Argus-frontend/src/stores/pinia.ts                 |    3 +
 Argus-frontend/src/types/assistant.ts              |  254 +
 Argus-frontend/src/views/HomeView.vue              |  982 ++++
 Argus-frontend/src/views/LoginView.vue             |  224 +
 Argus-frontend/src/views/NotFoundView.vue          |   69 +
 .../src/views/admin/UserManagementView.vue         |   49 +
 .../src/views/assistant/AssistantView.vue          |  479 ++
 .../assistant/components/AssistantCitationBar.vue  |  221 +
 .../assistant/components/AssistantComposer.vue     |  443 ++
 .../views/assistant/components/AssistantEmpty.vue  |  452 ++
 .../assistant/components/AssistantMessage.vue      |  571 +++
 .../assistant/components/AssistantSidebar.vue      |  611 +++
 .../assistant/components/AssistantTranscript.vue   |  289 ++
 .../views/assistant/components/ModeSwitcher.vue    |  144 +
 .../src/views/documents/DocumentListView.vue       |  540 ++
 .../views/documents/components/DocFiltersBar.vue   |  281 ++
 .../src/views/documents/components/DocStatsBar.vue |  243 +
 .../src/views/documents/components/DocTable.vue    |  463 ++
 .../documents/components/RecentFailuresPanel.vue   |  310 ++
 .../views/documents/components/UploadDialog.vue    |  552 +++
 Argus-frontend/src/views/groups/GroupsPage.vue     |  679 +++
 .../src/views/groups/components/GroupCard.vue      |  210 +
 .../src/views/groups/components/GroupStatsBar.vue  |  210 +
 .../src/views/groups/components/InvitationsTab.vue |  146 +
 .../views/groups/components/JoinedGroupsTab.vue    |   93 +
 .../src/views/groups/components/MyRequestsTab.vue  |  333 ++
 .../src/views/groups/components/OwnedGroupsTab.vue |   94 +
 .../groups/components/modals/CreateGroupModal.vue  |  168 +
 .../groups/components/modals/GroupManageModal.vue  |  585 +++
 .../groups/components/modals/JoinGroupModal.vue    |  158 +
 Argus-frontend/src/views/qa/QaView.vue             |  255 +
 .../src/views/qa/components/CitationRail.vue       |  308 ++
 .../src/views/qa/components/QaComposer.vue         |  301 ++
 .../src/views/qa/components/QaEmptyHero.vue        |  419 ++
 .../src/views/qa/components/QaMessage.vue          |  427 ++
 .../src/views/qa/components/QaSidebar.vue          |  521 ++
 .../src/views/qa/components/QaTranscript.vue       |  201 +
 .../src/views/qa/composables/useQaSessions.ts      |  110 +
 Argus-frontend/src/views/settings/SettingsView.vue |  255 +
 Argus-frontend/tsconfig.app.json                   |   18 +
 Argus-frontend/tsconfig.json                       |   11 +
 Argus-frontend/tsconfig.node.json                  |   27 +
 Argus-frontend/vite.config.ts                      |   39 +
 docs/assistant-module-guide.md                     | 1023 ++++
 docs/系统后续改造升级计划.md                       |  298 ++
 79 files changed, 24001 insertions(+), 1 deletion(-)
```

</details>

### 阶段 20：chore(deploy): 添加两节点环境的Docker Compose配置

- **提交**：`5b54b36`；原始提交时间：2026-05-12T14:45:34+08:00。
- **阶段目标**：复现原项目两节点Docker Compose部署材料，区分配置设计与实际部署结果。
- **涉及文件与模块**：`Argus-backend/deploy/two-node/docker-compose.server-a.yml`、`Argus-backend/deploy/two-node/docker-compose.server-b.yml`、`Argus-backend/deploy/docker/elasticsearch/Dockerfile`、`Argus-backend/deploy/two-node/README.md`、`README.md`。
- **实际变化**：8个文件变更：节点A配置pgvector/PostgreSQL、Elasticsearch、MinIO和Elasticvue，节点B配置Ollama、模型初始化、后端和前端，另加环境变量示例及部署说明。Elasticsearch镜像安装8.15.3版IK插件，后端以Maven spring-boot:run运行、前端以Vite开发服务器运行，并配置健康检查和服务依赖。提交同时新增根README并删除旧模块重构设计文档。
- **问题与证据边界**：历史未记载真实部署异常或成功日志；静态可见节点B把后端目录挂到/workspace，却把前端工作目录设为/workspace/frontend，与同级Argus-frontend的仓库布局不一致，不能写成已完成可用部署。
- **参考阶段方案**：记录服务拆分、镜像依赖和环境变量配置方案，但保留路径不一致这一静态限制；此阶段没有修正该路径，第33阶段才调整挂载后的前后端工作目录。
- **代码依据**：Argus-backend/deploy/two-node/docker-compose.server-a.yml#services；Argus-backend/deploy/two-node/docker-compose.server-b.yml#services.backend、services.frontend、services.ollama-model-init；diff第608-786行。；Argus-backend/deploy/docker/elasticsearch/Dockerfile#FROM、RUN。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 20 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../deploy/docker/elasticsearch/Dockerfile         |   3 +
 Argus-backend/deploy/two-node/README.md            | 589 ++++++++++++++++++
 .../deploy/two-node/docker-compose.server-a.yml    |  72 +++
 .../deploy/two-node/docker-compose.server-b.yml    |  95 +++
 Argus-backend/deploy/two-node/server-a.env.example |  14 +
 Argus-backend/deploy/two-node/server-b.env.example |  29 +
 .../2026-05-06-argus-module-refactoring-design.md  | 672 ---------------------
 README.md                                          | 314 ++++++++++
 8 files changed, 1116 insertions(+), 672 deletions(-)
```

</details>

### 阶段 21：feat(qa): 支持流式知识问答及 SSE 实时推送

- **提交**：`df0a8af`；原始提交时间：2026-05-13T13:13:06+08:00。
- **阶段目标**：复现QA从整段响应扩展到SSE逐片段推送的前后端链路。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/qa/controller/QaController.java`、`Argus-backend/src/main/java/com/argus/rag/qa/service/QaChatService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/service/QaService.java`、`Argus-frontend/src/api/qa.ts`、`Argus-frontend/src/views/qa/QaView.vue`、`Argus-frontend/src/views/qa/components/QaMessage.vue`、`Argus-backend/pom.xml`。
- **实际变化**：新增POST /api/qa/stream-ask，使用5分钟SseEmitter发送token、非空citations及error事件，同时保留同步/ask并在服务层校验群组可读权限。QaChatService.askStream先检索证据，无证据返回错误Flux，有证据则改用纯文本系统提示和ChatClient.stream().content()；前端通过带Bearer的fetch读取SSE并累加文本，QaMessage显示生成中的正文。提交还添加spring-boot-starter-test，合计9文件、613行新增和190行删除。
- **问题与证据边界**：历史未记载运行异常，但注释声称客户端断开会自动取消后台流，实际onCompletion/onTimeout/onError只写日志，subscribe返回值也未保存，不能声称实现了显式取消。前端onError设置错误后，未收到citations时的流结束兜底又清空reasonCode/reasonMessage，这是diff可直接看到的状态覆盖问题。
- **参考阶段方案**：如实记录新增流式协议、证据不足错误流及前端增量显示，不把注释当作已实现的取消机制；同时保留错误显示可能被兜底覆盖的限制。
- **代码依据**：Argus-backend/src/main/java/com/argus/rag/qa/controller/QaController.java#streamAsk、SSE_TIMEOUT_MS；diff第125-187行。；Argus-backend/src/main/java/com/argus/rag/qa/service/QaChatService.java#askStream、StreamContext；diff第506-543行。；Argus-frontend/src/api/qa.ts#streamAskQuestion、dispatchQaSseEvent；Argus-frontend/src/views/qa/QaView.vue#handleAsk；diff第702-919行。

**本次验证结果**

- `mvnw.cmd clean compile`：通过，退出码 `0`，耗时 19 秒；原始日志索引 `21-backend.log`。

```text
[INFO] BUILD SUCCESS
```

> 【截图占位：此处插入阶段 21 backend 的真实成功输出截图，对应 21-backend.log；不得用其他阶段截图替代】

- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。
<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.gitignore                                         |   2 +
 Argus-backend/pom.xml                              |   7 +
 .../com/argus/rag/qa/controller/QaController.java  | 116 +++++-
 .../com/argus/rag/qa/service/QaChatService.java    | 392 ++++++++++++---------
 .../java/com/argus/rag/qa/service/QaService.java   |  27 +-
 Argus-frontend/src/api/qa.ts                       | 148 ++++++++
 Argus-frontend/src/views/qa/QaView.vue             |  72 +++-
 .../src/views/qa/components/QaMessage.vue          |  30 +-
 .../src/views/qa/components/QaSidebar.vue          |   9 +-
 9 files changed, 613 insertions(+), 190 deletions(-)
```

</details>

### 阶段 22：test(qa): 添加 QaController SSE 流式接口单元测试

- **提交**：`a7e0791`；原始提交时间：2026-05-13T13:15:52+08:00。
- **阶段目标**：复现QA SSE控制器测试的新增内容，并把测试定义与测试结果分开。
- **涉及文件与模块**：`Argus-backend/src/test/java/com/argus/rag/qa/controller/QaControllerTest.java`。
- **实际变化**：仅新增382行QaControllerTest，使用@WebMvcTest、MockMvc和模拟QaService/CitationAssembler覆盖token及citations、无引用、无证据、权限失败、模型流中途或立即失败、参数校验及空Flux场景。测试以asyncDispatch读取SSE响应，并断言事件内容和HTTP状态，没有更改生产代码。
- **问题与证据边界**：历史未记载测试执行异常；静态可确认shouldStreamAllTokensCorrectly构造21个片段却断言22个事件，还把分开发送的按、钮、。断言成连续data:按钮。，因此不能把本阶段写成全部测试通过。
- **参考阶段方案**：将该阶段写成新增测试覆盖，并保留断言与样本不一致的事实；第27阶段diff明确把数量改为21并拆开字符断言，同时补UTF-8读取和测试上下文配置。
- **代码依据**：Argus-backend/src/test/java/com/argus/rag/qa/controller/QaControllerTest.java#HappyPath.shouldStreamAllTokensCorrectly；本阶段diff第172-200行。；Argus-backend/src/test/java/com/argus/rag/qa/controller/QaControllerTest.java#NoEvidence、PermissionDenied、LlmError、Validation、EdgeCases。；history/27-fedf19d.diff:1418-1437，后续实际修正同一测试的证据。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 22 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../argus/rag/qa/controller/QaControllerTest.java  | 382 +++++++++++++++++++++
 1 file changed, 382 insertions(+)
```

</details>

### 阶段 23：feat(metrics): 添加后台使用统计指标接口及前端菜单入口

- **提交**：`5264bba`；原始提交时间：2026-05-18T14:42:12+08:00。
- **阶段目标**：复现LLM用量采集、费用估算和管理员统计页面，明确统计口径及同批检索、预览改动。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/metrics/controller/AdminMetricsController.java`、`Argus-backend/src/main/java/com/argus/rag/metrics/collector/LlmUsageCollectorImpl.java`、`Argus-backend/src/main/java/com/argus/rag/metrics/cost/LlmCostCalculatorImpl.java`、`Argus-backend/src/main/resources/mappers/metrics/LlmUsageRecordMapper.xml`、`Argus-backend/src/main/java/com/argus/rag/qa/service/QaChatService.java`、`Argus-backend/src/main/java/com/argus/rag/assistant/service/AssistantService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/rag/HybridChunkRetrievalService.java`、`Argus-frontend/src/views/admin/MetricsView.vue`、`sql/schema.sql`。
- **实际变化**：37个文件变更，新增llm_usage_records、带@Async的采集器、按每千token静态费率计算的成本服务及管理员概览/平台/用户/群组/趋势/排行接口，前端加入指标页面和菜单并扩充用户管理页。QA改取ChatResponse中的usage，流式无usage时以输出字符数除4估算，助手输入和输出均按字符数除4估算并标记isEstimated。另将RRF_K从60改为0、score按1-exp(-x)归一化、证据评估改读score并调整阈值，同时Markdown预览改为读取原始源码供前端渲染。
- **问题与证据边界**：历史未记载运行异常；这些是应用层统计而非供应商账单，模型名在业务服务中固定为qwen-plus、费率为代码常量，不能宣称全量精确成本。QA统计主要在同步正常返回和流式完成回调写入，异常流仅日志处理，不能把成功率描述为覆盖全部失败请求；原证据评估读取hybridScore而展示score的口径也在此阶段变更。
- **参考阶段方案**：记录实际usage优先、缺失时估算、采集失败只记日志不阻断业务，以及管理员权限检查和SQL聚合口径；RPM/TPM按记录首末时间跨度计算，不是实时限流指标。检索归一化和Markdown预览应列为同批实际改动，不把此提交缩写为仅加菜单；未运行应用或核验账单。
- **代码依据**：Argus-backend/src/main/java/com/argus/rag/metrics/controller/AdminMetricsController.java#getOverview、getTrend、getUserRank、getGroupRank、requireSystemAdmin；Argus-backend/src/main/java/com/argus/rag/metrics/collector/LlmUsageCollectorImpl.java#record。；Argus-backend/src/main/java/com/argus/rag/qa/service/QaChatService.java#askWithUsage、extractUsageInfo、askStream、UsageInfo；Argus-backend/src/main/java/com/argus/rag/assistant/service/AssistantService.java#estimateTokens、recordUsage。；Argus-backend/src/main/java/com/argus/rag/metrics/cost/LlmCostCalculatorImpl.java#calculate、modelPricing；Argus-backend/src/main/resources/mappers/metrics/LlmUsageRecordMapper.xml#selectUsageStats、selectDailyTrend。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 23 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.gitignore                                         |    1 +
 .../controller/AssistantChatController.java        |    3 +
 .../AssistantConversationController.java           |    2 +
 .../controller/AssistantSessionController.java     |    2 +
 .../rag/assistant/service/AssistantService.java    |  238 +++-
 .../document/service/DocumentPreviewService.java   |   70 +-
 .../java/com/argus/rag/metrics/LlmEndpoint.java    |   13 +
 .../main/java/com/argus/rag/metrics/LlmModule.java |   11 +
 .../rag/metrics/collector/LlmUsageCollector.java   |   15 +
 .../metrics/collector/LlmUsageCollectorImpl.java   |   56 +
 .../metrics/controller/AdminMetricsController.java |   88 ++
 .../argus/rag/metrics/cost/LlmCostCalculator.java  |   19 +
 .../rag/metrics/cost/LlmCostCalculatorImpl.java    |   53 +
 .../rag/metrics/mapper/LlmUsageRecordMapper.java   |   43 +
 .../rag/metrics/model/dto/LlmUsageRecordDTO.java   |   30 +
 .../rag/metrics/model/dto/MetricsTrendRequest.java |   10 +
 .../metrics/model/entity/LlmUsageRecordEntity.java |   34 +
 .../argus/rag/metrics/model/enums/StatsPeriod.java |   27 +
 .../rag/metrics/model/vo/MetricsOverviewVO.java    |   25 +
 .../rag/metrics/model/vo/UsageRankItemVO.java      |   13 +
 .../argus/rag/metrics/model/vo/UsageStatsVO.java   |   24 +
 .../metrics/service/LlmUsageStatisticsService.java |   68 ++
 .../com/argus/rag/qa/controller/QaController.java  |    2 +
 .../rag/qa/rag/HybridChunkRetrievalService.java    |  106 +-
 .../com/argus/rag/qa/service/QaChatService.java    |  173 ++-
 .../java/com/argus/rag/qa/service/QaService.java   |   75 +-
 .../mappers/metrics/LlmUsageRecordMapper.xml       |  109 ++
 Argus-frontend/_test_marked.mjs                    |   28 +
 Argus-frontend/src/api/metrics.ts                  |  155 +++
 .../src/components/DocumentPreviewModal.vue        |  262 ++++-
 Argus-frontend/src/layouts/DefaultLayout.vue       |    8 +
 Argus-frontend/src/router/index.ts                 |    6 +
 Argus-frontend/src/views/admin/MetricsView.vue     |  876 +++++++++++++++
 .../src/views/admin/UserManagementView.vue         | 1154 +++++++++++++++++++-
 .../src/views/assistant/AssistantView.vue          |    2 +-
 Argus-frontend/src/views/qa/QaView.vue             |    2 +-
 sql/schema.sql                                     |   60 +
 37 files changed, 3636 insertions(+), 227 deletions(-)
```

</details>

### 阶段 24：docs(readme): 优化重构项目README文档内容和结构

- **提交**：`e2ef1be`；原始提交时间：2026-05-18T15:02:11+08:00。
- **阶段目标**：复现原项目中文README的结构与展示重写，不增加不存在的功能开发叙事。
- **涉及文件与模块**：`README.md`。
- **实际变化**：仅修改根README，新增436行、删除218行，重新组织项目亮点、Mermaid系统架构、功能模块、技术栈、快速开始、API概览和版本演进。该提交没有生产代码、数据库或测试变更。
- **问题与证据边界**：历史未记载异常；README中的功能宣传和启动示例不是运行或性能验证证据，不能把文档整理写成重新实现RAG或Agent。
- **参考阶段方案**：将该阶段记作已有能力的文档重整，保留来源并避免照搬企业级、零等待等宣传语为已验证结论；不执行文档中的启动命令。
- **代码依据**：README.md#系统架构、功能模块、快速开始、API概览、版本演进；diff仅包含README.md。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 24 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
README.md | 654 +++++++++++++++++++++++++++++++++++++++++---------------------
 1 file changed, 436 insertions(+), 218 deletions(-)
```

</details>

### 阶段 25：增加实体类注释

- **提交**：`dcc58a7`；原始提交时间：2026-05-20T09:39:07+08:00。
- **阶段目标**：复现统计模型的中文注释补充，准确区分注释修改与行为修改。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/metrics/model/dto/LlmUsageRecordDTO.java`、`Argus-backend/src/main/java/com/argus/rag/metrics/model/dto/MetricsTrendRequest.java`、`Argus-backend/src/main/java/com/argus/rag/metrics/model/entity/LlmUsageRecordEntity.java`、`Argus-backend/src/main/java/com/argus/rag/metrics/model/enums/StatsPeriod.java`、`Argus-backend/src/main/java/com/argus/rag/metrics/model/vo/MetricsOverviewVO.java`、`Argus-backend/src/main/java/com/argus/rag/metrics/model/vo/UsageRankItemVO.java`、`Argus-backend/src/main/java/com/argus/rag/metrics/model/vo/UsageStatsVO.java`。
- **实际变化**：7个统计模型文件增加类级与字段级说明，覆盖token、估算标识、费用、调用状态、时间范围、趋势及排行含义，并把若干行尾注释改为独立JavaDoc。166行新增、14行删除主要是注释和排版，字段类型、默认值及枚举计算逻辑未在该diff中改变。
- **问题与证据边界**：历史未记载异常；提交标题为增加实体类注释，但实际还涉及DTO、VO和枚举，不能写成新增统计功能或算法修复。
- **参考阶段方案**：按原diff描述模型语义补充与可读性整理，不宣称改进了统计精度或修复运行问题；未运行构建。
- **代码依据**：Argus-backend/src/main/java/com/argus/rag/metrics/model/entity/LlmUsageRecordEntity.java#isEstimated、costCurrency；Argus-backend/src/main/java/com/argus/rag/metrics/model/enums/StatsPeriod.java#TODAY、LAST_7_DAYS、LAST_14_DAYS、LAST_30_DAYS。；Argus-backend/src/main/java/com/argus/rag/metrics/model/vo/MetricsOverviewVO.java#DailyStats；Argus-backend/src/main/java/com/argus/rag/metrics/model/vo/UsageStatsVO.java。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 25 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../rag/metrics/model/dto/LlmUsageRecordDTO.java   | 32 +++++++++++++++++
 .../rag/metrics/model/dto/MetricsTrendRequest.java | 10 +++++-
 .../metrics/model/entity/LlmUsageRecordEntity.java | 40 ++++++++++++++++++++--
 .../argus/rag/metrics/model/enums/StatsPeriod.java | 13 +++++++
 .../rag/metrics/model/vo/MetricsOverviewVO.java    | 28 +++++++++++++--
 .../rag/metrics/model/vo/UsageRankItemVO.java      | 18 ++++++++--
 .../argus/rag/metrics/model/vo/UsageStatsVO.java   | 39 +++++++++++++++++----
 7 files changed, 166 insertions(+), 14 deletions(-)
```

</details>

### 阶段 26：docs: add English README

- **提交**：`8ec002c`；原始提交时间：2026-06-04T21:20:03+08:00。
- **阶段目标**：复现英文README与中英文文档入口的加入。
- **涉及文件与模块**：`README.md`、`README_EN.md`。
- **实际变化**：新增542行README_EN.md，介绍RAG链路、Agent、架构、技术栈、启动方式和API。中文README增加5行语言切换入口，英文README也链接回中文文档；仅有2个文档文件变更。
- **问题与证据边界**：历史未记载异常；英文文档加入不等于应用界面国际化，也不能作为其中部署说明已执行成功的证明。
- **参考阶段方案**：将阶段目标限定为双语项目说明和互链，避免声称完成前端语言切换或新增运行功能；未执行启动示例。
- **代码依据**：README.md#README_EN.md链接；README_EN.md#Core Highlights、System Architecture、Quick Start、API Overview。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 26 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
README.md    |   5 +
 README_EN.md | 542 +++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
 2 files changed, 547 insertions(+)
```

</details>

### 阶段 27：feat: persist QA records

- **提交**：`fedf19d`；原始提交时间：2026-06-05T10:49:07+08:00。
- **阶段目标**：复现QA记录和引用快照持久化的首次实现，明确同步、流式、权限和失败路径边界。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/qa/service/QaRecordPersistenceService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/service/QaRecordQueryService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/service/QaService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/controller/QaRecordController.java`、`Argus-backend/src/main/java/com/argus/rag/qa/controller/QaController.java`、`Argus-backend/src/main/resources/mappers/qa/QaRecordMapper.xml`、`Argus-frontend/src/api/qa.ts`、`Argus-backend/src/test/java/com/argus/rag/qa/controller/QaControllerTest.java`、`sql/schema.sql`。
- **实际变化**：25个文件变更，新增qa_records和qa_record_citations、实体/Mapper/查询与保存服务，保存问题、回答或拒答原因、证据等级、用量和最长1200字符的归一化引用快照；同步响应增加recordId，流式累积正文后保存并在正常完成时发送record事件。历史列表/详情通过SQL可见性规则控制：管理员全部可见，当前群组成员可看自己的记录，群主可看该组记录，分页默认20且最大100。前端此时仅加入历史API及消息recordId承接，尚未把服务端历史加载到侧栏，同时新增持久化测试并修正SSE测试数量、字符断言、UTF-8读取和测试上下文。
- **问题与证据边界**：历史未记载数据库执行或测试运行异常；代码显示saveCompleted捕获异常返回null，主表和引用逐条插入却无事务注解，不能保证失败时无部分记录。流式error路径会保存拒答或失败，但控制器仅正常完成时发送record，异常前的部分正文也未写入失败响应；同步初始化/检索直接抛异常不经过保存，不能宣称所有失败和取消均可靠落库。
- **参考阶段方案**：按实际方案记录非阻断持久化、引用快照、SQL权限过滤及正常完成后的recordId回传，并明确缺少事务完整性和全失败路径覆盖；无证据流按业务拒答保存，其他流异常按SYSTEM_ERROR保存。此阶段只接通API与记录标识，历史侧栏加载属于第29阶段；
- **代码依据**：Argus-backend/src/main/java/com/argus/rag/qa/service/QaRecordPersistenceService.java#saveCompleted、toCitationEntity、snippet、SaveCommand；diff第634-760行。；Argus-backend/src/main/java/com/argus/rag/qa/service/QaService.java#saveQaRecord、askStream；Argus-backend/src/main/java/com/argus/rag/qa/controller/QaController.java#streamAsk；diff第4-18、1009-1116行。；Argus-backend/src/main/resources/mappers/qa/QaRecordMapper.xml#visiblePredicate、selectVisibleRecords、selectVisibleRecord；sql/schema.sql#qa_records、qa_record_citations。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 27 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../com/argus/rag/qa/controller/QaController.java  |   4 +
 .../rag/qa/controller/QaRecordController.java      |  41 ++
 .../rag/qa/mapper/QaRecordCitationMapper.java      |  16 +
 .../com/argus/rag/qa/mapper/QaRecordMapper.java    |  38 ++
 .../rag/qa/model/dto/QaHistoryQueryRequest.java    |  16 +
 .../qa/model/entity/QaRecordCitationEntity.java    |  48 ++
 .../argus/rag/qa/model/entity/QaRecordEntity.java  |  56 ++
 .../argus/rag/qa/model/vo/AskQuestionResponse.java |  13 +-
 .../argus/rag/qa/model/vo/QaRecordDetailVO.java    | 108 ++++
 .../argus/rag/qa/model/vo/QaRecordListItemVO.java  |  33 ++
 .../com/argus/rag/qa/model/vo/QaRecordPageVO.java  |  16 +
 .../com/argus/rag/qa/service/QaChatService.java    |  46 +-
 .../rag/qa/service/QaRecordPersistenceService.java | 184 ++++++
 .../argus/rag/qa/service/QaRecordQueryService.java | 161 ++++++
 .../java/com/argus/rag/qa/service/QaService.java   | 106 +++-
 .../mappers/qa/QaRecordCitationMapper.xml          |  47 ++
 .../main/resources/mappers/qa/QaRecordMapper.xml   | 118 ++++
 .../argus/rag/qa/controller/QaControllerTest.java  |  75 ++-
 .../qa/service/QaRecordPersistenceServiceTest.java | 116 ++++
 .../com/argus/rag/qa/service/QaServiceTest.java    | 153 +++++
 Argus-frontend/src/api/qa.ts                       |  94 ++-
 Argus-frontend/src/views/qa/QaView.vue             |   8 +
 .../src/views/qa/composables/useQaSessions.ts      |   1 +
 docs/V5.0-QA问答记录持久化设计.md                  | 637 +++++++++++++++++++++
 sql/schema.sql                                     | 103 ++++
 25 files changed, 2212 insertions(+), 26 deletions(-)
```

</details>

### 阶段 28：Merge pull request #2 from DevYangJC/codex/qa-record-persistence

- **提交**：`dcd242b`；原始提交时间：2026-06-05T12:04:07+08:00。合并提交，父节点数 2。
- **阶段目标**：记录QA持久化分支的合并节点，不把合并重复记为功能开发。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/qa/service/QaRecordPersistenceService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/controller/QaRecordController.java`、`Argus-backend/src/main/java/com/argus/rag/qa/controller/QaController.java`、`sql/schema.sql`。
- **实际变化**：这是PR #2的合并提交，父提交为8ec002c和fedf19d；相对第一父提交的25文件diff与第27阶段相同。合并后的树与fedf19d完全一致，因此相对已经复现的第27阶段没有新增源码或独立功能。
- **问题与证据边界**：历史未记载合并冲突或异常；manifest的stat是相对第一父提交的统计，若按阶段顺序重复施加会把同一持久化功能计算两次。
- **参考阶段方案**：只记录分支成果进入主线的集成关系，引用第27阶段功能与限制，不虚构冲突解决、重新开发或测试通过；files列的是合并纳入路径而非独立新增实现。
- **代码依据**：只读Git对象核对：dcd242b与fedf19d的tree均为719a3a7bd802c0f8b738b6d5c17f2d3fa0cad16b；dcd242b父提交为8ec002c、fedf19d。；Argus-backend/src/main/java/com/argus/rag/qa/controller/QaController.java#streamAsk的record事件和Argus-backend/src/main/java/com/argus/rag/qa/controller/QaRecordController.java#listRecords、getRecord，均为第27阶段实现的合入。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 28 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../com/argus/rag/qa/controller/QaController.java  |   4 +
 .../rag/qa/controller/QaRecordController.java      |  41 ++
 .../rag/qa/mapper/QaRecordCitationMapper.java      |  16 +
 .../com/argus/rag/qa/mapper/QaRecordMapper.java    |  38 ++
 .../rag/qa/model/dto/QaHistoryQueryRequest.java    |  16 +
 .../qa/model/entity/QaRecordCitationEntity.java    |  48 ++
 .../argus/rag/qa/model/entity/QaRecordEntity.java  |  56 ++
 .../argus/rag/qa/model/vo/AskQuestionResponse.java |  13 +-
 .../argus/rag/qa/model/vo/QaRecordDetailVO.java    | 108 ++++
 .../argus/rag/qa/model/vo/QaRecordListItemVO.java  |  33 ++
 .../com/argus/rag/qa/model/vo/QaRecordPageVO.java  |  16 +
 .../com/argus/rag/qa/service/QaChatService.java    |  46 +-
 .../rag/qa/service/QaRecordPersistenceService.java | 184 ++++++
 .../argus/rag/qa/service/QaRecordQueryService.java | 161 ++++++
 .../java/com/argus/rag/qa/service/QaService.java   | 106 +++-
 .../mappers/qa/QaRecordCitationMapper.xml          |  47 ++
 .../main/resources/mappers/qa/QaRecordMapper.xml   | 118 ++++
 .../argus/rag/qa/controller/QaControllerTest.java  |  75 ++-
 .../qa/service/QaRecordPersistenceServiceTest.java | 116 ++++
 .../com/argus/rag/qa/service/QaServiceTest.java    | 153 +++++
 Argus-frontend/src/api/qa.ts                       |  94 ++-
 Argus-frontend/src/views/qa/QaView.vue             |   8 +
 .../src/views/qa/composables/useQaSessions.ts      |   1 +
 docs/V5.0-QA问答记录持久化设计.md                  | 637 +++++++++++++++++++++
 sql/schema.sql                                     | 103 ++++
 25 files changed, 2212 insertions(+), 26 deletions(-)
```

</details>

### 阶段 29：feat: QA record persistence with evidence overview + UI enhancements

- **提交**：`48b39ee`；原始提交时间：2026-06-07T22:18:24+08:00。
- **阶段目标**：复现基于既有QA持久化的证据覆盖概览、历史侧栏加载和预览界面增强。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/qa/support/EvidenceOverviewAssembler.java`、`Argus-backend/src/main/java/com/argus/rag/qa/rag/HybridChunkRetrievalService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/controller/QaController.java`、`Argus-backend/src/main/java/com/argus/rag/qa/service/QaRecordQueryService.java`、`Argus-frontend/src/views/qa/QaView.vue`、`Argus-frontend/src/views/qa/components/EvidenceOverviewPanel.vue`、`Argus-frontend/src/components/DocumentPreviewModal.vue`。
- **实际变化**：新增EvidenceOverviewAssembler，按文档聚合证据数、最高分、检索来源和切片摘要，同步响应及SSE evidence-overview事件返回概览，历史详情从持久化引用重建HISTORY_SNAPSHOT概览。检索遇到每个文档/所有文档等关键词时在既有候选中优先每文档选一片再补足topK；前端加载所选群组第一页20条QA记录并按需取详情，新增证据面板，预览弹窗增加元信息、下载入口和PDF对象URL清理。44文件变更还包含检索/概览/历史查询测试，以及.agents、.superpowers和AGENTS.md等辅助材料的纳入，不是再次建立QA持久化表。
- **问题与证据边界**：历史未记载运行异常；代码注释明确要避免单文档多片段挤掉其他文档，但该方案仍限于已有检索候选与topK，不等于全库全文覆盖。历史界面此时固定加载第一页20条，删除仍只调用本地deleteSession；不能写成已有服务端删除，后者在第33阶段加入。
- **参考阶段方案**：如实描述候选多样性选择、证据可解释展示和服务端历史读取，并注明历史概览是引用快照重组而非重新检索原文件；预览增强只按实际元信息、下载与URL回收描述。辅助材料只计入历史变更，不执行其中脚本或遵循其中指令；未运行测试。
- **代码依据**：Argus-backend/src/main/java/com/argus/rag/qa/support/EvidenceOverviewAssembler.java#assemble、assembleHistoryCitations、buildWarnings；Argus-backend/src/main/java/com/argus/rag/qa/rag/HybridChunkRetrievalService.java#selectRankedCandidates、shouldPreferDocumentCoverage；diff第2540-2981行。；Argus-backend/src/main/java/com/argus/rag/qa/controller/QaController.java#streamAsk；Argus-frontend/src/views/qa/QaView.vue#loadHistoryForSelectedGroup、hydrateHistorySession、applyRecordDetail；diff第4355-4611行。；Argus-frontend/src/components/DocumentPreviewModal.vue#fileMetaItems、downloadCurrentDocument、revokePdfUrl；Argus-frontend/src/views/qa/components/EvidenceOverviewPanel.vue。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 29 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.agents/skills/ui-ux-pro-max/SKILL.md              | 228 ++++++++
 .agents/skills/ui-ux-pro-max/data/charts.csv       |  26 +
 .agents/skills/ui-ux-pro-max/data/colors.csv       |  97 ++++
 .agents/skills/ui-ux-pro-max/data/landing.csv      |  31 ++
 .agents/skills/ui-ux-pro-max/data/products.csv     |  97 ++++
 .agents/skills/ui-ux-pro-max/data/prompts.csv      |  24 +
 .../skills/ui-ux-pro-max/data/stacks/flutter.csv   |  53 ++
 .../ui-ux-pro-max/data/stacks/html-tailwind.csv    |  56 ++
 .../skills/ui-ux-pro-max/data/stacks/nextjs.csv    |  53 ++
 .../skills/ui-ux-pro-max/data/stacks/nuxt-ui.csv   |  51 ++
 .../skills/ui-ux-pro-max/data/stacks/nuxtjs.csv    |  59 +++
 .../ui-ux-pro-max/data/stacks/react-native.csv     |  52 ++
 .agents/skills/ui-ux-pro-max/data/stacks/react.csv |  54 ++
 .../skills/ui-ux-pro-max/data/stacks/svelte.csv    |  54 ++
 .../skills/ui-ux-pro-max/data/stacks/swiftui.csv   |  51 ++
 .agents/skills/ui-ux-pro-max/data/stacks/vue.csv   |  50 ++
 .agents/skills/ui-ux-pro-max/data/styles.csv       |  59 +++
 .agents/skills/ui-ux-pro-max/data/typography.csv   |  58 +++
 .../skills/ui-ux-pro-max/data/ux-guidelines.csv    | 100 ++++
 .../scripts/__pycache__/core.cpython-310.pyc       | Bin 0 -> 8334 bytes
 .agents/skills/ui-ux-pro-max/scripts/core.py       | 238 +++++++++
 .agents/skills/ui-ux-pro-max/scripts/search.py     |  61 +++
 .../content/document-preview-directions.html       | 563 ++++++++++++++++++++
 .../doc-preview-mockup-20260607/state/server-info  |   1 +
 .../doc-preview-mockup-20260607/state/server.pid   |   1 +
 AGENTS.md                                          |  95 ++++
 .../com/argus/rag/qa/controller/QaController.java  |  13 +-
 .../argus/rag/qa/model/vo/AskQuestionResponse.java |  57 +-
 .../argus/rag/qa/model/vo/QaRecordDetailVO.java    |   3 +
 .../rag/qa/rag/HybridChunkRetrievalService.java    |  70 ++-
 .../com/argus/rag/qa/service/QaChatService.java    |  13 +-
 .../argus/rag/qa/service/QaRecordQueryService.java |   7 +-
 .../rag/qa/support/EvidenceOverviewAssembler.java  | 220 ++++++++
 .../argus/rag/qa/controller/QaControllerTest.java  |  28 +
 .../qa/rag/HybridChunkRetrievalServiceTest.java    | 121 +++++
 .../rag/qa/service/QaRecordQueryServiceTest.java   | 102 ++++
 .../qa/support/EvidenceOverviewAssemblerTest.java  | 112 ++++
 Argus-frontend/src/api/qa.ts                       |  40 ++
 .../src/components/DocumentPreviewModal.vue        | 579 ++++++++++++++-------
 Argus-frontend/src/views/qa/QaView.vue             | 170 +++++-
 .../views/qa/components/EvidenceOverviewPanel.vue  | 240 +++++++++
 .../src/views/qa/components/QaMessage.vue          |   6 +
 .../src/views/qa/components/QaSidebar.vue          |  33 +-
 .../src/views/qa/composables/useQaSessions.ts      |   5 +-
 44 files changed, 3830 insertions(+), 201 deletions(-)
```

</details>

### 阶段 30：Merge pull request #3 from DevYangJC/codex/qa-record-persistence

- **提交**：`61e5b0d`；原始提交时间：2026-06-07T22:28:24+08:00。合并提交，父节点数 2。
- **阶段目标**：记录证据概览与历史UI增强分支通过PR合入主线的事实。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/qa/support/EvidenceOverviewAssembler.java`、`Argus-backend/src/main/java/com/argus/rag/qa/controller/QaController.java`、`Argus-frontend/src/views/qa/QaView.vue`、`Argus-frontend/src/views/qa/components/EvidenceOverviewPanel.vue`。
- **实际变化**：PR #3合并提交61e5b0d的父提交是dcd242b和48b39ee，相对第一父提交列出44文件、3830行新增和201行删除。其结果树与第29阶段48b39ee完全一致，实际只是合入同一批证据概览、历史UI和辅助材料，没有独立代码增量。
- **问题与证据边界**：历史未记载合并冲突或异常；大份合并diff不代表再次实现QA持久化或证据概览。
- **参考阶段方案**：按集成节点记录，功能与限制沿用第29阶段；不要重复开发、重复计数，也不要杜撰冲突修复或验证通过。files表示相对第一父提交纳入的主要路径。
- **代码依据**：只读Git对象核对：61e5b0d与48b39ee的tree均为dd55f6c46a65a2538f077dd3b4527772b68590e4；父提交为dcd242b、48b39ee。；Argus-backend/src/main/java/com/argus/rag/qa/controller/QaController.java#streamAsk的evidence-overview事件；本阶段diff第2377-2423行与第29阶段对应内容一致。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 30 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.agents/skills/ui-ux-pro-max/SKILL.md              | 228 ++++++++
 .agents/skills/ui-ux-pro-max/data/charts.csv       |  26 +
 .agents/skills/ui-ux-pro-max/data/colors.csv       |  97 ++++
 .agents/skills/ui-ux-pro-max/data/landing.csv      |  31 ++
 .agents/skills/ui-ux-pro-max/data/products.csv     |  97 ++++
 .agents/skills/ui-ux-pro-max/data/prompts.csv      |  24 +
 .../skills/ui-ux-pro-max/data/stacks/flutter.csv   |  53 ++
 .../ui-ux-pro-max/data/stacks/html-tailwind.csv    |  56 ++
 .../skills/ui-ux-pro-max/data/stacks/nextjs.csv    |  53 ++
 .../skills/ui-ux-pro-max/data/stacks/nuxt-ui.csv   |  51 ++
 .../skills/ui-ux-pro-max/data/stacks/nuxtjs.csv    |  59 +++
 .../ui-ux-pro-max/data/stacks/react-native.csv     |  52 ++
 .agents/skills/ui-ux-pro-max/data/stacks/react.csv |  54 ++
 .../skills/ui-ux-pro-max/data/stacks/svelte.csv    |  54 ++
 .../skills/ui-ux-pro-max/data/stacks/swiftui.csv   |  51 ++
 .agents/skills/ui-ux-pro-max/data/stacks/vue.csv   |  50 ++
 .agents/skills/ui-ux-pro-max/data/styles.csv       |  59 +++
 .agents/skills/ui-ux-pro-max/data/typography.csv   |  58 +++
 .../skills/ui-ux-pro-max/data/ux-guidelines.csv    | 100 ++++
 .../scripts/__pycache__/core.cpython-310.pyc       | Bin 0 -> 8334 bytes
 .agents/skills/ui-ux-pro-max/scripts/core.py       | 238 +++++++++
 .agents/skills/ui-ux-pro-max/scripts/search.py     |  61 +++
 .../content/document-preview-directions.html       | 563 ++++++++++++++++++++
 .../doc-preview-mockup-20260607/state/server-info  |   1 +
 .../doc-preview-mockup-20260607/state/server.pid   |   1 +
 AGENTS.md                                          |  95 ++++
 .../com/argus/rag/qa/controller/QaController.java  |  13 +-
 .../argus/rag/qa/model/vo/AskQuestionResponse.java |  57 +-
 .../argus/rag/qa/model/vo/QaRecordDetailVO.java    |   3 +
 .../rag/qa/rag/HybridChunkRetrievalService.java    |  70 ++-
 .../com/argus/rag/qa/service/QaChatService.java    |  13 +-
 .../argus/rag/qa/service/QaRecordQueryService.java |   7 +-
 .../rag/qa/support/EvidenceOverviewAssembler.java  | 220 ++++++++
 .../argus/rag/qa/controller/QaControllerTest.java  |  28 +
 .../qa/rag/HybridChunkRetrievalServiceTest.java    | 121 +++++
 .../rag/qa/service/QaRecordQueryServiceTest.java   | 102 ++++
 .../qa/support/EvidenceOverviewAssemblerTest.java  | 112 ++++
 Argus-frontend/src/api/qa.ts                       |  40 ++
 .../src/components/DocumentPreviewModal.vue        | 579 ++++++++++++++-------
 Argus-frontend/src/views/qa/QaView.vue             | 170 +++++-
 .../views/qa/components/EvidenceOverviewPanel.vue  | 240 +++++++++
 .../src/views/qa/components/QaMessage.vue          |   6 +
 .../src/views/qa/components/QaSidebar.vue          |  33 +-
 .../src/views/qa/composables/useQaSessions.ts      |   5 +-
 44 files changed, 3830 insertions(+), 201 deletions(-)
```

</details>

### 阶段 31：Merge codex/qa-record-persistence into master

- **提交**：`826edba`；原始提交时间：2026-06-07T22:28:35+08:00。合并提交，父节点数 2。
- **阶段目标**：识别同一QA分支的另一条合并记录，避免重复叙述功能和冲突处理。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/qa/support/EvidenceOverviewAssembler.java`、`Argus-backend/src/main/java/com/argus/rag/qa/controller/QaController.java`、`Argus-frontend/src/views/qa/QaView.vue`。
- **实际变化**：826edba同样以dcd242b和48b39ee为父提交，与第30阶段61e5b0d不仅结果树相同，父提交顺序也相同。其相对第一父提交的44文件diff再次展示第29阶段内容，不能解释为在第30阶段之后继续增加功能。
- **问题与证据边界**：历史未记载为何形成这两个平行合并节点，也没有冲突修复证据；阶段编号并不意味着每个提交都以前一编号提交为父节点。
- **参考阶段方案**：仅记录另一个等价合并节点及其拓扑关系，第29阶段的功能不再展开为新的开发任务；不推测协作操作原因，不声称运行验证。files仅列合入路径。
- **代码依据**：只读Git对象核对：826edba和61e5b0d的父提交均为dcd242b、48b39ee，tree均为dd55f6c46a65a2538f077dd3b4527772b68590e4，与48b39ee相同。；Argus-backend/src/main/java/com/argus/rag/qa/controller/QaController.java#streamAsk；本阶段diff第2377-2423行仍为同一evidence-overview增量。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 31 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.agents/skills/ui-ux-pro-max/SKILL.md              | 228 ++++++++
 .agents/skills/ui-ux-pro-max/data/charts.csv       |  26 +
 .agents/skills/ui-ux-pro-max/data/colors.csv       |  97 ++++
 .agents/skills/ui-ux-pro-max/data/landing.csv      |  31 ++
 .agents/skills/ui-ux-pro-max/data/products.csv     |  97 ++++
 .agents/skills/ui-ux-pro-max/data/prompts.csv      |  24 +
 .../skills/ui-ux-pro-max/data/stacks/flutter.csv   |  53 ++
 .../ui-ux-pro-max/data/stacks/html-tailwind.csv    |  56 ++
 .../skills/ui-ux-pro-max/data/stacks/nextjs.csv    |  53 ++
 .../skills/ui-ux-pro-max/data/stacks/nuxt-ui.csv   |  51 ++
 .../skills/ui-ux-pro-max/data/stacks/nuxtjs.csv    |  59 +++
 .../ui-ux-pro-max/data/stacks/react-native.csv     |  52 ++
 .agents/skills/ui-ux-pro-max/data/stacks/react.csv |  54 ++
 .../skills/ui-ux-pro-max/data/stacks/svelte.csv    |  54 ++
 .../skills/ui-ux-pro-max/data/stacks/swiftui.csv   |  51 ++
 .agents/skills/ui-ux-pro-max/data/stacks/vue.csv   |  50 ++
 .agents/skills/ui-ux-pro-max/data/styles.csv       |  59 +++
 .agents/skills/ui-ux-pro-max/data/typography.csv   |  58 +++
 .../skills/ui-ux-pro-max/data/ux-guidelines.csv    | 100 ++++
 .../scripts/__pycache__/core.cpython-310.pyc       | Bin 0 -> 8334 bytes
 .agents/skills/ui-ux-pro-max/scripts/core.py       | 238 +++++++++
 .agents/skills/ui-ux-pro-max/scripts/search.py     |  61 +++
 .../content/document-preview-directions.html       | 563 ++++++++++++++++++++
 .../doc-preview-mockup-20260607/state/server-info  |   1 +
 .../doc-preview-mockup-20260607/state/server.pid   |   1 +
 AGENTS.md                                          |  95 ++++
 .../com/argus/rag/qa/controller/QaController.java  |  13 +-
 .../argus/rag/qa/model/vo/AskQuestionResponse.java |  57 +-
 .../argus/rag/qa/model/vo/QaRecordDetailVO.java    |   3 +
 .../rag/qa/rag/HybridChunkRetrievalService.java    |  70 ++-
 .../com/argus/rag/qa/service/QaChatService.java    |  13 +-
 .../argus/rag/qa/service/QaRecordQueryService.java |   7 +-
 .../rag/qa/support/EvidenceOverviewAssembler.java  | 220 ++++++++
 .../argus/rag/qa/controller/QaControllerTest.java  |  28 +
 .../qa/rag/HybridChunkRetrievalServiceTest.java    | 121 +++++
 .../rag/qa/service/QaRecordQueryServiceTest.java   | 102 ++++
 .../qa/support/EvidenceOverviewAssemblerTest.java  | 112 ++++
 Argus-frontend/src/api/qa.ts                       |  40 ++
 .../src/components/DocumentPreviewModal.vue        | 579 ++++++++++++++-------
 Argus-frontend/src/views/qa/QaView.vue             | 170 +++++-
 .../views/qa/components/EvidenceOverviewPanel.vue  | 240 +++++++++
 .../src/views/qa/components/QaMessage.vue          |   6 +
 .../src/views/qa/components/QaSidebar.vue          |  33 +-
 .../src/views/qa/composables/useQaSessions.ts      |   5 +-
 44 files changed, 3830 insertions(+), 201 deletions(-)
```

</details>

### 阶段 32：Merge branch 'master' of https://github.com/DevYangJC/Argus

- **提交**：`8320fa4`；原始提交时间：2026-06-07T22:28:58+08:00。合并提交，父节点数 2。
- **阶段目标**：记录两条等价master合并记录的汇合，明确这是无文件变化的纯合并。
- **涉及文件与模块**：无文件变化（纯合并）。
- **实际变化**：8320fa4的父提交为826edba和61e5b0d，manifest中stat与changes均为空，脱敏diff仅有提交头。两个父提交和本提交的结果树均相同，因此该阶段没有源码、配置、文档或测试内容变化。
- **问题与证据边界**：历史未记载异常或冲突；不能从Merge标题推导出功能开发、冲突解决或配置修复。
- **参考阶段方案**：仅写分支历史汇合说明，files保持空数组，不生成重复功能叙事；没有构建或测试结果可据此填写。
- **代码依据**：只读Git对象核对：8320fa4、826edba、61e5b0d的tree均为dd55f6c46a65a2538f077dd3b4527772b68590e4；无变化文件和代码符号可列。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 32 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
默认 git show --stat 无文件统计输出（合并阶段需结合第一父提交 diff 判断）。
```

</details>

### 阶段 33：refactor: restructure deploy layout, fix auth config, update docs and QA record query

- **提交**：`be2c123`；原始提交时间：2026-06-08T12:10:02+08:00。
- **阶段目标**：复现部署目录与认证命名调整，并补齐QA历史删除和直传文件哈希。
- **涉及文件与模块**：`Argus-backend/deploy/server-a.compose.yml`、`Argus-backend/deploy/server-b.compose.yml`、`Argus-backend/src/main/java/com/argus/rag/auth/config/AuthProperties.java`、`Argus-backend/src/main/java/com/argus/rag/auth/config/DevAdminInitializer.java`、`Argus-backend/src/main/java/com/argus/rag/document/service/DocumentUploadService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/service/QaRecordQueryService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/controller/QaRecordController.java`、`Argus-frontend/src/views/qa/QaView.vue`、`Argus-backend/src/main/resources/application-dev.yml`。
- **实际变化**：部署文件从two-node/docker布局重组为deploy根目录compose、env和images，工作目录改为/workspace/Argus-backend和/workspace/Argus-frontend，并同步部署文档与镜像源指南。AuthProperties前缀由rag.auth改为argus.rag.auth，DevAdminInitializer改读argus.rag.dev-admin，相关环境变量名、Cookie、数据库默认名和Elasticsearch索引/分析器名称统一到Argus命名，直传文件新增SHA-256计算并传入持久化命令。QA新增DELETE /api/qa/records/{recordId}，复用可见性检查并在事务中先删引用再删记录，前端历史删除改调API；提交另加入命名、上传及删除测试。
- **问题与证据边界**：历史未提供运行日志，但diff显示此前管理员初始化读取旧项目 dev-admin 配置而配置位于rag.dev-admin，命名绑定存在不一致；部署前端路径、直传fileHash为空和仅本地删除QA历史也有直接代码证据。变更范围超出提交标题的QA record query，不能遗漏真正新增的删除接口，也不能据此声称全部部署问题已消除。
- **参考阶段方案**：按实际变更记录前缀与路径统一、calculateSha256、服务端事务删除及前端等待删除成功后移除会话；删除权限沿用历史可见性规则，而非新建更严格的作者独占规则。史料中的配置和AGENTS修改仅作为被研究对象，
- **代码依据**：Argus-backend/deploy/server-b.compose.yml#services.backend.working_dir、services.frontend.working_dir；Argus-backend/src/main/java/com/argus/rag/auth/config/AuthProperties.java#ConfigurationProperties；Argus-backend/src/main/java/com/argus/rag/auth/config/DevAdminInitializer.java#构造器Value绑定。；Argus-backend/src/main/java/com/argus/rag/document/service/DocumentUploadService.java#calculateSha256、FinalizedUploadCommand；diff第2070-2121行。；Argus-backend/src/main/java/com/argus/rag/qa/controller/QaRecordController.java#deleteRecord；Argus-backend/src/main/java/com/argus/rag/qa/service/QaRecordQueryService.java#delete；Argus-backend/src/main/resources/mappers/qa/QaRecordCitationMapper.xml#deleteByRecordId。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 33 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
AGENTS.md                                          |   6 +-
 Argus-backend/deploy/{two-node => }/README.md      | 154 ++++++++++----
 .../deploy/{two-node => env}/server-a.env.example  |   2 +-
 .../deploy/{two-node => env}/server-b.env.example  |   6 +-
 .../{docker => images}/elasticsearch/Dockerfile    |   0
 ...r-compose.server-a.yml => server-a.compose.yml} |  16 +-
 ...r-compose.server-b.yml => server-b.compose.yml} |  20 +-
 Argus-backend/deploy/国内镜像源配置指南.md         | 235 +++++++++++++++++++++
 .../com/argus/rag/auth/config/AuthProperties.java  |   6 +-
 .../argus/rag/auth/config/DevAdminInitializer.java |  12 +-
 .../document/service/DocumentUploadService.java    |  18 +-
 .../ElasticsearchChunkIndexService.java            |  20 +-
 .../rag/qa/controller/QaRecordController.java      |   8 +
 .../rag/qa/mapper/QaRecordCitationMapper.java      |   3 +
 .../argus/rag/qa/service/QaRecordQueryService.java |  17 ++
 .../src/main/resources/application-dev.yml         |  37 ++--
 .../mappers/qa/QaRecordCitationMapper.xml          |   5 +
 .../com/argus/rag/common/NamingConventionTest.java |  88 ++++++++
 .../service/DocumentUploadServiceTest.java         |  98 +++++++++
 .../rag/qa/service/QaRecordQueryServiceTest.java   |  19 ++
 Argus-frontend/src/api/qa.ts                       |   7 +
 Argus-frontend/src/views/qa/QaView.vue             |  17 +-
 CLAUDE.md                                          |   6 +-
 README.md                                          |   4 +-
 README_EN.md                                       |   4 +-
 docs/V1.0-项目文档.md                              |  10 +-
 docs/V2.0-设计决策.md                              |   6 +-
 docs/V2.0-项目文档.md                              |  10 +-
 docs/启动流程与配置加载说明.md                     |   8 +-
 29 files changed, 710 insertions(+), 132 deletions(-)
```

</details>

### 阶段 34：排除无效代码

- **提交**：`a2fba34`；原始提交时间：2026-06-08T12:12:02+08:00。
- **阶段目标**：复现对本地辅助目录的忽略规则追加。
- **涉及文件与模块**：`.gitignore`。
- **实际变化**：仅在根.gitignore增加/.workbuddy一行，没有删除源码、移除已跟踪文件或改变业务行为。
- **问题与证据边界**：历史未记载异常；提交标题排除无效代码容易被误解为删除业务代码，但实际diff只有忽略规则。
- **参考阶段方案**：将阶段写成版本控制杂项整理，不虚构无效代码定位、业务修复或性能提升；
- **代码依据**：.gitignore#/.workbuddy；diff第8-12行。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 34 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.gitignore | 1 +
 1 file changed, 1 insertion(+)
```

</details>

### 阶段 35：chore: remove .agents and .superpowers from version control

- **提交**：`9ecf0e7`；原始提交时间：2026-06-08T12:13:57+08:00。
- **阶段目标**：复现将辅助技能与预览实验文件移出版本控制的历史整理。
- **涉及文件与模块**：`.agents/skills/ui-ux-pro-max/SKILL.md`、`.agents/skills/ui-ux-pro-max/scripts/core.py`、`.agents/skills/ui-ux-pro-max/scripts/search.py`、`.agents/skills/ui-ux-pro-max/scripts/__pycache__/core.cpython-310.pyc`、`.superpowers/brainstorm/doc-preview-mockup-20260607/content/document-preview-directions.html`、`.superpowers/brainstorm/doc-preview-mockup-20260607/state/server.pid`。
- **实际变化**：删除.agents下的技能说明、CSV数据、搜索脚本及Python缓存，以及.superpowers下的预览HTML和运行状态文件，stat为25文件、2117行删除并包含二进制缓存删除。没有删除根AGENTS.md，也没有移除已实现的QA证据面板或文档预览组件。
- **问题与证据边界**：历史未记载运行异常；可确认第29阶段纳入的辅助工具与实验产物在此移出版本快照，但无法仅凭提交证明工作目录本地文件是否仍保留。
- **参考阶段方案**：描述为从版本控制中移除辅助材料，不写成删除业务能力、删除所有Agent功能或清理历史凭据；忽略规则是在下一阶段补充。
- **代码依据**：.agents/skills/ui-ux-pro-max/SKILL.md及同目录data、scripts文件均为deleted file mode；.agents/skills/ui-ux-pro-max/scripts/__pycache__/core.cpython-310.pyc显示二进制删除。；.superpowers/brainstorm/doc-preview-mockup-20260607/content/document-preview-directions.html、.superpowers/brainstorm/doc-preview-mockup-20260607/state/server-info和.superpowers/brainstorm/doc-preview-mockup-20260607/state/server.pid为删除项；无业务符号变更。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 35 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.agents/skills/ui-ux-pro-max/SKILL.md              | 228 ---------
 .agents/skills/ui-ux-pro-max/data/charts.csv       |  26 -
 .agents/skills/ui-ux-pro-max/data/colors.csv       |  97 ----
 .agents/skills/ui-ux-pro-max/data/landing.csv      |  31 --
 .agents/skills/ui-ux-pro-max/data/products.csv     |  97 ----
 .agents/skills/ui-ux-pro-max/data/prompts.csv      |  24 -
 .../skills/ui-ux-pro-max/data/stacks/flutter.csv   |  53 --
 .../ui-ux-pro-max/data/stacks/html-tailwind.csv    |  56 --
 .../skills/ui-ux-pro-max/data/stacks/nextjs.csv    |  53 --
 .../skills/ui-ux-pro-max/data/stacks/nuxt-ui.csv   |  51 --
 .../skills/ui-ux-pro-max/data/stacks/nuxtjs.csv    |  59 ---
 .../ui-ux-pro-max/data/stacks/react-native.csv     |  52 --
 .agents/skills/ui-ux-pro-max/data/stacks/react.csv |  54 --
 .../skills/ui-ux-pro-max/data/stacks/svelte.csv    |  54 --
 .../skills/ui-ux-pro-max/data/stacks/swiftui.csv   |  51 --
 .agents/skills/ui-ux-pro-max/data/stacks/vue.csv   |  50 --
 .agents/skills/ui-ux-pro-max/data/styles.csv       |  59 ---
 .agents/skills/ui-ux-pro-max/data/typography.csv   |  58 ---
 .../skills/ui-ux-pro-max/data/ux-guidelines.csv    | 100 ----
 .../scripts/__pycache__/core.cpython-310.pyc       | Bin 8334 -> 0 bytes
 .agents/skills/ui-ux-pro-max/scripts/core.py       | 238 ---------
 .agents/skills/ui-ux-pro-max/scripts/search.py     |  61 ---
 .../content/document-preview-directions.html       | 563 ---------------------
 .../doc-preview-mockup-20260607/state/server-info  |   1 -
 .../doc-preview-mockup-20260607/state/server.pid   |   1 -
 25 files changed, 2117 deletions(-)
```

</details>

### 阶段 36：排除agents

- **提交**：`ebac4cf`；原始提交时间：2026-06-08T12:21:23+08:00。
- **阶段目标**：复现辅助目录移出版本控制之后的忽略规则补充。
- **涉及文件与模块**：`.gitignore`。
- **实际变化**：根.gitignore增加/.agents和/.superpowers两条规则及一个空行，stat为1文件、3行新增。该阶段没有继续删除文件，也没有修改根AGENTS.md或任何业务实现。
- **问题与证据边界**：历史未记载异常；排除agents的标题不能解释为移除根AGENTS.md，实际对象是两个以点开头的辅助目录。
- **参考阶段方案**：记录为承接第35阶段移除操作的防止重新纳入规则，区分目录忽略与文件删除；无独立功能开发或构建结果，
- **代码依据**：.gitignore#/.agents、/.superpowers；diff第8-14行。

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 36 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.gitignore | 3 +++
 1 file changed, 3 insertions(+)
```

</details>

### 阶段 37：修复群组控制很异常

- **提交**：`2c9a4c9`；原始提交时间：2026-06-09T12:30:51+08:00。
- **阶段目标**：修正群组描述为空时的规范化结果，并清理旧的 QA 持久化设计稿。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/group/service/GroupManagementService.java`、`docs/V5.0-QA问答记录持久化设计.md`。
- **实际变化**：normalizeDescription 在 StringUtils.hasText(description) 为 false 时，由返回 null 改为返回空字符串；其余长度检查未在该 diff 中改变。；删除 docs/V5.0-QA问答记录持久化设计.md 全部637行；这是设计文档删除，不是 QA 持久化业务代码删除。
- **问题与证据边界**：旧实现对空白描述返回 null；本阶段 schema.sql 快照中 groups.description 是 TEXT NOT NULL DEFAULT ''，可确认应用值与表约束存在不一致，空字符串更符合该约束。没有群组请求、SQL 异常或运行日志，不能把标题所说的异常确认为某一次非空约束错误，也不能断言所有群组问题都被修复。
- **参考阶段方案**：代码以空字符串替代 null，使缺省群组描述与既有 DDL 对齐；同时移除旧设计稿。历史未附验证日志，本次结果见下文。
- **代码依据**：history/37-2c9a4c9.diff:4-22，GroupManagementService.normalizeDescription 与 deleted file mode；blobs/a72041e9a02a45cbe3e127e432619e55e1b6d20bea90cb03ae54785bfedadd15:76-80，sql/schema.sql 的 groups.description；manifest 第37阶段映射该快照

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 37 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../rag/group/service/GroupManagementService.java  |   2 +-
 docs/V5.0-QA问答记录持久化设计.md                  | 637 ---------------------
 2 files changed, 1 insertion(+), 638 deletions(-)
```

</details>

### 阶段 38：增加项目文档

- **提交**：`84cf88e`；原始提交时间：2026-06-09T13:00:12+08:00。
- **阶段目标**：补充 V4.1 QA 记录持久化与切片证据链的项目说明和设计决策。
- **涉及文件与模块**：`docs/V4.1-设计决策.md`、`docs/V4.1-项目文档.md`。
- **实际变化**：新增两份文档，讲解 QaRecordPersistenceService、SaveCommand、qa_records/qa_record_citations 快照、历史查询可见性过滤和持久化异常降级。；描述检索元数据、EvidenceOverview、RRF 归一化、DOCUMENT_COVERAGE、多文档覆盖和历史证据快照重建。；记录流式问答的 evidence-overview/record 事件顺序、数据库初始化参考、API、运行指南；本次 diff 没有业务代码、依赖或配置变更。
- **问题与证据边界**：文档陈述问答历史无法回看、证据不可解释等背景，并把功能列为完成；这些是说明材料，不是本阶段新增实现或验收日志。仅凭文档中的 DDL、示例返回值和命令，不能确认数据库已迁移、接口已运行或构建已通过。
- **参考阶段方案**：通过文档整理既有方案、接口与工程权衡，不宣称此提交重新实现了文档列出的全部功能；后续 Demo 应把本阶段标记为文档补齐。
- **代码依据**：history/38-84cf88e.diff:4-19、484-510，两份新增文档与定位；history/38-84cf88e.diff:95-151，SaveCommand/toCitationEntity/saveCompleted 文档示例；history/38-84cf88e.diff:400-474、940-963，流式持久化和 SSE 文档说明

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 38 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
docs/V4.1-设计决策.md | 474 ++++++++++++++++++++++++++++++++++++++
 docs/V4.1-项目文档.md | 618 ++++++++++++++++++++++++++++++++++++++++++++++++++
 2 files changed, 1092 insertions(+)
```

</details>

### 阶段 39：feat: RAG知识库引入全局检索

- **提交**：`29fd06f`；原始提交时间：2026-06-11T20:31:01+08:00。
- **阶段目标**：为列文件、跨文档概览和汇总问题提供 GLOBAL 检索策略。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/qa/model/QueryPlanStrategy.java`、`Argus-backend/src/main/java/com/argus/rag/qa/rag/HybridChunkRetrievalService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/service/QueryPlanningService.java`、`Argus-backend/src/main/resources/prompts/query-planning/user.st`、`Argus-backend/src/test/java/com/argus/rag/qa/rag/HybridChunkRetrievalServiceTest.java`。
- **实际变化**：QueryPlanStrategy 增加 GLOBAL；QueryPlanningService 对 GLOBAL 保留原问题；查询规划提示词加入策略规则并把旧的非 JSON 示例改为 JSON 对象。；HybridChunkRetrievalService 新增 DocumentMapper 依赖；GLOBAL 分支按 groupId、READY、deleted=false、id 升序查询，最多50份文档。；每份文档只取一个有效切片，优先 chunkIndex=0，否则使用有效切片列表第一项；证据加入文件名、GLOBAL/GLOBAL_TRAVERSAL 元数据和固定 score=1.0。；非空 GLOBAL 结果直接标记 EvidenceLevel.SUFFICIENT；新增 shouldTraverseAllReadyDocumentsWhenStrategyIsGlobal 测试源码并调整旧测试构造器。
- **问题与证据边界**：普通相关性检索不等于遍历文档清单，新增分支针对这一场景。但 GLOBAL 并非无上限全库全文检索：有50文档上限、只取首片、跳过无可用片的文档，且代码仍提示已列出所有可用文档并直接给 SUFFICIENT，可能夸大覆盖度。没有模型规划输出或运行日志，不能证明每个汇总问题都会命中 GLOBAL；测试源码不等于测试通过。
- **参考阶段方案**：以独立的元数据遍历分支绕过普通双通道相关性召回，补充规则和测试案例；Demo 必须注明50份及首片概览的边界，不展示为全库完整统计已获验证。
- **代码依据**：history/39-29fd06f.diff:8-16、81-166，GLOBAL/retrieveGlobal/MAX_GLOBAL_DOCUMENTS；history/39-29fd06f.diff:227-242、249-319，QueryPlanningService 与 user.st；history/39-29fd06f.diff:357-410，GLOBAL 测试断言

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 39 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../com/argus/rag/qa/model/QueryPlanStrategy.java  |   5 +-
 .../rag/qa/rag/HybridChunkRetrievalService.java    | 118 +++++++++++++++++----
 .../argus/rag/qa/service/QueryPlanningService.java |   5 +
 .../main/resources/prompts/query-planning/user.st  |  60 +++++++----
 .../qa/rag/HybridChunkRetrievalServiceTest.java    |  60 +++++++++++
 5 files changed, 208 insertions(+), 40 deletions(-)
```

</details>

### 阶段 40：fix: 补全大文件分片上传前端链路并修复上传全链路质量问题

- **提交**：`4878b00`；原始提交时间：2026-07-09T10:57:54+08:00。
- **阶段目标**：接入大文件上传界面，并强化服务端分片校验、合并状态和失败补偿。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/document/service/DocumentUploadService.java`、`Argus-backend/src/main/resources/application.yml`、`Argus-frontend/src/api/document.ts`、`Argus-frontend/src/views/documents/components/UploadDialog.vue`。
- **实际变化**：服务端读取分片字节后重新计算 SHA-256，与传入 chunkHash 比较；对象上传改用已读取的字节和 ByteArrayInputStream。；分片 DB upsert 失败时补偿删除对象；合并状态更新增加 UPLOADING 条件并检查更新行数；禁止 COMPLETING/COMPLETED 会话继续上传；合并异常分支增加逐片清理，缺片判断增加 null 检查。；application.yml 增加 multipart 单文件50MB、请求60MB；前端最大选择限制由10MB提高为256MiB，超过10MiB走 chunked.uploadFile，否则直传。；document.ts 的 init/chunk/complete API 增加 AbortSignal 参数；UploadDialog 增加分片阶段、指纹、进度、取消和错误 UI。HybridChunkRetrievalService 只有构造器调用排版调整。；UploadDialog 导入 ../composables/useChunkedUpload，但本阶段完整文件清单中没有任何 useChunkedUpload 文件；第40—47阶段均如此，不能把标题里的补全链路当成完整可运行事实。
- **问题与证据边界**：diff 可确认旧分片路径没有此处新增的字节哈希复算、条件状态转换及相关补偿，不能据此认定实际发生过数据损坏或并发重复合并。更直接的静态缺口是前端新增导入指向未提交模块；manifest 的阶段40—47完整文件清单均缺失该实现。没有构建或上传日志，不能宣称大文件链路已通，也不应臆造具体编译输出。
- **参考阶段方案**：提交提供服务端完整性检查、条件状态更新和补偿，并搭建前端分片调用接口及 UI；前端 composable 缺失仍未解决。
- **代码依据**：history/40-4878b00.diff:22-169，computeSha256/compensateUploadedChunkObject/UPLOAD_STATUS_COMPLETING；history/40-4878b00.diff:193-276、308-333、432-459，multipart、AbortSignal、useChunkedUpload、上传路由与取消 UI；manifest.json，commits[stage=40..47].files 无 useChunkedUpload 路径；该文件到阶段48才新增

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 40 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../document/service/DocumentUploadService.java    |  74 ++++++++++---
 .../rag/qa/rag/HybridChunkRetrievalService.java    |   8 +-
 Argus-backend/src/main/resources/application.yml   |   4 +
 Argus-frontend/src/api/document.ts                 |  16 ++-
 .../views/documents/components/UploadDialog.vue    | 119 +++++++++++++++------
 5 files changed, 165 insertions(+), 56 deletions(-)
```

</details>

### 阶段 41：README 增加旧项目参考说明（原提交标题已脱敏）

- **提交**：`cacdbe3`；原始提交时间：2026-07-09T11:40:40+08:00。
- **阶段目标**：补充项目参考来源说明。
- **涉及文件与模块**：`README.md`。
- **实际变化**：README 新增参考和引用小节，明确说明项目参考了旧项目 的实现逻辑和代码。；仅增加说明文字，没有模型、上传、检索或构建配置变更。
- **问题与证据边界**：此前 README 缺少该段来源说明；本提交提供参考代码与逻辑的直接文字证据，但没有逐文件映射，不能推断具体哪些行来自旧项目，也不能把整个项目一概判定为复制。无运行故障日志，不能为文档提交编造运行问题。
- **参考阶段方案**：在复现记录开头保留参考来源事实，不将本次复制历史作为原创；本阶段只记录来源披露，不安排虚构的功能验收。
- **代码依据**：history/41-cacdbe3.diff:8-14，README 的参考和引用新增段落

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 41 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
README.md | 3 +++
 1 file changed, 3 insertions(+)
```

</details>

### 阶段 42：feat: add You.com web search as third retrieval channel

- **提交**：`b8ab20d`；原始提交时间：2026-07-27T11:44:16Z。
- **阶段目标**：增加可关闭的 You.com 网络检索补充证据通道。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/engine/youcom/YoucomSearchService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/rag/HybridChunkRetrievalService.java`、`Argus-backend/src/main/resources/application-dev.yml`。
- **实际变化**：新增 YoucomSearchService，以 JDK HttpClient 向 https://ydc-index.io/v1/search 同步 POST query/count，连接及请求超时10秒，使用 X-API-Key 请求头。；配置默认 enabled=false、top-k=10；未启用、缺少 key 或空查询返回空列表；401、429、其他非200和请求异常均降级为空，中断时恢复线程中断标记。；解析顶层 results 数组，取 title/url、首条 snippets 或 description，构造 YOUCOM/WEB_SEARCH 元数据与倒数排名分数。；混合检索在本地证据组装后按 plannedQuery 逐个调用网络搜索并追加、重新编号证据；网络结果不参加向量加关键词的 RRF 排序。；两个 HybridChunkRetrievalService 构造器均新增 YoucomSearchService 参数，但既有 HybridChunkRetrievalServiceTest 快照未更新，仍使用旧参数列表。
- **问题与证据边界**：注释称三通道并行，但新增代码是同步 httpClient.send 且在本地证据之后顺序调用；本地证据为空会提前返回，GLOBAL 分支也提前返回，因而不是全场景 Web 兜底。enabled=false 也意味着默认配置不会调用网络。构造器与保留测试调用签名存在可核对的静态不一致。没有 HTTP 响应、测试或运行日志，不能确认第三方端点协议已联调、401/429曾实际发生或测试具体报错。
- **参考阶段方案**：用可开关、限时、异常返回空的独立服务补充本地检索结果；不要把实现描述成并行融合或默认已联网。Demo 需另行确认开启配置、供应商响应结构、引用展示和测试构造器兼容性，
- **代码依据**：history/42-b8ab20d.diff:43-139、142-187，YoucomSearchService.search/parseResponse/extractSnippet；history/42-b8ab20d.diff:243-305，新增构造参数、空证据提前返回及顺序追加逻辑；history/42-b8ab20d.diff:307-320，默认禁用配置

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 42 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../rag/engine/youcom/YoucomSearchService.java     | 188 +++++++++++++++++++++
 .../rag/qa/rag/HybridChunkRetrievalService.java    |  40 ++++-
 .../src/main/resources/application-dev.yml         |   6 +
 3 files changed, 228 insertions(+), 6 deletions(-)
```

</details>

### 阶段 43：Merge pull request #7 from Cooperation-org/feat/youcom-search

- **提交**：`526917d`；原始提交时间：2026-08-13T10:43:35+08:00。合并提交，父节点数 2。
- **阶段目标**：将 You.com 功能分支合并到主线。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/engine/youcom/YoucomSearchService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/rag/HybridChunkRetrievalService.java`、`Argus-backend/src/main/resources/application-dev.yml`。
- **实际变化**：这是双亲合并提交，第一父提交 cacdbe3，第二父提交 b8ab20d；相对第一父提交的变更即上一阶段的 You.com 接入。；43-526917d.diff 与 42-b8ab20d.diff 去掉提交头后的补丁正文逐字相同；git diff --stat b8ab20d 526917d 无输出，两者代码树无差异。；未额外增加第二套搜索实现，也未修复阶段42的串行调用、默认禁用或测试构造器不同步问题。
- **问题与证据边界**：按52条提交简单线性累加补丁会把同一功能分支及其合并重复当作两次独立开发，甚至重复应用文件新增。这里应区分提交拓扑与线性展示序号。没有合并检查或运行日志，不能从 merge 标题推出质量验证已通过。
- **参考阶段方案**：复现时把本阶段记录为合并与主线落点；若阶段42已落到相同树，本阶段内容应保持不变，不重复应用同一补丁或重复宣称新增能力。本次构建结果见下文。
- **代码依据**：history/43-526917d.diff:1-4、198-320，合并标题及相对第一父提交补丁；history/42-b8ab20d.diff，与阶段43的补丁正文完全相同；C:/Users/Lenovo/IdeaProjects/GitHub/Argus，只读 git log 的 %H 与 %P 格式输出核对 526917d 双亲；git diff --stat b8ab20d 526917d 无树差异

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 43 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../rag/engine/youcom/YoucomSearchService.java     | 188 +++++++++++++++++++++
 .../rag/qa/rag/HybridChunkRetrievalService.java    |  40 ++++-
 .../src/main/resources/application-dev.yml         |   6 +
 3 files changed, 228 insertions(+), 6 deletions(-)
```

</details>

### 阶段 44：Update README.md

- **提交**：`d043446`；原始提交时间：2026-08-17T00:06:04+08:00。
- **阶段目标**：调整 README，删除参考来源段落。
- **涉及文件与模块**：`README.md`。
- **实际变化**：删除阶段41新增的参考和引用标题及参考 旧项目 实现逻辑和代码的说明。；没有业务源码、模型配置、依赖或服务器配置改动。
- **问题与证据边界**：来源披露文字从当前 README 消失，但历史删除操作不能证明已消除引用代码、改为独立实现或获得了新的原创归属。没有删除原因材料，也没有运行故障日志，不能自行补出动机。
- **参考阶段方案**：如实记录文档删减，同时在整个研究的来源声明中保留阶段41的可核验事实；不能把这一提交写成原创化重构或功能修复。
- **代码依据**：history/44-d043446.diff:8-14，README 来源说明删除；history/41-cacdbe3.diff:12-13，原来源说明

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 44 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
README.md | 2 --
 1 file changed, 2 deletions(-)
```

</details>

### 阶段 45：删掉所有阿里巴巴import

- **提交**：`7243e55`；原始提交时间：2026-11-16T16:57:57+08:00。
- **阶段目标**：尝试从 DashScope 聊天切换到智谱的 OpenAI 兼容接入，并调整开发环境默认连接。
- **涉及文件与模块**：`Argus-backend/pom.xml`、`Argus-backend/src/main/java/com/argus/rag/ArgusBackendApplication.java`、`Argus-backend/src/main/resources/application-dev.yml`、`Argus-backend/src/main/resources/application.yml`。
- **实际变化**：Lombok 1.18.34 升到1.18.38；注释 spring-ai-alibaba-bom、DashScope starter 和 spring-ai-alibaba-agent-framework 依赖。标题称删掉所有 Alibaba import，但实际没有全量删除源码 import；助手工厂和记忆 Hook 仍依赖 Alibaba graph 类。；主入口 SpringApplication.run 的类名由 ArgusBackendApplication 误改为 ArgusBackendAp3plication，当前声明类名没有相应变化。；默认 profile 由 local 改为 dev；聊天选择由 dashscope 改为 openai，模型 qwen-plus 改为 glm-4.7-flash，OpenAI base-url 由 DashScope compatible-mode 改为 https://open.bigmodel.cn/api/paas/v4/。；Embedding 配置由 text-embedding-v3/512 改为 embedding-2/1024，但同一有效配置中的 pgvector.dimensions 仍是512；sql/schema.sql 仍声明 VECTOR(512)。；DashScope 配置被错误放在 spring.ai.model 下；api-key 同样处于 model 层而非正确的 dashscope 子层。autoconfigure.exclude 只是注释，并未启用排除。；数据库、MinIO、Elasticsearch 默认连接由 localhost 改为同一内网服务器；修改开发账号等默认项并追加整份旧配置的注释备份。凭据已脱敏，不记录真实值。；有效 ingestion.chunking 仍为 target/max/overlap=240/320/32，ingestion.vector.add-batch-size 仍为9；本阶段没有改变语义切片或向量批处理实现。
- **问题与证据边界**：本提交不是单纯删 import：入口类名拼写错误、仍有 Alibaba 源码引用而相关依赖被注释、DashScope 属性层级不正确，以及 embedding1024/pgvector512 的配置不一致均有静态证据。OpenAI 兼容方式只设置 base-url，未见显式 chat/embedding 路径适配；没有最终 HTTP URL、响应体或启动日志，不能断言是哪一项首先失败，也不能归因于模型权限、额度或供应商故障。truncate:true 旁边仍是 Ollama 注释，不能据此保证该 OpenAI/智谱路径支持截断。
- **参考阶段方案**：代码采取替换接入配置、暂时注释依赖和切换 dev 环境的迁移尝试，但同时引入或暴露上述不一致；后续阶段46恢复依赖和入口名，阶段47修配置层级，阶段49再迁移到原生 ZhiPuAi 并对齐配置维度。本阶段不可标记为已迁移成功。
- **代码依据**：history/45-7243e55.diff:8-70，依赖注释、Lombok 和 ArgusBackendAp3plication；history/45-7243e55.diff:77-135、171-211、328-339，接入、连接与 profile 变更；blobs/19c62694bd77d0e0e37bf9c66607e16408c79fd2c46e1d97cd0547ebf6986019:30-51、72-77、110-117，第45阶段有效 application-dev.yml：错误层级、1024/512、240/320/32和批量9

**本次验证结果**

- `mvnw.cmd clean compile`：未通过，退出码 `1`，耗时 16 秒；原始日志索引 `45-backend.log`。

```text
[ERROR]   location: class com.argus.rag.assistant.memory.AssistantShortTermMemoryHook
[ERROR] /C:/Users/Lenovo/IdeaProjects/GitHub/Argus-replica/Argus-backend/src/main/java/com/argus/rag/qa/config/QaChatClientConfiguration.java:[6,42] package org.springframework.ai.rag.advisor does not exist
[ERROR] /C:/Users/Lenovo/IdeaProjects/GitHub/Argus-replica/Argus-backend/src/main/java/com/argus/rag/qa/config/QaChatClientConfiguration.java:[7,58] package org.springframework.ai.rag.generation.augmentation does not exist
[ERROR] /C:/Users/Lenovo/IdeaProjects/GitHub/Argus-replica/Argus-backend/src/main/java/com/argus/rag/qa/config/QaChatClientConfiguration.java:[39,46] cannot find symbol
[ERROR]   symbol:   class RetrievalAugmentationAdvisor
[ERROR]   location: class com.argus.rag.qa.config.QaChatClientConfiguration
[ERROR] /C:/Users/Lenovo/IdeaProjects/GitHub/Argus-replica/Argus-backend/src/main/java/com/argus/rag/qa/config/QaChatClientConfiguration.java:[99,12] cannot find symbol
[ERROR]   symbol:   class RetrievalAugmentationAdvisor
[ERROR]   location: class com.argus.rag.qa.config.QaChatClientConfiguration
[ERROR] /C:/Users/Lenovo/IdeaProjects/GitHub/Argus-replica/Argus-backend/src/main/java/com/argus/rag/assistant/memory/AssistantShortTermMemoryHook.java:[28,17] cannot find symbol
[ERROR]   symbol: variable HookPosition
[ERROR] -> [Help 1]
[ERROR] 
[ERROR] To see the full stack trace of the errors, re-run Maven with the -e switch.
[ERROR] Re-run Maven using the -X switch to enable full debug logging.
[ERROR] 
[ERROR] For more information about the errors and possible solutions, please read the following articles:
[ERROR] [Help 1] http://cwiki.apache.org/confluence/display/MAVEN/MojoFailureException
```

> 【截图占位：此处插入阶段 45 backend 的真实失败输出截图，对应 45-backend.log；不得用其他阶段截图替代】

- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。
<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
Argus-backend/pom.xml                              |   9 +-
 .../com/argus/rag/ArgusBackendApplication.java     |   2 +-
 .../src/main/resources/application-dev.yml         | 199 +++++++++++++++++----
 Argus-backend/src/main/resources/application.yml   |   2 +-
 4 files changed, 171 insertions(+), 41 deletions(-)
```

</details>

### 阶段 46：只改了application-dev

- **提交**：`ce1c871`；原始提交时间：2026-11-16T17:05:26+08:00。
- **阶段目标**：撤回前一阶段的依赖注释，并纠正应用入口类名。
- **涉及文件与模块**：`Argus-backend/pom.xml`、`Argus-backend/src/main/java/com/argus/rag/ArgusBackendApplication.java`。
- **实际变化**：恢复 Alibaba BOM、DashScope starter 和 Alibaba agent framework 的有效依赖声明。；SpringApplication.run 参数由 ArgusBackendAp3plication.class 恢复为 ArgusBackendApplication.class。；实际 changes 只有 pom.xml 与入口 Java；application-dev.yml 并未改变。前一阶段 Lombok 1.18.38、dev profile、OpenAI 兼容智谱配置等保留。
- **问题与证据边界**：标题是只改了application-dev，与实际 diff 正好不符。确切修复对象是阶段45引入的入口拼写和依赖移除；此时错误 DashScope 配置层级与 embedding/pgvector 维度不一致仍在。没有构建或启动日志，不能确认恢复依赖后服务已经启动。
- **参考阶段方案**：恢复源码所依赖的 Alibaba 组件并修正入口类引用；研究记录应以实际两文件变更命名本阶段，不把标题照抄为配置修复完成。
- **代码依据**：history/46-ce1c871.diff:4-57，pom.xml 三处依赖恢复；history/46-ce1c871.diff:58-69，main 入口类名纠正；manifest.json，commits[stage=46].changes 仅列 pom.xml 和 ArgusBackendApplication.java

**本次验证结果**

- `mvnw.cmd clean compile`：通过，退出码 `0`，耗时 20 秒；原始日志索引 `46-backend.log`。

```text
[INFO] BUILD SUCCESS
```

> 【截图占位：此处插入阶段 46 backend 的真实成功输出截图，对应 46-backend.log；不得用其他阶段截图替代】

- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。
<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
Argus-backend/pom.xml                                        | 12 ++++++------
 .../src/main/java/com/argus/rag/ArgusBackendApplication.java |  2 +-
 2 files changed, 7 insertions(+), 7 deletions(-)
```

</details>

### 阶段 47：能正常跑起来了

- **提交**：`7b33d30`；原始提交时间：2026-11-16T17:29:54+08:00。
- **阶段目标**：将 DashScope 配置恢复到正确的 Spring AI 属性层级。
- **涉及文件与模块**：`Argus-backend/src/main/resources/application-dev.yml`。
- **实际变化**：仅将 dashscope: 左移两个空格，由 spring.ai.model.dashscope 调整为 spring.ai.dashscope，使随后 api-key 行成为正确的 dashscope 子属性。；chat/embedding 仍选择 openai，聊天仍为 glm-4.7-flash，embedding-2 仍配置1024而 pgvector仍配置512；没有新增模型适配类、端点覆盖或数据库迁移。
- **问题与证据边界**：旧层级的属性路径错误可以由 diff 直接核实；本阶段只是一行缩进修正。标题能正常跑起来了是提交说明，不是日志证据，不能扩展为登录、上传、检索、工具调用和构建全部成功；也无法据此排除仍在的维度和端点适配问题。
- **参考阶段方案**：通过缩进修正让 DashScope 属性能按预期路径绑定；该路径仍使用占位启动 key，主聊天配置并没有改回 DashScope。仅记录配置应对，实际启动状态由复现执行方补录。
- **代码依据**：history/47-7b33d30.diff:8-16，spring.ai.dashscope 缩进修正；history/49-ffa6782.diff:180-187，直到阶段49才把 pgvector.dimensions 从512改为1024

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 47 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
Argus-backend/src/main/resources/application-dev.yml | 2 +-
 1 file changed, 1 insertion(+), 1 deletion(-)
```

</details>

### 阶段 48：改了分片的逻辑和配置了服务器之后的版本

- **提交**：`0f0e383`；原始提交时间：2026-11-18T15:03:17+08:00。
- **阶段目标**：改用服务器连接默认值，并以禁用大文件分片路径的方式回退到普通上传。
- **涉及文件与模块**：`Argus-backend/src/main/resources/application-dev.yml`、`Argus-frontend/src/composables/useChunkedUpload.ts`、`Argus-frontend/src/views/documents/components/UploadDialog.vue`。
- **实际变化**：DB_HOST、MINIO_ENDPOINT、ES_HOST 默认值由内网服务器改为同一公网服务器；数据库仍5432、MinIO仍HTTP 9000、ES仍HTTP 9200。这些是连接配置，不是服务器部署脚本或部署结果。；dev profile 新增 multipart 单文件50MB、请求100MB，覆盖主配置的请求60MB限制；单文件仍是50MB。；新增全局 composables/useChunkedUpload.ts，但 uploadFile 直接抛出已禁用分片上传、请上传小文件的错误；仅有 ref 状态及 reset/cancel 占位逻辑。；UploadDialog 的导入从不存在的局部 composable 改到新增全局路径，同时注释超过10MiB分片的判断及分支，实际所有文件都走 uploadDocument 直传。；前端 MAX_FILE_SIZE=256MiB 与最大256MB提示未同步调小；后端单文件50MB限制并未放开。；没有改变后端结构感知文本切片、embedding维度或 VectorIngestionService 批处理；研究区间37—52这些切片/批处理源码快照保持同一版本，有效配置仍是240/320/32 tokens和每次VectorStore.add最多9片。
- **问题与证据边界**：标题改了分片逻辑实际指传输分片被禁用，不是 RAG 语义切片优化；新 hook 不是完整分片实现。UI 允许256MiB但只走受50MB限制的直传，形成明确的大小边界不一致。外部服务器是否已装 PostgreSQL/pgvector、MinIO、ES、放通端口或改好表维度，没有脚本、SSH记录、请求或部署日志可确认；不能写成服务器配置已验证。
- **参考阶段方案**：通过补上可导入的占位模块并绕开分片调用，降低前端路径缺失造成的阻碍，以普通小文件上传作为代码层回退；同步修改远端连接默认值和请求大小。该应对不是恢复大文件能力，也未证明任何文件上传或向量化成功。
- **代码依据**：history/48-0f0e383.diff:8-42，数据库/MinIO/ES连接与multipart；history/48-0f0e383.diff:43-110，占位useChunkedUpload和直传回退；history/40-4878b00.diff:274-276、297-305、368-373，保留的256MiB限制和提示

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 48 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../src/main/resources/application-dev.yml         | 12 +++++++---
 Argus-frontend/src/composables/useChunkedUpload.ts | 27 ++++++++++++++++++++++
 .../views/documents/components/UploadDialog.vue    | 10 ++++----
 3 files changed, 41 insertions(+), 8 deletions(-)
```

</details>

### 阶段 49：能上传文档，但是没法调AI的版本

- **提交**：`ffa6782`；原始提交时间：2026-11-19T20:25:04+08:00。
- **阶段目标**：从智谱 OpenAI 兼容配置迁移到原生 ZhiPuAi starter，明确模型 Bean 并对齐向量配置维度。
- **涉及文件与模块**：`Argus-backend/pom.xml`、`Argus-backend/src/main/java/com/argus/rag/assistant/agent/AssistantReactAgentFactory.java`、`Argus-backend/src/main/java/com/argus/rag/assistant/support/config/AssistantChatModelConfiguration.java`、`Argus-backend/src/main/java/com/argus/rag/assistant/service/AssistantService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/service/QaChatService.java`、`Argus-backend/src/main/java/com/argus/rag/qa/service/QaService.java`、`Argus-backend/src/main/resources/application-dev.yml`。
- **实际变化**：pom 新增 spring-ai-starter-model-zhipuai，注释 spring-ai-starter-model-openai；DashScope starter 和 Alibaba agent framework 仍保留。；AssistantReactAgentFactory 注入参数加 @Qualifier("zhiPuAiChatModel")；新增 AssistantChatModelConfiguration，以 @Primary 的 primaryChatModel/primaryEmbeddingModel 返回明确限定的智谱模型 Bean。；spring.ai.model 的 openai 选择被注释，配置命名空间 openai 改为 zhipuai，使用 ZHIPUAI_API_KEY；旧 OpenAI_BASE_URL 显式值被注释，不再作为有效原生接入 base-url。；chat.options.model 从 glm-4.7-flash 改为 glm-4-flash；AssistantService、QaChatService、QaService 的 MODEL_NAME 常量从 qwen-plus 改为 glm-4-flash。模型选择配置与业务记录常量是不同层面，不能只凭常量认定实际请求模型。；embedding 仍为 embedding-2、配置1024维；pgvector.dimensions 从512改为1024，修正应用配置两端不一致。没有修改 sql/schema.sql，参考 DDL 到HEAD仍为VECTOR(512)，也没有提交实际数据库ALTER或重建记录。；有效切片240/320/32、向量add-batch-size=9、truncate:true及Ollama旧注释均保留；每次业务VectorStore.add最多9个Document不等于实际供应商HTTP批次或接口限额已核实。；LlmCostCalculatorImpl 未新增GLM费率，模型名未命中时沿用默认费率；Demo成本显示不能当作供应商实际账单或余额证据。
- **问题与证据边界**：标题称能上传但不能调AI，未附HTTP状态、响应体、模型请求、账号信息或异常日志，无法将失败归因为GLM权限、免费额度、欠费或特定模型不支持。本提交同时改变适配器、Bean选择、模型名和向量配置，不能把任何单项认作已证实根因。1024配置与仍为512的参考DDL存在静态不一致，但外部服务器实际表定义未知；仅上传完成也不等于后台摄入READY。原生客户端最终端点、维度/truncate参数是否被支持、供应商批处理行为仍需实测。
- **参考阶段方案**：代码采用原生智谱starter并显式选择聊天与嵌入Bean，避免继续依赖先前的OpenAI兼容路径配置，同时把PGvector应用配置对齐1024。其效果只能写为迁移应对；数据库向量列、实际请求/响应及后台摄入状态须由复现执行方后续核查，不能宣称上传或模型调用已修好。
- **代码依据**：history/49-ffa6782.diff:4-54、69-99，依赖迁移、Qualifier、@Primary模型配置；history/49-ffa6782.diff:56-68、125-187，MODEL_NAME、原生命名空间、glm-4-flash和pgvector1024；blobs/0d0058d0c8d813fd156fab67713bcddca22f105a9046461ca581d59312078d06:30-51、72-82、116-129，第49阶段有效配置

**本次验证结果**

- `mvnw.cmd clean compile`：通过，退出码 `0`，耗时 19 秒；原始日志索引 `49-backend.log`。

```text
[INFO] BUILD SUCCESS
```

> 【截图占位：此处插入阶段 49 backend 的真实成功输出截图，对应 49-backend.log；不得用其他阶段截图替代】

- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。
<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
Argus-backend/pom.xml                              | 13 ++++--
 .../agent/AssistantReactAgentFactory.java          |  3 +-
 .../rag/assistant/service/AssistantService.java    |  2 +-
 .../config/AssistantChatModelConfiguration.java    | 50 ++++++++++++++++++++++
 .../com/argus/rag/qa/service/QaChatService.java    |  2 +-
 .../java/com/argus/rag/qa/service/QaService.java   |  2 +-
 .../src/main/resources/application-dev.yml         | 16 +++----
 7 files changed, 72 insertions(+), 16 deletions(-)
```

</details>

### 阶段 50：能上传文档，用glm-4才能跑

- **提交**：`1196f89`；原始提交时间：2026-11-19T20:56:30+08:00。
- **阶段目标**：在短期记忆重建消息时保留核心系统指令，并强化知识库模式先调检索工具的提示。
- **涉及文件与模块**：`Argus-backend/src/main/java/com/argus/rag/assistant/memory/AssistantShortTermMemoryHook.java`。
- **实际变化**：AssistantShortTermMemoryHook 构造器新增 AssistantPromptContextBuilder 依赖。；assembleBeforeModelMessages 在组装会话摘要、记忆、历史和工具消息前调用 buildChatInstruction；非空时先添加 SystemMessage。；KB_SEARCH 模式下追加提示，要求先调用 knowledgeBaseSearch，再基于检索内容作答，未找到时明确说明；这是提示词约束，不是代码强制执行一次工具调用。；没有修改starter、端点、API key配置、聊天模型名称或embedding维度；application-dev.yml与阶段49保持同一blob，实际配置仍为glm-4-flash，而不是在此阶段切换为glm-4。
- **问题与证据边界**：标题用glm-4才能跑不能当作模型权限或额度结论。真实变更针对before-model消息重建：Hook使用UpdatePolicy.REPLACE，原组装顺序没有本次新增的核心instruction SystemMessage。补丁注释认为该过程丢失工具调用指令，但没有实际模型消息、tool_calls或失败日志，不能把这一解释当成已完成实验验证，也不能保证强化提示后必然调工具。
- **参考阶段方案**：在被REPLACE的新消息列表首部显式重建系统指令，对KB_SEARCH增加工具优先提示。Demo应分别核对最终发给模型的消息、工具注册、tool_calls和检索结果，而不是写成通过更换glm-4解决账号权限问题；本次未运行任何模型或构建验证。
- **代码依据**：history/50-1196f89.diff:8-29、32-57，AssistantPromptContextBuilder注入与SystemMessage/knowledgeBaseSearch提示；blobs/72c7da29ec5f5db5740882216133524487e0845da1dcc3f0443f0586ffbcda6f:56-105、166-208，BEFORE_MODEL与UpdatePolicy.REPLACE完整上下文；manifest.json，commits[stage=49]和[stage=50]的application-dev.yml同为oid 76cf39f3ebbc6c3c7c4967bec9bc2a874e9d3810

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 50 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../memory/AssistantShortTermMemoryHook.java       | 27 +++++++++++++++++++++-
 1 file changed, 26 insertions(+), 1 deletion(-)
```

</details>

### 阶段 51：发给同学的版本

- **提交**：`b3d718f`；原始提交时间：2026-11-22T10:30:55+08:00。
- **阶段目标**：为交接使用者提供最简前后端启动说明。
- **涉及文件与模块**：`启动方式.txt`。
- **实际变化**：仅新增3行启动方式.txt：后端通过IDE运行ArgusBackendApplication；前端进入Argus-frontend后执行npm install和npm run dev。；第三行在浏览器输入之后没有给出URL，文件无末尾换行；没有后端/前端源码、配置、依赖或部署包变更。
- **问题与证据边界**：该说明缺少浏览器地址、服务依赖准备和环境变量交接，不能据此保证接收者能够启动；发给同学是交接定位，不是运行结果。无实际安装、构建或启动日志，不能记录依赖已安装或Demo已运行。
- **参考阶段方案**：以文本补充操作入口，但应明确这只是简版交接说明，本阶段没有代码层故障修复。
- **代码依据**：history/51-b3d718f.diff:4-13，唯一新增文件及不完整浏览器说明

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 51 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
启动方式.txt | 3 +++
 1 file changed, 3 insertions(+)
```

</details>

### 阶段 52：宇阳阳小组协作修改前的最终版本

- **提交**：`7ba359a`；原始提交时间：2026-11-22T11:41:25+08:00。
- **阶段目标**：整理开发配置，形成协作修改前的最终交接快照。
- **涉及文件与模块**：`Argus-backend/src/main/resources/application-dev.yml`。
- **实际变化**：仅application-dev.yml变化；删除文件末尾整段旧配置注释备份，涉及旧DashScope/OpenAI、PGvector512、本机存储、认证、切片和You.com示例，删除的是注释而非正在运行的配置块。；脱敏diff在ZHIPUAI_API_KEY附近显示环境变量注入说明及占位值；两侧真实密钥已不可见，不把脱敏后的相同行值或新增说明当作原始提交完成密钥整改、轮换或安全验收的证据。；有效配置仍是原生ZhiPuAi、glm-4-flash、embedding-2/1024、PGvector配置1024；切片240/320/32、VectorStore.add批量9、You.com关闭、远端存储/数据库连接以及50MB单文件限制均未因删除注释而改变。；没有提交协作功能、合并其他成员的业务代码或新模型适配；这是协作修改前快照，而不是协作修改成果。阶段51的启动方式.txt原样保留。
- **问题与证据边界**：不能把最终版本或协作修改前的标题等同于功能完整、运行稳定或团队协作已经完成。HEAD仍保留前端256MiB提示对后端50MB直传限制、参考DDL VECTOR(512)对应用1024配置、检索测试构造器未同步等静态偏差；真实服务器表定义和运行结果未知。没有构建、部署或模型日志，所有成功结论都需后续实测。
- **参考阶段方案**：本阶段实际应对是减少重复注释配置、整理交接快照，未提供上述业务或环境偏差的修复。复现执行方应保留脱敏处理与原始提交事实的区分，填写真实构建和Demo结果时不能由标题或占位配置反推成功。
- **代码依据**：history/52-7ba359a.diff:4-21、22-133，唯一配置文件及旧注释块清理；manifest.json，commits[stage=52].changes只含application-dev.yml；该文件sha256与originalSha256不同，说明材料经过脱敏；history/49-ffa6782.diff:170-187、50-1196f89.diff:4-57、51-b3d718f.diff:4-13，最终模型接入、消息修复及交接说明的真实来源

**本次验证结果**

- 后端：本阶段未出现 POM 变化，或该阶段后端尚不存在，按触发规则跳过；不宣称本阶段编译通过。
- 前端：本阶段前端依赖清单未变化，或前端尚不存在，按触发规则跳过；不宣称本阶段构建通过。

> 【截图占位：此处插入阶段 52 的 git show --stat 或合并无差异输出，以及该快照文件校验结果；本阶段未触发构建，不标记为编译成功】

<details>
<summary>该提交 git show --stat 原始摘要</summary>

```text
.../src/main/resources/application-dev.yml         | 115 +--------------------
 1 file changed, 2 insertions(+), 113 deletions(-)
```

</details>

## 敏感配置脱敏与环境变量

所有真实密码与密钥在运行时必须通过环境变量注入，严禁进入公开仓库、截图和比赛作品贴。本次只使用安全占位符完成离线编译/打包，没有填入真实凭据，也没有验证真实账户或付费模型调用。占位符不是可供部署使用的密码，发布前必须替换为环境注入。

用户已确认将最初仅针对 application-dev.yml 的脱敏范围扩展到所有历史阶段的同类配置，以及文档中的对应值；因此验收不能仍只排除一个文件。最终有 12 个文件包含批准的脱敏差异，逐项单独核对；其余 343 个参考文件必须逐字节一致。

已另外执行 application-dev.yml 的 diff -u，比对显示只替换6行敏感默认值（包括旧JWT注释示例），变量名和结构保持不变；输出中的旧凭据行已隐藏，证据为 application-dev-redacted.diff。对全部12个例外文件独立读取原始Git对象，只归一化凭据值与批准占位符后比较全文，12项全部通过，结果保存在 allowed-differences.json。

| 文件 | 替换所在行（目标保留原行结构） |
| --- | --- |
| `Argus-backend/deploy/README.md` | 290 |
| `Argus-backend/deploy/env/server-a.env.example` | 4、11、12 |
| `Argus-backend/deploy/env/server-b.env.example` | 11、15、16 |
| `Argus-backend/deploy/server-a.compose.yml` | 9、46、47 |
| `Argus-backend/deploy/server-b.compose.yml` | 48、50、51 |
| `Argus-backend/src/main/java/com/argus/rag/auth/config/DevAdminInitializer.java` | 41 |
| `Argus-backend/src/main/resources/application-dev.yml` | 24、89、90、96、101、108 |
| `README.md` | 282、283、323、324 |
| `README_EN.md` | 283、284、325、326 |
| `docs/V1.0-项目文档.md` | 501、875 |
| `docs/V2.0-项目文档.md` | 763、764、837、838 |
| `docs/启动流程与配置加载说明.md` | 119、124、163、164、176 |

典型结构保持为 `${DB_PASSWORD:set-real-password-via-env}` 或 Docker Compose 的 `${POSTGRES_PASSWORD:-set-real-password-via-env}`；只替换默认值，保留变量名和注入语法。JWT 使用明显占位符，运行前注入足够长度的随机签名密钥，不得沿用默认值。

| 环境变量 | 用途 / 注入要求 |
| --- | --- |
| DB_HOST、DB_PORT、DB_NAME、DB_USERNAME、DB_PASSWORD | PostgreSQL 连接；密码必须外部注入；运行前显式指定自己的服务器，不直接沿用参考 IP |
| MINIO_ENDPOINT、MINIO_ACCESS_KEY、MINIO_SECRET_KEY、MINIO_BUCKET | MinIO 端点、身份/密钥与桶；使用自己的实例 |
| ARGUS_RAG_JWT_SECRET | JWT 签名密钥，至少满足代码长度要求，使用随机强密钥 |
| ARGUS_RAG_DEV_ADMIN_USERNAME、ARGUS_RAG_DEV_ADMIN_EMAIL、ARGUS_RAG_DEV_ADMIN_DISPLAY_NAME、ARGUS_RAG_DEV_ADMIN_PASSWORD、ARGUS_RAG_DEV_ADMIN_USER_CODE | dev 初始化管理员的账户信息；密码外部注入，勿将开发账户直接暴露公网 |
| ZHIPUAI_API_KEY | 当前智谱聊天/嵌入访问凭据；未在本次真实调用 |
| DASHSCOPE_API_KEY | 源依赖与配置保留的 DashScope 配置；当前默认仅为启动占位，不是有效 Key |
| ES_HOST、ES_PORT、ES_SCHEME | Elasticsearch 连接地址 |
| YOUCOM_API_KEY | 可选联网搜索凭据；当前配置 enabled=false，提供 Key 不等于启用功能 |
| SPRING_PROFILES_ACTIVE | 选择运行 profile；HEAD 默认为 dev，部署前核对具体配置 |
| VITE_DEV_PROXY_TARGET、VITE_API_BASE_URL | 开发代理目标与前端 API 地址；Vite 前缀变量会进入客户端，不能存放密钥 |

Docker Compose 环境示例还包含以下变量，必须区分与直接运行 Spring Boot 的 DB_* / MINIO_* 名称，不能假定它们自动等价：

`ARGUS_RAG_JWT_SECRET`、`BACKEND_PORT`、`DASHSCOPE_API_KEY`、`ELASTICSEARCH_HOST`、`ELASTICSEARCH_PORT`、`ELASTICSEARCH_SCHEME`、`ELASTICVUE_PORT`、`ES_JAVA_OPTS`、`FRONTEND_PORT`、`MINIO_API_PORT`、`MINIO_BUCKET`、`MINIO_CONSOLE_PORT`、`MINIO_HOST`、`MINIO_INTERNAL_PORT`、`MINIO_ROOT_PASSWORD`、`MINIO_ROOT_USER`、`OLLAMA_EMBEDDING_MODEL`、`OLLAMA_PORT`、`POSTGRES_DB`、`POSTGRES_HOST`、`POSTGRES_INTERNAL_PORT`、`POSTGRES_PASSWORD`、`POSTGRES_PORT`、`POSTGRES_USER`、`SPRING_PROFILES_ACTIVE`、`VITE_API_BASE_URL`、`VITE_DEV_PROXY_TARGET`。

历史部署模板与 HEAD 的模型/端口配置可能不同时演进；本次没有修改它们来“修通部署”。请在实际部署阶段核对端口映射、健康检查、服务依赖和环境变量覆盖关系。

## 最终验收与限制

| 检查项 | 实测结果 |
| --- | --- |
| 固定 HEAD 参考文件 | 355 个 |
| 未脱敏文件字节一致 | 343 个；未授权差异 0 个，额外文件（排除本记录和构建目录）0 个 |
| 脱敏例外 | 12 个文件；逐行核对替换，不作为源码差异隐瞒 |
| AGENTS.md | 与参考 Git blob 完全一致 |
| 最终后端 clean compile | 通过 |
| 最终 npm run build | 未通过 / 未执行 |
| 凭据复查 | 已知真实运行配置凭据与常见长 Key/私钥模式命中 0 处；不将此有限扫描表述为全面安全审计 |
| 文档结构 | 覆盖 52 个阶段及全部提交编号，包含《Qoder 使用过程》；包含 59 个截图占位，每个阶段至少一个 |
| 自动化测试套件 | 本任务未执行 mvnw test；编译通过不代表测试套件通过 |
| 外部依赖与真实 AI | 未启动或连接 PostgreSQL、MinIO、Elasticsearch，也未调用实际模型 |
| Qoder Sites | 历史复现阶段尚未部署，本次仅准备本地 Demo；2026-11-26 起正式演示路线已确定为 Qoder Sites + `https://106.14.125.122/api`，发布根目录 `Argus-replica`、静态目录 `Argus-frontend/dist`、启用 SPA、更新现有站点、不新建站点 |

### 最终命令输出摘要

`mvnw.cmd clean compile`，退出码 0，21 秒：

```text
[INFO] BUILD SUCCESS
```

> 【截图占位：此处插入最终 backend 的真实命令输出，日志 final-backend.log】

`npm install --no-audit --no-fund`，退出码 0，36 秒：

```text
added 348 packages in 35s
```

> 【截图占位：此处插入最终 frontend-install 的真实命令输出，日志 final-frontend-install.log】

`npm run build`，退出码 1，7 秒：

```text
dist/assets/MetricsView-l9Dtt1jh.js                 11.95 kB │ gzip:   4.06 kB
dist/assets/HomeView-CB6DjXbj.js                    17.76 kB │ gzip:   5.83 kB
dist/assets/pinia-DypgyJoy.js                       21.23 kB │ gzip:   9.05 kB
dist/assets/DocumentListView-DMOQ0hvv.js            28.39 kB │ gzip:   9.01 kB
dist/assets/QaView-Crl1EGeW.js                      30.79 kB │ gzip:  10.29 kB
dist/assets/GroupsPage-C9iM49gy.js                  32.04 kB │ gzip:   9.47 kB
dist/assets/AssistantView-DlGVHS-S.js               33.50 kB │ gzip:  11.11 kB
dist/assets/http-DzGX-Mxf.js                        41.76 kB │ gzip:  16.24 kB
dist/assets/DocumentPreviewModal-DS_Uozrt.js        49.06 kB │ gzip:  15.50 kB
dist/assets/runtime-core.esm-bundler--k6fTV2a.js    64.18 kB │ gzip:  25.20 kB
dist/assets/index-ChFziP4c.js                      916.18 kB │ gzip: 292.39 kB
✓ built in 1.12s
src/views/groups/components/InvitationsTab.vue(22,10): error TS2532: Object is possibly 'undefined'.
src/views/groups/components/InvitationsTab.vue(22,10): error TS2532: Object is possibly 'undefined'.
src/views/groups/components/JoinedGroupsTab.vue(22,10): error TS2532: Object is possibly 'undefined'.
src/views/groups/components/JoinedGroupsTab.vue(22,10): error TS2532: Object is possibly 'undefined'.
src/views/groups/components/OwnedGroupsTab.vue(20,10): error TS2532: Object is possibly 'undefined'.
src/views/groups/components/OwnedGroupsTab.vue(20,10): error TS2532: Object is possibly 'undefined'.
```

> 【截图占位：此处插入最终 frontend-build 的真实命令输出，日志 final-frontend-build.log】

`mvnw.cmd clean compile`，退出码 0，19 秒：

```text
[INFO] BUILD SUCCESS
```

> 【截图占位：此处插入最终 backend-after-redaction 的真实命令输出，日志 final-backend-after-redaction.log】

### 前端构建产物

`dist/` 总计 **1806988 字节（1.72 MiB）**，包含 35 个文件；下列大小为磁盘原始字节，不等于 gzip 网络传输体积。

| 主要产物 | 字节 |
| --- | ---: |
| `assets/index-ChFziP4c.js` | 916185 |
| `assets/index-fXpkhbJb.css` | 359107 |
| `assets/runtime-core.esm-bundler--k6fTV2a.js` | 64182 |
| `assets/DocumentPreviewModal-DS_Uozrt.js` | 49064 |
| `assets/http-DzGX-Mxf.js` | 41767 |
| `assets/AssistantView-DlGVHS-S.js` | 33504 |
| `assets/AssistantView-lnTB_Upe.css` | 32998 |
| `assets/GroupsPage-C9iM49gy.js` | 32048 |
| `assets/QaView-DnwSAzR7.css` | 30958 |
| `assets/QaView-Crl1EGeW.js` | 30790 |

> 【截图占位：此处插入最终 dist/ 大小统计与主要产物列表截图】

### 浏览器核验

已启动目标目录的 Vite 开发服务器（127.0.0.1:15173，Vite 8.0.11），并在 Qoder 内置浏览器实际执行页面导航、按钮点击和表单提交。仅验证公开页面与未登录分支，不能代替完整前端构建和真实业务联调。代理通过本次进程环境变量指向隔离的本机端口 19991，未启动后端或连接参考公网服务。浏览器返回的视口宽高均为0，因此本次只确认DOM内容与交互结果，不声称已验证视觉布局、响应式适配或生成有效界面截图。

- 首页 /：标题为“数据洞察中心”，读取到主标题“让每一次提问都有据可查”及功能、流程等板块；点击“登录”成功打开含登录/注册切换的弹窗。
- 登录弹窗空表单提交：显示“请输入登录标识和密码”，未填写或提交任何真实凭据。
- 切换注册页签：显示用户名、邮箱、显示名称、密码字段；空表单提交显示“请填写必填字段”，未创建账户。
- 未登录访问 /app/groups：实际重定向到 /login，标题为“登录 - 数据洞察中心”；独立登录页空提交显示必填提示。
- 访问不存在的 /replica-check-not-found：展示404、页面未找到和返回首页入口。
- 控制台跨页面保留记录中有4条 Vue Router next() 弃用警告，本次所查 error/warn 列表无 error 项；警告属于原项目，未修改。
- 未验证成功登录、群组操作、文档上传/摄入、AI问答及管理员功能，也未连接PostgreSQL、MinIO、Elasticsearch或调用模型；这些路径仍需真实环境与授权凭据。
- DOM环境的视口宽高为0，首页检测到的横向溢出值不具备布局判断意义；视觉与移动端检查保持未验证。
- 完成检查后已停止本次启动的开发服务器任务；未发布到 Qoder Sites。

> 【截图占位：此处插入本次实际可见页面及校验提示截图；未验证的登录、上传、AI 问答不能用此图冒充通过】

## Qoder 使用过程

### 本次实际承担的工作

- 读取用户提供的复现规范，检查工作区不是空目录、发现远端认证问题并等待确认，将原仓库改为只读参考、另建目标目录。
- 核对真实 52 个提交、逐提交 stat 和关键 diff；并行研究各段历史，但文件同步严格串行按 git log --reverse 的顺序执行。
- 从 Git 对象逐文件读取并写入各历史快照，处理历史文件删除，不复制源工作区未提交内容和既有依赖。
- 实际选择 JDK 21，执行本记录列出的阶段编译/安装/打包；保留成功、失败和跳过的真实原因，不将历史提交消息当成本次运行证明。
- 发现额外默认密钥后暂停请示，经用户批准统一脱敏所有阶段的同类配置及文档引用。
- 最终重新安装前端依赖，依 POM 编译后端；核验原始 blob SHA256、脱敏例外、文件清单、产物体积和配置凭据。
- 按真实证据生成本记录及截图占位；浏览器工作的实际范围以上节为准。

### 人机协作与尚需人工完成的内容

人工提供参考仓库和约束，确认目标目录、版本、完整历史范围、团队叙述方式及扩展脱敏范围；Qoder负责机械复现、执行验证、证据整理。真实运行环境变量、账户/服务授权、最后的业务操作截图及比赛陈述由项目团队核对补充。本文不伪造 Qoder 未做过的从零开发、线上部署、测试套件通过或真实 AI 调用。

### 证据保存与后续维护

本机临时证据目录为 `C:/Users/Lenovo/AppData/Local/Temp/argus-replica-20261123`，其中 `history/` 保存脱敏 diff，`logs/` 保存构建输出，`manifest.json`、`build-results.json`、`verification.json` 保存快照/执行/验收索引。它们位于项目之外，不混入一比一复现的源码目录；临时目录可能被系统清理，需留存证据时应由团队另行归档脱敏版本。

后续实际修改请追加新的日期、改动范围、验证结果，不改写参考提交的历史事实；截图占位必须替换成对应本次或后续真实执行的输出，不能虚构。

### 阶段 53：feat(deploy): 新增生产配置并收紧跨域与秘密注入（2026-11-24）

- **阶段目标**：完成 StudyHack 11/24 的生产底座与最小安全整改，准备非 dev 配置、准确 Origin CORS、跨站 Refresh Cookie 默认策略和可复核的秘密/外部依赖记录；不访问服务器、不扩大上传或课程功能范围。
- **涉及文件与模块**：新增 `Argus-backend/src/main/resources/application-prod.yml`、`Argus-backend/deploy/application-prod.env.example`、`Argus-backend/src/test/java/com/argus/rag/auth/config/AuthProfileConfigurationTest.java`；修改 `application.yml`、`CorsConfiguration.java`、`CrossDomainConfigurationTest.java`；新增 G0 范围建议、秘密检查记录、外部前置缺口表和本次交接文档。
- **实际变化**：prod 的数据库、MinIO、Elasticsearch、JWT 与模型凭据全部从环境变量读取且无明文默认值；dev 默认 CORS 改为准确本地 Origin；CORS 改为最高优先级过滤器并拒绝通配 Origin，覆盖 JWT 直接返回的 401；prod 默认跨站 Cookie 为 `None + Secure`，后端 multipart 上限为 10MB。`DevAdminInitializer` 仍仅 `@Profile("dev")`，新增条件测试证明 prod 不注册。用户已选择“QA + ≤10MB 直传”为 G0 候选，但文档明确尚未冻结。
- **对象权限核查**：限定检查群组、文档、直传/分片会话、QA 和 Assistant 会话后，未发现可确认的 ID 替换越权，因此没有修改业务权限代码；普通 `USER` 与 ADMIN 的权限边界保持不变，双账号真实联调仍待后续证据。
- **秘密检查**：当前源码、配置、SQL、部署模板、前端源码与文档中未发现真实明文密码、模型 Key、JWT、私钥或 URL 内嵌凭据；命中项均为占位值或环境变量语法。外部账号是否轮换无法从仓库证明，本次只列建议，没有执行任何轮换。
- **问题与证据边界**：主配置仍默认激活 dev，生产命令必须显式指定 prod；控制器已带 `/api` 前缀，因此不能再设置同值 servlet context path。前端 256MB 提示、自动就绪缺失和 Nginx 110MB 模板未在本次修改，上传候选仍需 11/25 统一边界。真实 Sites Origin、服务器依赖、域名/证书、体验账号、秘密来源与模型额度仍缺或待确认。
- **代码依据**：`application-prod.yml`、`CorsConfiguration.java`、`AuthProfileConfigurationTest.java`、`CrossDomainConfigurationTest.java` 及本次新增四份交付文档；历史提交事实未改写。

**本次验证结果**

- 后端：使用本机 Corretto 21.0.13 执行 `./mvnw clean compile`，通过；AGENTS.md 原 JDK 路径在当前 Git Bash 中不存在，已在交接文档记录实际路径。
- 定向测试：`CrossDomainConfigurationTest` 5 项、`AuthProfileConfigurationTest` 2 项，共 7 项全部通过，覆盖准确 Origin、预检、异常 401 的 CORS、过滤器注册顺序、通配拒绝、Cookie 与 prod/dev 条件注册。
- 全量测试：`./mvnw test` 共 30 项，29 项通过；唯一失败为既有 `NamingConventionTest`，在历史 `docs/development-log.md` 检出旧项目标记，不是本次引入，本次未扩大范围修改历史内容。
- prod 冒烟：使用本地无效占位环境启动，日志确认仅 `prod`、Java 21、端口 10001，配置解析和 MinIO 客户端初始化完成；随后因 PostgreSQL 本地连接被拒绝而退出，未出现 dev 管理员初始化日志。外部依赖连不上属于本次预期，不宣称健康检查或真实业务通过。
- 前端：本次未修改前端，未运行 `npm run build`；`VITE_API_BASE_URL` 与 Sites 重新构建发布仍按计划留到 11/25—11/26。
- 外部验证：未登录服务器、未配置 DNS/证书、未调用真实模型、未轮换真实凭据、未做真实跨站 Cookie 或双账号越权验收；相关项保留在外部前置缺口表。

### 阶段 54：补做生产交付收尾并记录服务器直连证据（2026-11-24）

- **阶段目标**：补正待办状态，修复既有命名约定失败，生成后端 JAR，统一直传文件上限，并移除 prod/test 对 DashScope Key 的无关要求；不冻结 G0、不部署服务器、不操作外部账号。
- **涉及文件与模块**：`docs/StudyHack-待办清单.md`、`docs/development-log.md`、`docs/handoff-2026-11-24-生产配置与安全整改.md`、`docs/StudyHack-2026-11-24-外部前置缺口表.md`、`docs/StudyHack-2026-11-24-G0范围建议.md`、`docs/StudyHack-2026-11-24-秘密检查记录.md`、`Argus-frontend/src/views/documents/components/UploadDialog.vue`、`Argus-backend/deploy/nginx.conf`、`Argus-backend/deploy/server-b.compose.yml`、`Argus-backend/src/main/resources/application-prod.yml`、`Argus-backend/src/main/resources/application-test.yml`、`Argus-backend/deploy/application-prod.env.example`。
- **实际变化**：历史开发日志中的旧项目标识做最小脱敏，阶段事实和提交记录保留；前端文件校验/提示改为 10MB；Nginx 请求包络改为 12MB；prod/test 排除全部 DashScope 自动配置并移除生产必填 Key；待办、缺口、G0 和交接文档同步真实状态。
- **外部信息**：用户提供 Sites Origin `https://argus-insights-6ymqxlsdfpp.qoder.website/`、服务器公网 IP `106.14.125.122`、当前测试授权和临时全开防火墙；体验账号与模型调用由用户人工验收；域名/证书暂缓。
- **问题与证据边界**：直连确认 PostgreSQL 5432 TCP 可达、ES 9200 为 yellow、MinIO 9000/9001 正常；80、443、10001、18080 不可达，因此未做真实 CORS、Cookie、健康检查、上传或 QA 验收。G0 仍未冻结。

**本次验证结果**

- `./mvnw clean package -DskipTests`：通过；JAR 为 `Argus-backend/target/Argus-backend-0.0.1-SNAPSHOT.jar`，99MB，SHA-256 `78c4a983a7990d03ab195d64ce7db06d8c69155ce85c9e61df6991d0cef61577`。
- `./mvnw test`：30 项全部通过，`NamingConventionTest` 已通过。
- 前端 `npm run type-check`：通过；未运行 `npm run build`，未改 `VITE_API_BASE_URL`，未重建或发布 Sites。
- Java 21.0.13 prod 冒烟：显式移除 `DASHSCOPE_API_KEY` 后仍完成配置加载、端口 10001 和 MinIO 客户端初始化，随后因本地 PostgreSQL 拒绝连接而退出；未出现 dev 管理员初始化。
- 服务器仅做已知端口直连探测；未登录 SSH、未修改服务器/域名/证书/防火墙/数据库/MinIO/ES/模型账号。

### 阶段 55：增加 Windows 本机启动器并修复 Origin 尾斜杠匹配（2026-11-25）

- **阶段目标**：降低手动启动成本，明确区分启动必需值与上传/QA 测试必需值；不填写或外传真实秘密，不部署服务器，不冻结 G0。
- **触发证据**：用户直接运行 JAR 时未指定 profile，日志显示默认 `dev`；PostgreSQL 返回 `password authentication failed for user "postgres"`，随后启动恢复任务无法创建事务，进程退出。根因是运行环境和数据库认证，不是 Java 21 或 Tomcat 故障。
- **涉及文件与模块**：新增 `Argus-backend/deploy/local-run.env.example`、`Argus-backend/deploy/start-backend.ps1`；本机 `Argus-backend/deploy/local-run.env` 只含占位符并被根目录和后端 `.gitignore` 排除；修改 `CorsConfiguration.java`、`CrossDomainConfigurationTest.java`、`docs/StudyHack-待办清单.md`、`docs/handoff-2026-11-24-生产配置与安全整改.md`、`docs/CHANGELOG-handoff.md`、`docs/StudyHack-2026-11-24-秘密检查记录.md`。
- **实际变化**：启动器加载单一环境文件并固定使用 prod，避免 dev 管理员初始化；数据库密码为占位符时提前停止；MinIO 和智谱 Key 为占位符时输出警告但允许先启动；JWT 默认每次启动生成 48 字节随机密钥。CORS 配置去除 Origin 尾部斜杠，新增浏览器 Origin 匹配回归测试。
- **秘密边界**：示例和脚本不含真实值；本机 `local-run.env` 不得上传。用户只需在本地填写数据库密码，上传和 QA 测试前再补 MinIO 与智谱 Key；脚本不打印任何环境变量值。

**本次验证结果**

- PowerShell `-ValidateOnly`：占位数据库密码返回明确错误；使用临时完整占位配置校验通过，并正确警告 MinIO 与模型测试限制；临时文件已删除。
- `./mvnw clean package`：31 项测试全部通过，`CrossDomainConfigurationTest` 为 6 项，`NamingConventionTest` 通过。
- 新 JAR：`Argus-backend/target/Argus-backend-0.0.1-SNAPSHOT.jar`，102,937,021 字节，SHA-256 `df38e9d6842aa38daccbac24cdc0544a0540c919799dc62ad0a1bf6e0755465d`。
- 未使用真实数据库密码、MinIO 凭据或模型 Key 启动；真实登录、上传、QA、跨站 Cookie 和公网入口仍未验收。

> **2026-11-26 路线更正**：阶段 56—57 的域名、DNS、DNS-01/HTTP-01 证书和服务器同源前端方案仅保留为历史事实，已被 Qoder Sites + `https://106.14.125.122/api` 取代。当前发布根目录为 `Argus-replica`，静态目录为 `Argus-frontend/dist`，启用 SPA，更新现有站点，不新建站点；根目录 `.Argus 数据洞察中心.qoder.site` 为准，`Argus-frontend` 内描述文件为旧快照，两个描述文件均不手改或删除。

### 阶段 56：拆分域名、DNS 与证书协作并明确服务器边界（2026-11-25）

- **阶段目标**：由外网同学协助域名、DNS 和 HTTPS 证书，服务器仍由用户本人操作；交接必须能直接执行，但不包含服务器权限或真实秘密。
- **触发信息**：用户明确服务器在自己手上，不交给同学；随后确认证书也交给同学研究，并要求给用户一份傻瓜教程。
- **涉及文件与模块**：新增 `docs/handoff-2026-11-25-域名DNS与证书协作.md`；同步 `docs/StudyHack-待办清单.md`、`docs/学习清单（发布前删）.md`、`docs/handoff-2026-11-24-生产配置与安全整改.md`、`docs/CHANGELOG-handoff.md`、`docs/StudyHack-2026-11-24-外部前置缺口表.md`、`docs/https-setup.md` 和本文。
- **实际变化**：外网同学只负责域名注册/选型、根域名 A 记录、DNS-01 证书研究/签发、证书安全交付和傻瓜教程；SSH、Nginx、JAR、systemd、防火墙、数据库、ES、MinIO 与模型配置全部保留给用户。证书首选 DNS-01，在同学自己的电脑上完成，不要求服务器开放 80/443；HTTP-01 仅作为服务器 80 就绪后的备选。
- **交付约束**：根域名 A 记录指向 `106.14.125.122`；证书交付 `fullchain.pem`、`privkey.pem`、SHA-256、有效期和续期负责人；私钥不得进入聊天、公开网盘或 Git 仓库；教程必须替换全部占位值并包含预期输出与失败处理。
- **官方依据**：阅读 Certbot User Guide 的 DNS Plugins、`certonly`、续期与证书文件说明，以及 Let’s Encrypt Challenge Types。前者确认 DNS 插件可在非目标 Web 服务器机器上签发；后者确认 DNS-01 不要求公网 Web 服务器，但自动化依赖 DNS API。
- **证据边界**：本次只新增和同步文档；未注册域名、未修改 DNS、未签发证书、未生成私钥、未登录服务器，也未执行任何外部基础设施变更。G0 仍未冻结。

**本次验证结果**

- 文档交叉核对：服务器相关任务负责人已从外网同学/组员改为用户；域名/DNS/证书保留为外网协作；`https-setup.md` 已从 DuckDNS 专用改为通用域名，并区分 DNS-01 与 HTTP-01。
- 安全核对：新增文档只含固定公网 IP、变量名、占位命令和官方文档链接，不含真实密码、Token、证书或私钥。
- 代码与制品：本次未改代码，未重新运行 Maven、前端构建或业务测试；既有 31 项后端测试与 JAR 校验值保持不变。

### 阶段 57：把证书交接扩展为完整公网入口傻瓜教程（2026-11-25）

- **阶段目标**：回答“同学交付后，用户照教程做完能否启用公网 HTTP/HTTPS 入口”；补齐证书之外的 Nginx、防火墙、前端、后端和验收步骤，避免只拿到证书却仍无法开站。
- **触发信息**：用户询问原版教程是否足够。原版只覆盖证书，不能独立启用公网入口，因此新增完整教程模板。
- **涉及文件与模块**：新增 `docs/公网入口傻瓜教程-待同学填写.md`；更新 `docs/handoff-2026-11-25-域名DNS与证书协作.md`、`docs/StudyHack-待办清单.md`、`docs/学习清单（发布前删）.md`、`docs/handoff-2026-11-24-生产配置与安全整改.md`、`docs/CHANGELOG-handoff.md`、`docs/StudyHack-2026-11-24-外部前置缺口表.md`、`docs/https-setup.md` 和本文。
- **实际变化**：同学必须返回填写完整的公网入口教程，覆盖 DNS 验证、证书落地、前端 `VITE_API_BASE_URL=/api` 构建、Nginx 80/443 与 `/api/` 反代、SSE 关闭缓冲、12MB 请求包络、云安全组/主机防火墙、后端 prod 服务与 `127.0.0.1:10001` 回环绑定、命令行/浏览器验收和回退。
- **职责边界**：同学只填写教程和交付域名/证书，不执行服务器命令；用户本人准备真实环境变量、上传制品、修改 Nginx/防火墙、启动服务并验收。
- **证据边界**：模板中的系统、路径、防火墙工具和服务账号均保留占位值，必须由同学按用户提供的非秘密服务器信息填写；未填写前不得执行。本次仍未登录服务器或修改外部基础设施。

**本次验证结果**

- 文档交叉核对：交接文档明确“只返回证书教程不算完成”，并链接完整公网入口模板；待办、学习清单、缺口表、HTTPS 清单和变更记录同步引用。
- 安全核对：模板不含真实密码、Token 或私钥；后端秘密只通过仓库外环境文件注入，证书私钥要求 `600` 权限且不进入 Git。
- 配置核对：公网后端命令显式使用 `prod` 和 `--server.address=127.0.0.1`；Nginx 仅代理 `/api/` 到回环 10001，首页使用 SPA `try_files`，上传包络为 12MB。
- 代码与制品：未改代码，未重新构建或运行测试；既有 JAR 校验值保持不变。

### 阶段 58：移植“我的大学”学生工作台并保留现有安全边界（2026-11-26）

- **阶段目标**：将用户提供的 My-College 参考项目中新增的“我的大学”学习与实践功能作为真实开发任务接入当前 Argus，而不是整目录覆盖；保留现有认证、群组、文档、QA、助手和部署配置的行为。
- **参考核对**：参考项目 README 明确基于固定 Argus 提交增量开发。排除依赖和构建产物后，当前项目 383 个文件、参考项目 363 个文件，其中 313 个同路径文件内容一致，28 个同路径文件有变化，22 个仅存在于参考项目；220 个同路径 Java 文件中 217 个一致，`pom.xml` 与 `package.json` 一致。两份本地目录均无 `.git`，因此结论是“增量功能移植”，不虚构远端分支历史。
- **后端新增**：新增 `college` 包的 `Workspace`、`WorkspaceMapper`、`WorkspaceController`、`TaskController`、`NoticeController`，以及 `WorkspaceControllerTest` 和 `sql/college.sql`。工作台按登录用户 ID 保存 JSON 状态，`revision` 条件更新防止多设备互相覆盖，冲突返回 409；练习/阅读接口复用现有智谱模型并约束结构化输出；通知接口读取固定公开来源、做 HTML 解码、超时和缓存处理。
- **前端新增与接入**：新增 `Landing.vue`、`CollegeView.vue`、`Experiment.vue`、`models.ts` 和校园图片；接入首页、`/app/college/:module?` 路由、默认登录落地页、侧边栏和移动端宽度修复。模块覆盖学习助手、专业实践、校园机会、我的项目、升学就业、校园办事；复用既有 QA/助手 SSE 客户端。RC 确定性例题与参数变更在前端计算，学习引用只接受原文逐字存在的材料摘录。
- **保留决策**：不覆盖 `App.vue`、`api/http.ts`、`api/qa.ts`、`api/assistant.ts`、`vite.config.ts`、各环境配置、CORS 和 dev 管理员初始化。未引入参考项目的明文开发管理员密码或通配 CORS 默认值，未复制静态演示与外部交接目录。
- **数据一致性修正**：将 `sql/schema.sql` 的 pgvector 维度从 512 修正为 1024，与当前模型配置一致。该变更只影响新建表；已存在的 512 维表仍需单独迁移或重建向量索引，不能由 `CREATE TABLE IF NOT EXISTS` 自动完成。
- **验收脚本**：新增 `tests/browser_workspace.py`、`tests/live_workspace.py`、`tests/live_course.py`，分别覆盖模拟浏览器主流程、真实工作台隔离/并发和真实课程链路；本次仅完成三份脚本的 Python 语法编译。

**本次验证结果**

- 后端：使用本机 Corretto 21.0.13 执行 `./mvnw clean package`，编译 216 个主源码、11 个测试源码；共 33 项测试全部通过，`WorkspaceControllerTest` 2 项覆盖登录所有者隔离和过期 revision 防覆盖。
- 制品：`Argus-backend/target/Argus-backend-0.0.1-SNAPSHOT.jar`，102,951,711 字节，SHA-256 `c7754c533e9badb06b8f8864269baedd1e51d673598faf27dcff770457db47eb`。
- 前端：`npm run type-check` 和生产 `npm run build` 均通过；保留严格 `VITE_API_BASE_URL` 校验、SSE 代理关闭缓冲及后端不可用提示。
- 模拟浏览器：使用临时 Mock API + 本机 Vite + Microsoft Edge 验证公开首页、登录弹窗、`/app/college/practice`；RC 示例得到 `[10,100,5]`、`τ = 1 s`、`3.161 V`，“把电阻翻倍”得到 `[20,100,5]`、`τ = 2 s` 并保留前一条曲线；快照保存、刷新后恢复、学习模块原文引用展开均通过。仅观察到既有 Vue Router `next()` 弃用警告。临时服务与 Mock 文件已停止/清理。
- 真实环境边界：未执行 `sql/college.sql`，未连接真实 PostgreSQL/MinIO/Elasticsearch/模型，未验证清华通知源可达性、真实多设备并发或完整上传到 AI 课程链路。Python Playwright 未安装，因此 `browser_workspace.py` 未直接运行，上述浏览器结果来自 browser-use 人工流程；这些项目仍须在配置真实依赖后验收。

### 阶段 59：更正正式演示部署路线并归档旧域名方案（2026-11-26）

- **触发信息**：用户明确当前正式演示路线为 Qoder Sites 前端 + `https://106.14.125.122/api`，不再使用 DuckDNS 域名直连方案；随后更正本次只修改 `docs` 下错误或过时文件。
- **实际变化**：`https-setup.md` 与公网入口教程改为现行 Sites/IP API 流程；发布根目录固定为 `Argus-replica`，静态目录为 `Argus-frontend/dist`，启用 SPA，更新现有站点，不新建站点。根目录 `.Argus 数据洞察中心.qoder.site` 为当前依据，`Argus-frontend` 内描述文件为旧快照，两份描述文件均保留且未手改。
- **归档处理**：域名/DNS/DNS-01/HTTP-01 交接文件改为历史归档；计划、待办、外部缺口、生产交接、学习清单、学习笔记和变更记录补充现行路线或历史取代说明，开发日志保留阶段 56—57 原事实并增加更正提示。
- **构建与制品**：文档固定 `VITE_API_BASE_URL=https://106.14.125.122/api` 由被 Git 忽略的 `.env.production.local` 提供；同步更新当前教程中的 JAR 校验值为打包后重命名的 `Argus-backend/target/Argus-backend-0.0.1-SNAPSHOT1126.jar`、102,951,711 字节、SHA-256 `c7754c533e9badb06b8f8864269baedd1e51d673598faf27dcff770457db47eb`。阶段 58 的原始打包文件名保留为当时事实。
- **静态复核**：实际 JAR 尺寸与 SHA-256 和文档一致；`Argus-frontend/dist` 构建时间为 2026-11-26 19:46，`http-Bw8X26vZ.js` 与 `index-zQ4CvKqB.js` 均含固定 IP API。本次未重新构建或发布，Sites 远端产物和服务器联通仍未验收。
- **文档质量复核**：本地文件链接均存在；32 个源码行号链接均指向有效且非空行，待办清单中过期行号已校正；表格列数检查通过。敏感信息扫描只命中显式占位符或变量名，没有新增真实密码、Token、私钥或模型凭据。
- **范围边界**：本次只修改 `docs`，未改 README、环境文件、两个 `.qoder.site`、源码或部署配置；未新建站点、未发布、未登录服务器、未修改 DNS/证书/防火墙，也未执行任何付费操作。
- **上传边界**：文档明确不得上传 `.env`、`deploy/local-run.env`、`target`、`node_modules` 或任何密码、Token、私钥及数据库/模型凭据。

**本次验证结果**

- 发布依据核对：根目录描述文件的 deployment/release 标识新于前端目录旧快照；未修改任一描述文件。
- Git 忽略规则核对：根 `.gitignore` 覆盖 `.env.*.local` 和 `deploy/local-run.env`，前端 `.gitignore` 覆盖 `*.local`、`dist` 与 `node_modules`，根规则同时忽略 `target`。
- 文档交叉核对：现行操作文档不再要求 DuckDNS、域名 A 记录或 DNS-01/HTTP-01 作为前置；历史材料均明确标注已被现行路线取代。
- 代码与运行环境：本次未运行构建、测试或公网验收；Sites、IP API、后端和真实跨站 Cookie 仍需按更新后的清单复验。

### 阶段 60：恢复旧版登录前首页并完成蓝白登录后 UI 与引导优化（2026-11-26）

- **触发信息**：用户要求先按旧项目和参考截图恢复登录前首页，只替换新增或调整功能的按钮；随后按两份 UI/引导文档优化登录后页面，并明确沿用原本蓝白色，不使用当前褐色。本轮继续遵守“保留路由 URL、后端 API、鉴权、SSE、数值模型和核心业务交互”与“不夸大真实能力”的约束。
- **登录前恢复**：`router/index.ts` 的 `/` 恢复指向 `HomeView.vue`，登录后路由、守卫和 URL 不变；新版 `CampusLanding.vue` 保留但不再作为根路由组件。首页布局与参考图核对，仅将“免费试用/进入工作台/查看演示”等入口映射为“注册账号/进入我的大学”。
- **登录弹窗**：`LoginModal.vue` 增加可选 `initialMode`，首页导航、主按钮和底部 CTA 可分别打开登录或注册模式；原有表单清理、认证请求和成功跳转逻辑不变。
- **能力文案纠偏**：删除首页“支持断点续传”的过期描述。当前前端分片上传 composable 已禁用，首页改为只描述受支持文档上传后的解析、切片和索引，不宣称未启用能力。
- **设计系统**：`assets/main.css` 保留 `#4A90D9`、`#3A7BC8`、`#5CC9C1` 等原蓝白基础，新增页面背景、READY/处理中/警告/错误状态和键盘焦点样式；学生工作台、实验、文档、小组、QA、助手、设置与管理员页面统一使用蓝白卡片和语义状态，不引入褐色体系。
- **引导架构**：新增 `constants/moduleGuides.ts`、`ModuleGuideHeader.vue`、`ModuleGuidePopover.vue`、`EmptyExampleCards.vue`。引导内容按模块集中维护，页面统一提供一句话定位、可展开步骤/示例/能力边界、空状态示例和关键状态提示；示例只填充并聚焦输入框，不自动发送。
- **真实能力边界**：短材料模式明确为原文句子核对；课程知识库只检索所选小组内 READY 文档；RC 明确为理想直流阶跃模型；悬臂梁只描述已实现的静载模型；校园机会注明内置来源范围；综合发展评分说明需要用户学校认定目录；“需核实”表示原通知未写明。
- **登录后布局**：`DefaultLayout.vue` 增加按路由/模块生成的标题、学习与实践/校园事务/成长沉淀/资源与协作/账号与系统分组导航和“我的经历”入口。顶栏在大学工作台内通过 `argus:open-context-assistant` 打开上下文助手，在其他页面进入独立助手，避免混淆两种入口。
- **窄屏侧栏**：768px 以下展开侧栏增加遮罩关闭，选择导航项或点击品牌后自动收起；修复展开侧栏遮住关闭按钮、用户无法返回内容区的问题。
- **学生工作台**：`CollegeView.vue` 重构首页、模块页、消息块、引用、机会来源、项目归档、就业轨道、快照和上下文助手展示；保留 `/college/workspace` GET/PUT、`revision`、409 冲突、防抖保存、阅读/练习接口、QA/助手 SSE、通知和项目到经历流程。首次概览支持“不再提示”并写入本地存储。
- **实验界面**：`Experiment.vue` 更名为 `ExperimentWorkspace.vue` 以满足 Vue 多单词组件命名；保留 RC/梁计算和 101 点曲线，新增参数卡、当前/前次曲线、时间/位置滑块、电压/挠度、时间常数/末端挠度、图例和快照提示。
- **业务页面**：文档页补充状态统计、状态解释和 READY 文档“去问答”；小组页补充单人建组说明、统计和空状态；设置与管理员页补充统一引导；QA/助手页补充范围说明、示例、消息排版和可展开引用。原有上传、预览、下载、删除、重试、成员管理、邀请、审批、改密、历史记录和模式切换逻辑均保留。
- **静态检查清理**：清理分片上传禁用后的未使用参数、旧注释和阈值，移除多余导入/变量，将 `CollegeView.vue` 页面状态同步移出 computed 副作用；未借清理重构后端或改变业务协议。
- **文档同步**：本阶段追加到开发日志，并在学习清单新增“登录前恢复、引导系统、蓝白设计、上下文助手、响应式侧栏与真实能力边界”学习条目。

**本次验证结果**

- `npm run lint`：oxlint 与 ESLint 均通过，0 warnings、0 errors。
- `npm run type-check`：通过；`npm run build`：通过，生产文件输出到 `Argus-frontend/dist`。Vite 仍提示主 chunk 约 917 kB 超过 500 kB，为非阻断警告，本轮未做打包拆分。
- 公开页面：核对旧版参考图，注册/登录模式切换、恢复后的“进入我的大学”入口和真实能力文案正常。
- 模拟登录流程：使用浏览器内临时 Mock API 验证学生工作台首页、首次概览、使用说明、示例填充、上下文助手、RC 曲线/参数/滑块/快照、文档状态与“去问答”、QA/助手示例和模式切换、小组与设置页；临时 Mock 未写入仓库。
- 窄屏验证：浏览器视口宽度 632px，学生工作台、文档、小组、QA、助手、设置六个页面均无页面级横向溢出；展开侧栏的遮罩关闭与选择导航后自动收起通过。
- 保留边界：未改后端 API、鉴权、SSE 客户端和数值计算模型；未启动真实 PostgreSQL/MinIO/ES/模型，未执行真实登录、上传、SSE 端到端、多设备并发、管理员业务或公网发布，因此这些仍属于后续真实联调项。
- 外部副作用：未修改参考项目 `C:\Users\Lenovo\IdeaProjects\GitHub\Argus`，未部署、未购买基础设施、未处理真实凭据。

### 阶段 61：简化登录、增加体验用户并保留旧版登录前入口（2026-11-26）

- **阶段目标**：让同学无需注册即可用普通用户名进入演示，同时保留原账号密码登录和管理员保护；不修改数据库结构、不增加依赖，也不把免密普通用户当作生产级身份认证。
- **触发与回退**：先按 `docs/qoder-prompt-login-simplification.md` 评估，再按用户“开始改”指令实施。首页曾尝试改成“体验用户 / 管理员登录”双模式，用户明确指出“并非双模式”，要求先退回原登录界面，只额外增加“体验用户”按钮；随后删除双模式切换，恢复原登录表单并在其下方增加体验入口。
- **后端认证**：`AuthService.java:62-84,136-180,194-246` 将密码改为可选；未知合法用户名在空密码时创建或复用 `USER`，用户名仅允许字母、数字、下划线和短横线，保留名直接要求密码。创建账户写入 `{no-password}` 标记、合成邮箱和 ACTIVE 状态，`UserMapper.xml:15-24` 用 `on conflict do nothing` 处理并发重名，随后重新查询持久化结果。所有 ADMIN 空密码登录均拒绝，禁用账号仍拒绝登录；已有 USER 空密码也可进入，这是本次演示策略的明确边界。
- **令牌与接口**：登录成功仍走原 JWT Access Token + HttpOnly Refresh Cookie；`AuthController.java` 删除 `/register`，保留 login/refresh/logout/me。`PasswordHasher.java:8-14` 统一判断密码是否启用，当前用户响应新增 `passwordEnabled`，避免把 `{no-password}` 当 BCrypt 哈希校验。
- **前端交互**：`LoginModal.vue:36-111,158-179` 和 `LoginView.vue:27-81,138-159` 保留登录表单，新增体验用户名表单；体验入口收到“该账号需要密码登录”时回填用户名并切回密码表单。`HomeView.vue:14-24,97-128` 延续阶段 60 的旧版首页，只把入口映射为“登录 / 体验用户”；登录后仍直达“我的大学”。`SettingsView.vue:11-26,90-93` 对无密码账户隐藏改密表单，侧栏也只对有密码账户显示系统设置。
- **问题与修复**：AGENTS.md 中的 JDK 路径在当前 Git Bash 不存在，改用本机 Corretto 21.0.13 编译；首次前端构建暴露 `password?` 的可选类型问题，修正空值判断后通过。现有 33 项测试不包含新增免密登录分支的专门测试，因此不能把“测试总数未变”写成新认证逻辑已被完整覆盖。
- **制品与发布**：`./mvnw package` 通过，33 项测试全部通过；当前工作区制品为 `Argus-backend/target/Argus-backend-0.0.1-SNAPSHOT1127.jar`，102,949,322 字节，SHA-256 为 `e1553cb70baa79510de2e9b5538a916a3553059e4c8c0dc6225bf9ec5c1f5bf6`（11/27 静态复核一致）。前端重新类型检查、构建并更新现有 Qoder Site；线上首页返回 200，“体验用户”入口可见。发布的是登录简化版本，不等于真实免密登录、管理员登录和跨站 Cookie 已完成端到端验收。

**本次验证结果**

- 后端：Corretto 21.0.13 执行 `./mvnw clean compile` 和 `./mvnw package` 均通过；33 项既有测试全部通过。
- 前端：`npm run build` 及后续 `npm run type-check && npm run build` 通过；Vite 主 chunk 超过 500 kB 的既有警告仍存在。
- 浏览器与发布：本地 prod 启动后 `/api/auth/me` 返回预期 401 JSON；线上首页和体验入口可见。未使用真实管理员凭据，未验证完整登录后业务链路。
- 安全边界：免密入口按用户名识别普通 USER，无法证明真实身份，存在冒用他人普通用户名的风险；生产公开前必须重新评估认证、限流和账号策略。未改 schema、未引入新依赖、未提交环境文件或秘密。

### 阶段 62：核对上传能力并恢复 256 MB 分片链路（2026-11-27）

- **触发信息**：用户先询问知识库检索是否由 AI 逐文件阅读，再要求加入图片上传、PDF 和拖拽，随后要求扩大文件上限并恢复分片；同时明确复杂、无把握或耗时功能应先说明并留给 Codex。
- **检索解释**：文档上传后已经解析、切片并写入 pgvector 与 Elasticsearch；提问时先做查询规划和双路检索，再融合证据交给模型，不是模型在提问时逐个打开全部文件。
- **能力核对**：PDF 上传和解析已经存在，`PdfDocumentParser.java:57-63` 用 PDFBox 提取文本层；拖拽选择也已存在于 `UploadDialog.vue:188-210`。本轮没有重复添加这两项能力。扫描版/纯图片 PDF 没有 OCR 回退，图片格式也没有解析器，`DocumentParserFactory.java:48-54,66-72` 当前只注册 TXT、MD、PDF、DOCX。
- **图片边界**：直接允许上传 PNG/JPG 会出现“文件收下了但无法解析和检索”的假成功，因此没有加入。用户提出“不解析图片，提问时再识别”后，进一步区分为：显式指定图片可从 MinIO 取回并调用视觉模型，较可行；自动判断问题与哪张图片相关，需要 OCR、描述文本、元数据索引或扫描大量候选图，成本和延迟不可控。图片上传、视觉索引与按需取图均留给 Codex。
- **前端恢复**：`UploadDialog.vue:26-28,111-147` 将上限改为 256 MB，≤10 MB 继续直传，>10 MB 自动分片。`useChunkedUpload.ts:5-136` 使用 5 MB 分片、Web Crypto SHA-256、init/chunk/complete 三阶段、服务端已传分片跳过、顺序上传、AbortController 取消和阶段进度；秒传直接返回文档 ID。完成后将 composable 进度同步回弹窗，修复成功时可能仍显示 0% 的问题。
- **后端复用**：`DocumentUploadService.java:63-82,137-179,193-255,276-335` 原本已支持 256 MB 总量、10 MB 单分片、哈希秒传、会话续传、分片幂等和服务端合并，无需改后端。前端选择 5 MB 是为了留在后端 10 MB 分片和 Nginx 12 MB 请求包络之内；`document.ts:236-246,294-311,350-357` 将直传、分片和合并超时设为 120 秒。
- **取舍与不足**：当前哈希会把整个文件或分片读入 ArrayBuffer，256 MB 文件的内存占用仍需真机观察；取消只中止浏览器请求，不保证删除服务端已收分片；刷新后需重新选择同一文件才能通过哈希恢复会话。合并失败后的对象、元数据和会话状态一致性，以及过期会话清理仍待加固。

**本次验证结果**

- `npm run type-check && npm run build`：通过；主 chunk 大小警告为非阻断项。
- 本地预览：上传弹窗和 256 MB 提示可见；未启动本地后端，未完成真实直传、5 MB 分片、秒传、续传、取消、合并或 256 MB 文件验收。
- 代码范围：只改前端上传编排、界面和请求超时；未改数据库、MinIO、Nginx、后端接口或依赖。
- 发布边界：生产包已在本机更新，但本轮上传改动尚未重新发布到 Sites。

### 阶段 63：增加可选深浅主题并降低公共入口干扰（2026-11-27）

- **阶段目标**：在不改页面结构和业务协议的前提下提供 light/dark 切换，优先统一核心页面，不承诺一次覆盖所有第三方组件和硬编码颜色。
- **共享状态**：新增 `useTheme.ts:3-48`，用模块级 `ref` 保存唯一主题状态，首次调用时读取 `argus-theme`，没有有效选择则跟随 `prefers-color-scheme`；切换后同步 `documentElement.dataset.theme` 和 `html.dark`，并写入 localStorage。`App.vue:2-7` 在路由视图渲染前初始化主题。
- **样式接入**：`main.ts:3-4` 引入 Element Plus 官方深色 CSS 变量；`assets/main.css:7,85-117` 增加 `color-scheme` 和整套深色 token，核心页面改用变量表达背景、文字、边框和状态色。
- **公共入口**：`ThemeToggle.vue:8-23` 提供图标按钮、动态 aria-label 和 `aria-pressed`；首页导航、独立登录页右上角、登录后顶栏均可切换。用户反馈“按钮位置别太突兀”，因此保留公共位置，但将按钮改为透明边框、低不透明度、悬停才显示浅底色，而不是移入设置或增加醒目色块。
- **验证与缺口**：类型检查、生产构建通过；浏览器验证浅色/深色切换、刷新后主题和按钮状态恢复，并检查独立登录页入口。上传弹窗、问答、助手、文档预览、图表等仍可能有硬编码颜色；尚未实时跟随系统主题变化，也未同步浏览器 `theme-color`，这些列入 Codex 待办。
- **发布边界**：本机生产包已更新，主题改动尚未重新发布到 Sites。

### 阶段 64：按反馈回退侧栏并实现上下文大学子菜单（2026-11-27）

- **第一次质疑：删除范围过大**：阶段 60 后侧栏信息被过度精简，用户指出“我原来不就只让你删中间那 7 个嘛”，组员也认为删除后不便切换模块。本轮恢复“文档管理、知识库问答、AI 助手、协作小组”四个业务入口，与“我的大学”组成五个 USER 主入口；ADMIN 底部保留“用户管理、使用统计、系统设置”。七个大学子模块不永久平铺在侧栏，避免重复占位。
- **激活状态**：`DefaultLayout.vue:59-81,145-186` 按路由前缀恢复各业务入口高亮，大学入口覆盖 `/app/college/*`，当前大学子模块由 `route.params.module` 计算。
- **第二次方案：上下文展开**：用户建议“鼠标悬停到我的大学上显示子模块”，并要求更高级但不冗余。最终在“我的大学”内增加七项子菜单、序号、当前项高亮、展开箭头和过渡；进入大学子路由时展开，离开大学时关闭，折叠侧栏点击大学入口时先展开。
- **第二次质疑：桌面点击固定挡视线**：初版让桌面点击箭头固定展开，用户明确反对“为啥固定在那挡视线”。回退为桌面悬停展开、鼠标移出立即收起，点击箭头只临时切换，不再固定；`closeCollegeMenu` 在 `(hover: hover) and (pointer: fine)` 设备上总是关闭。触屏设备没有可靠悬停，因此仍保留点击展开/固定，`collegeMenuPinned` 只用于触屏分支。
- **可访问性**：箭头按钮提供 `aria-label`、`aria-expanded`、`aria-controls`，子菜单提供 `aria-label`，当前子模块同时显示文字高亮和状态点，不只依赖颜色。
- **未实施项**：前期建议中的 `Ctrl+K` 快速切换没有实现，本阶段不宣称支持；桌面固定交互已明确删除，没有保留兼容开关。

**本次验证结果**

- `npm run type-check && npm run build`：在恢复四个入口、增加子菜单、回退桌面固定后分别通过；仍有非阻断的主 chunk 警告。
- 浏览器：验证悬停显示七项、鼠标移出收起、桌面点击不固定、模拟触屏点击后保持展开、子模块跳转与高亮、离开大学关闭、折叠侧栏展开，以及 ADMIN 底部入口。
- 测试环境边界：隐藏浏览器视口曾报告宽高为 0，导致侧栏初始折叠和过渡动画异常；通过顶栏主动展开及轮询断言完成验证。没有本地后端，浏览器测试注入临时 USER 并使用路由，大学页面真实 API 仍显示读取失败，因此结果只证明布局和导航交互。
- 外部副作用：临时预览服务和浏览器页面已关闭；未发布本轮上传、主题和侧栏改动，未修改服务器、DNS、证书或任何真实凭据。
