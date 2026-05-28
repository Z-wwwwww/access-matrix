# Access Matrix

> Enterprise-ready **identity · RBAC · multi-tenancy** foundation. Drop-in, hackable, MIT licensed.

**English** · [中文](README.zh-CN.md)

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Java](https://img.shields.io/badge/Java-25-orange.svg)](https://openjdk.org/projects/jdk/25/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Vue](https://img.shields.io/badge/Vue-3.5-42b883.svg)](https://vuejs.org/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15+-336791.svg)](https://www.postgresql.org/)

```
access-matrix/
├── backend/      Spring Boot 4 (Java 25) — Maven multi-module; auth / RBAC / data-scope / audit
├── frontend/     Vue 3 + Vite 6 + Tailwind v4 — admin console + example app
└── infra/        Local infrastructure (Keycloak realms + launcher scripts)
```

---

## ✨ Features

| Area | What you get |
|---|---|
| **Authentication** | Three modes — `permit-all` (tests) / `jwt` (in-house HS256) / `oidc` (Keycloak or any OIDC IdP) |
| **SSO** | Plugs into Keycloak / Azure AD / Okta via Authorization Code + PKCE |
| **RBAC** | Six-table model (user / role / permission / menu / dept / bindings) with wildcard matching (`*:*` / `module:*` / exact); 2- and 3-segment permission codes (`user:read`, `platform:tenant:read`) |
| **Data scopes** | Five modes (ALL / DEPT_AND_SUB / DEPT / SELF / CUSTOM); SQL conditions injected at the data-mapper layer |
| **Multi-tenancy** | One Keycloak realm per tenant; MyBatis-Plus interceptor auto-injects `WHERE tenant_id=?`; JWT `tid` claim + `X-Tenant-Id` header dual-path |
| **Tenant lifecycle** | Platform console with create (auto-invites first admin) / edit / suspend / resume / hard delete (recycle-bin model with typed confirmation) |
| **Support sessions** | Short-lived JWT impersonation for platform-ops triage, with RFC 8693 `act` claim + `[support]` audit prefix |
| **User onboarding** | Invite-by-email (set your own password) or direct create (admin sets a temporary one) |
| **Password ↔ SSO migration** | Change one line of yml + restart → automatic migration in either direction. Idempotent, rollback-able, business ULIDs / roles / audit preserved |
| **Break-glass credential** | Super-admin-only emergency password kept independently of the IdP; self-service rotation; survives Keycloak outages via a 5-click hot-zone + `/auth/login` |
| **Force-logout** | `ForceLogoutService` + Redis blocklist; permission changes take effect immediately |
| **Audit** | `@OpLog` annotation → `core_oplog` table written async |
| **i18n** | Mail templates in 5 languages (ja_JP / en / zh_CN / zh_TW / ko_KR); UI in step |
| **Self-checking startup** | `TenantSchemaGuard` refuses to boot if any business table is missing its `tenant_id` column or is wrongly excluded; `PermissionConsistencyGuard` keeps DB permission rows in sync with Java constants |
| **Tests** | 180 backend + 64 frontend, plus Testcontainers integration tests |

---

## 🚀 Five-minute quick start

**Prerequisites**: JDK 25, Node 20+, PostgreSQL 15+, Redis 7+.

```bash
# 1. Clone
git clone <your-fork-url> access-matrix && cd access-matrix

# 2. Create the DB
psql -h 127.0.0.1 -U postgres \
  -c "CREATE DATABASE new_inntouch_core WITH ENCODING 'UTF8' TEMPLATE template0;"

# 3. Backend (local profile, auto-migrates + seeds demo-admin/demo-admin)
cd backend
./mvnw -pl core-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=local

# 4. Frontend (in another terminal)
cd frontend
npm install && npm run dev
```

Open http://localhost:5273/login → `demo-admin` / `demo-admin` → you're in.

> Default flow is in-house password login. To enable SSO via Keycloak see [Getting Started](docs/getting-started.md#5-enable-sso-keycloak-mode). To migrate an existing password-mode deploy to SSO without data loss: [docs/migration-password-to-sso.md](docs/migration-password-to-sso.md).

---

## 🏢 Platform tenant lifecycle

The platform-ops side of the SaaS (the `system` realm) gets a tenant-management console at `/platform/tenants`. Sign in as `ops/ops` via `?tenant=system` and you can:

```
                    suspend             hard delete
                    (confirm)           (type tenantCode)
   ┌──────────┐  ─────────────►  ┌──────────┐  ────────────►  ☒ gone forever
   │  active  │                  │ suspended│
   └──────────┘  ◄─────────────  └──────────┘
                     resume
```

| Operation | What happens | Reversible |
|---|---|---|
| **Create** | Keycloak realm + registry row + numbering counter + RBAC scaffolding (role/perm/menu) + first admin user + invite email — all in one transaction | n/a |
| **Edit** | Patch displayName / contactEmail; KC realm displayName updated in lockstep | yes |
| **Suspend** | `status=0` + KC realm disabled; tenant stays visible with badge | yes (Resume) |
| **Resume** | `status=1` + KC realm re-enabled | n/a |
| **Hard delete** | Drops business data across every per-tenant table + KC realm + registry row. Only callable on suspended tenants; requires typing the tenant_code to confirm | **no** — irreversible |
| **Support session** | Mints a 30-min JWT impersonating the tenant's SUPER_ADMIN; `[support]` username prefix + RFC 8693 `act` claim provide audit trail | terminate from banner |

See [docs/system-realm.md](docs/system-realm.md) for the full design — audit posture, KC ordering rationale, support session mechanics.

---

## 📚 Docs

| Doc | Content |
|---|---|
| [**Getting Started**](docs/getting-started.md) | Detailed install walkthrough (PG / Redis / Keycloak / troubleshooting) |
| [**User Guide**](docs/user-guide.md) | How to use the admin console (login / users / roles / permissions / data scopes / multi-tenancy) |
| [**Development**](docs/development.md) | Adding menus / permissions / migrations; testing conventions; module layout |
| [**Deployment**](docs/deployment.md) | Production deployment (env vars / Keycloak / Postgres / Redis) |
| [Contributing](CONTRIBUTING.md) | Conventional Commits, PR rules, branch model |
| [data-scope demo](docs/data-scope-demo.md) | The five data-scope modes demonstrated with five seeded users |
| [Keycloak setup](infra/keycloak/README.md) | Local Keycloak launcher + realm config |
| [**`system` realm**](docs/system-realm.md) | The hidden platform-ops realm; tenant lifecycle + support sessions |
| [**Break-glass**](docs/break-glass.md) | Super-admin emergency credential model |
| [**password → SSO migration**](docs/migration-password-to-sso.md) | Zero-data-loss password→SSO runbook |
| [**SSO → password migration**](docs/migration-sso-to-password.md) | Reverse migration runbook |

Module-level (for developers):
- [backend/AGENTS.md](backend/AGENTS.md) — module boundaries, Flyway, security, API conventions
- [frontend/AGENTS.md](frontend/AGENTS.md) — component layering, shared components, services conventions

---

## 🎬 Initial state (out-of-the-box after `git clone` + first start)

### Keycloak realms (auto-imported from `infra/keycloak/realms/*.json`)

| Realm | Purpose | Note |
|---|---|---|
| `master` | Keycloak's own | admin/admin |
| `system` | Platform-ops | JWTs with `tid=system` trigger the MP cross-tenant bypass |
| `demo` | Example business tenant | Template for new tenants |

> New business tenants are provisioned end-to-end from `/platform/tenants` (realm cloned + DB seeded + invite emailed) — no shell needed.

### Tenants in `core_tenant`

| Tenant ID | Display | Note |
|---|---|---|
| `system` | Platform Operations | 1:1 with system realm |
| `demo` | Demo Tenant | 1:1 with demo realm |

### Built-in roles (`is_built_in=1`, locked from UI edit/delete)

| Role | Tenant | Wildcard | Holder |
|---|---|---|---|
| **SUPER_ADMIN** | every business tenant | `tenant:*` (everything except the `platform:` namespace) | Each business tenant's super admin |
| **Platform Admin** | `system` only | `*:*` (the `platform:` namespace only) | SaaS operators |

> The two super-wildcards have **symmetric carve-outs** — a business super-admin can't reach `/platform/*`, a platform admin can't impersonate business users casually (they have to start a support session, which is audited).

### Seed users (only on `@Profile("local")`; prod/dev deployments start empty)

| Realm | Username | Password | Role / Department |
|---|---|---|---|
| system | `ops` | `ops` | Platform Admin |
| demo | `demo-admin` | `demo-admin` | SUPER_ADMIN / HQ |
| demo | `tanaka_taro` | `demo123` | 取締役 (ALL scope) / HQ |
| demo | `yamada_hanako` | `demo123` | 東京支社長 (DEPT_AND_SUB) / TOKYO |
| demo | `sato_ken` | `demo123` | 大阪支社課長 (DEPT) / OSAKA |
| demo | `suzuki_misaki` | `demo123` | 一般社員 (SELF) / TOKYO |
| demo | `takahashi_shinichi` | `demo123` | 京都連絡担当 (CUSTOM) / HQ |

Seeders: `LocalAdminSeeder` / `SystemAdminSeeder` / `DemoSeeder` plus two `*KeycloakAdminSeeder`s.

---

## 🛠 Tech stack

| Layer | Choice |
|---|---|
| **Backend** | Java 25 / Spring Boot 4.0 / Spring Security 7 / MyBatis-Plus 3.5 / Flyway 11 |
| **Frontend** | Vue 3.5 / Vite 6 / Tailwind v4 / Radix Vue / Pinia / vue-i18n |
| **Database** | PostgreSQL 15+ (JSONB) |
| **Cache / session** | Redis 7 (Lettuce) / Caffeine 3 |
| **Auth** | OIDC (Keycloak recommended) / HS256 JWT / BCrypt + HIBP |
| **Testing** | JUnit 5 / Mockito / Testcontainers / Vitest / Playwright |
| **Logging** | Log4j2 async (Disruptor) |

---

## 🏗 Architecture

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

One tenant = one Keycloak realm. Users authenticate against Keycloak, get a JWT, and the backend's MyBatis-Plus interceptor isolates per-tenant data via the JWT's `tid` claim.

---

## 🔄 Zero-data-loss mode switching

Switch between **password ↔ SSO** in either direction. Each user only has to set a password once; everything else (ULIDs, roles, departments, audit, numbering) stays put.

```yaml
app:
  security:
    mode: oidc                          # was: password
  migration:
    run-on-startup: password-to-sso     # or: sso-to-password
    tenants: demo
```

Restart → the backend mirrors each `core_auth_user` into the right Keycloak realm (or the reverse), fires `executeActionsEmail(UPDATE_PASSWORD)` (or mints reset tokens + sends self-hosted reset mail), and binds the rows on first SSO login (or NULLs `keycloak_id` after password reset). Idempotent + reversible + audited.

Detail in [docs/migration-password-to-sso.md](docs/migration-password-to-sso.md) and [docs/migration-sso-to-password.md](docs/migration-sso-to-password.md).

---

## 🤝 Contributing

PRs welcome. Please follow [Conventional Commits](https://www.conventionalcommits.org/) and use `backend` / `frontend` / `infra` / `docs` as the scope:

```
feat(backend): add SAML 2.0 support
fix(frontend): close menu drawer on route change
docs(getting-started): clarify Keycloak port conflict
```

See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## 📄 License

[MIT](LICENSE) © Access Matrix contributors.

Third-party components retain their own licenses — Keycloak is Apache 2.0, Spring Boot is Apache 2.0, PostgreSQL is the PostgreSQL License.
