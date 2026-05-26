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

# 3. 后端（local profile，自动建表 + 种 admin/admin）
cd backend
./mvnw -pl core-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=local

# 4. 前端（另开终端）
cd frontend
npm install && npm run dev
```

浏览器开 http://localhost:5273/login → `admin` / `admin` → 进入系统。

> 默认走传统 password 登录。要启用 SSO（Keycloak），见 [完整启动指南](docs/getting-started.md#启用-sso-keycloak-模式)。

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

模块级（给开发者看）：
- [backend/AGENTS.md](backend/AGENTS.md) — 模块边界 / Flyway / 安全 / API 约定
- [frontend/AGENTS.md](frontend/AGENTS.md) — 组件分层 / 共享组件 / services 约定

---

## 🎬 演示数据

`local` profile 自动种 5 个 demo 用户，演示 5 种数据范围效果：

| 用户 | 角色 | 数据范围 | 看到的 task |
|---|---|---|---|
| `tanaka_taro` | 取締役 | ALL | 全部 4 个部门 |
| `yamada_hanako` | 東京支社長 | DEPT_AND_SUB | 東京 + 子部门京都 |
| `sato_ken` | 大阪支社課長 | DEPT | 仅大阪 |
| `suzuki_misaki` | 一般社員 | SELF | 仅自己创建的 |
| `takahashi_shinichi` | 京都連絡担当 | CUSTOM | role_dept 显式绑定的京都 |

密码统一 `demo123`。详见 [data-scope demo](docs/data-scope-demo.md)。

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
