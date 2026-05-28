# `system` realm —— 平台运维

[English](system-realm.md) · **中文**

本文档介绍 `system` realm：一个隐藏的 Keycloak realm，
专门留给 SaaS 运营方自己的员工使用，用来管理业务租户、
执行跨租户的管理操作。

它**不是**给业务客户用的。客户的数据和登录流程位于
`demo` / `acme` / `<their-tenant-id>` 这些 realm 之下；`system` realm
与它们并列存在，扮演"控制平面"的角色。

## 为什么要单独一个 realm

促成这种隔离的有三方面考虑：

1. **Realm 级别的隔离**。Keycloak 的 realm 会隔离签名密钥、
   会话存储、用户表和策略。把平台员工和业务租户放在同一个
   realm 里，意味着其中一方的配置错误可能渗透到另一方。

2. **不同的安全姿态**。system realm 跑的是更严格的默认值（见下文）
   —— 失败 5 次锁定、更短的会话、不允许自助注册。业务租户通常
   承受不起这么严格（客服压力会爆炸）；但平台运维员工可以。

3. **跨租户权限需要一个落点**。MyBatis-Plus 的租户拦截器会在每条
   业务 SQL 上注入 `WHERE tenant_id = ?`，按调用者 JWT 中的 `tid`
   限定范围。来自 system realm 的调用者持有 `tid='system'`，
   拦截器会把这个值识别为"此调用者绕过 scope"—— 从而允许它跨
   所有业务租户 SELECT/UPDATE。如果没有一个专门的租户 ID，
   这个 bypass 就没有一个干净的触发条件。

## Realm 配置 —— 和 `demo` 的差别在哪

| 设置项 | demo | system |
|---|---|---|
| `displayName` | "Demo Tenant" | "Platform Operations" |
| `accessTokenLifespan` | 1800 (30 分钟) | 900 (15 分钟) |
| `ssoSessionMaxLifespan` | 36000 (10 小时) | 14400 (4 小时) |
| `bruteForceProtected` | false | true |
| `failureFactor` | 30 | 5 |
| `waitIncrementSeconds` | 60 | 300 |
| `maxFailureWaitSeconds` | 900 | 3600 |
| `tid` mapper claim.value | `demo` | `system` |

其他一切（客户端配置、默认 scope、主题、登录流程）都跟 `demo` 保持一致。
严格的部分只体现在会话 / 锁定相关。

## 身份构成

| 构造 | 存放位置 | 备注 |
|---|---|---|
| `system` realm | Keycloak | 从 `infra/keycloak/realms/system-realm.json` 导入 |
| 该 realm 颁发的 JWT 上的 `tid` claim | Keycloak 协议映射器 | 永远是 `system` |
| `core_auth_user.tenant_id='system'` 的行 | Postgres | 平台运维用户存在这里，每位员工一行 |
| `PLATFORM_ADMIN` 角色 | 租户 `system` 下的 `core_rbac_role` | 由 V26 用 ULID `00000000000000000000ROLE50` 初始化 |
| `platform:*` 通配权限 | 租户 `system` 下的 `core_rbac_permission` | 由 V26 初始化 |
| 默认运维用户（仅 dev） | `ops` / `ops` | 由 `SystemAdminSeeder` + `SystemKeycloakAdminSeeder` 在 `@Profile("local")` 下生成 |

## 权限模型 —— `*:*` vs `tenant:*`

系统中存在两个超级通配符，每个属于一个 scope，它们之间**不会**
互相覆盖：

| 通配符 | 持有者 | 授予什么 | 不授予什么 |
|---|---|---|---|
| `*:*` | PLATFORM_ADMIN（在 `system` realm 内） | 完整的 `platform:` 命名空间 —— 租户管理、跨租户操作 | 业务租户数据 —— 他们没法冒充 `acme` 的 SUPER_ADMIN |
| `tenant:*` | 某个业务租户的 SUPER_ADMIN（比如 `demo`、`acme`） | 该租户范围内的一切 —— `user:*`、`role:*`、`auth:*`、`dept:*` 等等 | `platform:*` 命名空间 —— 他们够不到 `POST /platform/tenants` |

这种符号分配把"看起来最厉害"的通配符（`*:*`）分给"在 scope 内最厉害"
的角色（PLATFORM_ADMIN）；业务超级管理员拿的是 `tenant:*`，它读起来
就是"我是这个租户内的老大" —— scope 直接写在名字里。

这种切分是有意为之的：一家 SaaS 公司的运维员工管理租户的计费 /
注册 / 暂停，但他们**不应**能读到客户的业务记录。这两个 scope
通过代码（PermissionMatcher 双向拒绝越界匹配）来强制这一边界，
而不是仅靠"信任"。

### 影响

- 想拿到"上帝模式"必须同时持有两个通配符（很少见，但必要时
  可审计）。没有任何一个单一通配符能覆盖全部。
- `platform:*` 仍可作为更窄的下放（资源通配）使用 —— 如果你想要
  一个"初级平台运维"角色，拥有所有 platform 权限但没有
  PLATFORM_ADMIN 专属的 `*:*` 标志。
- `tenant:*` 的匹配天然能扩展到将来任意业务模块新加的权限 ——
  随着权限目录的增长，无需更新通配符的注册。

## MyBatis-Plus 的 bypass

`MybatisPlusConfig.mybatisPlusInterceptor()` 配置了一个
`TenantLineInnerInterceptor`，它的 `ignoreTable(...)` 在
`RequestContext.tenantId == "system"` 时返回 true。这会让 system realm
的调用者在**所有**表上都绕过 `WHERE tenant_id = ?` 注入。

为什么是"一刀切"地跳过、而不是逐表配置：

- 角色检查（`platform:*`）在 controller 层通过 `@RequiresPermission`
  完成。等到 SQL 准备发出时，授权决策已经做过了 —— SQL 层
  无需再去二次猜测。
- 反方案（给每个跨租户的 mapper 方法都加 `@InterceptorIgnore`）
  扩展性很差，并且会把意图渗透进几百个文件里。

含义：**一旦有 bug 让非 PLATFORM_ADMIN 调用者拿着 `tid='system'`
进入系统，他就能读到所有租户的数据**。纵深防御依赖于 realm
边界（Keycloak 拒绝向不存在于该 realm 的用户颁发 `tid='system'`
的 token）以及角色检查（没有 `platform:*` 权限，controller 直接拒绝）。

## 本地开发环境

克隆仓库后，用 `local` profile 启动后端：

```
SystemAdminSeeder         → 把 ops 用户插入 core_auth_user（tenant=system）
SystemKeycloakAdminSeeder → 把该用户镜像到 Keycloak 的 system realm（仅 mode=oidc）
V26                       → 初始化 PLATFORM_ADMIN 角色 + platform:* 权限
```

用 `ops` / `ops` 登录，访问 `/platform/tenants` —— 租户管理控制台
（见下方"管理业务租户"）让你能在这里创建 / 暂停 / 硬删业务租户。

## 生产环境配置

通过以下命令配置 system realm：

```
$KEYCLOAK_HOME/bin/kc.sh start --import-realm
  --hostname=https://idp.your-saas.com
```

仓库中提交的 `system-realm.json` 是模板，可进一步收紧：

- 配置真实的 `smtpServer`，让管理员密码重置可用。
- 给每位运维用户开启 MFA（`requiredActions` 中加入 `CONFIGURE_TOTP`）。
- 把 realm 的管理控制台放在私有网络里 —— 平台运维不应当从公网可达。
- 按组织自己的节奏轮换运维用户的密码 / token；`SystemAdminSeeder`
  是 `@Profile("local")` 的，生产环境根本不会跑。

## 添加平台运维用户

在租户管理 UI 上线之前：

```
1. KC 管理控制台 → realm 选择器 → system → Users → Add user
2. 设置 username、email、enabled、emailVerified
3. Credentials tab → 设置永久密码
4. 在 core_auth_user 插入对应行，tenant_id='system'
5. 通过 core_rbac_user_role 把该行绑定到 PLATFORM_ADMIN 角色
   （同样 tenant_id='system'）
6. 首次 SSO 登录时，OidcJitUserService 会把这些行串起来
```

租户管理 PR 落地后，这一步会变成一次 REST 调用加一封邮件。

## 管理业务租户

`/platform/tenants` 控制台中的租户生命周期，仿照"回收站"模型 ——
每一步破坏性操作都需要明确的前置步骤，最具破坏性的那一步还需要
键入确认：

```
                    suspend             hard delete
   ┌──────────┐  ─────────────►  ┌──────────┐  ────────────►  ☒ 永久
   │  active  │                  │ suspended │   (键入确认)      消失
   └──────────┘  ◄─────────────  └──────────┘
                     resume
```

### 创建 (POST /platform/tenants)

整个端到端 onboarding 跑在一个 `@Transactional` 里：
1. 基于 demo 模板创建 Keycloak realm（KC 优先 → 不会留下孤儿行）
2. 插入 `core_tenant` 注册表行
3. 初始化 `core_numbering_management`（按租户的计数器）
4. 初始化 RBAC：tenant:* 权限 + SUPER_ADMIN 角色 + 角色-权限
   绑定 + 克隆 demo 的菜单树
5. 创建首个管理员用户（无密码）+ 绑定 SUPER_ADMIN
6. 创建对应的 Keycloak 用户（无凭据）
7. 生成邀请 token，并把链接发邮件到 `contactEmail`

被邀请者打开 `/invite/<token>`，设置密码，就成为新租户的第一个
SUPER_ADMIN。

### 暂停 (POST /platform/tenants/{id}/suspend)

- `status` 由 1 翻成 0；`mark` 保持 1
- KC realm 的 `enabled` 翻成 false（活动会话在下次刷新时被踢；
  新登录被拒）
- 租户仍在列表里可见，带"Suspended"徽章
- 可通过 Resume 反转

### 恢复 (POST /platform/tenants/{id}/resume)

- `status` 由 0 翻成 1；KC realm 的 `enabled` 回到 true
- 幂等（对已 active 的租户调用是无操作）

### 硬删 (DELETE /platform/tenants/{id})

"清空回收站" —— 唯一一个破坏性操作。

**前置条件**：
- 租户当前必须处于 suspended 状态（`status=0`）。active 的会被拒绝
  —— UI 在 active 行甚至不会显示删除按钮。
- 请求体必须包含 `confirmCode`，且与租户的 `tenant_code` 精确一致。
  前端按这个同一个键入匹配来 gate；后端会再校验一次，免得有人
  挂着 curl 偷偷塞个 path-id 蒙混过关。
- 内置租户（`system`、`demo`）拒绝。

**顺序（先 DB）**：
1. 按 FK 安全顺序（关联表 → 父表 → 用户表 → 辅助表），
   `DELETE FROM <每张按租户的表> WHERE tenant_id = ?`。
   目前是硬编码的；将来某个业务模块新加一张按租户的表时，
   只需在 `TenantAdminService.hardDelete` 里加一行。
2. `kc.realm(code).remove()` —— 删除 realm + 用户 + 会话 + clients。
3. `DELETE FROM core_tenant WHERE id = ?`（注册表行放最后）。

"先 DB"是刻意的：DB 失败时 realm 还在、注册表行也还在，可以重试。
反过来则会导致数据无法被访问但仍占着空间，要恢复就必须手工
重建 realm。

**不可逆**。无 undo。恢复手段是"从备份还原"，这需要 DB + KC 的备份
在删除之前已经做好。

### 支持会话

见下方[§ 支持会话](#支持会话平台运维冒充)—— 用于工单分诊的短期 JWT
冒充，带审计。

## 支持会话（平台运维冒充）

当某个租户提了一个支持工单 —— "我的报表数字不对" —— 平台运维团队
需要短暂地以该租户身份操作。如果没有专门的工具，他们要么 ssh
进 DB（无 UI、无审计），要么借客户管理员的凭据（无问责）。
"支持会话"特性就是官方的路径。

### 它做了什么

`POST /platform/tenants/{id}/support-session`（由
`platform:tenant:impersonate` 控制，请求体 `{ reason }`）会签发
一个 30 分钟有效的 HS256 JWT，包含：

- `sub` = 目标租户中最早一个活跃 SUPER_ADMIN 用户的 id
- `tid` = 目标租户的 code
- `scope` = `tenant:*`（在该租户内拥有完整的 SUPER_ADMIN 权限）
- `preferred_username` = `"[support] <ops>"` —— 可见的审计前缀
- `act` claim —— RFC 8693 风格的 actor 记录：
  ```json
  {
    "sub":        "<ops user id in system tenant>",
    "tid":        "system",
    "username":   "<ops username>",
    "session_id": "<uuid>",
    "reason":     "<text from the request body>",
    "mode":       "FULL"
  }
  ```

前端会在用支持会话的值覆盖之前，把原本 ops 的 token + tenant_id
分别存到独立的 localStorage 键里；这样终止会话时，原会话能干净地
还原回来。每个页面顶部都会显示一条持续的红色横幅，展示当前活动
会话、倒计时和一个 Terminate 按钮。

### 审计追踪 —— 两层

1. **用户名前缀**。下游的 oplog 切面会读取 `RequestContext.username()`
   （从 JWT 的 `preferred_username` 取），所以这段会话里的每一次写入
   都会以 `[support] ops` 的形式落到 `core_oplog`。"今天 ops 在 acme
   做了什么"这种常规查询能立刻浮现这些记录，无需解码 JWT。

2. **会话开始时平台侧的 oplog 行**。controller 上的
   `@OpLog(action="tenant.impersonate.start")` 注解会在 system 租户
   的审计日志中写一行单独的记录，`target_id = <acme 的注册表 id>`、
   `request_body` 就是 reason。所以"我在 HH:MM 为 OS-1234 开了一次
   支持会话"，在真正的支持工作开始前就已经被记录下来。

3. **（可选，未来）** 想要完整取证轨迹的人，可以解码 JWT 并读取
   `act` claim —— 结构化记录就在那里，尽管目前还没有任何代码把它
   投影到一个单独的列。

### 为什么用目标租户的 SUPER_ADMIN 作为 JWT 主体

另一种方案是每个租户都搞一个影子用户 `__support__`（审计上更干净，
因为租户自身 SUPER_ADMIN 的动作不会跟平台运维的支持工作混淆）。
但那要加 DDL，还会在 `core_auth_user` 里多一行让人迷惑的记录。
v1 版本里我们直接用现成的 SUPER_ADMIN —— `[support]` 前缀 +
`act` claim 已经能携带区分信息。如果将来审计模糊在实践中真的成了
问题，影子用户的重构是个 follow-up。

### 已知限制（v1）

- **只支持 FULL 模式** —— 签发出的 token 拿的是 `tenant:*`，包含写入。
  READ_ONLY 模式（scope 收窄为 `*:read`）作为 follow-up 在跟踪；
  现在审计轨迹是防止误写的唯一保护。
- **没有服务端撤销列表**。在 UI 上"终止"只是把 token 在客户端丢掉。
  在后端，token 在到 `exp`（30 分钟）之前都仍然有效。把
  `session_id` 加进 `ForceLogoutService` 的 Redis 踢出集合就能补上
  这个口子 —— 一个小 follow-up。
- **内置租户被拒绝**。`system` 和 `demo` 不能作为支持会话的目标
  —— 没有运营上的理由这样做，而一旦出错波及面也很大。硬编码拒绝。
- **前端不自动续期**。30 分钟设计上覆盖一次分诊；没有 extend/renew
  接口。如果一次会话确实需要更长，就 terminate 然后重新开始
  （会生成新的 `session_id` 和一条新的 oplog，保留可问责性）。

### 运维 runbook

```
1. ops 登录 ?tenant=system，进入 /platform/tenants
2. 找到目标租户那一行 → 点击 LifeBuoy（救生圈）图标
3. 输入一个有意义的理由（≥5 字符；"test" 这种会被前端拒绝）
4. 点击 "Start support session" → 页面以新身份重新加载
5. 顶部红色横幅显示："Acting as <displayName> (<code>) — MM:SS"
6. 该干啥干啥。每一次写入都会以 "[support] ops" 落到 core_oplog。
7. 点击横幅上的 Terminate，或者等 30 分钟自动过期。
8. 页面以 ops 身份重新加载回 /platform/tenants。
```

## 参考

- `infra/keycloak/realms/system-realm.json` —— realm 模板
- `backend/.../bootstrap/startup/SystemAdminSeeder.java` —— DB 种子
- `backend/.../bootstrap/startup/SystemKeycloakAdminSeeder.java` —— KC 同步
- `backend/.../core-common/security/BuiltInRoles.java` —— `PLATFORM_ADMIN_ID`
- `backend/.../core-system/security/PlatformPermissions.java` —— `platform:*` 编码
- `backend/.../core-infrastructure/config/MybatisPlusConfig.java` —— 那个 bypass
- `backend/.../core-system/platform/service/TenantImpersonationService.java` —— 支持会话签发
- `backend/.../core-infrastructure/security/JwtIssuer.java` —— `issueSupportSession`
- `frontend/src/stores/auth.js` —— `enterSupportSession` / `terminateSupportSession`
- `frontend/src/components/layout/SupportSessionBanner.vue` —— 红色横幅
- `backend/core-bootstrap/.../db/migration/V26__system_platform_admin.sql` —— 角色 / 权限种子
