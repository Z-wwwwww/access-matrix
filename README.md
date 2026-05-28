# Access Matrix

> 企业级 **账号 · 权限 · 多租户** 基盘 — Spring Boot 4 + Vue 3 + PostgreSQL，开箱即用。
>
> Enterprise-ready **identity · RBAC · multi-tenancy** foundation. Drop-in, hackable, MIT licensed.

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
| **认证** | 3 种模式：`permit-all`（测试）/ `jwt`（自家 HS256）/ `oidc`（Keycloak / 任意 OIDC IdP） |
| **SSO** | 接入 Keycloak / Azure AD / Okta，前端走 Authorization Code + PKCE |
| **RBAC** | 用户 / 角色 / 权限 / 菜单 / 部门六张主表，通配权限匹配（`*:*` / `module:*` / 精确） |
| **数据范围** | 5 种 scope（ALL / DEPT_AND_SUB / DEPT / SELF / CUSTOM），切面强制注入 SQL 条件 |
| **多租户** | MyBatis-Plus 拦截器自动注入 `tenant_id`；JWT `tid` claim + `X-Tenant-Id` header 双路径 |
| **用户开通** | 邀请邮件（用户自设密码）/ 直接创建（管理员设临时密码）双模式 |
| **password ↔ SSO 双向迁移** | 改一行 yml + 重启 = 全员自动迁移；**正向**（password→SSO）走 KC 自助改密邮件，**反向**（SSO→password）走自家 reset 落地页；含链接过期重发模式；可选 `auto-on-mode-flip` 只切 mode 自动派发；幂等可回滚；业务 ULID / 角色 / 审计全保留 |
| **Break-Glass 应急凭据** | super-admin 专属"应急密码"；OIDC 模式下日常密码归 KC，应急密码独立存 DB；自家 UI 自助轮换 + 状态显示；KC 挂掉时通过 5 击 hot-zone + `/auth/login` 进入；OIDC 模式下旧 `/admin/auth/reset-password` 自动禁用、用户管理界面对应按钮置灰 |
| **强制下线** | `ForceLogoutService` + Redis 黑名单，权限变更立即生效 |
| **审计** | `@OpLog` 注解 → `core_oplog` 表异步落库 |
| **国际化** | 邮件模板 5 语言（ja_JP / en / zh_CN / zh_TW / ko_KR），UI 同步 |
| **测试** | 120+ 单测（后端 64 + 前端 44 + e2e 12），含 Testcontainers 端到端集成 |

---

## 🚀 5 分钟跑起来（最小场景）

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

> 默认走传统 password 登录。要启用 SSO（Keycloak），见 [完整启动指南](docs/getting-started.md#启用-sso-keycloak-模式)。
> **已有 password 项目要切 SSO**？走自动化迁移：[docs/migration-password-to-sso.md](docs/migration-password-to-sso.md) — 改一行 yml + 重启搞定，业务数据零损失。

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
| [**password → SSO 迁移**](docs/migration-password-to-sso.md) | password 项目零数据损失切到 SSO 的 runbook：自动 mirror + 邮件过期重发 + 5 大坑 + 健康检查 SQL + 回滚步骤 |
| [**SSO → password 迁移**](docs/migration-sso-to-password.md) | 反向 runbook：自家 reset 落地页 + token 表 + 5 语言邮件；含 `auto-on-mode-flip` 自动派发说明 |
| [**Break-Glass 应急凭据**](docs/break-glass.md) | super-admin 独立应急密码：什么是 / 为什么不同步 / 怎么轮换 / 什么时候用 / 威胁模型 |
| [**`system` realm（平台运维）**](docs/system-realm.md) | 平台运营专用的隐形 realm + `PLATFORM_ADMIN` 角色 + MP 拦截器 bypass：跨租户管理的"控制平面"；含「支持会话」(impersonation) 工单处理流程 |

模块级（给开发者看）：
- [backend/AGENTS.md](backend/AGENTS.md) — 模块边界 / Flyway / 安全 / API 约定
- [frontend/AGENTS.md](frontend/AGENTS.md) — 组件分层 / 共享组件 / services 约定

---

## 🎬 初始化与演示数据

`git clone` 后第一次启动（Flyway 迁移 V1-V29 + `--profile=local` 种子 + Keycloak `--import-realm`）拿到的全景：

### Keycloak Realms（`infra/keycloak/realms/*.json` 自动导入）

| Realm | 用途 | 备注 |
|---|---|---|
| `master` | Keycloak 自带 | admin/admin |
| `system` | 平台运营 realm | `tid=system` 的 JWT 触发 MyBatis-Plus 拦截器跨租户 bypass |
| `demo` | 业务示例 realm | 普通业务租户的样板 |

> 新增业务租户走 `infra/keycloak/new-tenant.ps1 -Name <name>`（克隆 `demo-realm.json` 并改 `tid` claim mapper）。

### 业务租户 `core_tenant`

| Tenant ID | Display | 备注 |
|---|---|---|
| `system` | Platform Operations | 跟 system realm 一一对应 |
| `demo` | Demo Tenant | 跟 demo realm 一一对应 |

### 内置角色（is_built_in = 1，UI 上锁，不可编辑/删除）

| 角色 | Tenant | 通配权限 | 持有者 |
|---|---|---|---|
| **SUPER_ADMIN** | demo | `tenant:*`（命中除 `platform:` 外的一切） | 每个业务租户的超管 |
| **Platform Admin** | system | `*:*`（仅命中 `platform:` 命名空间） | SaaS 运营人员 |

> 两个 super-wildcard **对称豁免、互不覆盖** —— 业务超管不能调 `/platform/*`，平台管理员不能假冒业务用户。详见 [`docs/system-realm.md`](docs/system-realm.md) 和 `PermissionMatcher.java`。

### Demo 角色（is_built_in = 0，演示数据范围 5 种 scope，可删可改）

| 角色 | 数据范围 | 演示效果 |
|---|---|---|
| デモ：全範囲 | ALL | 全部 4 个部门 |
| デモ：自部署＋下位 | DEPT_AND_SUB | 東京 + 子部门京都 |
| デモ：自部署のみ | DEPT | 仅大阪 |
| デモ：本人のみ | SELF | 仅自己创建的 |
| デモ：カスタム部署 | CUSTOM | role_dept 显式绑定的京都 |

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

种子代码：`LocalAdminSeeder` / `SystemAdminSeeder` / `DemoSeeder` + 两个 `*KeycloakAdminSeeder`，全部 `@Profile("local")`。详细数据范围对照见 [data-scope demo](docs/data-scope-demo.md)。

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

### password → SSO

```yaml
app:
  security:
    mode: oidc
  migration:
    run-on-startup: password-to-sso     # 首次群发
    tenants: demo
```

重启 → 后端为每个 `core_auth_user` 在 Keycloak realm 建一个无密码用户，触发 KC `executeActionsEmail(UPDATE_PASSWORD)`，用户收到"设置密码"链接。点击 → 在 KC 自助页选新密码 → 首次 SSO 登录 → **bind path** 把 `keycloak_id` 写回原行 + 清掉 `password_hash`（super-admin 例外，保留 break-glass）。业务 ULID / 角色 / 部门 / 审计 / 编号全部保留，最终行状态跟"一直 OIDC 模式"完全一致。

链接过期了？KC 默认 12h 过期 — 改成 `password-to-sso-resend` 重启即可批量补发（仅对未绑用户）。

### SSO → password

```yaml
app:
  security:
    mode: password
  migration:
    run-on-startup: sso-to-password     # 反向迁移
    tenants: demo
```

重启 → 后端为每个 KC 绑定的用户 mint 一个 reset token，MailService 发 5 语言邮件，链接落到自家 `/reset-password/{token}` 页面。点击 → 在我们自己的页面选新密码 → 后端 bcrypt 写 `password_hash` + NULL 掉 `keycloak_id` + `KC.disableUser`。最终行状态跟"一直 password 模式"完全一致。

### 自动派发（可选，opt-in）

如果想极致简化为"只改 mode 一行"：

```yaml
app:
  migration:
    auto-on-mode-flip: true   # 默认 false；显式打开后只切 mode 自动派发对应迁移
```

`ModeFlipDetector` 比对 `core_meta` 里的 last-applied 与当前 mode，自动调对应 service。默认 `false` 是因为在 dev 环境无意切 mode 不应该触发千人邮件 — 每个环境显式开启更安全。

整个过程**幂等**（重复跑安全），**可回滚**（mode 改回 + 重启），并生成 `logs/migration-*-*.json` 报告供审计。完整 runbook：

- 正向：[docs/migration-password-to-sso.md](docs/migration-password-to-sso.md)
- 反向：[docs/migration-sso-to-password.md](docs/migration-sso-to-password.md)

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
