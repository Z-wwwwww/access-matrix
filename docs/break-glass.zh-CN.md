# 应急 break-glass 凭据

[English](break-glass.md) · **中文**

"应急 break-glass 密码"机制的运维指南 —— 当 Keycloak（SSO）不可用时，
让超级管理员仍能登录的应急凭据。

---

## 它是什么

一份单独管理、独立存储的密码，仅对超级管理员账户启用，以 bcrypt
形式保存在 `core_auth_user.password_hash` 中。只用于 `POST /auth/login`
—— 也就是遗留的密码登录路径。`DualModeJwtDecoder` 会同时接受由它
签发的 HS256 token 和 Keycloak 签发的 RS256 token，因此这套凭据
无论是否处于 OIDC 模式都能正常工作。

## 它不是什么

- **不是** 你日常用的 SSO 密码。SSO 密码在 Keycloak 里。
  两者不会同步，也不应当相同。
- **不是** 普通用户的功能。普通用户只通过 SSO 认证；KC 挂了，他们就等。
- **不是** 管理员用来重置他人密码的"后门"。它只验证持有者本人；
  要给其他用户重置 KC 密码，请用 Keycloak 管理控制台。

## 为什么要单独一套凭据

Keycloak 把密码存成 argon2id；我们把 break-glass 存成 bcrypt。
两种算法之间无法互转，而 SSO 登录路径也不会查我们自己的数据库。
如果硬要保持两边同步，每次 KC 改密码都会出现下面其中之一：

- 明文从 KC 传到我们后端（明文泄露，很糟），或
- 静默失败（两边分叉，应急路径就废了）。

让二者彼此独立，并各自按自己的节奏轮换 —— 是这种场景下唯一一种
不需要"系统 A 必须信任系统 B"假设的设计。

---

## 设置 / 轮换

### 通过 UI（推荐）

1. 用超级管理员账号通过 SSO 登录。
2. 右上角用户菜单 → **Break-glass 密码**。（非超级管理员看不到，
   且仅当 `VITE_OIDC_ENABLED=true` 时可见。）
3. 对话框会显示当前是否已配置，并提供设置新值的表单。
4. 保存。把这个值放进密码管理器或组织的密钥保险库 ——
   **没有找回流程**。

### 通过 API

```bash
# 获取状态 —— { configured: true|false }
curl -X GET https://<host>/api/me/break-glass-password/status \
  -H "Authorization: Bearer $SSO_TOKEN"

# 设置 / 轮换
curl -X POST https://<host>/api/me/break-glass-password \
  -H "Authorization: Bearer $SSO_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"password": "<your new break-glass password>"}'
```

这两个接口都需要已认证会话（也就是调用者必须已绑定 SUPER_ADMIN 角色）
—— 它们位于 `/me/...` 命名空间下。

### 通过 SQL（真正的最后一招）

```sql
-- 哈希使用 bcrypt strength 12。可以用任何 bcrypt 工具生成；
-- JVM 中等价于 org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12)。
UPDATE core_auth_user
   SET password_hash = '$2a$12$...',
       update_user   = 'manual-sql-recovery',
       update_time   = NOW()
 WHERE tenant_id = 'demo'
   AND username  = 'admin';
```

除非 API 路径真的不可达（前端全挂、但后端仍然能用），否则别走这条路。
审计日志不会记录到这次轮换。

---

## 使用方式

仅在 KC 不可达时：

1. 访问 `/login`。在应用 logo（或页头品牌标志）上连续快速点击五次，
   即可解锁遗留的密码登录表单 —— SPA 平时会自动跳转到 KC，这个
   热区是用来绕过这次跳转的。
2. 输入超级管理员的用户名 + break-glass 密码。
3. 现在你拿着一个 HS256 token 登录上来了。去做你来做的事。

KC 恢复之后，你应该：

1. 退出登录。
2. 正常通过 SSO 登录。
3. 如果你怀疑这次需要应急是因为凭据问题而不是基础设施问题，
   就顺手轮换一下 break-glass 凭据。

## 最佳实践

| 实践 | 原因 |
|---|---|
| 每季度轮换一次。 | 万一凭据通过截图 / 邮件 / 提交泄露，能限制波及范围。 |
| 存进密码管理器，别贴便利贴。 | 如果两个超级管理员各自轮换，密码管理器才是唯一的事实来源。 |
| 别复用你的 SSO 密码。 | KC 被攻破时，不应该顺带把 break-glass 路径也送出去。 |
| 每次使用后立刻轮换。 | 这次使用本身可能已经被记录 / 被旁观者看到。 |
| 每个环境至少配两个超级管理员。 | 一个人弄丢凭据时，另一个还能从管理控制台给他重新发一份。 |

---

## OIDC 模式下被禁用的路径

| 路径 | OIDC 模式行为 | 原因 |
|---|---|---|
| `POST /admin/auth/reset-password` | 直接 400 拒绝 | 它只写本地 password_hash，根本不会同步到 KC；还会破坏非超级管理员行上 JIT 绑定清理的不变式。 |
| 用户管理页 → "重置密码"按钮 | 灰色不可点，悬浮 tooltip 说明原因 | 同上；UI 先一步拦住，让操作员立刻看到反馈，而不必跑到一个会拒绝的接口去碰一鼻子灰。 |
| `POST /me/break-glass-password` | 正常启用，仅限超级管理员 | 这是设置 break-glass 凭据的**唯一**支持路径。 |

如果你确实需要给一个普通用户设密码（比如调试卡住的账号），
请用 Keycloak 管理控制台 —— 它会把 argon2id 写进 KC 的 `credential`
表，那才是 SSO 路径实际校验的凭据。

---

## 使用时自动告警

OIDC 模式下，每一次成功的 `/auth/login` 都会向当事用户自己的邮箱
异步触发一封邮件，内容包含：

- 登录时间戳
- 客户端 IP 和 User-Agent
- 租户 ID
- 一个醒目的"Sign in via SSO and rotate"按钮，链接到 SPA

含义是：在 OIDC 模式下，`/auth/login` 是**唯一**接受用户名 + 密码的
路径。一次成功的登录就意味着 break-glass 被用了。这封告警让合法
拥有者能在几分钟内察觉"等等，这不是我"：

```
T0  攻击者使用泄露的 break-glass 凭据
T1  AuthService.login 成功 → MailService.sendHtmlAsync 派发
T2  拥有者邮箱在几秒内收到提示（前提是 SMTP 健康）
T3  拥有者看到 "From IP 198.51.100.x" —— 不是自己 —— 打开 SPA，轮换
```

这封告警是**只发给当事人**的（只发给本次完成认证的那个用户，而不是
所有超级管理员）。两个原因：

1. **靶向性**：一封"有人在攻击我的账号"的通知，应该送到合法拥有者
   阅读邮件的地方，而不是淹没在全公司的噪音底噪里。
2. **隐私**：超级管理员的登录事件不应该免费在管理员池中广播；
   横向监视是另一个功能需求，有它自己的信任假设。

如果你想要更广的广播（比如 PagerDuty webhook、Slack 频道、
所有超级管理员一起收到），可以在审计日志下游加个消费者 ——
`LoginAuditService.record(...)` 会把每一次登录尝试都写进
`core_auth_login_log`，包括 `success` 和 `failure_reason`。
跑个定时任务监控 SUPER_ADMIN 账号上 `success=true AND mode=oidc`
的行，就能以你想要的粒度拿到同一份信号。

邮件发送是**尽力而为**的 —— 如果 SMTP 也挂了（很可能这次让操作员
不得不动用 break-glass 的事故，本身就把 SMTP 一起带下水了），
登录依然会成功。无论邮件是否发出，审计日志和控制台日志仍然会
记录该事件。

## 威胁模型

| 攻击 | 缓解 |
|---|---|
| 初始的 break-glass 密码泄露（提交进 git、发到 Slack 之类）。 | 立刻通过 UI/API 轮换。SSO 不受影响 —— 以超级管理员身份登录，打开 Break-Glass 对话框，设置新值即可。 |
| 攻击者拿到了某个超级管理员的 SSO 会话，并在他眼皮底下轮换了 break-glass。 | 所有 `setBreakGlassPassword` 调用都被 `@OpLog(action="auth.breakGlassSet")` 记录到操作日志。定期审阅审计日志能发现非预期的轮换。 |
| 攻击者对 /auth/login 暴力枚举。 | `AccountLockoutService`（15 分钟窗口内 5 次失败）按用户名限流；`AuthRateLimitFilter` 按 IP 限流。 |
| KC 被攻破 → 所有 SSO 账号被接管。 | break-glass 是独立的 —— 攻击者拿不到 bcrypt 哈希就没法借 KC 的失守再进我们这边。 |

## 参考

- `backend/.../auth/controller/BreakGlassController.java` —— 接口
- `backend/.../auth/service/AuthService.java` —— 遗留登录路径
- `backend/.../security/DualModeJwtDecoder.java` —— 同时接受 HS256
  （break-glass）和 RS256（SSO）
- `frontend/src/components/layout/BreakGlassPasswordDialog.vue` —— UI
- `docs/migration-password-to-sso.zh-CN.md` —— 关于为什么超级管理员
  的行在 SSO 迁移过程中会保留 `password_hash` 的背景说明
