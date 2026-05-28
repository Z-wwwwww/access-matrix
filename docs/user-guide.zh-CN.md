# User Guide

[English](user-guide.md) · **中文**

面向**业务管理员**和**最终用户**：登录、用户管理、角色、权限、数据范围、多租户、密码自助。

> 安装步骤见 [Getting Started](getting-started.zh-CN.md)。本文档假设系统已经跑起来。

---

## 1. 登录

### 1.1 两种登录方式

| 模式 | 入口 | 凭据 | 适用 |
|---|---|---|---|
| **Password** | `/login` 输用户名+密码 | 业务表 `core_auth_user` 里 BCrypt hash | 单机 dev、无 IdP 环境 |
| **SSO (OIDC)** | `/login` 点 "Sign in with SSO" → 跳 Keycloak | Keycloak / Azure AD / 任意 OIDC IdP | 生产、企业内网、有现成 IdP |

后端 `app.security.mode` 决定哪个模式生效。SSO 模式下两个按钮都在，password 模式下只有用户名密码框。

### 1.2 第一次登录

| 模式 | 用户名 | 密码 |
|---|---|---|
| Password (local profile) | `admin` | `admin` |
| SSO (local profile) | `admin` | `admin` （`LocalKeycloakAdminSeeder` 启动时自动建） |

登录后会看到管理后台。如果是 SSO 首次登录，`OidcJitUserService` 会自动把 Keycloak 用户跟业务系统的 admin 行绑定（写 `keycloak_id`）。

### 1.3 切换租户

登录页底部有 **"显示高级"** 链接，展开后能输入租户 ID。默认 `default`。

> 切换租户 = 切换 Keycloak realm。每个租户独立的用户库、角色、权限。详见 [多租户](#5-多租户)。

---

## 2. 用户管理（invite / direct 模式）

进入 **系统 → 用户**，点 "新建" 打开抽屉。

### 2.1 选择 provision 模式

抽屉顶部有两个 radio 按钮：

| 模式 | 流程 | 适合 |
|---|---|---|
| **邀请邮件 (INVITE)** | 管理员只填邮箱 → 后端建 Keycloak 用户（无密码）→ 发邀请邮件 → 用户点链接自己设密码 | 真实员工 / 客户。用户自己掌握密码，管理员永远不知道 |
| **直接创建 (DIRECT)** | 管理员填初始密码 → 后端建 Keycloak 用户（密码标记 temporary）→ 发"账号开通"通知邮件 | bot / 共享账号 / SMTP 不通时兜底 |

### 2.2 INVITE 模式详细流程

```
管理员操作                    系统行为                       用户操作
─────────                    ─────────                      ─────────
填表 (username/email/dept)
点保存       ──────────►   1. 调 Keycloak Admin API
                              建 user（无 credential）
                           2. INSERT core_auth_user
                              keycloak_id = <UUID>
                           3. INSERT core_user_invite
                              token_hash = SHA256(token)
                              expires_at = now + 7d
                           4. 发邮件到 user.email
                              链接 http://app/invite/<token>

                                                            收邮件 → 点链接

                           5. GET /auth/invite/<token>
                              ├─ probe valid ✓
                              └─ render password form
                                                            输密码 → 提交
                           6. POST /auth/invite/<token>
                              ├─ consume token (used_at = now)
                              └─ Keycloak setPassword(temp=false)
                                                            跳 /login
                                                            正常 SSO 登录
```

**邀请链接有效期 7 天**（`app.invite.token-ttl`），一次性消费。过期或已使用都返回同一个 "无效邀请" 提示（防 token 枚举）。

### 2.3 DIRECT 模式详细流程

```
管理员填表 (含 password)
点保存       ──────────►   1. Keycloak Admin API
                              建 user，password marked TEMPORARY
                           2. INSERT core_auth_user
                              keycloak_id = <UUID>
                           3. 发邮件到 user.email
                              "您的账号 X，初始密码 Y，请首次登录修改"

                                                            收邮件
                                                            用 X/Y 登录
                           4. Keycloak 强制要求改密码
                                                            设新密码 → 进入系统
```

### 2.4 邮件发不出去时怎么办？

`app.mail.enabled=false` 或 SMTP 配置错时，MailService **不报错**，只在 log 输出"would have sent X to Y"。这是设计 —— 用户行已经建好，只是没收到邮件。

恢复方法：
- 修 SMTP 配置后，管理员可以**重新触发邀请**（暂未实现 UI；目前需手动 SQL 删 `core_user_invite` 行 + 重新调 admin API）
- 或者切到 DIRECT 模式重发一遍（先用 SQL 改 user 状态 → 重新建）

> **TODO**：未来会加 "重发邀请" 按钮，参见 [开发路线](development.zh-CN.md#路线图)

### 2.5 修改用户

进入用户详情，可以改：

| 字段 | 改了之后 |
|---|---|
| email / displayName / deptId / status | 业务表直接 update |
| **password** | **不能在这改** —— 用户自己去 Keycloak Account Console；管理员需要重置密码用 Keycloak admin API |
| **role 分配** | 业务侧 `user_role` 表，立刻生效（触发 `evictUser` 让 Redis 缓存失效） |

### 2.6 删除用户

| 模式 | 行为 |
|---|---|
| Password mode | 业务表软删（`mark=0`）+ `user_role` 软删 + 立刻强制下线 |
| OIDC mode | 业务表软删 + Keycloak 那边**也**删（`KeycloakUserService.deleteUser`）+ 强制下线 |

**保护**：
- `admin` 内置用户不能删
- 最后一个 SUPER_ADMIN 不能删 / 不能禁用 / 不能撤销 SUPER_ADMIN 角色

---

## 3. 角色 · 权限

### 3.1 内置角色

| 角色 | ID | 说明 |
|---|---|---|
| **SUPER_ADMIN** | `00000000000000000000ROLE01` | 系统最高权限，含通配 `*:*`，受"最后一个"保护 |

新建角色全部从这里派生。

### 3.2 权限编码规则

权限码格式：`<resource>:<action>`，例：

| 权限码 | 含义 |
|---|---|
| `user:read` | 用户列表 / 详情 |
| `user:create` | 新建用户 |
| `user:delete` | 删除用户 |
| `role:*` | 角色资源的所有动作（通配） |
| `*:*` | 所有资源所有动作（超管专用） |

后端代码用 `@RequiresPermission("user:read")` 注解保护 endpoint。`PermissionMatcher` 做三层匹配：

```
配的权限          要的权限          结果
─────             ─────            ────
*:*               任何             ✅
user:*            user:read        ✅
user:read         user:read        ✅
user:read         user:delete      ❌
user:*            role:read        ❌
```

### 3.3 给角色分配权限

进入 **系统 → 角色** → 点角色 → "权限" tab → 树形选择权限。保存后立即生效（Redis cache evict）。

### 3.4 给用户分配角色

进入 **系统 → 用户** → 点用户 → "角色" tab → 勾选角色 → 保存。

一个用户可以有多个角色，**权限是并集**（OR）。

### 3.5 前端按钮级权限

前端用 `v-permission` 指令隐藏没权限的按钮：

```html
<button v-permission="'user:create'">新建</button>
<button v-permission="['user:update','user:delete']">编辑/删除</button>  <!-- 必须同时有 -->
<button v-any-permission="['user:update','user:delete']">操作</button>   <!-- 任一即可 -->
```

指令查 `authStore.authorities`，跟后端 `@RequiresPermission` 用同一套权限码。

---

## 4. 数据范围（5 种 scope）

同一个 endpoint，不同用户看到不同的数据行。由角色的 `data_scope` 字段决定：

| Scope | 含义 | 典型角色 |
|---|---|---|
| **1 ALL** | 看所有租户数据 | 超管 / 总经理 |
| **2 DEPT_AND_SUB** | 自己部门 + 所有子部门 | 部门长 |
| **3 DEPT** | 仅自己部门 | 课长 |
| **4 SELF** | 仅自己创建的 | 一般员工 |
| **5 CUSTOM** | `role_dept` 表显式绑定的部门 | 跨部门联络人 |

### 4.1 业务侧怎么用

在 Mapper / Service 上加 `@DataScope` 注解：

```java
@DataScope(deptColumn = "dept_id", userColumn = "create_user")
public List<Task> findAll() {
    return taskMapper.selectList(null);
}
```

`DataScopeAspect` 切面自动在 SQL 后追加 `AND (dept_id IN (...) OR create_user = ...)` 条件。

### 4.2 多角色 union 规则

一个用户多个角色 → **数据范围取并集**（更宽）。例：用户同时有 DEPT 和 SELF 角色 → 看自己部门 + 所有自己创建的（即使不在本部门）。

### 4.3 demo 演示

`local` profile 种了 5 个 demo 用户 + 15 条 task 数据演示 5 种 scope。详见 [data-scope demo](data-scope-demo.zh-CN.md)。

---

## 5. 多租户

### 5.1 概念

| 概念 | 实现 |
|---|---|
| **租户标识** | 字符串 `tenant_id` （ULID 或人类可读字母） |
| **业务侧隔离** | 每张表都有 `tenant_id` 列；MyBatis-Plus 拦截器自动在 SQL `WHERE` 追加 `tenant_id = current` |
| **IdP 侧隔离** | 每个租户 = 一个 Keycloak realm |
| **JWT 携带** | `tid` claim（OIDC mode）或 `X-Tenant-Id` header（pre-auth / fallback） |

### 5.2 切换租户

**SSO 模式**：登录到不同 realm 即可（每个 realm 独立用户库）。

**Password 模式**：登录页 "显示高级" → 输 tenant ID。前端把 ID 存进 `localStorage.tenantId`，每次请求带 `X-Tenant-Id` header。

### 5.3 添加新租户（运维侧）

1. **Keycloak**：admin console → Create realm → name = 新 tenant ID
   - 在新 realm 里建 client `access-matrix-backend`（复制 `default` realm 的设置）
   - 配 `tid` hardcoded claim mapper，value = 新 tenant ID
2. **业务侧**：什么都不用做。所有迁移 / 表都已经 `tenant_id` 化。第一个用户登录会自动 JIT 建 `core_auth_user` 行
3. **角色/权限**：新租户首次启动时没有角色 → 用 SUPER_ADMIN（跨租户的）登录建一套，或者写 seeder 自动种

### 5.4 用户 / 角色名跨租户允许重名

V20 把唯一约束改成 `(tenant_id, username)` 复合。两个租户都能有叫 `admin` 的用户，互不干扰。

---

## 6. 密码自助 / 忘记密码

接 Keycloak 后，密码相关全部走 IdP，业务侧不再持有密码。

### 6.1 修改密码

登录后，右上角用户菜单 → **"修改密码"** → 弹窗显示 "Open Account Console" 按钮 → 新 tab 打开 Keycloak Account Console（`http://localhost:8180/realms/<tenant>/account/`）→ 在 Keycloak 里改。

### 6.2 忘记密码

`/login` 页面右下 **"忘记密码？"** → 跳到 Keycloak 的 `reset-credentials` 流程 → 输邮箱 → Keycloak 发重置链接到邮箱 → 点链接设新密码 → 跳回业务系统。

> 这要求 Keycloak realm 的 SMTP 配置好（Realm settings → Email），否则邮件发不出去。

### 6.3 启用 MFA / 第二因素

完全由 Keycloak 管。进 admin console → realm → Authentication → Required Actions → 启用 `Configure OTP` 或 `WebAuthn Register`。

用户下次登录会被强制 enroll MFA。业务系统什么都不用改。

---

## 7. 审计日志

业务关键操作（删用户 / 改角色权限 / 强制下线…）通过 `@OpLog` 注解自动落 `core_oplog` 表。

进入 **系统 → 操作日志** 查看（需要 `oplog:read` 权限）。

字段：
- `operator` 操作人 ULID
- `action` 操作类型（delete-user, assign-roles, etc.）
- `target` 操作对象 ID
- `detail` JSON 附加信息
- `tenant_id` / `trace_id` / `create_time`

---

## 8. 强制下线

管理员对某用户点 "强制下线" → `ForceLogoutService.kickOut(userId)` → 写 Redis 集合 → 下次该用户任何 API 调用都返回 401。

适用场景：
- 离职员工，密码还没改但要立刻断访问
- 安全事件，可疑账号要冻结
- 改了权限想立刻生效（`evictUser` 也走类似机制）

---

## 9. 常见管理任务

### 9.1 给新员工开账号

1. 系统 → 用户 → 新建
2. 选 **INVITE** 模式
3. 填邮箱 + 选部门 + 选角色
4. 保存 → 系统自动发邀请邮件
5. 员工点链接设密码 → 自动登录

### 9.2 升级一个普通用户为管理员

1. 系统 → 用户 → 点用户 → 角色 tab
2. 勾上 `SUPER_ADMIN`（或自建的 "Admin" 角色）
3. 保存 → 该用户 Redis 缓存自动失效，下次请求生效
4. （可选）触发"强制下线" 让用户重新登录刷新所有客户端

### 9.3 员工离职

1. 系统 → 用户 → 找到该用户
2. 点 "禁用" → 状态变 0 → 立刻强制下线
3. 或者直接 "删除" → 软删 + Keycloak 那边删 + 强制下线
4. 该用户创建的业务数据不受影响（外键不删）

### 9.4 同事说他没权限看某个页面

1. 系统 → 角色 → 找他的角色
2. 权限 tab → 看是否勾了对应权限
3. 没勾 → 勾上保存 → 让他刷新页面
4. 如果是数据范围问题（看到了页面但列表空）→ 检查角色的 `data_scope` 字段

---

## 10. 下一步

- 开发新功能：[Development](development.zh-CN.md)
- 上线部署：[Deployment](deployment.zh-CN.md)
