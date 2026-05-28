# Access Matrix

> 企业级 **账号 · 权限 · 多租户** 基盘 — Spring Boot 4 + Vue 3 + PostgreSQL，开箱即用，MIT 许可。

[English](README.md) · **中文**

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883.svg)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-336791.svg)](https://www.postgresql.org/)

```
access-matrix/
├── backend/      Spring Boot 4 (Java 25) — Maven 多模块；auth / RBAC / 数据范围 / 审计
├── frontend/     Vue 3 + Vite 6 + Tailwind v4 — 管理后台 + 业务示例
└── infra/        本地基础设施脚本（Keycloak realm + 启动器）
```

---

## ✨ 主要特性

| 维度 | 实现 |
|---|---|
| **认证** | 3 种模式：`permit-all`（测试）/ `jwt`（自家 HS256）/ `oidc`（Keycloak 或任意 OIDC IdP） |
| **SSO** | 接入 Keycloak / Azure AD / Okta，前端走 Authorization Code + PKCE |
| **RBAC** | 用户 / 角色 / 权限 / 菜单 / 部门六张主表，通配权限匹配（`*:*` / `module:*` / 精确）；支持 2 段与 3 段权限码（`user:read`、`platform:tenant:read`） |
| **数据范围** | 5 种 scope（ALL / DEPT_AND_SUB / DEPT / SELF / CUSTOM），切面强制注入 SQL 条件 |
| **多租户** | 每租户一个 Keycloak realm；MyBatis-Plus 拦截器自动注入 `WHERE tenant_id=?`；JWT `tid` claim + `X-Tenant-Id` header 双路径 |
| **租户生命周期** | 平台运营控制台支持：创建（自动邀请首位 admin）/ 编辑 / 暂停 / 恢复 / 硬删除（回收站模式 + 输入 tenant_code 二次确认） |
| **支持会话 (impersonation)** | 短时 JWT 让运营以租户 SUPER_ADMIN 身份排查工单；RFC 8693 `act` claim + `[support]` 用户名前缀双重留痕 |
| **用户开通** | 邀请邮件（用户自设密码）/ 直接创建（管理员设临时密码）双模式 |
| **password ↔ SSO 双向迁移** | 改一行 yml + 重启 = 全员自动迁移；幂等、可回滚；业务 ULID / 角色 / 审计全保留 |
| **Break-Glass 应急凭据** | super-admin 专属"应急密码"，独立于 IdP；自家 UI 自助轮换；KC 挂掉时通过 5 击 hot-zone + `/auth/login` 进入 |
| **强制下线** | `ForceLogoutService` + Redis 黑名单，权限变更立即生效 |
| **审计** | `@OpLog` 注解 → `core_oplog` 表异步落库 |
| **国际化** | 邮件模板 5 语言（ja_JP / en / zh_CN / zh_TW / ko_KR），UI 同步 |
| **启动期自检** | `TenantSchemaGuard` 检测业务表必须含 `tenant_id` 列且不在错误的 EXCLUDED 名单；`PermissionConsistencyGuard` 自动同步 Java 常量与 DB 权限行 |
| **测试** | 后端 180 + 前端 64，含 Testcontainers 端到端集成 |

---

## 🚀 5 分钟跑起来

**前置**：JDK 25、Node 20+、PostgreSQL 15+、Redis 7+。

```bash
# 1. clone
git clone <your-fork-url> access-matrix && cd access-matrix

# 2. 建数据库
psql -h 127.0.0.1 -U postgres \
  -c "CREATE DATABASE new_inntouch_core WITH ENCODING 'UTF8' TEMPLATE template0;"

# 3. 后端（local profile，自动建表 + 种 demo-admin/demo-admin）
cd backend
./mvnw -pl core-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=local

# 4. 前端（另开终端）
cd frontend
npm install && npm run dev
```

浏览器开 http://localhost:5273/login → `demo-admin` / `demo-admin` → 进入系统。

> 默认走自家 password 登录。要启用 SSO（Keycloak），见 [完整启动指南](docs/getting-started.md#5-启用-sso-keycloak-模式)。
> **已有 password 项目要切 SSO**？走自动化迁移：[docs/migration-password-to-sso.md](docs/migration-password-to-sso.md)，改一行 yml + 重启搞定，业务数据零损失。

---

## 🏢 租户生命周期（平台运营）

平台运营侧（`system` realm）有一个 `/platform/tenants` 控制台。以 `ops/ops` 经 `?tenant=system` 登入，可以：

```
                    暂停                  硬删除
                    (确认对话框)          (输入 tenantCode 二次确认)
   ┌──────────┐  ─────────────►  ┌──────────┐  ────────────►  ☒ 永久删除
   │  active  │                  │ suspended│
   └──────────┘  ◄─────────────  └──────────┘
                     恢复
```

| 操作 | 实际动作 | 可逆 |
|---|---|---|
| **创建** | Keycloak realm + 注册行 + 采番计数器 + RBAC 脚手架（role/perm/menu）+ 首位 admin 用户 + 邀请邮件 — 一个事务搞定 | — |
| **编辑** | 修改 displayName / contactEmail；KC realm displayName 同步 | 可 |
| **暂停** | `status=0` + KC realm 禁用；租户仍在列表中带"停止"徽标 | 可（恢复） |
| **恢复** | `status=1` + KC realm 重新启用 | — |
| **硬删除** | 删光所有 per-tenant 业务表行 + KC realm + 注册行。**只对 suspended 行可调**，必须输入 tenant_code 二次确认 | **不可** — 永久 |
| **支持会话** | 铸造 30 分钟 JWT 让 ops 以租户 SUPER_ADMIN 身份操作；用户名带 `[support]` 前缀 + JWT 内嵌 RFC 8693 `act` claim 双重留痕 | 横幅 Terminate 即终止 |

完整设计（审计姿态、KC/DB 操作顺序、支持会话机制）见 [docs/system-realm.md](docs/system-realm.md)。

---

## 📚 文档

| 文档 | 内容 |
|---|---|
| [**Getting Started**](docs/getting-started.md) | 详细安装引导（PG / Redis / Keycloak / 排错） |
| [**User Guide**](docs/user-guide.md) | 使用手册（登录 / 用户管理 / 角色 / 权限 / 数据范围 / 多租户） |
| [**Development**](docs/development.md) | 开发指南（项目结构 / 加菜单 / 加权限 / 测试规范） |
| [**Deployment**](docs/deployment.md) | 生产部署（环境变量 / Keycloak / Postgres / Redis） |
| [Contributing](CONTRIBUTING.md) | 贡献指南、Conventional Commits、PR 规范 |
| [data-scope demo](docs/data-scope-demo.md) | 5 种数据范围实际效果演示（5 个 demo 用户） |
| [Keycloak setup](infra/keycloak/README.md) | 本地 Keycloak 启动 + realm 配置 |
| [**`system` realm（平台运维）**](docs/system-realm.md) | 平台运营专用的隐形 realm + 租户生命周期 + 支持会话 |
| [**Break-Glass 应急凭据**](docs/break-glass.md) | super-admin 独立应急密码 |
| [**password → SSO 迁移**](docs/migration-password-to-sso.md) | password 项目零数据损失切到 SSO 的 runbook |
| [**SSO → password 迁移**](docs/migration-sso-to-password.md) | 反向 runbook |

模块级（给开发者看）：
- [backend/AGENTS.md](backend/AGENTS.md) — 模块边界 / Flyway / 安全 / API 约定
- [frontend/AGENTS.md](frontend/AGENTS.md) — 组件分层 / 共享组件 / services 约定

---

## 🎬 初始化与种子数据（`git clone` 后首次启动的全景）

### Keycloak Realms（`infra/keycloak/realms/*.json` 自动导入）

| Realm | 用途 | 备注 |
|---|---|---|
| `master` | Keycloak 自带 | admin/admin |
| `system` | 平台运营 | `tid=system` 的 JWT 触发 MyBatis-Plus 拦截器跨租户 bypass |
| `demo` | 业务示例 | 新租户的模板 |

> 新增业务租户已不再需要手敲 shell —— 直接在 `/platform/tenants` 上 GUI 操作（克隆 realm + seed DB + 发邀请邮件全自动）。

### 业务租户 `core_tenant`

| Tenant ID | Display | 备注 |
|---|---|---|
| `system` | Platform Operations | 与 system realm 一一对应 |
| `demo` | Demo Tenant | 与 demo realm 一一对应 |

### 内置角色（`is_built_in=1`，UI 上锁，不可编辑/删除）

| 角色 | Tenant | 通配权限 | 持有者 |
|---|---|---|---|
| **SUPER_ADMIN** | 每个业务租户 | `tenant:*`（命中除 `platform:` 外的一切） | 每个业务租户的超管 |
| **Platform Admin** | `system` only | `*:*`（仅命中 `platform:` 命名空间） | SaaS 运营人员 |

> 两个 super-wildcard **对称豁免、互不覆盖** —— 业务超管不能调 `/platform/*`，平台管理员不能随意假冒业务用户（必须走带审计的"支持会话"）。

### 种子用户（仅 `@Profile("local")` 创建；prod / dev 部署完全空表）

| Realm | 账号 | 密码 | 角色 / 部门 |
|---|---|---|---|
| system | `ops` | `ops` | Platform Admin |
| demo | `demo-admin` | `demo-admin` | SUPER_ADMIN / HQ |
| demo | `tanaka_taro` | `demo123` | 取締役（ALL）/ HQ |
| demo | `yamada_hanako` | `demo123` | 東京支社長（DEPT_AND_SUB）/ TOKYO |
| demo | `sato_ken` | `demo123` | 大阪支社課長（DEPT）/ OSAKA |
| demo | `suzuki_misaki` | `demo123` | 一般社員（SELF）/ TOKYO |
| demo | `takahashi_shinichi` | `demo123` | 京都連絡担当（CUSTOM→京都）/ HQ |

种子代码：`LocalAdminSeeder` / `SystemAdminSeeder` / `DemoSeeder` + 两个 `*KeycloakAdminSeeder`。

---

## 🛠 技术栈

| 层 | 技术 |
|---|---|
| **Backend** | Java 25 / Spring Boot 4.0 / Spring Security 7 / MyBatis-Plus 3.5 / Flyway 11 |
| **Frontend** | Vue 3.5 / Vite 6 / Tailwind v4 / Radix Vue / Pinia / vue-i18n |
| **Database** | PostgreSQL 15+ (JSONB) |
| **Cache / Session** | Redis 7 (Lettuce) / Caffeine 3 |
| **Auth** | OIDC (Keycloak 推荐) / HS256 JWT / BCrypt + HIBP |
| **Testing** | JUnit 5 / Mockito / Testcontainers / Vitest / Playwright |
| **Logging** | Log4j2 async (Disruptor) |

---

## 🏗 架构总览

```
                    ┌─────────────────────┐
                    │   Vue 3 SPA         │  :5273
                    │  (Vite dev / Nginx) │
                    └──────────┬──────────┘
                               │
                               ▼  Bearer JWT + X-Tenant-Id
                    ┌─────────────────────┐
                    │  Spring Boot 4      │  :9135 /api
                    │  ├─ Resource Server │
                    │  ├─ RBAC + Scope    │
                    │  └─ MyBatis-Plus    │
                    └─────────┬──┬────────┘
                              │  │
                ┌─────────────┘  └─────────────┐
                ▼                              ▼
       ┌────────────────┐            ┌────────────────┐
       │  PostgreSQL    │            │  Redis 7       │
       │  - core_*      │            │  - permission  │
       │  - business_*  │            │    cache       │
       │  - keycloak    │            │  - refresh tok │
       └────────────────┘            │  - kickout set │
                                     └────────────────┘
                ▲                              ▲
                │                              │
                └─────────────┬────────────────┘
                              │
                    ┌─────────┴───────────┐
                    │  Keycloak 26        │  :8180
                    │  (OIDC IdP, 1 realm │
                    │   per tenant)       │
                    └─────────────────────┘
```

每个租户 = 一个 Keycloak realm。用户在 Keycloak 登录拿 JWT，业务侧根据 JWT 的 `tid` claim 自动隔离数据。

---

## 🔄 mode 切换零数据损失（双向自动迁移）

支持 **password ↔ SSO 任意方向**切换，业务数据 0 损失，每个用户只需要"重新设一次密码"一个动作。

```yaml
app:
  security:
    mode: oidc                          # 原本: password
  migration:
    run-on-startup: password-to-sso     # 或: sso-to-password
    tenants: demo
```

重启 → 后端为每个 `core_auth_user` 在对应 Keycloak realm 建一个无密码用户（或反过来），触发 KC `executeActionsEmail(UPDATE_PASSWORD)`（或 mint reset token + 发自家 5 语言邮件），首次 SSO 登录走 bind path 把 `keycloak_id` 写回原行 + 清掉 `password_hash`（或相反）。整个过程**幂等**、**可回滚**、**留 logs/migration-*.json 审计报告**。

完整 runbook：
- [docs/migration-password-to-sso.md](docs/migration-password-to-sso.md)
- [docs/migration-sso-to-password.md](docs/migration-sso-to-password.md)

---

## 🤝 贡献

PR 欢迎。请按 [Conventional Commits](https://www.conventionalcommits.org/) 写 commit message，`scope` 用 `backend` / `frontend` / `infra` / `docs`：

```
feat(backend): add SAML 2.0 support
fix(frontend): close menu drawer on route change
docs(getting-started): clarify Keycloak port conflict
```

详见 [CONTRIBUTING.md](CONTRIBUTING.md)。

---

## 📄 License

[MIT](LICENSE) © Access Matrix contributors.

依赖三方组件遵循各自 License。Keycloak 是 Apache 2.0，Spring Boot 是 Apache 2.0，PostgreSQL 是 PostgreSQL License。
