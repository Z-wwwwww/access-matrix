# Getting Started

从零开始本地跑起整个项目。覆盖两种登录场景：

- **最小场景**：用项目自家的 password 登录（5 分钟，无第三方依赖）
- **完整场景**：接入 Keycloak SSO + 邮件 + 邀请流程（生产环境同款）

---

## 1. 前置依赖

| 软件 | 版本 | 说明 |
|---|---|---|
| **JDK** | 25 | 项目用 Java 25（virtual threads、scoped values）。Maven Wrapper 不会自动下 JDK。 |
| **Node.js** | 20+ | 前端 Vite 6 要求。`nvm` 管理推荐。 |
| **PostgreSQL** | 15+ | 主库 + Keycloak 数据 schema 共用 |
| **Redis** | 7+ | 权限缓存、刷新 token、强制下线集合 |
| **Maven** | 3.9+ | 项目自带 `mvnw` wrapper，可不装本地 Maven |

**可选**：

| 软件 | 用途 |
|---|---|
| **Keycloak 26+** | SSO 模式必需（[下载 ZIP](https://www.keycloak.org/downloads)） |
| **Docker Desktop** | 跑 Testcontainers IT；不装的话相关 IT 自动跳过 |
| **psql CLI** | 数据库手动操作；项目内已有 `db/migration/V*.sql` 自动执行 |

---

## 2. 数据库初始化

### 2.1 创建主库

```bash
psql -h 127.0.0.1 -U postgres \
  -c "CREATE DATABASE new_inntouch_core WITH ENCODING 'UTF8' TEMPLATE template0;"
```

> 名字 `new_inntouch_core` 是项目历史遗留命名。如要改，同时改 `application-local.yml` 的 `spring.datasource.url`。

### 2.2 （可选）创建 Keycloak 用 schema

只在准备启用 SSO 时需要：

```bash
psql -h 127.0.0.1 -U postgres -d new_inntouch_core \
  -c "CREATE SCHEMA IF NOT EXISTS keycloak;"
```

Keycloak 用这个 schema 存自己的内部表，跟应用业务表（`public` schema）物理隔离。

### 2.3 Flyway 自动建表

**不需要手动建任何业务表**。后端首次启动时 Flyway 会按 V1 → V22 顺序自动跑 `backend/core-bootstrap/src/main/resources/db/migration/*.sql`：

| 迁移 | 内容 |
|---|---|
| V1-V4 | 元表、用户表、登录日志、采番系统 |
| V5-V9 | RBAC 主表（role / permission / menu / dept / user_role / role_permission / role_menu / role_dept） |
| V10-V19 | demo 数据、菜单图标、多语言菜单、清理 |
| V20 | `(tenant_id, username)` 唯一索引（V20 多租户化） |
| V21 | `core_auth_user.keycloak_id` 链接列（SSO） |
| V22 | `core_user_invite` 邀请 token 表 |

---

## 3. 启动后端（local profile）

```bash
cd backend
./mvnw -pl core-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=local
```

Windows PowerShell：

```powershell
cd backend
.\mvnw.cmd -pl core-bootstrap -am spring-boot:run "-Dspring-boot.run.profiles=local"
```

**期望输出末尾**：

```
============================================================
  CORE-SERVICE is READY
------------------------------------------------------------
  profile        : local
  port           : 9135
  context-path   : /api
  security.mode  : permit-all   (or oidc, depending on application-local.yml)
============================================================
LocalAdminSeeder: ensured admin user (id=...) is bound to SUPER_ADMIN role
```

**首次启动会做的事**：
- 跑 V1-V22 所有迁移
- 种 1 个 `admin/admin` 业务用户 + SUPER_ADMIN 角色绑定 + 本社部门绑定
- 种 5 个 demo 用户（密码 `demo123`）+ 15 条演示 task

---

## 4. 启动前端

```bash
cd frontend
npm install     # 首次
npm run dev
```

浏览器开 http://localhost:5273/login → 输 `admin` / `admin` → 进入系统。

---

## 5. 启用 SSO (Keycloak 模式)

可选但强烈推荐 —— 生产环境基本都用 SSO。

### 5.1 下载 Keycloak

到 https://www.keycloak.org/downloads 拿 **Server 版** ZIP（约 200MB），解压到任意目录：

```
C:\SERVER\keycloak-26.6.2\    (Windows 默认路径，对应启动脚本)
~/tools/keycloak-26.6.2/      (mac/linux)
```

不需要 Docker。Keycloak 是个 Quarkus Java 应用，直接跑就行。

如果路径不同，设环境变量：

```powershell
setx KEYCLOAK_HOME "D:\my-path\keycloak-26.6.2"
```

### 5.2 启动 Keycloak

```powershell
infra\keycloak\start-keycloak.bat
```

```bash
infra/keycloak/start-keycloak.sh
```

启动脚本会做几件事：
- 用 `keycloak` schema 连本机 Postgres（跟应用共用 DB 实例，schema 隔离）
- HTTP 端口 8180（避免跟 Spring Boot 默认 8080 冲突）
- `--import-realm` 自动加载 `infra/keycloak/realms/default-realm.json`（含 `access-matrix-backend` client + `tid` claim mapper）
- 30 秒内启动完成

**首次启动会跑 Liquibase 迁移**（Keycloak 自己的，~30s），后续启动 < 10s。

控制台：http://localhost:8180/admin （`admin` / `admin`）

详细 Keycloak 配置见 [infra/keycloak/README.md](../infra/keycloak/README.md)。

### 5.3 切到 OIDC 模式

编辑 `backend/core-bootstrap/src/main/resources/application-local.yml`：

```diff
app:
  security:
-   mode: permit-all
+   mode: oidc
```

编辑 `frontend/.env.development`：

```diff
- VITE_OIDC_ENABLED=false
+ VITE_OIDC_ENABLED=true
```

**重启**后端 + 前端（前端 `.env` 改动需要重启 vite dev server，不会热刷）。

### 5.4 第一次 SSO 登录

后端启动时 `LocalKeycloakAdminSeeder` 会自动在 Keycloak 的 `default` realm 里建一个 `admin` 用户（密码 `admin`，permanent）。

浏览器 http://localhost:5273/login → 点 **"Sign in with SSO"** → 跳到 Keycloak 登录页 → 输 `admin` / `admin` → 跳回前端，登录成功。

**幕后发生的事**：
1. Keycloak 签发 JWT，`sub` = Keycloak UUID，`tid` = `default`
2. 后端验签通过
3. `OidcJitUserService` 首次见到这个 UUID → 走 bind path → 把 `keycloak_id` 写到 `LocalAdminSeeder` 种的那行 `core_auth_user`
4. `RequestContext.userId` 是业务 ULID，立刻就是超管

后续登录直接走 fast path（按 keycloak_id 命中业务行）。

---

## 6. 启用邮件（可选）

开启后台用户管理的 **invite** 模式（自动给新用户发邀请邮件）。

### 6.1 准备 SMTP 凭据

项目默认配置走腾讯企业邮（`smtp.exmail.qq.com:465 SSL`）。其他 SMTP 服务（Gmail / AWS SES / 公司 SMTP）也可用，改 `CORE_MAIL_HOST` / `CORE_MAIL_PORT` 即可。

腾讯企业邮**必须用客户端专用密码**（不是登录密码）：
1. 登 https://exmail.qq.com
2. 右上角设置 → 安全 → 客户端专用密码 → 新增
3. 拿到 16 位密码（只展示一次）

### 6.2 配置

环境变量方式（推荐）：

```powershell
$env:CORE_MAIL_USERNAME = "your-name@your-company.com"
$env:CORE_MAIL_PASSWORD = "XXXXXXXXXXXXXXXX"  # 16-char app password
$env:CORE_MAIL_ENABLED = "true"
$env:CORE_MAIL_FROM = "your-name@your-company.com"
```

或者写进 `application-local.yml`（**不要提交 password**）：

```yaml
spring:
  mail:
    username: your-name@your-company.com
    password: XXXXXXXXXXXXXXXX
app:
  mail:
    enabled: true
    from: your-name@your-company.com
```

### 6.3 验证

重启后端，看启动 log 应该有：

```
MailHealthIndicator: Status UP
```

不出现 `AuthenticationFailedException: 500 Error: bad syntax` 之类的。

详细使用见 [User Guide](user-guide.md#用户管理invite--direct-模式)。

---

## 7. 跑测试

### 7.1 后端单测（不需要 Docker）

```bash
cd backend
./mvnw test
```

期望：**49 个 core-system + 15 个 core-common = 64 unit tests pass**。

Testcontainers IT（`OidcJitProvisioningIT`、`MultiTenantSchemaIT`）需要 Docker；没装时自动 skip。

### 7.2 前端单测

```bash
cd frontend
npm run test          # vitest 跑 44 个测试
npm run test:e2e      # playwright（需要前后端都在跑）
```

---

## 8. 常见问题排错

### 8.1 后端启动报 `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${CORE_DB_URL}`

**原因**：`SPRING_PROFILES_ACTIVE` 没设，走了 fail-closed 的 prod 默认，但 prod 要求外部环境变量。

**修**：启动时加 `-Dspring-boot.run.profiles=local`，或者设环境变量 `$env:SPRING_PROFILES_ACTIVE = "local"`。

### 8.2 SSO 登录页报 `Invalid parameter: redirect_uri`

**原因**：Keycloak client 的 Valid Redirect URIs 不含前端的 callback 路径。

**修**：进 Keycloak admin → `default` realm → Clients → `access-matrix-backend` → Valid Redirect URIs，加 `http://localhost:5273/*`。

仓库里 `default-realm.json` 已经包含了这个，**只在你改过 redirect URI 或自建 realm 时**才会遇到。

### 8.3 邮件发送报 `500 Error: bad syntax`

**原因**：腾讯企业邮拒绝用登录密码做 SMTP 认证。

**修**：到 ExMail 控制台生成"客户端专用密码"（16 位），替换 `CORE_MAIL_PASSWORD`。

### 8.4 SSO 登录后跳回 /login，URL 带 `?sso_error=1`

**原因**：`SsoCallback.vue` 拿到 code 后跟 Keycloak 换 token 失败。

**排查**：打开 DevTools → Network → 找 `/protocol/openid-connect/token` 这次 POST：
- HTTP 400 `invalid_grant` → state 或 code_verifier 不匹配（一般是浏览器多 tab 同时操作）
- HTTP 401 → client 配置错误，确认 `VITE_OIDC_CLIENT_ID` 跟 Keycloak 里的 Client ID 一致
- 网络错 → Keycloak 没跑，检查 8180 端口

### 8.5 后端启动报 `log4j-slf4j2-impl cannot be present with log4j-to-slf4j`

**原因**：跑测试时引入了 `spring-boot-starter-logging` 的传递依赖。

**修**：3 个 pom 已经加了 exclusion（core-common / core-system / core-bootstrap），不会再遇到。如果你新加了 spring-boot-starter-test 到别的模块，照着做 exclusion 即可。详见 `core-common/pom.xml` 里 `spring-boot-starter-test` 那块。

### 8.6 前端登录页没有 "Sign in with SSO" 按钮

**原因**：`VITE_OIDC_ENABLED=false` 或没设。

**修**：编辑 `frontend/.env.development`，设 `VITE_OIDC_ENABLED=true`，**重启 vite dev server**（`.env*` 改动 vite 不热刷新）。

---

## 9. 下一步

- 📖 [**User Guide**](user-guide.md) — 学会怎么管理用户 / 角色 / 权限 / 数据范围 / 多租户
- 🛠 [**Development**](development.md) — 怎么加新菜单 / 权限 / 数据库迁移
- 🚀 [**Deployment**](deployment.md) — 怎么部到生产
