# Development

**English** · [中文](development.zh-CN.md)

For **developers**: project layout, adding new features (menus / permissions / migrations), test conventions, and code style.

> A working local environment is a prerequisite — see [Getting Started](getting-started.md).

---

## 1. Project layout

```
access-matrix/
├── backend/                       Spring Boot 4 multi-module (Java 25)
│   ├── core-common/              Error codes / Result / RequestContext / ULID
│   ├── core-infrastructure/      Security / Web filters / Persistence / Mail / Keycloak admin
│   ├── core-system/              Auth / RBAC / Dept / Menu / OpLog
│   ├── business-demo/            Sample business module (task), demonstrates data scope
│   └── core-bootstrap/           Spring Boot entry point, application.yml, Flyway migrations, startup seeder
│
├── frontend/                      Vue 3 + Vite 6 (JavaScript)
│   ├── src/
│   │   ├── views/                Pages (grouped by business domain)
│   │   ├── components/           UI components (ui/ + shared/ + layout/)
│   │   ├── composables/          Reusable hooks (usePermission / useToast / …)
│   │   ├── directives/           v-permission / v-role / v-any-permission
│   │   ├── stores/               Pinia (auth / menu / tabs / theme)
│   │   ├── utils/                permission / oidc / pkce / jwt-decode
│   │   ├── router/               Routes + guards
│   │   └── lang/                 i18n for 5 languages
│   ├── services/                 axios endpoints (named by business domain)
│   └── tests/                    vitest unit tests + Playwright e2e
│
├── infra/                         Local infrastructure
│   └── keycloak/                 Startup scripts + realm export JSON
│
└── docs/                          Documentation (the directory you're reading)
```

### 1.1 Backend module dependency direction (strict)

```
core-bootstrap
   ↓ depends on
core-system, business-demo          (siblings; do not depend on each other)
   ↓ depends on
core-infrastructure
   ↓ depends on
core-common
```

**Violating the direction = compile failure**. This is intentional — it prevents business modules from becoming tightly coupled to one another.

---

## 2. Adding a new menu + page

End-to-end example: add a "Dictionary" page underneath the "System" menu.

### 2.1 Backend: insert the menu row (Flyway migration)

Create `backend/core-bootstrap/src/main/resources/db/migration/V23__add_dict_menu.sql`:

```sql
-- Note: the V number must be strictly increasing and unique
INSERT INTO core_rbac_menu (
    id, tenant_id, parent_id, name, path, component,
    icon, sort_order, menu_type, status, mark
) VALUES (
    -- ULID can be generated via IdGenerator.ulid() at build time; for
    -- migrations we hand-pick stable 26-char strings.
    '01HXXXXXXXXXXXXXXXMENU01',  -- new menu id
    'demo',
    '00000000000000000000MENU05', -- parent = System
    'dict',
    '/system/dict',
    'system/Dict/Dict',
    'Book',
    50,
    2,  -- 2 = menu (not directory)
    1,
    1
);

-- Also add an i18n title (the menu_title_i18n column was introduced in V17)
UPDATE core_rbac_menu
   SET title_i18n = '{"ja_JP":"辞書管理","en":"Dictionary","zh_CN":"字典管理","zh_TW":"字典管理","ko_KR":"사전 관리"}'::jsonb
 WHERE id = '01HXXXXXXXXXXXXXXXMENU01';

-- Attach the menu to the SUPER_ADMIN role (otherwise even the super-admin won't see it)
INSERT INTO core_rbac_role_menu (id, tenant_id, role_id, menu_id, mark)
VALUES (
    IdGenerator.ulid_placeholder(),  -- in practice use a UUID or a fixed ULID
    'demo',
    '00000000000000000000ROLE01', -- SUPER_ADMIN
    '01HXXXXXXXXXXXXXXXMENU01',
    1
);
```

### 2.2 Frontend: add the page component

`frontend/src/views/system/Dict/Dict.vue`:

```vue
<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/shared/DataTable.vue'

const { t } = useI18n()
const rows = ref([])

onMounted(async () => {
  // const r = await getDictListApi()
  // rows.value = r.data.data.records
})
</script>

<template>
  <div class="p-4">
    <h1 class="text-xl font-bold mb-4">{{ t('dict.title') }}</h1>
    <DataTable :rows="rows" />
  </div>
</template>
```

**You don't write the route by hand** — when the backend returns menu data, the `beforeEach` guard in `router/index.js` calls `addRoute()` dynamically. The `component` field points at `frontend/src/views/<path>.vue`.

### 2.3 Add i18n keys

Add an entry to all 5 lang files:

```js
// frontend/src/lang/ja_JP.js
export default {
  // ...existing...
  dict: {
    title: '辞書管理',
    // ...
  }
}
```

### 2.4 Restart + verify

Restart the backend → Flyway runs V23 → the menu row appears in the table → refresh the frontend → "Dictionary" appears under the System menu.

---

## 3. Adding a new permission

Permission code format: `<resource>:<action>`, lowercase with hyphens.

### 3.1 Backend: register the permission code + guard with an annotation

```java
// SystemPermissions.java (or the business module's own PermissionCode class)
public final class SystemPermissions {
    public static final String DICT_READ   = PermissionCode.register("dict:read",   "system");
    public static final String DICT_CREATE = PermissionCode.register("dict:create", "system");
    public static final String DICT_UPDATE = PermissionCode.register("dict:update", "system");
    public static final String DICT_DELETE = PermissionCode.register("dict:delete", "system");
}
```

Once registered, codes are collected by `PermissionRegistry`. At startup, `PermissionConsistencyGuard` will:
- Auto-INSERT any permission code that's present in code but missing from the DB
- Auto-soft-delete any DB row whose code has been removed from source, and revoke its role_permission bindings

**So adding a permission code = editing code + restarting.** No hand-written SQL required.

### 3.2 Use it in a controller

```java
@RestController
@RequestMapping("/admin/dict")
public class DictAdminController {

    @GetMapping
    @RequiresPermission(SystemPermissions.DICT_READ)
    public JsonResult<PageResult<Dict>> list(...) { ... }

    @PostMapping
    @RequiresPermission(SystemPermissions.DICT_CREATE)
    public JsonResult<String> create(@RequestBody Dict d) { ... }
}
```

The `PermissionAspect` aspect checks automatically and returns 403 when the permission is missing.

### 3.3 Frontend: hide buttons with v-permission

```html
<button v-permission="'dict:create'">Create</button>
<button v-permission="'dict:delete'">Delete</button>
```

### 3.4 Bind to a role

After startup, go to System → Roles → select a role → Permissions tab → tick dict:read / dict:create → Save.

---

## 4. Adding a new Flyway migration

### 4.1 Numbering rules

- `V<N>__<snake_name>.sql`
- N must be strictly increasing, unique, and free of colons
- Use snake_case for a short, descriptive name

```
V23__add_dict_menu.sql
V24__core_dict_table.sql
V25__demo_dict_data.sql
```

### 4.2 Content conventions

- **Always use IF NOT EXISTS / IF EXISTS** — so re-runs don't error out
- **Never modify a migration that's already been released** (it has already run elsewhere); to change it, add a new V file with an ALTER
- **Every new table must have a `tenant_id` column + index** (required for multi-tenancy):

```sql
CREATE TABLE IF NOT EXISTS core_dict (
    id          CHAR(26)    PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL DEFAULT 'demo',
    -- ... business cols ...
    mark        SMALLINT    NOT NULL DEFAULT 1,
    create_user VARCHAR(64),
    update_user VARCHAR(64),
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_core_dict_tenant ON core_dict (tenant_id) WHERE mark = 1;
```

### 4.3 Soft-delete conventions

- All business tables use `mark SMALLINT NOT NULL DEFAULT 1` (1 = alive, 0 = deleted)
- Partial unique indexes must include `WHERE mark = 1` — otherwise a soft-deleted row holds the unique slot forever
- Add `@TableLogic(value="1", delval="0")` on the entity
- **Delete rows via `UpdateWrapper.set("mark", 0)`**, never `setMark(0) + updateById` (`@TableLogic` strips `mark` from the SET clause, causing a silent no-op) — see [memory/tablelogic_updatebyid_gotcha](../memory/tablelogic_updatebyid_gotcha.md)

### 4.4 Run it once you've written it

```bash
./mvnw -pl core-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=local
```

Seeing `Successfully applied migration V23` in the startup log means you're good.

---

## 5. Test conventions

### 5.1 Backend unit tests (Mockito)

Place under `<module>/src/test/java/<package>/`, file name `*Test.java`, JUnit 5 + Mockito 5.

**Pattern**:
- `@Mock` the mapper, `@InjectMocks` the service
- Stub the mocked mapper with `@Mock` + `when().thenReturn()`
- Assert SQL → captor + `UpdateWrapper.getSqlSet()` / `getParamNameValuePairs()`

Reference: [`RoleAdminServiceDeleteTest.java`](../backend/core-system/src/test/java/com/platform/system/rbac/service/RoleAdminServiceDeleteTest.java)

### 5.2 Backend integration tests (Testcontainers)

Place under `<module>/src/test/java/<package>/it/`, file name `*IT.java`, annotate with `@Testcontainers(disabledWithoutDocker = true)`.

When Docker isn't available they're **skipped automatically** — the build won't fail.

Reference: [`OidcJitProvisioningIT.java`](../backend/core-bootstrap/src/test/java/com/platform/core/bootstrap/it/OidcJitProvisioningIT.java)

### 5.3 Frontend unit tests (Vitest)

Place either as `<src file>.test.js` (co-located) or under `tests/components/*.test.js`.

- Pure logic (util / composable) → call directly
- Vue components → mount with `@vue/test-utils`
- Pinia stores → `vi.mock('@/stores/auth')` and provide a minimal stub

### 5.4 Frontend e2e (Playwright)

Place under `tests/e2e/*.spec.js`.

- Assumes by default that both the frontend and backend are running (5273 + 9135)
- When the stack isn't up, tests skip automatically (the `/actuator/health` probe fixture)
- Set `E2E_REQUIRE_STACK=1` in CI to force them to pass

### 5.5 Running tests

```bash
# All backend tests
./mvnw test

# A single test class
./mvnw -pl core-system test -Dtest='RoleAdminServiceDeleteTest'

# Frontend vitest
cd frontend && npm run test

# Frontend e2e (requires the full stack running)
cd frontend && npm run test:e2e
```

---

## 6. Code style

### 6.1 Java

- Use records, not classes, for DTOs
- ULID primary keys (`CHAR(26)`); no auto-increment longs
- Avoid `static` fields with `@Value` injection (Spring anti-pattern)
- Soft-delete via `UpdateWrapper.set("mark", 0)`; **never** `setMark + updateById`
- SQL in `@Select` annotations must explicitly include `tenant_id = #{tenantId}` (hand-written SQL is not auto-rewritten by the MP interceptor)

### 6.2 Vue

- Composition API + `<script setup>`
- Business page components live under `views/<domain>/`
- Generic UI components live under `components/ui/` (no business logic)
- Shared business components live under `components/shared/` (e.g. DeptTreeDialog / UserPicker)
- Service files are named `services/<resource>.js` and contain axios calls

### 6.3 Commit conventions (Conventional Commits)

```
<type>(<scope>): <short summary>

<body>

<footer with breaking changes / refs>
```

type: `feat` / `fix` / `chore` / `docs` / `test` / `refactor` / `perf` / `style`

scope: `backend` / `frontend` / `infra` / `docs` / `repo`

---

## 7. Adding a new Keycloak protocol mapper

Example: also surface the user's `dept_id` in the JWT so the SPA can use it directly without calling `/me` every time.

1. Keycloak admin → realm → Clients → `access-matrix-backend`
2. Client scopes tab → find the dedicated scope, or create a new one
3. Add mapper → "User Attribute" type
4. Set User Attribute = `dept_id`, Token Claim Name = `dept_id`, tick "Add to access token"
5. Export the realm → replace `infra/keycloak/realms/default-realm.json`
6. Commit

Frontend reads it as `useAuthStore().claims.dept_id` (already unpacked via `decodeJwt`).

Backend reads it as `jwt.getClaimAsString("dept_id")`; you can write it through to `core_auth_user.dept_id` inside `OidcJitUserService`.

---

## 8. Roadmap (short-term TODOs)

- [ ] Add a "Resend invitation email" button to User.vue
- [ ] SAML 2.0 support (Keycloak already supports it; we just need the business side not to insist on JWT)
- [ ] Self-service account request page (public sign-up, optional)
- [ ] Extend the `@OpLog` annotation to every business write operation
- [ ] Allow per-locale email templates to override the default SMTP templates

PRs welcome.

---

## 9. Development resources

- Backend: [backend/AGENTS.md](../backend/AGENTS.md) — AI collaboration conventions + module constraints
- Frontend: [frontend/AGENTS.md](../frontend/AGENTS.md) — component layering + services conventions
- RBAC design: [backend/docs/RBAC建设方案.md](../backend/docs/RBAC建设方案.md)
- Deployment: [Deployment](deployment.md)
