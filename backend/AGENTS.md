# Access Matrix — Backend AI Development Guide

> Companion frontend: `../frontend/` (Vue 3 + Vite + Tailwind v4). This repo is a monorepo; for the root-level cross-stack conventions see [../AGENTS.md](../AGENTS.md).
> Backend listens on `:9135` by default, context-path `/api`. Time is stored/transported as timezone-agnostic instants (`timestamptz` / `OffsetDateTime`); the business timezone (`AppTime.zone()`, per-deployment config `app.timezone` / `CORE_TIMEZONE`, default `Asia/Tokyo`) applies only to wall-clock decisions.

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
| Timezone | Instants end-to-end (`timestamptz` + `OffsetDateTime` + offset-bearing ISO wire format); business timezone via `AppTime.zone()` (wall-clock decisions only; per-deployment `app.timezone` / `CORE_TIMEZONE`, default `Asia/Tokyo`, invalid id fails the boot) |

## Module Boundaries (the most important rule)

```
core-bootstrap        ─┐ bootstrap + Flyway + global config (application*.yml)
                       │
core-system          ─┤ system domain: auth / rbac / menu / dept / oplog (controller/service/mapper/entity/dto)
business-demo        ─┤ business domain: reference module (Task CRUD — data-scope + domain-event showcase)
business-{module}    ─┤ future businesses (pms / crm / ...): one Maven module each
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
                  AdminAuthController (POST /admin/auth/unlock, force-logout/{id})
    service/      AuthService / LoginAuditService
    mapper/       UserMapper / LoginLogMapper
    entity/       UserEntity / LoginLogEntity
    dto/          LoginRequest / TokenResponse / RefreshRequest / UnlockRequest
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
      LocalAdminSeeder         seeds demo-admin/demo-admin user in dev (@Profile("dev"))
      FlywayRepairConfig       FlywayMigrationStrategy bean, repair() + migrate()
  src/main/resources/
    application.yml            shared base defaults (mybatis-plus / management / actuator /
                                 springdoc; security.mode=oidc fail-closed default)
    application-dev.yml        development (oidc, localhost DB, tenant on, cors *,
                                 expose-error-details=true; @Profile("dev") seeders run here)
    application-test.yml       test / junit (jwt, env-driven DB, no Keycloak)
    application-prod.yml       production (oidc, Redis SSL, Swagger off; secrets as no-default ${VAR})
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
12. **NO state change outside a `@Service`** — controllers/aspects/utilities must not write business tables directly. Mutations live in a service so audit + domain-event emission have exactly one seam (and so an AI actor can later be plugged in there). See "Domain events & state-change conventions".
13. **NO mutated current-value-only column for revenue-relevant fields** (price / status / availability) — keep an append-only history (change events or history rows). This data is non-back-fillable and is the substrate for future AI / revenue management.
14. **NO data-scoped get/update/delete-by-id without a row-level visibility gate** — `selectById` is tenant-scoped (interceptor) but **NOT** data-scoped, so a DEPT/SELF-scoped caller can otherwise read or mutate any row in the tenant by guessing its id (broken object-level authorization / IDOR). On any entity that participates in data scope (`@DataScope` lists, `dept_id`/`create_user` columns), every by-id `get`/`update`/`delete` must gate the fetched row through `DataScopeHelper.isVisible(dataScopeResolver.currentDecision(), row.deptId, row.createUser)` and throw `NOT_FOUND` (not `FORBIDDEN`, so the id's existence isn't revealed) when it returns false. Scoping the list query alone is not enough. (Pattern: `TaskService.loadVisibleOr404`.)

## Business code recipe — adding a new table / endpoint

This is the canonical checklist when an AI agent or human is asked to "add an Orders module" or similar. Follow these and the existing guards (`TenantSchemaGuard`, `PermissionConsistencyGuard`, ArchitectureTest) won't fail at startup.

### Fastest path: use the scaffold tool

Before writing anything by hand, try:

```bash
# A. New module (typical for a real business domain — orders, billing, etc.):
./mvnw -pl core-bootstrap exec:java \
    -Dexec.mainClass=com.platform.core.bootstrap.tools.BusinessModuleScaffold \
    -Dexec.args="<resource> --new-module=<module-name>"

# B. Legacy mode (adds a second resource to the existing business-demo module):
./mvnw -pl core-bootstrap exec:java \
    -Dexec.mainClass=com.platform.core.bootstrap.tools.BusinessModuleScaffold \
    -Dexec.args="<resource>"
```

Both modes clone `business-demo/task/*` with identifier substitution and write the migration at the next free V≥1000. New-module mode additionally creates `backend/business-<name>/` (its own Maven module with `pom.xml`, a `<Name>Permissions` class with 4 auto-registered constants, and an empty `db/migration/` dir) and wires it into `backend/pom.xml` + `backend/core-bootstrap/pom.xml`. Legacy mode auto-injects 4 perm constants into `business-demo/.../security/DemoPermissions.java`. Either way you only need to edit business fields next. See [`docs/development.md` § Adding a new business module](../docs/development.md#adding-a-new-business-module-end-to-end-checklist) for the full walkthrough.

If you prefer (or need) to do it by hand, the DO / DON'T below is the spec.

### DO

- **Migration**: place under `backend/business-<module>/src/main/resources/db/migration/V<N>__*.sql` with version **≥ 1000** (V1-V999 reserved for the framework). Every business table MUST include:
  ```sql
  id            char(26)     NOT NULL PRIMARY KEY,
  tenant_id     varchar(64)  NOT NULL,
  mark          smallint     NOT NULL DEFAULT 1,
  create_user   varchar(64),
  update_user   varchar(64),
  create_time   timestamptz  NOT NULL DEFAULT now(),
  update_time   timestamptz  NOT NULL DEFAULT now()
  ```
  Time columns are **`timestamptz`, never `timestamp`** (V58 converted the legacy ones; don't reintroduce zone-less wall clocks). True calendar concepts (check-in date, business date) use `date` — they are not instants and must not be converted.
- **A property/site table gets a `timezone` column on day one** (IANA id, e.g. `Asia/Tokyo`, `NOT NULL`). It anchors every wall-clock decision for that physical site — night audit / business-date rollover, per-property cron, date parts in generated numbers, guest-facing "15:00 check-in" semantics. It is the cheapest column to add at design time and the hardest to retrofit; until it exists, the single business timezone lives in `AppTime.zone()` (`app.timezone`).
- **Unique indexes lead with `tenant_id`**: `CREATE UNIQUE INDEX uk_xxx_yyy ON xxx (tenant_id, business_key) WHERE mark = 1;` — never a single-column `(business_key)` unique.
- **Entity extends `BaseEntity`**: never redeclare `id` / `tenantId` / `mark` / audit fields. `@TableName("business_xxx")` + business fields only. `BaseEntity` + `AuditMetaObjectHandler` auto-fill the rest on INSERT.
- **Mapper extends `BaseMapper<XxxEntity>`** and lives under `..mapper..` package. Custom queries via `@Select` MUST include `tenant_id = #{...}` predicate.
- **Controller**: `@RestController` + `@RequestMapping("/business-xxx/...")`. Every public HTTP method MUST be annotated with `@RequiresPermission(XxxPermissions.SOME_CODE)`. NEVER use a string literal — always a constant from a `*Permissions.java` class.
- **Permission codes** register through a constants class: `public static final String XXX_READ = "xxx:read"` + `static { PermissionCode.registerAll(XxxPermissions.class, "xxx"); }`. `PermissionConsistencyGuard` will fail-fast on startup if a `@RequiresPermission` references an unregistered string.
- **Service does the work**: controller delegates to a `@Service` class. Controllers don't access mappers directly, services don't access controllers.
- **Emit a domain event on every state change**: inside the `@Service`, in the same `@Transactional` method as the write, inject `EventPublisher` and call `publish(DomainEvent.of("<AggregateType>", id, "<aggregate>.<verb>", payloadDto))` (e.g. `"Reservation"`, `"reservation.created"`). For revenue-relevant fields (price / status / availability) also keep append-only history, never a current-value-only column. See "Domain events & state-change conventions" + Hard Rules 12–13.
- **Status / type / state columns go through a dictionary** — define an `enum implements DictEnum` (code + i18n labelKey), register it in your module's `@Component` registrar (`DictRegistry.register("xxx_status", XxxStatus.class)`), and at the service boundary validate the input with `DictEnum.requireValid(XxxStatus.class, req.status(), "status")`. Branch on the enum constant (`TaskStatus.DONE.code()`), never a magic number. See "Dictionaries".

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
| Time | `OffsetDateTime` everywhere (entities/DTOs/`now()`); columns are `timestamptz` (V58); Jackson writes ISO-8601 with offset. **Never** use `LocalDateTime` for timestamps. Wall-clock/calendar decisions (cron, day/month bucketing in SQL via `AT TIME ZONE`, date parts in numbering, email-facing formatting) go through `AppTime.zone()` (per-deployment `app.timezone` / `CORE_TIMEZONE`, default `Asia/Tokyo`; bound at boot by `AppTimeConfigurer`, invalid id fails fast) |

## Multi-tenant

- Switch: `app.mybatis.tenant.enabled`; on in all profiles (fail-closed base default = true)
- Resolution:
  - Authenticated requests: `CoreRequestContextFilter` reads from the JWT `tid` claim → writes to `RequestContext`
  - Unauthenticated requests: fall back to the `X-Tenant-Id` header, then to `default`
- Interceptor: `TenantLineInnerInterceptor` automatically appends `tenant_id = ?` to MyBatis-Plus-generated SQL
- Exception tables: `MybatisPlusConfig.TENANT_EXCLUDED_TABLES` — `flyway_schema_history`, `core_meta`, `core_job_lock`, `core_rbac_menu`
- Hand-written SQL (`@Select` / `@Update`) is **not affected by the interceptor** and must include an explicit `tenant_id` predicate
- Cross-tenant operations (e.g. platform super admin) — not supported yet; if needed, add an `app.mybatis.tenant.bypass-role` config + aspect
- Platform-ops bypass: a caller with `RequestContext.tenantId() == "system"` (PLATFORM_ADMIN's JWT `tid='system'`) makes `ignoreTable` skip scoping for **every** table — that's how platform consoles read/write across tenants without `@InterceptorIgnore`

### Table storage forms (pick one when adding a table)

Structurally there are only **two** forms, separated by one switch — whether the table is in `TENANT_EXCLUDED_TABLES`. Both are **enforced at startup by `TenantSchemaGuard`** (has-`tenant_id`→must-NOT-be-excluded; no-`tenant_id`→MUST-be-excluded) and at compile time by `ArchitectureTest` (every `@TableName` extends `BaseEntity` unless allowlisted), so a wrong combination fails the build/boot.

| | **① Tenant-managed** (default) | **② Global** |
|---|---|---|
| In `TENANT_EXCLUDED_TABLES` | no | yes |
| `tenant_id` column | yes | **none** |
| Entity | `extends BaseEntity` | standalone (declare own `id`/`mark`/audit; add simple name to `ArchitectureTest.ENTITIES_WITHOUT_BASE_ENTITY_OK`) |
| Examples | every business table; also `core_job` / `core_tenant` | `core_meta`, `core_job_lock`, `core_rbac_menu` |

**Decision rule** — ask: *do business-tenant users (JWT `tid` ≠ `system`) need to read this table?*
- **Yes, and each tenant's rows differ** → ① (the scaffold tool's default).
- **Yes, but it's one shared set for the whole installation** → ② (e.g. `core_rbac_menu`: global nav, filtered per user by `permission_code`). A shared set can't be tenant-scoped, so it must be excluded; keeping a constant `tenant_id` column would be dead weight + a `TenantSchemaGuard` "wasted exclusion" warning, so drop it.
- **No — only platform-ops (`tid='system'`) touch it** → ① with every row `tenant_id='system'` (e.g. `core_job`, `core_tenant`). The platform-ops bypass handles cross-tenant reads; keeping `BaseEntity` keeps the entity uniform. (`tenant_id='system'` here is just form ① holding mono-tenant data — **not** a third pattern.)

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
4. `DataScopeAspect` verifies, prior to the Mapper call, that this request invoked `apply()` — **if not, throw 500 in dev/test and log a WARN in prod**.

**Single-object endpoints (get / update / delete by id) — Hard Rule 14**: `apply()` only scopes *list* queries. By-id endpoints fetch with `selectById`, which the tenant interceptor scopes by `tenant_id` but **not** by data scope. Gate the fetched row explicitly, or a DEPT/SELF-scoped caller reads/mutates any row in the tenant by id (IDOR):
   ```java
   private Foo loadVisibleOr404(String id) {
       Foo f = mapper.selectById(id);
       if (f == null || f.getMark() == null || f.getMark() != 1
               || !DataScopeHelper.isVisible(dataScopeResolver.currentDecision(),
                       f.getDeptId(), f.getCreateUser())) {
           throw new BusinessException(ErrorCode.NOT_FOUND, "Foo not found: " + id);
       }
       return f;
   }
   ```
   Call it from `get`/`update`/`delete` instead of a bare `selectById`. Throw `NOT_FOUND` (not `FORBIDDEN`) so an out-of-scope id is indistinguishable from a missing one.

See: `DataScopeHelper` (`apply` for lists, `isVisible` for single objects) / `DataScopeContext` / `DataScopeAspect`. Reference: `TaskService.loadVisibleOr404`.

## Audit (@OpLog)

- Annotate write endpoints (POST/PUT/DELETE) with `@OpLog(module="system", action="user.delete", targetType="user")`
- `OpLogAspect` (order=50, runs after `PermissionAspect` order=10) automatically persists to `core_oplog`: operator / time / module / action / target ID / request URI / request body (password fields force-masked) / client IP / UA / success flag / error message / elapsed ms
- Async write: `@Async`; failures only WARN and never block the business flow
- Login audit goes through `LoginAuditService.record(tenantId, ...)` separately (note: tenantId must be passed explicitly because worker threads do not inherit the ThreadLocal)

## Domain events & state-change conventions (`core_domain_event`)

Foundation-stage groundwork so future AI / revenue-management features have the *time-series, non-back-fillable* data they need (booking pace, pickup curves, every price change + reason). `core_domain_event` (V36) is the platform **event store + transactional outbox** — distinct from `core_oplog`:

| | `core_oplog` (@OpLog) | `core_domain_event` |
|---|---|---|
| Records | *who called which HTTP endpoint* (request audit) | *what changed in the domain* (business fact) |
| Shape | request URI / body / IP / UA | `aggregate_type` + `event_type` + structured `payload` JSONB |
| Reader | a human admin | machines: analytics / AI / projections |
| Lifecycle | insert-only | insert + outbox dispatch bookkeeping |

These conventions apply as business modules land (they have no effect on a request that changes nothing):

1. **All state changes go through a `@Service`** — never scatter table writes across controllers/aspects/utilities. This is the only place an event can be reliably emitted and the only seam where an AI actor can later be plugged in. (Reinforces Hard Rules 6–7.)
2. **A state-changing service emits a domain event** into `core_domain_event`, **in the same transaction** as the business write (transactional outbox — no event without the write, no write without the event). Serialize `payload` with the Jackson3 `JsonMapper` bean (not Jackson 2 `ObjectMapper`). Set `actor` / `actor_type` from `RequestContext` (1 human / 2 AI service account / 3 system).
3. **Keep history for revenue-relevant fields** (price, status, availability) — model them as append-only change events / history rows, not just a mutated current-value column. The current value is recoverable from history; history is not recoverable from the current value.
4. Standard audit columns (`create_user` / `update_user` / `create_time` / `update_time`) stay mandatory and auto-filled — see Flyway conventions. They answer "who last touched the row"; domain events answer "what happened, in order".

**Java side** (in `core-infrastructure/.../event/`):
- Emit: inject `EventPublisher` and call `publish(DomainEvent.of("Rate", id, "rate.price_changed", payloadDto))` inside the business `@Transactional` method. `OutboxEventPublisher` writes synchronously in that same transaction (payload → JSONB via the Jackson3 `JsonMapper`; tenant/actor/trace from `RequestContext`).
- Drain: `OutboxDispatcher` (`@Scheduled`) polls pending rows cross-tenant (it runs as the `system` tenant so the MP interceptor bypasses scoping) and hands each to an `EventDispatchSink`. With no sink bean registered it falls back to `LoggingEventDispatchSink` — events are persisted but not forwarded downstream. **To forward to a bus/analytics store, register one `@Component implements EventDispatchSink`.**
- Config: `app.outbox.enabled` (default true) / `poll-interval-ms` (5000) / `batch-size` (200) / `max-attempts` (5).

**Naming conventions:**
- `aggregateType` — **PascalCase singular** noun naming the entity kind: `Reservation`, `Rate`, `Room`, `Invoice`. NOT the table name (`pms_reservation`), NOT plural. One aggregate type per business entity that has a lifecycle worth tracking.
- `eventType` — **`<aggregate>.<verb>`**, lowercase, dot-separated, **past tense** (it already happened): `reservation.created`, `reservation.cancelled`, `rate.price_changed`, `room.status_changed`. The prefix is the lowercased aggregate; the verb is `snake_case` if multi-word. Stable string — analytics/AI filter on it, so don't rename casually.
- `payload` — a small DTO or `Map` carrying the *delta*, not the whole entity. For a change, include both sides (`old`/`new`) so consumers needn't replay history to see what moved. Use a Java `record` for type safety; it serializes to JSONB via the Jackson3 `JsonMapper`.

**Complete service example** (the canonical shape — copy this when adding a state-changing endpoint):

```java
@Service
public class RateService {

    private final RateMapper rateMapper;
    private final EventPublisher events;          // inject the publisher

    public RateService(RateMapper rateMapper, EventPublisher events) {
        this.rateMapper = rateMapper;
        this.events = events;
    }

    /** Payload record — the delta, serialized to JSONB. */
    public record PriceChanged(BigDecimal oldPrice, BigDecimal newPrice, LocalDate stayDate) {}

    @Transactional                                 // event + write share ONE transaction
    public void changePrice(String rateId, BigDecimal newPrice, LocalDate stayDate) {
        RateEntity rate = rateMapper.selectById(rateId);
        if (rate == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "rate not found");
        }
        BigDecimal oldPrice = rate.getPrice();

        rate.setPrice(newPrice);                   // 1. the business write
        rateMapper.updateById(rate);

        events.publish(DomainEvent.of(             // 2. the event — same tx, after the write
            "Rate",                                //    aggregateType  (PascalCase singular)
            rateId,                                //    aggregateId
            "rate.price_changed",                  //    eventType      (<aggregate>.<verb>, past tense)
            new PriceChanged(oldPrice, newPrice, stayDate)));  // payload (the delta)
    }
}
```

Notes that keep it correct:
- The `@Transactional` is on the **service** method — the event insert and the business write commit or roll back together. Don't put it on the controller.
- `tenant_id`, `actor`, `actor_type`, `trace_id`, `occurred_at` are filled by `OutboxEventPublisher` from `RequestContext` — **do not** set them in the payload.
- Use `DomainEvent.system(...)` instead of `.of(...)` when the change comes from a background job (no end user on the thread); it stamps `actor_type=3`.
- Nothing extra to register: `core_domain_event` already exists (V36) and `EventPublisher` is an injectable bean. You only write the two lines above.

## Dictionaries (status / type / state / scope enums)

Coded values (status / type / state / scope / lookups) are served read-only to the frontend at `GET /dict/{code}` so dropdowns/labels never hardcode them. **Three forms** — pick by "does code branch on it?" and "who owns the value set?":

| | **① Business · pure DB** | **② System · pure enum** | **③ Business · DB + enum** |
|---|---|---|---|
| Value set owner | ops (runtime) | code (closed) | ops (runtime), code branches a subset |
| Source of truth | `core_dict_item` (DB) | `enum` in `DictRegistry` | DB for display; `enum` for the branched subset |
| In `DictRegistry`? | no | **yes** | **no** (enum used only for branching) |
| `GET /dict/{code}` reads | DB items | enum items | **DB items** |
| Code branches on it? | no | yes | yes (a subset) |
| Input validation | vs DB (`DictQueryService.isValidValue`) | vs enum (`DictEnum.requireValid`) | vs DB (open set) |
| Examples | `gender` | `common_status`, `menu_type`, `data_scope`, `tenant_status`, `job_*` | `task_status` |

Resolution: `DictQueryService.read()` checks `DictRegistry` first (→ ②), else reads the DB (→ ① / ③).

**Adding a form ② (built-in enum):** `enum Xxx implements DictEnum` — `code()` (stored int) + `labelKey()` (frontend i18n key) + optional `cssClass()`; register in a `@Component` registrar via `DictRegistry.register("code", Xxx.class)` (force-load like `*Permissions`, one per module). Validate writes with `DictEnum.requireValid(Xxx.class, v, "field")` (→ 400). Branch/default on the constant (`if (s == Xxx.DONE.code())`, `setStatus(CommonStatus.ENABLED.code())`); reverse lookup `DictEnum.fromCode(Xxx.class, n)`. Generic enabled/disabled = `CommonStatus` (core-common).

**Adding a form ③ (DB + branch enum):** seed the options into `core_dict`/`core_dict_item` (a migration), keep an `enum implements DictEnum` for the branched subset **but do NOT register it in `DictRegistry`** (so reads hit the DB / stay runtime-editable). Validate input vs the DB (`dictQueryService.isValidValue("code", v)`, open set), branch on the enum (`fromCode` → unknown ops-added values simply don't branch).

**Delete-protection** (`DictGuards`, for ① / ③): a managed item is **not hard-deletable** when it's a branch value (enum) OR still referenced by data — only disable (`status=0`). Declare per dict in a module registrar: `DictGuards.register("task_status").branchEnum(TaskStatus.class).usedBy("demo_task","status")`. Protection is computed (enum membership + live `DictUsageMapper` count), not a stored flag. Reference reads are cross-tenant by design (global dict, platform-ops).

**Backend does NOT localize** — it stores/branches on the value and exposes `labelKey` (built-in) or `label_i18n` (managed); the frontend resolves the label. For a backend-originated message, put `{value, labelKey}` in the payload and let the frontend render it. Reference impl: `TaskStatus` + `DemoDictGuardRegistrar` (business-demo, form ③), `CommonStatus`/`TriggerType` (core-common), `com.platform.system.dict.builtin.*` (core-system, form ②).

## Error codes & exceptions

- Business exception: `throw new BusinessException(ErrorCode.X, "msg")`
- The global exception handler (in `core-infrastructure.web` or `core-common`) converts `BusinessException` to `JsonResult.error(code, msg)`, HTTP 400/401/403
- Do not `throw new RuntimeException(...)`; do not catch + wrap + rethrow
- Validation: `@Valid` + `@NotBlank` / `@Size` / `@Email`; DTOs use Java records
- **User-facing error messages are localized on the FRONTEND, not the backend.** For a business error the user will see, pass a stable **i18n key** as the message — e.g. `throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.dict.itemInUse")` — defined in `frontend/src/lang/*.js` under the `error.*` namespace. The axios interceptor runs every error message through `t()` (key → localized; legacy prose → passed through unchanged), so keys localize and old prose still works. Keep these messages **parameter-free** (don't append ids/values — the dynamic part can't survive the key). Internal/never-shown errors may keep prose.

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
| dev     | oidc          | on     | false                  | true                       |
| test    | jwt           | on     | true (base)            | false                      |
| prod    | oidc          | on     | true (Redis SSL)       | false                      |

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
