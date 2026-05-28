# backend — Access Matrix Foundation

Multi-module Spring Boot 4 / JDK 25 platform foundation: authentication,
RBAC, data-scope, multi-tenancy, audit. Naming is business-neutral
(`core-*`, `com.platform.core.*`, `CORE_*`) so the foundation can host
multiple downstream business modules.

> User-facing docs (install / usage / deployment) live in [/docs](../docs/).
> This README is the **backend-engineer reference** — modules, profiles,
> schema, endpoint inventory, JVM flags, locked design decisions.

---

## Tech stack

| Layer        | Choice                                                              |
| ------------ | ------------------------------------------------------------------- |
| Language     | Java 25 LTS (virtual threads, scoped values)                        |
| GC           | Generational ZGC                                                    |
| Framework    | Spring Boot 4.0 (Spring 7, Spring Security 7)                       |
| Web          | Tomcat 11 (Jakarta EE 11), virtual threads enabled                  |
| Persistence  | MyBatis-Plus 3.5.16 (`mybatis-plus-spring-boot4-starter`)           |
| Database     | PostgreSQL 15+ (`new_inntouch_core`), JSONB for i18n menu titles    |
| Migrations   | Flyway 11 (`flyway-core` + `flyway-database-postgresql`)            |
| L1 cache     | Caffeine 3 via Spring Cache                                         |
| L2 / session | Redis 7 (Lettuce), Spring Data Redis 4                              |
| Auth         | Spring Security 7 OAuth2 Resource Server. Three modes: `permit-all` / `jwt` (HS256 in-house) / `oidc` (Keycloak or any OIDC IdP) |
| IdP integ.   | `keycloak-admin-client` for backend-driven user provisioning        |
| Mail         | `spring-boot-starter-mail` + Freemarker, 5-language templates       |
| Rate limit   | Bucket4j 8.10 in-memory per-IP, applied to `/auth/*`                |
| Password     | BCrypt cost 12 + HIBP k-anonymity check                             |
| IDs          | Monotonic ULID (CHAR(26))                                           |
| Logging      | Log4j2 async (Disruptor 4, 256k ring)                               |
| API docs     | springdoc-openapi 3                                                 |
| JSON         | Jackson 3 (`tools.jackson.*`) + Blackbird                           |

---

## Module layout

```
core-parent  (pom)
├── core-common          JsonResult / PageResult / ErrorCode / BusinessException
│                        RequestContext (ThreadLocal, virtual-thread safe) / IdGenerator
├── core-infrastructure  security / web / persistence / cache / mail / keycloak admin
│                        OidcUserResolver SPI, JwtDecoder beans, MailService,
│                        KeycloakUserService, NumberingService
├── core-system          system functions: auth / user / role / menu / dept / oplog
│                        OidcJitUserService, InviteTokenService, UserAdminService
│                        with INVITE / DIRECT provision modes
├── business-demo        示例业务模块：task — 演示 5 种 data scope 效果
└── core-bootstrap       Spring Boot entrypoint (main, ComponentScan, application*.yml,
                         log4j2-spring.xml, Flyway V1–V22, LocalAdminSeeder,
                         LocalKeycloakAdminSeeder, DemoSeeder)
```

Dependency direction (strict):

```
core-bootstrap
   ↓ depends on
core-system, business-demo          (siblings; do not depend on each other)
   ↓ depends on
core-infrastructure
   ↓ depends on
core-common
```

Adding a new business module → sibling of `business-demo`. Adding a new
system function → subpackage of `core-system`. See
[../docs/development.md](../docs/development.md) for the full guide.

---

## Profiles

| Profile | Default `security.mode` | DB / Redis / JWT source |
| ------- | ----------------------- | ----------------------- |
| `local` | `permit-all` (overridable to `oidc`) | hardcoded localhost (PG `abcd@1234`, Redis db=1) |
| `dev`   | `jwt` or `oidc`         | env: `CORE_DB_URL/USERNAME/PASSWORD`, `CORE_REDIS_*`, `CORE_JWT_SECRET` |
| `test`  | `jwt`                   | env (same shape as dev) |
| `prod`  | `jwt` or `oidc`         | env, `CORE_JWT_SECRET` ≥ 32 bytes, Redis SSL on |

Non-local profiles fail-fast at startup if `CORE_JWT_SECRET` is missing
or under 32 bytes — no silent fallback to dev placeholder.

`local` profile additionally runs:
- `LocalAdminSeeder` (always) — seeds business-side `demo-admin/demo-admin`
  user bound to SUPER_ADMIN role + HQ department
- `LocalKeycloakAdminSeeder` (only when `mode=oidc`) — seeds Keycloak's
  `demo` realm with a `demo-admin/demo-admin` user, paired with the above
  so the first SSO login JIT-binds to the SUPER_ADMIN row

See [../docs/getting-started.md](../docs/getting-started.md) for the
full local / Keycloak setup walkthrough.

---

## Endpoint inventory

| Method | Path                              | Auth        | Notes                                      |
| ------ | --------------------------------- | ----------- | ------------------------------------------ |
| GET    | `/api/health`                     | open        | app + profile + timestamp                  |
| POST   | `/api/auth/login`                 | open        | username / email / user_no (password mode) |
| POST   | `/api/auth/refresh`               | refresh tok | single-use rotation                        |
| POST   | `/api/auth/logout`                | refresh tok | revoke + clear cookie                      |
| GET    | `/api/auth/invite/{token}`        | open        | OIDC mode — invite landing probe           |
| POST   | `/api/auth/invite/{token}`        | open        | OIDC mode — consume + set permanent password |
| GET    | `/api/user/me`                    | Bearer JWT  | full profile                               |
| GET    | `/api/admin/users`                | `user:read` | paginated list, optionally filtered by dept |
| POST   | `/api/admin/users`                | `user:create` | invite / direct mode in body (`mode`)    |
| PUT    | `/api/admin/users/{id}`           | `user:update` | …                                        |
| DELETE | `/api/admin/users/{id}`           | `user:delete` | soft-delete + Keycloak delete + kickout  |
| PUT    | `/api/admin/users/{id}/roles`     | `user:update` | reassign roles                           |
| GET    | `/api/admin/roles`                | `role:read` | …                                          |
| POST   | `/api/admin/auth/unlock`          | `auth:unlock` or `*:*` |                                |
| POST   | `/api/admin/auth/reset-password`  | `auth:reset-password` or `*:*` | enforces password policy + HIBP |
| GET    | `/api/swagger-ui.html`            | open        | OpenAPI 3 UI                               |
| GET    | `/api/actuator/health`            | open        | probes enabled                             |
| GET    | `/api/v3/api-docs`                | open        | OpenAPI 3 JSON                             |

Error envelope is consistent across all endpoints:

```json
{"code": <int>, "msg": "<string>", "data": <T | null>}
```

| Code | Meaning                  |
| ---- | ------------------------ |
| 0    | success                  |
| 400  | bad request              |
| 401  | unauthenticated / invalid token |
| 403  | forbidden / account disabled |
| 404  | not found                |
| 429  | rate-limited (`/auth/*`) |
| 500  | server error             |
| 700  | generic business error   |
| 701  | validation failed        |
| 702  | optimistic lock conflict |
| 703  | resource in use (IN_USE) — caller may retry with `?force=true` |
| 710  | missing tenant context   |
| 720  | external service error   |
| 730  | account locked           |

---

## Schema

Flyway migrations under `core-bootstrap/src/main/resources/db/migration/`:

| Version | Tables / changes                                              |
| ------- | ------------------------------------------------------------- |
| V1      | `core_meta`                                                   |
| V2      | `core_auth_user` (username / email / user_no login, JSONB roles/auths) |
| V3      | `core_auth_login_log` (async audit)                           |
| V4      | `core_numbering_management` / `_key` + `USER` numbering seed  |
| V5      | `core_rbac_role` / `_permission` / `_role_permission` + seed SUPER_ADMIN |
| V6      | `core_rbac_menu` + `_role_menu` + seed system/oplog menu      |
| V7      | `core_rbac_dept` + `_role_dept` + seed HQ / TOKYO / OSAKA     |
| V8      | `core_oplog` (audit log)                                      |
| V9      | RBAC foreign keys                                             |
| V10     | `demo_task` + KYOTO dept (5-scope demo)                       |
| V11     | `core_rbac_menu.is_pinned`                                    |
| V12     | menu icons (Lucide name strings)                              |
| V13     | clean up wildcard permissions                                 |
| V14     | restore SUPER_ADMIN `*:*` wildcard binding                    |
| V15     | demo JP data                                                  |
| V16     | 5 demo users (`tanaka_taro` …) for scope demo                 |
| V17     | `core_rbac_menu.title_i18n JSONB`                             |
| V18     | DROP `core_rbac_role.code` (ULID lookup via BuiltInRoles)     |
| V19     | DROP `core_auth_user.{roles,authorities}` JSONB (use joins)   |
| V20     | `(tenant_id, username)` partial unique (multi-tenant)         |
| V21     | `core_auth_user.keycloak_id` link (OIDC JIT binding)          |
| V22     | `core_user_invite` (single-use invite tokens, hash-only)      |

`AuthSchemaBootstrap` re-runs V1-V4 DDL idempotently as a safety net for
dirty `flyway_schema_history` situations.

### Table naming convention

| Prefix | Owner | Examples |
| ------ | ----- | -------- |
| `core_` | platform (`core-system`, `core-infrastructure`) | `core_auth_user`, `core_rbac_role`, `core_oplog`, `core_user_invite`, `core_meta` |
| `demo_` | example business module (`business-demo`) | `demo_task` |
| `<biz>_` | your business modules | `pms_reservation`, `iot_device`, … |

`flyway_schema_history` and `keycloak` schema are exempt.

---

## JVM flags

The Spring Boot Maven plugin wires these into `mvn spring-boot:run`,
and prod `java -jar` should match:

```
-XX:+UseZGC
-XX:MaxRAMPercentage=75.0
-Xms512m
-XX:+AlwaysPreTouch
-Dfile.encoding=UTF-8
--enable-native-access=ALL-UNNAMED
-XX:+EnableDynamicAgentLoading
```

---

## Tests

```bash
./mvnw test                                                # full suite
./mvnw -pl core-system test -Dtest='RoleAdminServiceDeleteTest'
./mvnw -pl core-bootstrap test -Dtest='OidcJitProvisioningIT'   # needs Docker
```

| Module | Unit tests | Integration tests (Testcontainers) |
| ------ | ---------- | ---------------------------------- |
| core-common | 15 (PermissionMatcher / BuiltInRoles / RequestContext) | — |
| core-system | 49 (Role/Dept/User delete, DataScope, PermissionGuard, OidcJit, InviteToken) | — |
| core-bootstrap | 3 (LocalKeycloakAdminSeeder) | 2 (MultiTenantSchemaIT, OidcJitProvisioningIT) |

Integration tests gated with `@Testcontainers(disabledWithoutDocker = true)` —
no Docker → auto-skip, build stays green.

---

## Locked design decisions

| ID         | Decision                                                                 |
| ---------- | ------------------------------------------------------------------------ |
| AUTH-M-01  | Three security modes: `permit-all`, `jwt`, `oidc` — switchable per profile |
| AUTH-M-02  | OIDC JIT provisioning: first JWT for an unknown sub → INSERT core_auth_user, bind by username if a legacy row exists |
| AUTH-P-01  | BCrypt cost 12 (jwt mode)                                                |
| AUTH-P-02  | Password minimum length 8 (PCI 8.3)                                      |
| AUTH-P-03  | Require all 4 character classes                                          |
| AUTH-P-04  | HIBP enabled, fail-open if HIBP unreachable                              |
| AUTH-I-01  | Login identifier accepts `username` / `email` / `user_no` interchangeably |
| AUTH-B-06  | Admin unlock endpoint + audit log                                        |
| AUTH-P-07  | Password self-service delegated to Keycloak Account Console (oidc mode)  |
| INV-01     | Invite token stored as SHA-256 hash only; cleartext lives in email URL  |
| INV-02     | Invite tokens single-use, 7-day TTL (overridable via `app.invite.token-ttl`) |
| RBAC-01    | Permission wildcards: `*:*` / `resource:*` / exact match                 |
| RBAC-02    | Multi-role data scope = UNION (more permissive)                          |
| MT-01      | Every business table has `tenant_id` column + partial unique indexes scoped to `(tenant_id, ...)` WHERE mark=1 |
| MT-02      | Hand-written `@Select` SQL MUST include explicit `tenant_id = #{tenantId}` filter |
| LOGIC-01   | Soft delete: `UpdateWrapper.set("mark", 0)` — never `setMark(0) + updateById` |
| naming     | `core-*` / `com.platform.core.*` / `CORE_*` — business-neutral foundation |
| ID strategy| Monotonic ULID over UUID for B+Tree locality                             |
| Storage    | PostgreSQL JSONB for `menu.title_i18n`; JDBC `stringtype=unspecified` for transparent binding |

---

## See also

- [/docs/getting-started.md](../docs/getting-started.md) — local install walkthrough
- [/docs/user-guide.md](../docs/user-guide.md) — user / admin guide
- [/docs/development.md](../docs/development.md) — adding menus / permissions / migrations
- [/docs/deployment.md](../docs/deployment.md) — production deployment
- [AGENTS.md](AGENTS.md) — backend-specific AI/dev conventions
- [docs/RBAC建设方案.md](docs/RBAC建设方案.md) — RBAC design document (Chinese)
