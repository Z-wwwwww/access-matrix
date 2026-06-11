# 鉴权迁移 (password ↔ SSO)

两个方向的迁移 runbook：改一行 `app.security.mode` + 重启即自动迁移，幂等、可回滚，业务 ULID / 角色 / 审计全保留。按需选择方向：

- **方向一**：password-only → SSO (Keycloak / OIDC)
- **方向二**：SSO (Keycloak / OIDC) → password

---

# 方向一：password → SSO (Keycloak / OIDC)

本 runbook 指导运维人员将一个正在运行的 access-matrix 部署从
`app.security.mode=password`（HS256，`AuthController.login`
基于 `core_auth_user.password_hash` 校验）切换到
`app.security.mode=oidc`（RS256，由 Keycloak 持有凭证），实现 **零业务数据丢失**
且 **每位用户仅需强制重置一次密码**。

整个迁移由 **`backend/.../bootstrap/migration/`** 下的代码 **完全自动化** ——
没有需要照看的 shell 脚本，也不需要手工编辑 SQL。运维人员要做的只是设置两个配置开关、
重启后端，并查看生成的 JSON 报告。

---

## 为什么要强制重置

在 password 模式时代密码以 **bcrypt** 哈希形式存储在 `core_auth_user.password_hash`，
而切换到 OIDC 后则以 **argon2id** 哈希形式存储在 Keycloak 的 `credential` 表中。
bcrypt → argon2id 的转换在数学上是不可能的（密码学哈希函数都是单向的）。
唯一能让用户密码迁移过去的方式，就是让用户重新输入一次，
这正是 Keycloak 的 `UPDATE_PASSWORD` required-action 邮件所做的事。

除此之外没有其他需要重新输入的内容。用户保留：

- 业务 `core_auth_user.id`（ULID —— 迁移前后不变）
- `username`、`email`、`display_name`、`user_no`
- 角色分配（`core_user_role`）
- 部门（`core_dept_user`）
- 审计历史（`core_audit_log` / `core_op_log` 都以 `user_id` 为键）

---

## 让这一切运转起来的架构组件

| 组件 | 作用 |
|---|---|
| `core_auth_user.keycloak_id` 列（V21） | 可空的、指向 KC 用户 UUID 的外键，`(tenant_id, keycloak_id)` 上有部分唯一索引 |
| `OidcJitUserService` 的 "bind path" | 首次 SSO 登录时，通过 `(tenant, username)` 查找遗留行，写入其 `keycloak_id`，并清空 `password_hash`（非超管），使该行最终与 JIT 配置出来的用户形态一致 |
| `KeycloakUserService.createUser` + `executeActionsEmail` | 将数据库用户镜像到 KC realm 并触发密码重置邮件 |
| `PasswordToSsoMigrationService` | 对所有遗留行批量执行上述操作，幂等，多租户感知 |
| `PasswordToSsoMigrationRunner` | 由 `app.migration.run-on-startup=password-to-sso` 触发的 `ApplicationRunner` |
| `DualModeJwtDecoder` | 同时接受 HS256（遗留）和 RS256（KC）—— 给你一个平滑过渡的重叠窗口 |

---

## 前置条件

- [ ] 后端能连通的 Keycloak ≥ 26（见 `infra/keycloak/README.md`）。
- [ ] 名称与你的租户 id 匹配的 realm 已存在（单租户部署用 `demo`）。
      如有需要，用 `infra/keycloak/new-tenant.ps1 -Name <tenant>` 生成。
- [ ] realm 已配置可用的 **SMTP**。否则密码重置邮件会失败，用户会卡住。
      先从管理控制台手动触发一次 "execute actions email" 进行验证。
- [ ] 你已对 `core_auth_user` 做了快照 / 时间点备份。迁移 **不会修改** 这张表，
      但备份并不需要任何代价。
- [ ] `app.security.oidc.issuer-base-uri` 指向你打算使用的 Keycloak
      （默认：`http://localhost:8180`）。在 `application.yml`
      中或通过环境变量覆盖来确认。

---

## 分步操作

### 第 1 步 —— 确定切换窗口

向用户通告：
- SSO 强制启用的确切日期。
- 在此之前他们会收到一封 "设置密码" 邮件。
- 必须点击链接的截止时间。
- 邮件丢失时的兜底联系人（能重发的管理员）。

Keycloak 的 `executeActionsEmail` 链接默认 **12 小时** 过期
（realm 设置里的 `actionTokenGeneratedByAdminLifespan`）。如果你的用户群需要更长时间，
可以调整。

### 第 2 步 —— 启用迁移开关

在生效中的 `application*.yml`（或托管部署上的环境变量）里：

```yaml
app:
  security:
    mode: oidc                       # required — migration is OIDC-conditional
  migration:
    run-on-startup: password-to-sso  # opt-in trigger
    tenants: demo                 # comma-separated; one realm per item
    report-dir: logs                 # where the JSON report lands
```

等价的环境变量形式：

```sh
CORE_MIGRATION_RUN_ON_STARTUP=password-to-sso
CORE_MIGRATION_TENANTS=demo,acme,beta
```

这个开关 **默认是安全的**（`""` ⇒ 什么都不做）。忘记设置是无害的；
执行成功后忘记取消也是无害的（重复运行是幂等的 —— 见下文）。

### 第 3 步 —— 重启后端

```sh
# Kubernetes
kubectl rollout restart deployment/backend

# bare metal
systemctl restart access-matrix-backend
```

下次启动时 runner 触发。请关注：

```
[migration] starting password-to-sso for tenants=[demo]
[migration] tenant=demo found 47 candidates
[migration] tenant=demo created=47 skipped=0 failed=0
[migration] complete: created=47 skipped=0 failed=0 report=logs/migration-password-to-sso-20260527-141503.json
```

### 第 4 步 —— 查看报告

runner 会写入 `logs/migration-password-to-sso-<timestamp>.json`，
每个租户分三类：

| 类别 | 含义 | 应对 |
|---|---|---|
| `created` | KC 用户已创建，重置邮件已发送 | 无 —— 用户自助完成 |
| `skipped` | 已存在于 KC 中，或缺失 email/username | 排查 `skipped[*].reason` |
| `failed` | KC 创建或邮件发送抛错 | 阅读 `failed[*].errorMessage`，修复后重跑 |

常见的 `skipped.reason` 取值：

- `kc-user-already-exists` —— 幂等重跑，无需处理
- `missing-email` —— 为该用户补全 `core_auth_user.email`，然后重跑
- `missing-username` —— 数据质量问题，需要排查

常见的 `failed.stage` 取值：

- `create-kc-user` —— KC 不可达、admin 凭据错误，或 KC 中已经存在同名但身份不同的用户。
  查看 `errorMessage`。
- `send-reset-email` —— KC 用户已经创建，但发邮件那一步抛错。
  通常是 realm SMTP 配置有问题。修复 SMTP，然后重跑 —— 服务会发现 KC 用户已存在，
  跳过创建，也 **不会** 再次触发邮件（这样用户不会被打扰）。对于这些用户，
  请用 KC 管理控制台从用户详情页手动触发 `Credential Reset`。

### 第 5 步 —— 关闭触发

报告全绿之后，移除迁移开关，以免后续重启又毫无意义地敲打 Keycloak admin API：

```yaml
app:
  migration:
    # run-on-startup: password-to-sso     ← remove or comment out
    tenants: demo
```

重启。正常启动流程恢复。

### 第 6 步 —— 跟踪 bind path 落地（并顺手清理状态）

`OidcJitUserService` 中的 bind path 做的不只是写 `keycloak_id`。
每位用户首次 SSO 登录时，它 **同时** 会清空他们的 `password_hash`
（除非他们持有 `SUPER_ADMIN`，这种情况下哈希会被保留，
以便在 KC 不可达时仍可应急登录）。
这意味着被迁移的用户最终与新建的 JIT 用户在字节级别上一致 ——
"如同始终都是 OIDC" 的最终状态会随着用户登录自动达成，
无需单独的清理步骤。



随着用户完成密码重置流程并通过 SSO 登录，
`OidcJitUserService.bind path` 会把 `keycloak_id` 回写到他们的行上。

查看迁移进度的健康检查 SQL：

```sql
SELECT
    COUNT(*) FILTER (WHERE keycloak_id IS NULL)       AS not_yet_bound,
    COUNT(*) FILTER (WHERE keycloak_id IS NOT NULL)   AS bound,
    COUNT(*)                                          AS total
FROM core_auth_user
WHERE mark = 1
  AND tenant_id = 'demo';
```

在切换窗口期间每天运行一次。当 `not_yet_bound` 下降到可接受的残余水平
（休假中的人等），就进入清理阶段。

### 第 7 步 —— （可选）清理未登录用户残留的 `password_hash`

第 6 步覆盖的是真正通过 SSO 登录的用户。对于已经迁移到 KC 但从未登录过的拖延者
（他们的 `password_hash` 仍然是 password 时代的陈旧 bcrypt 值，
也尚未走完 SSO 流程所以 bind path 还没触发），你可以显式把哈希置 NULL：

```sql
-- DRY RUN — count first
SELECT COUNT(*) FROM core_auth_user u
WHERE mark = 1
  AND keycloak_id IS NOT NULL
  AND password_hash IS NOT NULL
  AND id NOT IN (
      SELECT user_id FROM core_user_role
       WHERE role_id = '<SUPER_ADMIN role id>' AND mark = 1
  );

-- Then run the update
UPDATE core_auth_user
   SET password_hash = NULL,
       update_user   = 'sso-migration-cleanup',
       update_time   = NOW()
 WHERE mark = 1
   AND keycloak_id IS NOT NULL
   AND password_hash IS NOT NULL
   AND id NOT IN (
       SELECT user_id FROM core_user_role
        WHERE role_id = '<SUPER_ADMIN role id>' AND mark = 1
   );
```

（V24 已将 `password_hash` 放宽为可为 NULL —— 无需另行 ALTER。）

---

## 邮件过期 —— 批量重发（模式：`password-to-sso-resend`）

Keycloak 的 reset-credentials 链接默认 12 小时后过期
（由 realm 上的 `actionTokenGeneratedByAdminLifespan` 控制）。
没来得及点击的用户无法自行恢复。对于一两个拖延者，让他们联系你，
然后从 KC 管理控制台一个个重新签发。对于成规模的批次（几十名用户），
请使用专门的重发模式：

```yaml
app:
  security:
    mode: oidc
  migration:
    run-on-startup: password-to-sso-resend   # NOT "password-to-sso"
    tenants: demo
```

重启。runner 会遍历 `core_auth_user` 中 `keycloak_id IS NULL` 的行
（== 尚未完成 SSO 的用户），按用户名查找已有的 KC 用户，
对他们重新触发一次 `executeActionsEmail`。链接仍然有效的用户会收到一封重复邮件 ——
Keycloak 通过让新 token 替换旧 token 来优雅处理这种情况。

报告落在 `logs/migration-password-to-sso-resend-<timestamp>.json`，
分类形状与初次迁移一致：

| 类别 | 含义 |
|---|---|
| `created` | 重发成功（沿用了原桶名，可读作 "已发出的邮件"） |
| `skipped[reason=no-kc-user-yet-run-migration-first]` | 该行从未走过初始迁移。请先运行 `password-to-sso`。 |
| `skipped[reason=missing-username]` | 数据质量 —— 需要人工排查。 |
| `failed[stage=send-reset-email]` | 该用户上的 SMTP 失败。 |
| `failed[stage=lookup-kc-user]` | Keycloak 不可达。 |

### resend 模式的安全特性

- **不创建 KC 用户** —— 那是初始迁移的工作。
  显式地把配置异常情况（"DB 中有行，但 KC 一侧从未被镜像过"）落到 skipped 桶里，
  好让运维人员注意到。
- **不触碰已绑定用户** —— 候选查询会筛选 `keycloak_id IS NULL`，
  所以用户一旦完成 SSO，就会自动从后续每一次 resend 中脱落。
- **幂等** —— 重跑会再发一次同样的邮件。适用于多批次通知（例如第 7 天提醒）。
- **追踪模式和初始迁移相同** —— 每个租户使用合成的 `RequestContext`，
  MP 租户拦截器正确地划定作用域，多租户部署使用同一份 `app.migration.tenants` 列表。

### 何时使用哪种模式

| 情景 | 模式 |
|---|---|
| 首次切换到 OIDC | `password-to-sso` |
| 运维补全了 `core_auth_user.email` 的列，重试少数 `skipped[missing-email]` 行 | `password-to-sso`（幂等 —— 其他用户会作为 `kc-user-already-exists` 被跳过） |
| 已经过去 12 小时，约 30% 用户尚未点击链接 | `password-to-sso-resend` |
| 初次运行时 SMTP 宕机 —— KC 用户已创建但邮件没发出去 | `password-to-sso-resend`（对已存在的 KC 用户重新发邮件） |
| 某个特定用户邮件丢了 | KC 管理控制台 → 用户 → Credentials → Credential Reset → 勾选 UPDATE_PASSWORD → Send email（比重启更快） |

---

## 回滚

如果出了严重问题：

1. 把 `app.security.mode` 改回 `password`（或 `jwt`）。
2. 重启。
3. 旧的 `/auth/login` 路径恢复在线；`password_hash` 完好的用户可以照常登录。

这 **只在** 你尚未执行可选的第 7 步清理时有效。
一旦 `password_hash` 被置为 NULL，唯一能向前推进的办法就是修好 OIDC 那一侧，
让用户完成入职流程。

---

## 提前知道这五个坑

### 1. realm 必须有可用的 SMTP

如果 KC 连不上 SMTP，`executeActionsEmail` 会把用户落进 `failed` 桶。
启动迁移前先从 KC 管理控制台发一封测试邮件。

### 2. 切换窗口期间不要让用户在 KC 修改自己的用户名

`OidcJitUserService.bind path` 通过 `(tenant, preferred_username)`
查找遗留行。如果用户在首次 SSO 登录 **之前** 在 KC 账号控制台修改了用户名，
bind path 会找不到对应的行，给他们创建一个全新的 `core_auth_user` 行 ——
原行成为孤儿。默认 realm 配置 `editUsernameAllowed: false` 可以避免这种情况；
请确认你的配置也是如此。

### 3. profile 字段在迁移后不会同步

bind path 只写 `keycloak_id`。如果用户后来在 KC 修改了 email，
`core_auth_user.email` 仍保留旧值。请决定谁拥有 profile 更新权限 ——
要么锁死 KC 只允许在管理 UI 里改，要么写一个 KC EventListener
把 UPDATE_PROFILE 事件推到后端。

### 4. JIT 新用户陷阱

迁移完成后，如果有人 **直接在 Keycloak 中** 被创建（比如通过 KC 管理控制台），
而没有经过你的管理 UI，他们的首次登录会走 OidcJitUserService 的 **provision path** ——
创建一个全新的 `core_auth_user` 行，没有角色、没有部门、没有 `user_no`。
他们能登录但什么都看不到。第 5 步之后，请禁止在 KC 里创建用户；
统一使用后端的管理 UI。

### 5. 表结构漂移：`password_hash NOT NULL`

V2 迁移把 `password_hash VARCHAR(255) NOT NULL` 声明为非空。
OIDC JIT 和 INVITE 模式的用户创建都把它留作 NULL，依赖于 MyBatis-Plus 的
`NOT_NULL` 字段策略把这一列从 `INSERT` 语句中省略。
目前能正常工作，但很脆：

- 任何未来用 `entity.setPasswordHash(null)` 配合 `DEFAULT` 字段策略写入的代码路径都会失败。
- 第 7 步的清理 `UPDATE ... SET password_hash = NULL` 会因 `NOT NULL` 约束失败。

用一个小迁移修复（建议 ID：`V23`）：

```sql
ALTER TABLE core_auth_user
  ALTER COLUMN password_hash DROP NOT NULL;
COMMENT ON COLUMN core_auth_user.password_hash IS
  'NULLABLE — set only when the row represents a break-glass admin or a legacy password-mode user. Normal OIDC users carry NULL here, with credentials owned by Keycloak.';
```

---

## 为什么用启动 runner 而非 HTTP 端点？

考虑过、被否决了：一个 `@PostMapping("/admin/migration/run")`。理由：

1. **统一审计链路**：通过发版触发迁移，会复用与生产中任何其他改动相同的纸面记录
   （发版日志、变更工单）。HTTP 端点会引入一条单独的审计面，需要额外防备被攻陷的管理员会话。
2. **确定性**：相同的配置项，相同的重启，在每个环境得到相同的行为。
3. **幂等已足够**：迁移天然是一次性的；"运行一次" 这种机制不需要比
   "改配置项 → 重启" 更花哨。代价（一次重启）对一个组织一辈子只做一次的操作来说很小。

如果你确实需要无需重启就触发，可以从一个一次性的 Spring-Boot CLI 子命令里直接实例化
`PasswordToSsoMigrationService` —— 但到了那个地步，走重启路径才更简单。

---

## 另请参阅

- `backend/.../bootstrap/migration/PasswordToSsoMigrationService.java` ——
  核心逻辑
- `backend/.../bootstrap/migration/PasswordToSsoMigrationRunner.java` ——
  启动钩子
- `backend/.../system/auth/service/OidcJitUserService.java` ——
  在首次 SSO 登录时完成迁移后半段的 bind path
- `backend/core-bootstrap/src/test/.../PasswordToSsoMigrationIT.java` ——
  对真实 KC + Postgres 的端到端冒烟测试
- `infra/keycloak/README.md` —— 本迁移所依赖的 Keycloak 配置
- `backend/core-bootstrap/src/main/resources/db/migration/V21__core_auth_user_keycloak_link.sql`
  —— 让 bind path 得以工作的表结构列 / 索引

---

# 方向二：SSO (Keycloak / OIDC) → password

（本文「方向一：password → SSO」）的反向对应版本：
将一个一直运行在 `app.security.mode=oidc` 的部署切回内置的密码认证
（`mode=password` 或 `mode=jwt`），实现 **不丢失业务数据**
且 **每位用户仅需强制重置一次密码**。

整个迁移由 `backend/.../bootstrap/migration/SsoToPasswordMigrationService`
**完全自动化**。运维人员要做的只是设置两个配置开关、重启后端、查看生成的 JSON 报告。
其余的由用户自己点击邮件中的链接并设定新密码来完成。

---

## 什么情况下你会想这么做

- 合规要求一度把你推向 SSO，后来又变了方向。
- 维护 Keycloak 的运维成本对你团队的规模而言并不划算。
- 在长期承诺 OIDC 之前先验证回滚路径。
- 你接手了一个使用 OIDC 的项目，但想在开发环境用 password 模式。

如果你没有具体理由，**就别做这件事**。OIDC 模式具备严格更强的能力
（MFA、联邦、账号控制台、审计）。本项目支持反向操作只是为了避免把你锁死。

---

## 为什么（又一次）要强制重置

理由和正向迁移一样：密码以 **argon2id** 形式存在 Keycloak 的 `credential` 表中，
而在 `core_auth_user.password_hash` 中以 **bcrypt** 形式存储。
跨密码学哈希算法的转换是不可能的。让用户的密码迁移过去的唯一安全方式，
是让他们重新输入一次，这次输入到我们自己的系统里。

无需用户操作就能携带过来的东西：

- 业务 `core_auth_user.id`（ULID —— 不变）。
- `username`、`email`、`display_name`、`user_no`、`dept_id`、`status`。
- 角色绑定（`core_user_role`）。
- 审计历史（`core_audit_log` / `core_op_log` 都以 `user_id` 为键）。

需要用户操作的东西：

- 通过邮件里的重置链接设置新密码。

完成重置后会消失的东西：

- `core_auth_user.keycloak_id`（变 NULL —— 这行不再声称属于任何 KC 身份）。
- 对应的 KC 用户被禁用（不是删除；KC 一侧的审计记录被保留）。

最终状态在字节层面上与一个生在 password 时代的用户完全一致：
`password_hash` 已设置，`keycloak_id` 为 NULL。

---

## 让这一切运转起来的架构组件

| 组件 | 作用 |
|---|---|
| `core_password_reset_token` 表（V24） | 一次性、只存哈希的 token，是 `core_user_invite` 的姊妹表 |
| `PasswordResetTokenService` | mint / peek / consume —— `InviteTokenService` 的克隆体 |
| `POST /auth/password-reset/{token}` | 免鉴权端点：bcrypt + 写入 `password_hash`、把 `keycloak_id` 置 NULL、KC.disableUser |
| `ResetPasswordAccept.vue` | 前端落地页（样式与 InviteAccept.vue 一致） |
| `SsoToPasswordMigrationService` | 批量处理 `keycloak_id IS NOT NULL` 的用户，签发 token 并发邮件 |
| `PasswordToSsoMigrationRunner`（已扩展） | 识别 `sso-to-password` 模式 |
| `user-password-reset.{en,ja_JP,zh_CN,zh_TW,ko_KR}.ftl` | 邮件模板，5 种语言 |

---

## 分步操作

### 第 1 步 —— 确定切换窗口

与正向方向同样的考虑。请通告：

- SSO 何时不再可用（你会在窗口的 **末尾** 翻转模式）。
- 用户会在窗口期间收到 "设置密码" 邮件。
- 截止时间。Token TTL 默认 7 天（可通过 `app.password-reset.token-ttl` 配置，
  Duration 语法与邀请 token 相同）。
- 邮件丢失时的兜底联系人。

### 第 2 步 —— 触发迁移

暂时保持 `mode=oidc`（runner 只在正向方向上对 OIDC 有条件；
反向 service 没有这种门控，但最容易理解的心智模型是
"在 SSO 还能用时触发，之后再翻转模式"）。

```yaml
app:
  security:
    mode: oidc                          # keep SSO live during the cutover window
  migration:
    run-on-startup: sso-to-password     # NEW value, third recognised mode
    tenants: demo                    # comma-separated; one realm per item
```

重启。runner 会遍历 `core_auth_user` 中 `keycloak_id IS NOT NULL` 的行，
为每位用户签发一次性的重置 token，并通过模板触发重置邮件。请关注：

```
[migration] starting sso-to-password for tenants=[demo]
[rev-migration] tenant=demo found 47 candidates (still KC-bound)
[reset] minted token for user 01ULID... (tenant demo) expires 2026-06-03T14:15:00
... ×47
[rev-migration] tenant=demo emails-sent=46 skipped=1 failed=0
[migration] complete mode=sso-to-password created=46 skipped=1 failed=0 report=logs/migration-sso-to-password-20260527-141503.json
```

### 第 3 步 —— 查看报告

`logs/migration-sso-to-password-<timestamp>.json` 的结构与正向方向一致：

| 类别 | 含义 | 应对 |
|---|---|---|
| `created` | token 已签发 + 邮件已入队 —— 可读作 "已发出的邮件" | 无 —— 用户自助完成 |
| `skipped[missing-email]` | DB 行没有 email | 补全后重跑 |
| `skipped[missing-username]` | 数据质量问题 | 手工修复 |
| `failed[mint-token]` | token 行的数据库写入失败 | 排查后重跑 |
| `failed[send-reset-email]` | token 已签发但邮件投递抛错 | 修复 SMTP 后重跑（会签发新的 token —— 旧的自然过期） |

### 第 4 步 —— 用户完成重置

每封邮件包含一个链接 `https://<your-app>/reset-password/<TOKEN>`。

前端流程：

```
ResetPasswordAccept.vue mounts → GET /auth/password-reset/{token} probe
                              ↓
                         valid? render form
                              ↓
              user types new password (×2 for confirmation)
                              ↓
                  POST /auth/password-reset/{token}
                              ↓
PasswordResetController:
    1. passwordPolicy.validate(req.password())            ← length / complexity / HIBP
    2. tokens.consume(token)                              ← single-use; sets used_at
    3. UPDATE core_auth_user SET
         password_hash = bcrypt(req.password()),
         keycloak_id   = NULL,
         update_user   = 'password-reset'
       WHERE id = <token.user_id> AND tenant_id = <token.tenant_id>
    4. KeycloakUserService.disableUser(realm, kcId)       ← best-effort; logged if fails
                              ↓
                       "done" screen + link to /login
```

一旦 `keycloak_id` 变为 NULL，该用户就会从此后所有候选查询中脱落。
这就是 "始终如同 password 模式" 的最终状态。

### 第 5 步 —— 跟踪进度，然后翻转模式

健康检查 SQL（正向方向的镜像）：

```sql
SELECT
    COUNT(*) FILTER (WHERE keycloak_id IS NOT NULL)   AS still_kc,
    COUNT(*) FILTER (WHERE keycloak_id IS NULL
                       AND password_hash IS NOT NULL) AS migrated,
    COUNT(*) FILTER (WHERE keycloak_id IS NULL
                       AND password_hash IS NULL)     AS broken,
    COUNT(*)                                          AS total
FROM core_auth_user
WHERE mark = 1 AND tenant_id = 'demo';
```

`broken`（keycloak_id 为 NULL 且 password_hash 为 NULL）是危险状态 ——
用户已经与 KC 解绑但从未设置密码。如果看到非零值，
请在翻转模式之前先排查。

当 `still_kc` 下降到可接受的残余水平（拖延的用户）时，翻转：

```diff
 app:
   security:
-    mode: oidc
+    mode: password
   migration:
-    run-on-startup: sso-to-password
+    # run-on-startup: sso-to-password   (uncomment to re-fire reminders)
```

重启。遗留的 `/auth/login` 路径现在是唯一的登录入口；
KC token 不再被接受（`DualModeJwtDecoder` 仅在 mode != oidc 时回落到 HS256）。

### 第 6 步 —— （可选）落后者处理

翻转模式后仍在 `still_kc` 桶里的用户无法登录。他们的选项：

- 联系管理员 → 管理员以 `sso-to-password` 模式重跑迁移
  （签发新 token，发新邮件）。
- 管理员在用户管理控制台重置其密码
  （`POST /admin/user/{id}/reset-password`，权限 `auth:reset-password`）：
  password 模式下会生成一次性临时密码写入本地 hash 并一次性回显。

批量重发邮件：

```yaml
app:
  security:
    mode: password         # already flipped
  migration:
    run-on-startup: sso-to-password
```

重跑会看到同样这批 `keycloak_id IS NOT NULL` 的用户（他们没重置过）
并签发新的 token。旧 token 自然过期。

---

## 回滚（窗口期内）

反向迁移在用户开始完成重置流程之前是完全可逆的。具体来说：

| 状态 | 回滚动作 |
|---|---|
| 已安排迁移但 `app.security.mode` 仍是 oidc | 移除 `run-on-startup`，重启。在外的重置 token 自然过期。SSO 不受影响。 |
| 部分用户已经完成重置（这些行的 `keycloak_id` 为 NULL） | 这些用户需要通过正向迁移重新镜像回 KC。KC 用户是被禁用（不是删除），所以可以在管理控制台重新启用；你需要手工更新 `keycloak_id`，或让用户通过 SSO 登录（JIT bind path 会自动接好）。 |
| 模式已翻转到 password | 翻回 oidc；已经完成重置的用户其 `password_hash` 已设置而 `keycloak_id` 为 NULL —— 他们会把自己旧的 SSO 账号视为 "尚未绑定"，下次 SSO 登录时 JIT 创建出一个新的业务行。**在决定如何处理已完成重置的行之前，不要把模式翻回去。** |

底线：回滚在迁移窗口期内最干净，随着用户完成重置会越来越难，
模式翻转后基本上就是 "重新做一遍正向迁移"。

---

## 五个坑

### 1. 后端必须有可用的 SMTP

反向方向依赖 `MailService.sendHtmlAsync`（底层使用配置在 `spring.mail.*`
下的 Spring Mail `JavaMailSender`）。先发一封测试邮件；
迁移不会在启动时验证 SMTP，只会在每个用户上以 `stage=send-reset-email` 失败。

### 2. KC 用户是被禁用，不是被删除

用户完成重置后，会对对应的 KC 用户调用 `KeycloakUserService.disableUser`。
KC 一侧的历史（`USER_INFO` 事件日志、登录历史）被保留，
但该用户无法再通过 KC 登录。

如果你想要彻底删除 KC 一侧，可以在迁移完成后手工执行：

```sql
-- list disabled-but-still-present KC users
SELECT u.id, u.username FROM keycloak.user_entity u
WHERE u.realm_id = (SELECT id FROM keycloak.realm WHERE name = 'demo')
  AND u.enabled = false;
```

然后对想清理的用户 `kcadm delete users/<uuid>`。

### 3. V24 已放宽 password_hash NOT NULL

如果你不知怎么用着 V24 之前的部署还跳过了迁移，
重置端点的 `UPDATE ... SET password_hash = bcrypt(...)` 会成功，
但 `keycloak_id` 写 NULL 也会成功 —— V24 删除约束这一步是对称生效的。

V24 还会添加 `core_password_reset_token` 表；没有它，
重置端点首次被调用时就会失败。在跑迁移 **之前**
请确保 Flyway 状态已到 V24。

### 4. 重置端点是免鉴权的 —— 如果你定制了 security，请记得登记它

`PasswordResetController` 挂载在 `/auth/password-reset/{token}` 下，
这被 `SecurityConfig` 中默认的 `PERMIT_PATHS = ["/auth/**", ...]` 覆盖。
如果你定制了这份列表，请确保 `/auth/password-reset/**` 仍然被允许；
否则用户点开邮件链接会收到 401。

### 5. 邮件语言默认是日语

迁移以 `Locale.JAPAN` 发送邮件，因为在迁移那一刻收件人并没有已登录的 profile
可用来取 locale。如果你的用户群主要不讲日语，有两个选择：

- 在 `core_auth_user` 上加一个 `locale` 列（目前不存在），
  并在 `SsoToPasswordMigrationService.sendResetEmail` 里使用它。
- 等用户登录一次后再用其偏好 locale 发一封跟进邮件。

模板已经支持 5 种语言；日语默认只是迁移任务挑选模板时的取舍。

---

## 另请参阅

- 见本文「方向一：password → SSO」
- `backend/.../bootstrap/migration/SsoToPasswordMigrationService.java` —— 核心逻辑
- `backend/.../bootstrap/migration/PasswordToSsoMigrationRunner.java` —— 启动钩子（同时处理两个方向）
- `backend/.../auth/controller/PasswordResetController.java` —— 免鉴权端点
- `backend/core-bootstrap/src/main/resources/db/migration/V24__core_password_reset_and_nullable_pwhash.sql` —— 表结构
- `frontend/src/views/login/ResetPasswordAccept.vue` —— 前端落地页
