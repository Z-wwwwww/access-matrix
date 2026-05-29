# Keycloak —— access-matrix 的本地 SSO/IdP

[English](README.md) · **中文**

开发环境中我们使用 **Keycloak 26+** 作为 OIDC 身份提供方。它以独立 Java 进程的方式运行（无需 Docker），并连接到应用本身使用的同一个 Postgres 实例，通过 schema 隔离。

## 为什么用本地 Keycloak

后端的 `app.security.mode: jwt` 是按照 OIDC 形态的 JWT 设计的（`tid` / `sub` / `preferred_username` / `scope` 等 claim —— 见 `application.yml`）。Keycloak 是最容易获得的、能够发出完全相同形态 JWT 并开箱支持多租户（一租户一 **realm**）的开源 IdP。

在开发环境本地运行 Keycloak 的好处：

- 让每位贡献者都有一台真实的 OIDC 服务器可供认证；
- 让多租户链路（`X-Tenant-Id` header → realm → `tid` claim → `RequestContext.tenantId`）无需共享服务即可端到端可测；
- 离线可用。

## 前置条件

| 要求 | 说明 |
| --- | --- |
| **JDK 17+** | 后端已经使用了 JDK 25。 |
| **Postgres 17+** | 应用使用的同一个实例（`127.0.0.1:5432`，数据库 `new_inntouch_core`）。 |
| **`keycloak` schema** | 通过 `CREATE SCHEMA IF NOT EXISTS keycloak;` 创建 —— 已完成。 |
| **Keycloak 26+ ZIP** | 从 <https://www.keycloak.org/downloads> 下载。 |

> 无需 Docker。Keycloak 以 Quarkus 应用形态发布；解压 ZIP 即可。

## 一次性配置

1. **下载并解压 Keycloak**

   ```powershell
   # Windows — script default path:
   #   C:\SERVER\keycloak-26.6.2\
   # Extract somewhere else? Set KEYCLOAK_HOME or edit start-keycloak.bat.
   ```

   ```bash
   # macOS / Linux — script default path (Git Bash / WSL):
   #   /c/SERVER/keycloak-26.6.2/
   # Or set KEYCLOAK_HOME explicitly.
   ```

2. **（已完成）确认 `keycloak` schema 存在**

   ```sql
   -- run as postgres against new_inntouch_core
   CREATE SCHEMA IF NOT EXISTS keycloak;
   ```

3. **（可选）设置 `KEYCLOAK_HOME`**，如果解压位置不是启动脚本默认路径：

   ```powershell
   setx KEYCLOAK_HOME "C:\SERVER\keycloak-26.6.2"
   ```

## 启动

```powershell
# Windows
infra\keycloak\start-keycloak.bat
```

```bash
# macOS / Linux
infra/keycloak/start-keycloak.sh
```

然后打开：

- 管理 UI —— <http://localhost:8180/admin>（用户 `admin`，密码 `admin`）
- Realm UI —— <http://localhost:8180/>

> 端口 8180 是故意避开 Spring Boot 默认的 8080。

首次启动时 Keycloak 会在 `keycloak` schema 上跑自己的 Liquibase 迁移（约 30 秒）。后续启动 < 10 秒。

## 定义一个租户（realm）

多租户约定：

> **realm 名 == 租户 id == 子域名标签**

所以新增租户 "acme" 意味着 Keycloak 中存在一个名为 `acme` 的 realm，该 realm 发出的每个 JWT 都带 `tid="acme"`，SPA 通过 `https://acme.access-matrix.com/` 访问该租户。前端的 `utils/tenant.js` 和后端的 MyBatis `TenantLineInnerInterceptor` 都基于这个约定。

### 新增租户（推荐方式）

使用仓库中提交的辅助脚本，它会克隆 `demo-realm.json` 并改写 realm 名和 `tid` 硬编码 claim mapper：

```powershell
# Windows
.\infra\keycloak\new-tenant.ps1 -Name acme
```

```bash
# macOS / Linux
infra/keycloak/new-tenant.sh acme
```

然后重启 Keycloak —— `start-keycloak.{bat,sh}` 已经传了 `--import-realm`，下次启动时新文件会被自动加载。在管理控制台中（realm 选择器 → `acme`）确认后，再在 Users 标签下开通第一个管理员用户。

### 通过管理 UI 手工新增租户

适用于一次性、不走辅助脚本的情况：

1. 左上角 realm 选择器 → **Create realm**。
2. Realm 名 → 即租户 id（例如 `acme`）。
3. 在该 realm 内：
   - **Clients** → 创建 `access-matrix-backend`，Client authentication = OFF，Standard flow ON，有效重定向 URI 为
     `https://acme.access-matrix.com/sso/callback`（开发环境用
     `http://localhost:5273/sso/callback`）。在每个 realm 的 client 上注册一条通配符
     `https://*.access-matrix.com/sso/callback` 即可一次性覆盖所有租户。
   - **Client scopes** → 在 `access-matrix-backend`-dedicated scope 上，新增一个名为 `tid` 的 hardcoded-claim mapper，claim 名 `tid`，claim 值等于 realm 名。这正是将 `tenant_id` 透传到下游每个 API 调用的关键。
   - **Client scopes** → 确保 `email`、`profile`、`roles` 在 "Default" 已分配 scope 中。
   - **Users** → 至少创建一个管理员，设置非临时密码，邮箱标记为已验证。
   - **Realm settings → Themes → Login theme** → `access-matrix`（即 `infra/keycloak/themes/` 下提交的品牌主题）。

## 导出 / 提交 realm 配置

当某个 realm 配置已经达到希望分发给其他开发者的状态时：

1. 管理 UI → realm → **Realm Settings** → 三点菜单 → **Partial export**
2. 勾选 "Include groups and roles" 和 "Include clients"
3. 将 JSON 保存到 `infra/keycloak/realms/<realm-name>.json`
4. 提交它。下次启动时，启动脚本的 `--import-realm` 标志会在任何贡献者机器上从这份 JSON 重新水合该 realm。

> `realms/` 已经committed `demo-realm.json`（业务示例租户）和
> `system-realm.json`（平台运营租户）。新增租户请用 `new-tenant.ps1`
> 克隆 `demo-realm.json` 并改名，避免手编 JSON。

## 自定义主题（access-matrix 品牌）

`infra/keycloak/themes/access-matrix/` 下提交的登录主题对 Keycloak 登录页进行了重新设计，使其与 access-matrix Vue UI 保持一致：

- Slate-50 页面背景（与 SPA 的 `bg-background` 一致）
- Tailwind blue-600 主按钮 + focus ring
- 圆角输入框（8px）/ 卡片（12px）/ 柔和阴影
- System UI 字体栈（与 SPA 一致）

该主题继承自 `keycloak.v2`（基于 PatternFly），因此能免费复用所有流程的模板（登录 / 忘记密码 / 修改密码 / 验证邮箱 / required-action / …），只通过 CSS 自定义属性覆盖颜色 / 字体 / 间距。未来 Keycloak 升级触及模板 HTML 时也能自动吸收；只有 PatternFly 大版本升级时需要在此处关注。

**同步**：`start-keycloak.bat` / `.sh` 在每次启动时通过 `xcopy` / `rsync` 将主题同步到 `$KEYCLOAK_HOME/themes/`，因此对 `infra/keycloak/themes/access-matrix/` 的修改在 Keycloak 重启后生效（如设置了 `--spi-theme-cache-themes=false`，登录页 `Ctrl+Shift+R` 即可生效）。

**按 realm 启用**：提交的 `demo-realm.json` 已设置 `"loginTheme": "access-matrix"`，因此通过 `--import-realm` 重新导入即可自动拾取。对于已存在的 realm（在主题提交之前创建的），可以二选一应用：

```bash
# Option A: admin console
#   Realm settings → Themes → Login theme → access-matrix → Save

# Option B: one-liner via kcadm
$KEYCLOAK_HOME/bin/kcadm.sh config credentials \
    --server http://localhost:8180 --realm master --user admin --password admin
$KEYCLOAK_HOME/bin/kcadm.sh update realms/demo -s 'loginTheme=access-matrix'
```

新增主题：在 `infra/keycloak/themes/<name>/` 下放置同级目录，然后将该 realm 的 `loginTheme` / `accountTheme` / `adminTheme` / `emailTheme` 设为该名称。

## 多租户路由（前端 ↔ realm）

SPA 在运行时通过 `frontend/src/utils/tenant.js` 决定 "我现在正在登录哪个 realm"。解析优先级：

1. `?tenant=<name>` query string（localhost 上的开发覆盖，会粘到 localStorage 以便后续刷新继承）。
2. `window.location.hostname` 的子域名 —— 例如 `acme.access-matrix.com` → realm `acme`。保留标签如 `www`、`app`、`api`、`kc` 等会回退到下一个来源。
3. `localStorage.tenant_id`（来自之前显式选择的延续）。
4. `"demo"`。

一旦确定，同一个值会同步驱动两件事：

- `oidcConfig().issuer` 变为 `${VITE_OIDC_ISSUER_BASE}/realms/<tenant>`，即 OIDC 重定向的目标 realm。
- `X-Tenant-Id: <tenant>` 作为预认证回退附加在每个 axios 请求上（认证后，后端优先采用 JWT 中的 `tid` claim）。

重定向 URI 是从 `window.location.origin` 推导而来，因此子域名宿主会回到自身。在 Keycloak client 配置中注册一条通配符如 `https://*.access-matrix.com/sso/callback`，新增租户就无需再修改 valid-redirect-uri 列表。

## 后端配置（已完成 —— `app.security.mode=oidc`）

接入分布在两个 bean 中：

- `MultiRealmJwtDecoder`（位于 `core-infrastructure/security`）接受 `app.security.oidc.issuer-base-uri` 下任意 realm 的 token。每个 realm 的 JWKS 在首次出现时按需获取并缓存。也可降级为单 realm `issuer-uri`，用于加固的单租户部署。
- `OidcJitUserService` 在首次 SSO 登录时按租户开通一行业务 `core_auth_user` —— JIT 同样以信任前缀为门控，使得由 `AdminAuthController.login` 签发的 HS256 应急 token 可正确通行。

`AdminAuthController.login` 的密码路径仍作为应急通道保留（`app.security.mode=jwt`，或通过 `DualModeJwtDecoder` 同时接受 RS256 旁路的 HS256 token），以免 Keycloak 不可用时把所有人都锁在门外。

## 故障排查

| 现象 | 可能原因 |
| --- | --- |
| `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${...}` | 环境变量未导出 —— 请通过启动脚本运行，而不是裸跑 `kc.bat`。 |
| `permission denied for schema keycloak` | Postgres 用户不是该 schema 的 owner。修复：`ALTER SCHEMA keycloak OWNER TO postgres;`。 |
| 管理 UI 进入 "Update profile" 死循环 | 忘记将开发用户邮箱标记为已验证 —— 在 Users → Details 中切换。 |
| 8180 端口已被占用 | 启动前设置 `KC_HTTP_PORT=8280`（或类似）。 |

## 生产环境定型

生产环境我们以生产模式（`kc.sh start`）运行 Keycloak，置于 TLS 终结反向代理之后，启用 `hostname-strict=true`、`proxy-headers=xforwarded`，并使用独立的专用 DB 用户 —— 而非应用本身的 `postgres` 超级用户。

### 网络分流 —— 认证面 vs 管理面

这是生产加固里**最重要的一项决策**，所以单列一节。Keycloak 的端点分两类，暴露要求**完全相反**：

| 类别 | 路径前缀 | 谁访问 | 公网可达性 |
| --- | --- | --- | --- |
| **认证面** | `/realms/<realm>/*`、`/resources/*` | 终端用户**浏览器**（登录、token、账号台、**租户切换**） | **必须公开** |
| **管理面** | `/admin/*`（admin console SPA **和** Admin REST API） | 仅运维 | **仅内网** |

认证面**必须**公开：OIDC 授权码流程会把用户浏览器重定向到 `/realms/<tenant>/protocol/openid-connect/auth`。租户切换（登录主题里 realm 药丸的切换器）走的是同一套机制 —— 跳到 SPA，再由 SPA 打 `/realms/<新租户>/...`。所以**自由切换 realm 就要求 `/realms/*` 公网代理出去**。这是设计使然且安全，因为认证面只暴露用户本就该访问的登录/token 端点。

管理面**不需要**公开。KC 管理控制台是挂在 `/admin/<realm>/console/` 下的 SPA，它真正的读写操作全靠调用 `/admin/realms/*` 下的 **Admin REST API**。在反代上拒掉 `/admin/*`，**即使 admin 密码完全正确**，控制台从公网也用不了 —— 它依赖的 API 够不着。**这就是为什么主防线是网络隔离，而非密码保密**。密码保密是第二层（纵深防御），不是唯一一道。

> **两个面为什么能干净拆分：** 它们路径前缀不同（`/realms/` vs `/admin/`），反代用简单的 location 规则就能放行一个、拒绝另一个 —— 完全不影响登录和租户切换。

### 推荐的反向代理规则

```nginx
# ── 认证面：公开 ────────────────────────────────────────────────
# 业务用户在这里登录、切换租户。
location /realms/    { proxy_pass http://keycloak_upstream; }
location /resources/ { proxy_pass http://keycloak_upstream; }

# ── 单独拒掉 master realm（理由见下）─────────────────────────────
# 必须排在通用 /realms/ 规则之前，或用更精确的正则匹配。
# 没有任何合法业务流程会以 master 为目标。
location ~ ^/realms/master(/|$) { deny all; return 404; }

# ── 管理面：仅内网 ──────────────────────────────────────────────
# admin console SPA + Admin REST API。运维通过 VPN / bastion /
# 内网访问，绝不走公网监听器。
location /admin/     { deny all; return 404; }
```

运维从内网（VPN、bastion 或仅内网的监听器）访问管理控制台。Keycloak 26 还支持专门的管理面 hostname，让控制台自身的链接永远不指向公网地址：

```bash
KC_HOSTNAME=https://auth.yourcompany.com          # 公开的认证面
KC_HOSTNAME_ADMIN=https://kc-admin.internal:8443  # 管理面，仅内网 DNS 解析
```

### 建议：在公网边缘单独拒掉 `master` realm

**做什么：** 在拒掉 `/admin/*` 之外，于公网监听器上**显式拒掉 `/realms/master/*`**（上面那条正则规则）。

**为什么值得为它单列一条规则：**

1. **`master` 没有任何业务用途。** 没有租户映射到它，没有应用 client 认证到它。"realm 名 == 租户 id"的约定意味着每个*真实*租户是 `demo`、`system`、`acme`…… —— 永远不会是 `master`。所以公网拒掉它在功能上零成本。

2. **`master` 是跨 realm 的超级 realm。** 它的管理员能管理*所有*其它 realm，它持有 bootstrap 的 `admin` 超管，是整个 IdP 里价值最高的目标。把它的登录页挡在公网之外，等于把它整个从攻击面上移除（没有撞库、没有暴力破解、不暴露它的登录流 / MFA 配置）。

3. **路径一致性让人容易遗漏。** `master` 走的是和业务 realm **完全相同**的 `/realms/<name>/*` 结构 —— 在 URL 层它毫无特殊。正是这种一致性，导致一条放行全部的 `/realms/*` 规则会**悄悄连 `master` 一起暴露**。显式的 deny 规则把意图写明、可审计，而不是依赖每个人都记得"`/realms/*` 里其实藏着管理超级 realm"。

4. **纵深防御，而非唯一防御。** 即使漏了这条规则，`master` 仍受两道保护：(a) realm 隔离的凭据 —— 业务用户在 `master` 里没有账号；(b) `/admin/*` 拦截 —— 让一张 `master` token 无处可用。这条规则是廉价、显式的最外层，意味着别处某一处配错不会就此暴露超级 realm。

> 登录主题 `login.js` 里的前端 `BLOCKED_REALMS` 黑名单（`master`、`admin`、`www`…）是**UX 防呆，不是安全控制** —— 它只阻止迷糊的用户在租户切换框里输 `master` 然后撞上"client not found"死页面。真正的保护是上面的反代规则加上 realm 隔离的凭据。别把两者混为一谈。

### 后端 → Keycloak 管理凭据（开发捷径 → 生产 service account）

ops 新建租户时，后端要调 Keycloak Admin REST API 去建 realm + 首个管理员，这就需要后端以管理员身份认证到 KC。凭据配在 `app.keycloak.admin`（`application.yml`），各环境用 `CORE_KEYCLOAK_ADMIN_*` 覆盖。

**开发默认（绝不可上生产）：**

```yaml
app:
  keycloak:
    admin:
      realm:     master      # CORE_KEYCLOAK_ADMIN_REALM
      client-id: admin-cli   # CORE_KEYCLOAK_ADMIN_CLIENT_ID
      username:  admin       # CORE_KEYCLOAK_ADMIN_USERNAME
      password:  admin       # CORE_KEYCLOAK_ADMIN_PASSWORD
```

这是 **ROPC（Resource Owner Password Credentials）** 模式：后端重放一个**人类**管理员的用户名+密码。生产三大问题：

1. **ROPC 已被 OAuth 2.1 弃用**且脆弱 —— 改一下 admin 密码，provisioning 就静默崩（正是你遇到的"`admin` 密码漂移 → invalid_grant 400")。
2. **`master` admin 权限无边** —— 能对所有 realm 为所欲为，泄露后爆炸半径最大。
3. 人类凭据本就不该当服务凭据。

**生产：专用 service-account client（`client_credentials`）。** 代码**已支持**——`KeycloakRealmService.newAdminClient()` 在检测到 client secret 时自动切到 `client_credentials` 模式（`AppKeycloakProperties.Admin.isServiceAccount()` ⇔ `client-secret` 非空）。所以切换是**纯配置，无需改代码**：

1. **在 `master` realm 建一个 confidential client**，如 `access-matrix-provisioner`：
   - *Client authentication* = **开**（机密客户端）
   - *Service accounts roles* = **开**（启用 `client_credentials` 模式）
   - *Standard flow* / *Direct access grants* = **关**（纯机器对机器）
   - 复制生成的 **client secret**。

2. **授予最小权限的 service-account 角色**：在该 client 的 *Service account roles* 页，分配 `master` realm 角色 **`create-realm`**。
   - 为什么够用：持有 `create-realm` 的主体创建 realm 后，Keycloak 会**自动**把该 realm 的管理角色（`<realm>-realm` client 角色：`manage-users`、`manage-realm`…）授予这个创建者。于是同一个 service account 随后就能建该租户的管理员、之后也能禁用/删除这个 realm —— 全程不需要那个无所不能的 `master` `admin` 角色。
   - **注意**：这只覆盖 service account **自己创建**的 realm。带外创建的 realm（如启动时导入的 `demo`/`system`，或别的管理员建的）不会带上它的管理角色，因此从平台控制台对**那些** realm 做 suspend/delete/update 会 403。若后端还需管理既有 realm，再额外分配相应的 `<realm>-realm` 角色（或务实但不够紧的做法：给 `master` `admin` 角色）。

3. **让后端指向这个 service account**（环境变量，secret 从 vault 取，切勿提交）：

   ```bash
   CORE_KEYCLOAK_SERVER_URL=https://auth.yourcompany.com
   CORE_KEYCLOAK_ADMIN_REALM=master
   CORE_KEYCLOAK_ADMIN_CLIENT_ID=access-matrix-provisioner
   CORE_KEYCLOAK_ADMIN_CLIENT_SECRET=<从-vault-取>   # 一旦存在即切到 client_credentials
   # CORE_KEYCLOAK_ADMIN_USERNAME / _PASSWORD 留空 —— 会被忽略
   ```

   设 `client-secret` 就是开关：`newAdminClient()` 随即用 `grantType=client_credentials` + secret，完全不碰 username/password。

4. **运维卫生**：secret 放 secrets manager（Vault / AWS Secrets Manager / sealed secret），定期轮换，网络上只让后端能访问 token 端点。要最强姿态，用 mTLS 或 signed-JWT 客户端认证（`private_key_jwt`）替代 client_secret。
