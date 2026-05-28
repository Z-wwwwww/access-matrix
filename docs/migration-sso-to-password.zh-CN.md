# Migration: SSO (Keycloak / OIDC) → password

[English](migration-sso-to-password.md) · **中文**

[migration-password-to-sso.zh-CN.md](migration-password-to-sso.zh-CN.md) 的反向对应版本：
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
- 管理员从 `/admin/auth/reset-password` 走相同流程（遗留端点，
  仍可供超管使用，需要 `*:*` 权限）。

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

## 模式翻转自动触发（opt-in）

如果你更喜欢 "翻一下模式就完事"、不要第二个开关：

```yaml
app:
  security:
    mode: password       # or oidc — either direction works
  migration:
    auto-on-mode-flip: true    # off by default; opt-in per environment
    tenants: demo
```

`ModeFlipDetector` 从 `core_meta.security.last_applied_mode`
读取上次生效的模式，与当前模式比较，并派发对应方向的迁移。
不需要 `run-on-startup`。

注意事项：

- 出于安全默认关闭 —— 在开发环境一个随手的模式翻转就让上千用户被群发邮件，
  比生产环境多一行配置要糟糕得多。
- 涉及 `permit-all` 的转换被忽略（不执行迁移，只重置基线）。
- 第一次启动被视作 "无基线，只记录当前" —— 第一次真正的迁移必须显式触发。
  之后的翻转会被自动派发。
- 检测器在 `Ordered.LOWEST_PRECEDENCE` 上运行，所以它看到的是 Flyway 之后、
  seeder 之后的状态。

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

- [migration-password-to-sso.zh-CN.md](migration-password-to-sso.zh-CN.md) —— 正向方向
- `backend/.../bootstrap/migration/SsoToPasswordMigrationService.java` —— 核心逻辑
- `backend/.../bootstrap/migration/PasswordToSsoMigrationRunner.java` —— 启动钩子（同时处理两个方向）
- `backend/.../bootstrap/migration/ModeFlipDetector.java` —— `auto-on-mode-flip` 自动检测
- `backend/.../auth/controller/PasswordResetController.java` —— 免鉴权端点
- `backend/core-bootstrap/src/main/resources/db/migration/V24__core_password_reset_and_nullable_pwhash.sql` —— 表结构
- `frontend/src/views/login/ResetPasswordAccept.vue` —— 前端落地页
