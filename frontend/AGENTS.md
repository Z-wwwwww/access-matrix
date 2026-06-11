# Access Matrix — Frontend AI Development Guide

> Companion backend: `../backend/` (Spring Boot 4 + Spring Security 6 + JWT + multi-tenant RBAC), listens on `:9135`. This repo is a monorepo; for the root-level cross-stack conventions see [../AGENTS.md](../AGENTS.md).
> Vite proxies to the backend via `/proxy_url`; the dev port defaults to `5273`.

## Project Overview
This project is the admin frontend for **Access Matrix** (RBAC / permission matrix): system features such as users, roles, permissions, menus, departments, and op log, plus business modules (e.g. PMS) that can be plugged in later.

```bash
npm install
npm run dev      # dev server (:5273, proxies /api → backend :9135)
npm run build    # production build → dist/
npm run lint     # ESLint
npm run test     # Vitest
```

## Tech Stack (MANDATORY)
- Vue 3.5+ (Composition API, `<script setup>`)
- **JavaScript ONLY** (TypeScript forbidden)
- Vite 6
- Tailwind CSS v4 (`@import "tailwindcss"`, `@theme` tokens)
- Radix Vue (headless primitives)
- class-variance-authority (CVA), clsx, tailwind-merge
- ECharts 6 + vue-echarts
- lucide-vue-next (icons)
- Vue Router 4
- Pinia
- Axios (global interceptors in src/services/request.js)
- **vue-i18n v9** (Composition API mode, `legacy: false`) — supports ja_JP / en / zh_CN / zh_TW / ko_KR; default ja_JP

## Hard Rules
1. **NO TypeScript** — JS across the stack
2. **NO inline styles** — Tailwind classes only
3. **NO API calls in components** — all HTTP goes through `src/services/`
4. **NO pages in components/** — pages live under `src/views/`
5. **NO duplicate components** — check `docs/component-register.md` before creating one
6. **NO hand-written `<table>`** — all data tables use `@/components/shared/DataTable` (tree usage in the "Tree table" section)
7. **NO native form elements** (in views) — `<input>` / `<select>` / `<textarea>` / `<input type=date>` / `<input type=checkbox>` / `<input type=radio>` must be swapped for the corresponding `Input` / `Select` / `Textarea` / `DatePicker` / `Checkbox` / `RadioGroup` (or `Radio`) under `@/components/ui/`
8. **NO `window.confirm()` / `alert()`** — use `useConfirm()` plus the globally mounted `<ConfirmDialog />`
9. Naming: components PascalCase; services / composables camelCase
10. All props must have a `default`
11. **NO hardcoded business strings** (in new code) — user-visible labels / titles / placeholders / buttons / toasts must use `t()`, with translations placed in `src/lang/{locale}.js` or `src/lang/{module}/{locale}.js`
12. **Detail page template** (`*Edit.vue` / `*Detail.vue`): outer `<Card>` → header (`flex justify-between p-4 border-b`) → `<Tabs v-model="activeTab" :items="tabItems">` (no `container-class` / `sticky`) → optional footer. See `RoleEdit.vue` / `UserEdit.vue`.
13. **NO hardcoded enum dropdowns / labels** — for any status / type / state / scope value, the `<Select>` options, the displayed label, and the `<Badge>` variant all come from `useDict(code)`. Never hand-write a `{1:'…',2:'…'}` label map, an inline `options` array of enum literals, or a `row.status === 1 ? … : …` label ternary. The value (number) is the contract you compare on; the **label/options/variant are always the dict's**. See `views/demo/Task/Task.vue` + the "Dictionaries" section.

## System files vs business files — where do they go

**Core principle**: access-matrix is a platform-type project. **System modules** (accounts / permissions / audit) and **business modules** (PMS / CRM / ...) must live in separate directories and services. The backend module boundary is the same (`core-system` vs `business-{name}`).

### Views (pages)

| Type | Directory | Examples |
|------|-----------|----------|
| System admin | `src/views/system/{Feature}/` | `system/User/User.vue`, `system/Role/RoleEdit.vue`, `system/Dept/Dept.vue`, `system/OpLog/OpLog.vue`, `system/Profile/Profile.vue` |
| Platform ops (tid=`system`) | `src/views/platform/{Feature}/` | `platform/Tenant/Tenant.vue`, `platform/Menu/Menu.vue`, `platform/Job/Job.vue` |
| Business module | `src/views/{businessModule}/{Feature}/` | `{module}/{Feature}/{Feature}.vue`, `{module}/{Feature}/{Feature}Edit.vue` — `{module}` is chosen by each business (e.g. `pms` / `crm`) and created on demand |
| Login / common | `src/views/login/`, `src/views/404.vue`, `_iframe.vue`, `_redirect.vue` | same |

> The foundation **does not pre-create placeholder directories**. Create `src/views/{module}/` when you need a particular business module; multiple businesses can coexist. The rules only constrain **hierarchy and naming style** (kebab/camel business name + PascalCase Feature).

### Services (API wrappers)

**All `.js` files sit flat under `src/services/`; no subdirectories.** Import via the `@/services/*` alias. File-name prefixes indicate ownership.

| Type | Filename | Examples |
|------|----------|----------|
| Foundation | `request.js` | The Axios instance (interceptors / token header / unified error handling) — no business file should bypass it and `import axios` directly |
| System domain | No prefix, simple noun | `auth.js`, `user.js`, `role.js`, `permission.js`, `menu.js`, `dept.js`, `oplog.js`, `scope.js` |
| Business domain | **Prefixed by business module** + camelCase resource | `pmsReservation.js`, `pmsPayment.js`, `pmsListingProperty.js`, `crmCustomer.js` |

Rules:
- Only consider opening a `src/services/{module}/` subdirectory when a single business has more than ~15 service files; **flat by default**
- Do not stuff business endpoints into system-domain files
- Do not `import axios` inside components; HTTP goes through the services layer

### Component layering (no reverse cross-layer references)

| Layer | Directory | Who can use it |
|-------|-----------|----------------|
| Foundation UI | `src/components/ui/` (Card / Input / Select / Drawer / Dialog / Checkbox / internal parts used by DataTable, etc.) | Anyone; itself depends on no other layer |
| Shared business | `src/components/shared/` (DataTable / UserPicker / ConfirmDialog / IconPicker / LucideIcon / LoadingOverlay / FileDownloadLink / ExportFileButton / DateRangeSelector / SwitchField / AreaCascader / SingleImgManualUploader / ToastContainer) | Uses ui/; must not be depended on by ui/ in reverse |
| Layout | `src/components/layout/` (AppLayout / AppHeader / AppSidebar / AppTabBar / EmptyLayout / ChangePasswordDialog) | Uses ui + shared |
| Pages | `src/views/` | Uses ui + shared + layout + composables + services |

### Others

- `src/composables/` — logic reusable across pages (useConfirm / useTheme / useToast / usePopupFollowTrigger, etc.)
- `src/lib/` — pure-function utilities (cn / cva / date / download / validators)
- `src/stores/` — Pinia stores
- `src/lang/` — vue-i18n translations
- `src/router/` — **static routes only** (login / common / fallback); business routes are injected dynamically from the backend menu
- `src/styles/` — global CSS / Tailwind tokens (`main.css`)

## Routing (backend-menu-driven)

- Static routes: `src/router/index.js` only registers login-free pages (`/login`, `/404`, `/forget`, etc.)
- Dynamic routes: inside the `beforeEach` route guard, call **`GET /api/menu/me`** to fetch the current user's menu → `menuToRoutes()` to convert → `router.addRoute()` to inject dynamically
- A business page only needs to drop a component at `src/views/{module}/{Feature}/{Feature}.vue`; **the path and hierarchy are controlled by the backend `core_rbac_menu` table**
- The backend `component` field is a relative path (e.g. `/system/User/User`); frontend directory casing is **not case-sensitive**

## Backend API Conventions (access-matrix backend)

| Item | Reality |
|------|---------|
| Base URL | `http://127.0.0.1:9135/api` (dev); the frontend Vite proxies via `/proxy_url` |
| Auth header | **`Authorization: Bearer <jwt>`** — with the `Bearer ` prefix, unlike the legacy PMS backend |
| Multi-tenant | Requests carry an `X-Tenant-Id` header; once a JWT is issued, the backend reads from the `tid` claim |
| Refresh token | HttpOnly cookie `core_refresh`; axios `withCredentials: true` carries it automatically |
| Pagination params | **`page` + `size`** (not `limit` / `pageSize`). Backend `PaginationInnerInterceptor` maxLimit = 500 |
| Response wrapper | `{ code: 0, msg: "", data: ... }`; for pagination `data = { records, total, page, limit }` |
| List / detail / CRUD | RESTful: `GET /admin/{module}/list`, `GET /admin/{module}/{id}`, `POST /admin/{module}`, `PUT /admin/{module}/{id}`, `DELETE /admin/{module}/{id}` |
| Me-endpoints | `GET /api/menu/me` for the current user's menu tree; `GET /api/permission/me` for the current user's permission-code Set |
| User permission codes | JWT scope claim: `*:*` (super admin) or `__compact__` (others, triggers backend cache lookup) — the frontend should not parse `scope`; use `/permission/me` uniformly |
| Force logout | The backend `ForceLogoutFilter` checks globally; the axios 401 interceptor clears tokens and redirects to login |
| Date format | Timestamps travel as ISO-8601 **with offset** (backend typically returns UTC, e.g. `2026-06-10T08:38:33Z`; send via `toBackendDate(val)` from `@/lib/date`). Display is forced Asia/Tokyo through the `toJST*` helpers — never parse/format timestamps with raw `new Date()` / `Date.parse` / string slicing |

## Tree table (Dept / Menu template)

DataTable **has no native tree mode** (its `expandable` is row-expansion detail panel, not indent-children). Tree usage:

```vue
<script setup>
const expanded = ref(new Set())
const flatTree = computed(() => {
  // walk tree, 仅在祖先全部 expanded 时纳入
})
const columns = [{ key: 'name', title: '名称' }, /* ... */]
function toggle(id) { ... }
</script>

<template>
  <DataTable :columns="columns" :data="flatTree" :loading="loading" :show-pagination="false">
    <template #cell-name="{ row }">
      <div :style="{ paddingLeft: row.level * 18 + 'px' }">
        <button v-if="row.children?.length" @click="toggle(row.id)">
          <ChevronDown v-if="expanded.has(row.id)" /><ChevronRight v-else />
        </button>
        {{ row.name }}
      </div>
    </template>
  </DataTable>
</template>
```

See `views/system/Dept/Dept.vue` and `views/platform/Menu/Menu.vue`.

## Dictionaries (dropdown / label data)

Status / type / state / scope enums are served by the backend dict API and consumed via `useDict` — **never** hardcoded in pages (Hard Rule 13).

- **Read**: `const d = useDict('common_status')` →
  - `d.options` — computed, **enabled-only**, for `<Select :options="d.options.value" />`
  - `d.label(v)` — localized label; resolves disabled items too; unknown value → raw value (never throws)
  - `d.cssClass(v)` — `<Badge>` variant (or undefined): `<Badge :variant="d.cssClass(row.status) || 'outline'">{{ d.label(row.status) }}</Badge>`
- Several at once: `useDicts(['a','b'])`. **Outside** a component (store/util/interceptor): `await resolveDictLabel(code, value)`.
- **Two sources, one contract** (the page doesn't care which): built-in enums (status/type the backend branches on) + managed dicts (runtime-editable lookups, admin UI at `/platform/dicts`). Both yield `{ value, label, cssClass, enabled }`.
- Search filter needing an "all" entry: prepend `{ label: t('…'), value: '' }` to `d.options.value`.
- Cached per session in `stores/dict.js` (one HTTP per code). Common codes: `common_status` (1/0 enabled), `tenant_status`, `menu_type`, `data_scope`, `job_trigger_type`, `job_run_status`, `task_status`, `task_priority`. Add new codes backend-side (see backend/AGENTS.md "Dictionaries").

## Internationalization (i18n)

- Library: vue-i18n v9 (`legacy: false`); entry `src/lang/index.js`, registered in main.js via `app.use(i18n)`
- Languages: ja_JP / en / zh_CN / zh_TW / ko_KR; default ja_JP; persisted to `localStorage['i18n-lang']`
- File layout:
  - `src/lang/{locale}.js` — main language file; imports module sub-files
  - `src/lang/{module}/{locale}.js` — module translations
- Key naming: dot-separated, lowercase module name + camelCase fields, e.g. `user.company`, `reservation.checkInDate`, `common.button.search`
- New code: use `t()` + translation keys; do not hardcode business copy
- **Backend error messages are localized centrally**: `services/request.js` runs every error `msg` through `t()` (the backend sends a stable i18n key like `error.dict.itemInUse` for user-facing business errors — see backend/AGENTS.md; legacy prose passes through unchanged). So define backend error copy under the `error.*` namespace in `src/lang/*.js`; pages don't need to special-case it.
- **Historical exception**: the admin pages under `views/system/*` (User / UserEdit / Role / RoleEdit / Dept / OpLog) were ported from an old template and hand-tweaked, and still contain hardcoded Japanese. **Do not actively rewrite them for i18n's sake** — migrate them only when you happen to be modifying those specific pages.

## Registries (always check before creating)

- Component registry: `docs/component-register.md`
- Service registry: `docs/service-register.md`

Check these before creating a component / service; when you add one, append it here to avoid reinventing the wheel.

---

## Behavioral Guidelines

Cross-stack — see [../AGENTS.md § Behavioral guidelines (both stacks)](../AGENTS.md#behavioral-guidelines-both-stacks) (Think Before Coding / Simplicity First / Surgical Changes / Goal-Driven Execution).
