# Access Matrix — Backend AI Development Guide

> Companion frontend: `../frontend/` (Vue 3 + Vite + Tailwind v4). This repo is a monorepo; for the root-level cross-stack conventions see [../AGENTS.md](../AGENTS.md).
> Backend listens on `:9135` by default, context-path `/api`, dev profile forces `Asia/Tokyo` timezone.

## Project Overview

**Access Matrix** is a platform-level accounts + permissions + multi-tenant foundation. A Spring Boot 4 multi-module Maven project, strictly split between "system domain" and "business domain":

- **system features** — accounts / roles / permissions / menus / departments / op log / audit / force-logout — provided by the `core-system` module
- **business features** — individual business systems (e.g. PMS) — each starts its own `business-{module}` module and is **not allowed** to leak into `core-*` packages
- **infrastructure** — cross-domain shared security / cache / persistence / web aspects — in `core-infrastructure`
- **reusable types** — Result / error codes / annotations / context — in `core-common`
- **bootstrapper** — `main()` + Flyway migrations + global config — in `core-bootstrap`

## Tech Stack

| Category | Choice |
|----------|--------|
| Java | **25** |
| Framework | Spring Boot 4.0.6 + Spring Security 6 (OAuth2 Resource Server) |
| ORM | MyBatis-Plus 3.5.16 |
| DB | PostgreSQL |
| Migration | Flyway 11 (`repair-on-migrate` via a `FlywayMigrationStrategy` bean) |
| Cache | Caffeine (L1); Redis (refresh token / lockout / force-logout state) |
| Auth | JWT (HS256) + HttpOnly Cookie refresh token |
| Password | BCrypt (cost = 12) |
| Rate limit | bucket4j |
| ID | ULID Creator (CHAR(26) PK) |
| Timezone | `Asia/Tokyo` |

## Module Boundaries (the most important rule)

```
core-bootstrap        ─┐ bootstrap + Flyway + global config (application*.yml)
                       │
core-system          ─┤ system domain: auth / rbac / menu / dept / oplog (controller/service/mapper/entity/dto)
business-pms         ─┤ business domain: PMS (currently an empty package-info.java placeholder)
business-{module}    ─┤ future businesses: one Maven module each
                       │
core-infrastructure  ─┤ cross-cutting: security aspects / audit / web filter / cache config / MybatisPlusConfig
                       │
core-common         ──┘ pure types: BusinessException / ErrorCode / JsonResult / PageResult / @OpLog / @RequiresPermission / @DataScope / PermissionMatcher / IdGenerator / RequestContext
```

Dependencies **only flow downward**:

```
core-bootstrap → core-system & business-* → core-infrastructure → core-common
```

Never allowed:
- `core-common` depending on any other module in reverse
- `core-infrastructure` depending on `core-system` or `business-*`
- `core-system` depending on `business-*`, or vice versa
- Cross-business imports (`business-pms` cannot depend on `business-crm`, and vice versa) — cross-business collaboration goes through events / interfaces / HTTP

## System files vs business files — where do they go

### System features (accounts / RBAC / audit) → `core-system`

```
core-system/src/main/java/com/platform/system/
  auth/
    controller/   AuthController (POST /auth/login, /refresh, /logout)
                  AdminAuthController (POST /admin/auth/unlock, reset-password, force-logout/{id})
    service/      AuthService / LoginAuditService
    mapper/       UserMapper / LoginLogMapper
    entity/       UserEntity / LoginLogEntity
    dto/          LoginRequest / TokenResponse / RefreshRequest / ResetPasswordRequest / UnlockRequest
  rbac/
    controller/
      admin/      RoleAdminController / UserAdminController / MenuAdminController / DeptAdminController
                  PermissionAdminController / OpLogQueryController
      MeMenuController / MePermissionController / ScopeMeController / DeptController
    service/      RoleAdminService / UserAdminService / MenuQueryService / DeptAdminService
                  PermissionQueryService / PermissionCacheService / DataScopeQueryService / OpLogService
    mapper/       RoleMapper / PermissionMapper / MenuMapper / DeptMapper
                  UserRoleMapper / RolePermissionMapper / RoleMenuMapper / RoleDeptMapper
    entity/       RoleEntity / PermissionEntity / MenuEntity / DeptEntity / link-table entities / OpLogEntity
    dto/          RoleDto / UserDto / MenuNode / DeptNode / DeptAdminDto / OpLogQuery, etc.
```

### Business features (PMS / CRM / etc.) → `business-{module}`

```
business-pms/src/main/java/com/platform/business/pms/
  {feature}/
    controller/   GET /pms/reservation/list, POST /pms/reservation, ...
    service/
    mapper/
    entity/
    dto/
```

Conventions:
- Business endpoint paths: `/{businessModule}/{feature}/...` (**do not** use the `/admin/` prefix — that naming is reserved for system-domain RBAC write endpoints)
- Business table names: `{businessModule}_{feature}_{noun}`, e.g. `pms_reservation`
- Business columns must participate in data scoping: tables carry `dept_id` (department dimension) and/or `create_user` (personal dimension); annotate Mapper methods with `@DataScope`, and have the Service call `DataScopeHelper.apply` (see the "Data scope" section)

### Cross-cutting (shared across businesses) → `core-infrastructure`

```
core-infrastructure/src/main/java/com/platform/core/infrastructure/
  security/
    JwtIssuer / JwtDecoder config
    AccountLockoutService / ForceLogoutService / ForceLogoutFilter
    PasswordPolicyService / RefreshTokenStore / RefreshCookieService
    AuthRateLimitFilter / SecurityConfig
    rbac/
      PermissionAspect / PermissionResolver / UserPermissionsLookup
      DataScopeAspect / DataScopeContext / DataScopeResolver / DataScopeHelper / DataScopeDecision / UserDataScopeLookup
  audit/
    OpLogAspect / OpLogRecord / OpLogSink
  web/
    CoreRequestContextFilter (injects traceId / tenantId / userId into RequestContext + MDC)
  persistence/
    BaseEntity / AuditMetaObjectHandler (auto-fills create_user / update_time)
  config/
    MybatisPlusConfig (TenantLineInnerInterceptor / PaginationInnerInterceptor / OptimisticLockerInnerInterceptor / BlockAttackInnerInterceptor)
    properties/ AppSecurityProperties / AppMybatisProperties
  numbering/ NumberingService (number generator, reusable across businesses)
```

### Shared API types → `core-common`

```
core-common/src/main/java/com/platform/core/common/
  audit/        @OpLog annotation
  context/      RequestContext (ThreadLocal: tenantId / userId / username / locale / traceId)
  error/        BusinessException / ErrorCode
  id/           IdGenerator (ULID)
  result/       JsonResult / PageResult
  security/     @RequiresPermission / @DataScope / PermissionMatcher
```

`core-common` is **absolutely pure**: must not depend on any Spring context / DB / Redis / Web.

### Bootstrap + global config → `core-bootstrap`

```
core-bootstrap/
  src/main/java/com/platform/core/bootstrap/
    CoreApplication.java       @SpringBootApplication, main()
    startup/
      AuthSchemaBootstrap      startup sanity check
      LocalAdminSeeder         seeds demo-admin/demo-admin user in dev (@Profile("local"))
      FlywayRepairConfig       FlywayMigrationStrategy bean, repair() + migrate()
  src/main/resources/
    application.yml            shared config (mybatis-plus / management / actuator / springdoc)
    application-local.yml      local (security.mode=permit-all, tenant.enabled=false, log expose-error-details=true)
    application-dev.yml        staging (security.mode=jwt, tenant.enabled=true)
    application-prod.yml       production (same as dev + Redis SSL)
    application-test.yml       for junit
    db/migration/V*.sql        Flyway migrations (rules in the "Flyway" section)
    log4j2-spring.xml          logging config (MDC: traceId / tenantId / userId)
```

## Hard Rules

1. **NO business code in core-system** — `core-system` is system-domain only. New businesses must open a new module.
2. **NO system code leaks into business modules** — `business-pms` may not define user / role / permission tables; reuse the existing `core_*` tables.
3. **NO cross-business deps** — business modules may not import one another.
4. **NO raw SQL outside `@Select` / `@Update` / `@Delete` annotations OR `V*__*.sql` migrations** — temporary debugging aside.
5. **NO new tenant-bypassing query without justification** — by default all queries go through the MyBatis-Plus tenant interceptor; hand-written `@Select` must explicitly include a `tenant_id` predicate (see `UserMapper.findByIdentifier`).
6. **NO `@PreAuthorize`** — endpoint authorization uses `@RequiresPermission` uniformly (custom AOP, readable, supports wildcards).
7. **NO inline permission checks in controllers** — use the permission aspect + `RequestContext` user identity; do not write `if (currentUser.isAdmin())` in controllers.
8. **NO `BaseMapper.selectList` without `@DataScope`** for cross-department queries — any user-perspective list query must go through `@DataScope` + `DataScopeHelper.apply`.
9. **NO `confirm()`-style imperative approval skipping** — risky actions (deleting SUPER_ADMIN / changing tenant / changing password policy) must go through `core_oplog` audit + a secondary confirmation.
10. **NO unchecked `selectById` after JWT** on multi-tenant-enabled paths — note that the refresh token path uses `findByIdAndTenant` to prevent the MyBatis-Plus tenant interceptor from mis-applying the `X-Tenant-Id` header to the token holder.
11. **NEVER modify an already-shipped `V*__*.sql`** — adding/changing columns means creating `V{N+1}__*.sql`. `FlywayRepairConfig` tolerates checksum drift, but schema-history readability still relies on append-only.

## Business code recipe — adding a new table / endpoint

This is the canonical checklist when an AI agent or human is asked to "add an Orders module" or similar. Follow these and the existing guards (`TenantSchemaGuard`, `PermissionConsistencyGuard`, ArchUnit) won't fail at startup.

### DO

- **Migration**: place under `backend/business-<module>/src/main/resources/db/migration/V<N>__*.sql` with version **≥ 1000** (V1-V999 reserved for the framework). Every business table MUST include:
  ```sql
  id            char(26)     NOT NULL PRIMARY KEY,
  tenant_id     varchar(64)  NOT NULL,
  mark          smallint     NOT NULL DEFAULT 1,
  create_user   varchar(64),
  update_user   varchar(64),
  create_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   timestamp    NOT NULL DEFAULT CURRENT_TIMESTAMP
  ```
- **Unique indexes lead with `tenant_id`**: `CREATE UNIQUE INDEX uk_xxx_yyy ON xxx (tenant_id, business_key) WHERE mark = 1;` — never a single-column `(business_key)` unique.
- **Entity extends `BaseEntity`**: never redeclare `id` / `tenantId` / `mark` / audit fields. `@TableName("business_xxx")` + business fields only. `BaseEntity` + `AuditMetaObjectHandler` auto-fill the rest on INSERT.
- **Mapper extends `BaseMapper<XxxEntity>`** and lives under `..mapper..` package. Custom queries via `@Select` MUST include `tenant_id = #{...}` predicate.
- **Controller**: `@RestController` + `@RequestMapping("/business-xxx/...")`. Every public HTTP method MUST be annotated with `@RequiresPermission(XxxPermissions.SOME_CODE)`. NEVER use a string literal — always a constant from a `*Permissions.java` class.
- **Permission codes** register through a constants class: `public static final String XXX_READ = "xxx:read"` + `static { PermissionCode.registerAll(XxxPermissions.class, "xxx"); }`. `PermissionConsistencyGuard` will fail-fast on startup if a `@RequiresPermission` references an unregistered string.
- **Service does the work**: controller delegates to a `@Service` class. Controllers don't access mappers directly, services don't access controllers.

### DON'T

- ❌ **No `tenant_id` column** on a per-tenant business table. `TenantSchemaGuard` fail-fasts the boot.
- ❌ **Adding the table to `MybatisPlusConfig.TENANT_EXCLUDED_TABLES`** unless the data is genuinely global (one row-set for the whole installation — like `core_meta`). If you're not sure, it's not global.
- ❌ **`@InterceptorIgnore` on business operations** — that bypasses the MP tenant filter; reserved for cross-tenant platform-ops endpoints only.
- ❌ **Literal permission codes in `@RequiresPermission("xxx:yyy")`** — use a constant. The guard rejects literal codes that don't appear in `PermissionRegistry`.
- ❌ **`@PreAuthorize`** — endpoint auth uses `@RequiresPermission` uniformly.
- ❌ **Inline permission checks in controllers** (`if (currentUser.isAdmin()) { ... }`). Use the AOP + `RequestContext`.
- ❌ **`roleIds.contains(BuiltInRoles.SUPER_ADMIN_ID)`** — that constant is demo-specific. Use `BuiltInRoleLookup.superAdminRoleId(tenantId)` for per-tenant resolution.
- ❌ **Modifying an already-applied `V*__*.sql`** — write a new `V{N+1}` patch migration. See V14 / V30 / V33 for examples.

### Where to put the files

| Concern | Path |
|---|---|
| Migration | `backend/business-<module>/src/main/resources/db/migration/V<N>__*.sql` |
| Entity | `backend/business-<module>/src/main/java/.../entity/XxxEntity.java` |
| Mapper | `backend/business-<module>/src/main/java/.../mapper/XxxMapper.java` |
| Service | `backend/business-<module>/src/main/java/.../service/XxxService.java` |
| Controller | `backend/business-<module>/src/main/java/.../controller/XxxController.java` |
| Permission constants | `backend/business-<module>/src/main/java/.../security/XxxPermissions.java` |

### Reference implementation

`backend/business-demo/` is the canonical model — `TaskEntity` / `TaskMapper` / `TaskController` / `DemoPermissions` show every convention in action. When in doubt, copy that module's shape.

### What enforces these rules

| Layer | Mechanism | Caught when |
|---|---|---|
| Compile | ArchUnit tests in `core-system` | `./mvnw test` |
| Boot | `TenantSchemaGuard`, `PermissionConsistencyGuard` | `./mvnw spring-boot:run` |
| Runtime | `TenantLineInnerInterceptor`, `AuditMetaObjectHandler` | first API call |

## Flyway conventions

- Migration file naming: `V{N}__{snake_case_description}.sql`, version numbers sequential
- All scripts must be **idempotent**:
  - Tables: `CREATE TABLE IF NOT EXISTS`
  - Indexes: `CREATE [UNIQUE] INDEX IF NOT EXISTS`
  - Constraints: wrap in `DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = '...') THEN ALTER TABLE ... ADD CONSTRAINT ... END IF; END $$;`
  - Seeds: `INSERT ... ON CONFLICT DO NOTHING`
- Soft delete: all business tables carry `mark SMALLINT NOT NULL DEFAULT 1` (1 = active, 0 = deleted); unique indexes use `WHERE mark = 1`
- Multi-tenant: all tenant-scoped tables carry `tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'`, with `tenant_id` first in unique indexes; non-tenant tables (e.g. `core_numbering_*`) must be added to `MybatisPlusConfig.TENANT_EXCLUDED_TABLES`
- Audit columns: business tables carry `create_user / update_user / create_time / update_time`, auto-filled by `AuditMetaObjectHandler`
- Link tables (user_role / role_permission, etc.) use `ON DELETE RESTRICT` on FKs (see `V9__core_rbac_fk.sql`)
- At startup `FlywayRepairConfig` runs `repair()` then `migrate()` to absorb dev-time checksum drift; **even so, intentionally modifying old V files is not allowed**

## Security & authentication

| Topic | Implementation | File |
|-------|----------------|------|
| JWT issuance | HS256; payload `sub` (userId) / `tid` (tenant) / `preferred_username` / `scope` / `roles` (JSONB) / `iat` / `exp` | `JwtIssuer` |
| JWT validation | Automatic via Spring Security `oauth2.jwt()`; permit-all profile uses manual decode | `SecurityConfig` + `PermissionResolver` |
| scope claim | **Only `*:*` (super admin) or `__compact__` (others)** — never inline permission codes; resolution goes through `UserPermissionsLookup` (Caffeine cache) so "permission changes take effect immediately" | `AuthService.chooseScopeClaim` |
| refresh token | Redis key `auth:refresh:{token}` → value `userId\|tenantId\|issuedAtSec`, TTL 7d; rotation uses atomic `GETDEL` | `RefreshTokenStore` |
| refresh tenant decoupling | The refresh path uses `UserMapper.findByIdAndTenant` (hand-written SQL is not rewritten by the tenant interceptor) | `AuthService.refresh` |
| force-logout | Redis key `core:auth:logout:{userId}` → epoch sec, TTL 8d (> refresh 7d) | `ForceLogoutService` |
| force-logout global | `ForceLogoutFilter` (OncePerRequestFilter, order = HIGHEST + 30) checks `iat <= kickOutAt` on every JWT-bearing request | `ForceLogoutFilter` + `SecurityConfig` |
| Account lockout | Redis key `auth:fail:{tenant}:{id}` + `auth:lock:{tenant}:{id}`, **tenant-isolated** | `AccountLockoutService` |
| Password policy | Length / character class + HIBP remote check (degrades fail-open) | `PasswordPolicyService` |
| Rate limit | bucket4j; positioned in front of the login path | `AuthRateLimitFilter` |

## API conventions

| Item | Rule |
|------|------|
| context-path | global `/api` |
| System admin write endpoints | `/admin/{module}/...`, e.g. `POST /admin/role`, `PUT /admin/user/{id}/roles` |
| System admin read endpoints | `/admin/{module}/list` or `/admin/{module}/{id}` (protected by `@RequiresPermission`) |
| Me-endpoints | `GET /menu/me`, `GET /permission/me`, `GET /scope/me`, `GET /dept/tree` (login only, no fine-grained permission needed) |
| Business endpoints | `/{businessModule}/{feature}/...` (**do not** overuse `/admin/`) |
| Authorization | Annotate methods with `@RequiresPermission("module:action")` or `@RequiresPermission(anyOf={...})` |
| Audit | Annotate write endpoints with `@OpLog(module, action, targetType)`; rows land in `core_oplog` automatically |
| Pagination | `page` (1-based) + `size` (max 500); returns `PageResult<T>(records, total, page, limit)` |
| Response | All wrapped in `JsonResult<T>`: `{ code, msg, data }`; errors via `BusinessException(ErrorCode.X, msg)` |
| Time | `LocalDateTime`; Jackson configured to write ISO + `Asia/Tokyo` timezone |

## Multi-tenant

- Switch: `app.mybatis.tenant.enabled`; local profile = false, dev/prod = true
- Resolution:
  - Authenticated requests: `CoreRequestContextFilter` reads from the JWT `tid` claim → writes to `RequestContext`
  - Unauthenticated requests: fall back to the `X-Tenant-Id` header, then to `default`
- Interceptor: `TenantLineInnerInterceptor` automatically appends `tenant_id = ?` to MyBatis-Plus-generated SQL
- Exception tables: `MybatisPlusConfig.TENANT_EXCLUDED_TABLES` — flyway_*, core_meta, core_numbering_*
- Hand-written SQL (`@Select` / `@Update`) is **not affected by the interceptor** and must include an explicit `tenant_id` predicate
- Cross-tenant operations (e.g. platform super admin) — not supported yet; if needed, add an `app.mybatis.tenant.bypass-role` config + aspect

## Data scope (@DataScope)

Five presets:

| value | Name | SQL condition |
|------:|------|---------------|
| 1 | ALL | none |
| 2 | DEPT_AND_SUB | `dept_id IN (current dept subtree)` |
| 3 | DEPT | `dept_id = current dept` |
| 4 | SELF | `create_user = current user` |
| 5 | CUSTOM | `dept_id IN (role-specified dept subtrees)` |

**How to use**:
1. Business table carries a `dept_id` column and/or a `create_user` column.
2. Annotate the Mapper method with `@DataScope` (the annotation itself is informational).
3. In the Service:
   ```java
   DataScopeDecision dec = dataScopeResolver.currentDecision();
   LambdaQueryWrapper<Foo> w = new LambdaQueryWrapper<>();
   DataScopeHelper.apply(w, dec, Foo::getDeptId, Foo::getCreateUser);
   return mapper.selectPage(page, w);
   ```
4. `DataScopeAspect` verifies, prior to the Mapper call, that this request invoked `apply()` — **if not, throw 500 in local/dev/test and log a WARN in prod**.

See: `DataScopeHelper` / `DataScopeContext` / `DataScopeAspect`.

## Audit (@OpLog)

- Annotate write endpoints (POST/PUT/DELETE) with `@OpLog(module="system", action="user.delete", targetType="user")`
- `OpLogAspect` (order=50, runs after `PermissionAspect` order=10) automatically persists to `core_oplog`: operator / time / module / action / target ID / request URI / request body (password fields force-masked) / client IP / UA / success flag / error message / elapsed ms
- Async write: `@Async`; failures only WARN and never block the business flow
- Login audit goes through `LoginAuditService.record(tenantId, ...)` separately (note: tenantId must be passed explicitly because worker threads do not inherit the ThreadLocal)

## Error codes & exceptions

- Business exception: `throw new BusinessException(ErrorCode.X, "msg")`
- The global exception handler (in `core-infrastructure.web` or `core-common`) converts `BusinessException` to `JsonResult.error(code, msg)`, HTTP 400/401/403
- Do not `throw new RuntimeException(...)`; do not catch + wrap + rethrow
- Validation: `@Valid` + `@NotBlank` / `@Size` / `@Email`; DTOs use Java records

## Tests

There is no `src/test` yet. Conventions for adding tests:
- Unit: `{Module}ServiceTest` in that module's `src/test/java/`, covering core services
- Integration: `@SpringBootTest` against the `test` profile + Testcontainers PostgreSQL/Redis
- ArchUnit: write module boundary guards in `core-bootstrap/src/test/java/` (forbid reverse deps, forbid `business-pms` from using `core_*` table Mappers, etc.)

## Naming conventions

| Type | Rule | Example |
|------|------|---------|
| Table | snake_case, prefixed with `core_` (system domain) / `{module}_` (business domain) | `core_auth_user`, `pms_reservation` |
| Column | snake_case | `tenant_id` / `created_at` |
| Java class | PascalCase, unique within the module package | `UserAdminService` |
| Java field | camelCase | `tenantId` |
| API path | kebab-case segments; actions use RESTful verb + resource | `/admin/user/list`, `/auth/force-logout/{id}` |
| Permission code | `resource:action`, supports `*:*` / `resource:*` wildcards | `user:delete`, `auth:unlock`, `*:*` |
| Bean injection | Constructor injection (no `@Autowired` fields); private final fields | see all `*Service` |

## Profile matrix

| profile | security.mode | tenant | refresh-cookie.secure | debug.expose-error-details |
|---------|---------------|--------|------------------------|----------------------------|
| local   | permit-all    | off    | false                  | true                       |
| dev     | jwt           | on     | true                   | false                      |
| prod    | jwt           | on     | true (Redis SSL)       | false                      |
| test    | (junit)       | -      | -                      | -                          |

---

## Behavioral Guidelines

### 1. Think Before Coding
- State your assumptions first; ask when unsure
- When there are multiple solutions, lay out the options instead of silently picking one
- If you spot an existing simple approach, suggest it
- If something is unclear or naming is confusing, stop and ask

### 2. Simplicity First
- Solve the problem with the least code
- Do not abstract for single-use cases
- Do not add unrequested "flexibility"
- Do not write error handling for impossible scenarios
- If 200 lines could have been 50 → rewrite

### 3. Surgical Changes
- Touch only what needs to change
- Do not casually "improve" nearby code
- Do not refactor what isn't broken
- Clean up orphan code you produced; do not proactively delete pre-existing dead code

### 4. Goal-Driven Execution
- "Add validation" → "write a test for the invalid input, make it pass"
- "Fix the bug" → "write a reproducing test, then fix"
- For multi-step tasks, give a verifiable step plan

---

**Signs these conventions are taking hold**: clean module boundaries, new features that land on the existing aspects (@RequiresPermission/@OpLog/@DataScope), and an append-only Flyway history.
