# Access Matrix — Frontend AI Development Guide

**English** · [中文](AGENTS.zh-CN.md)

> Companion backend: `../backend/` (Spring Boot 4 + Spring Security 6 + JWT + multi-tenant RBAC), listens on `:9135`. This repo is a monorepo; for the root-level cross-stack conventions see [../AGENTS.md](../AGENTS.md).
> Vite proxies to the backend via `/proxy_url`; the dev port defaults to `5273`.

## Project Overview
This project is the admin frontend for **Access Matrix** (RBAC / permission matrix): system features such as users, roles, permissions, menus, departments, and op log, plus business modules (e.g. PMS) that can be plugged in later. Views, components, and services follow an **AI-driven development workflow** (Spec + Skill + natural language).

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
- Axios (global interceptors in services/request.js)
- **vue-i18n v9** (Composition API mode, `legacy: false`) — supports ja_JP / en / zh_CN / zh_TW / ko_KR; default ja_JP

## Hard Rules
1. **NO TypeScript** — JS across the stack
2. **NO inline styles** — Tailwind classes only
3. **NO API calls in components** — all HTTP goes through `services/`
4. **NO pages in components/** — pages live under `src/views/`
5. **NO duplicate components** — check `docs/component-register.md` before creating one
6. **NO hand-written `<table>`** — all data tables use `@/components/shared/DataTable` (tree usage in the "Tree table" section)
7. **NO native form elements** (in views) — `<input>` / `<select>` / `<textarea>` / `<input type=date>` / `<input type=checkbox>` / `<input type=radio>` must be swapped for the corresponding `Input` / `Select` / `Textarea` / `DatePicker` / `Checkbox` / `RadioGroup` (or `Radio`) under `@/components/ui/`
8. **NO `window.confirm()` / `alert()`** — use `useConfirm()` plus the globally mounted `<ConfirmDialog />`
9. Naming: components PascalCase; services / composables camelCase
10. All props must have a `default`
11. Dictionary data lives in `src/dict/storage.js` (statically bundled); access it via `useDict(code)` / `useDicts([code,...])`
12. **NO hardcoded business strings** (in new code) — user-visible labels / titles / placeholders / buttons / toasts must use `t()`, with translations placed in `src/lang/{locale}.js` or `src/lang/{module}/{locale}.js`
13. **Detail page template** (`*Edit.vue` / `*Detail.vue`): outer `<Card>` → header (`flex justify-between p-4 border-b`) → `<Tabs v-model="activeTab" :items="tabItems">` (no `container-class` / `sticky`) → optional footer. See `RoleEdit.vue` / `UserEdit.vue`.

## System files vs business files — where do they go

**Core principle**: access-matrix is a platform-type project. **System modules** (accounts / permissions / audit) and **business modules** (PMS / CRM / ...) must live in separate directories and services. The backend module boundary is the same (`core-system` vs `business-{name}`).

### Views (pages)

| Type | Directory | Examples |
|------|-----------|----------|
| System admin | `src/views/system/{Feature}/` | `system/User/User.vue`, `system/Role/RoleEdit.vue`, `system/Menu/Menu.vue`, `system/Dept/Dept.vue`, `system/Permission/Permission.vue`, `system/OpLog/OpLog.vue` |
| Business module | `src/views/{businessModule}/{Feature}/` | `{module}/{Feature}/{Feature}.vue`, `{module}/{Feature}/{Feature}Edit.vue` — `{module}` is chosen by each business (e.g. `pms` / `crm`) and created on demand |
| Login / common | `src/views/login/`, `src/views/404.vue`, `_iframe.vue`, `_redirect.vue` | same |

> The foundation **does not pre-create placeholder directories**. Create `src/views/{module}/` when you need a particular business module; multiple businesses can coexist. The rules only constrain **hierarchy and naming style** (kebab/camel business name + PascalCase Feature).

### Services (API wrappers)

**All `.js` files sit flat under `services/`; no subdirectories.** File-name prefixes indicate ownership.

| Type | Filename | Examples |
|------|----------|----------|
| Foundation | `request.js` | The Axios instance (interceptors / token header / unified error handling) — no business file should bypass it and `import axios` directly |
| System domain | No prefix, simple noun | `auth.js`, `user.js`, `role.js`, `permission.js`, `menu.js`, `dept.js`, `oplog.js`, `scope.js`, `dict.js` |
| Common dropdown / helper | No prefix | `adminCommon.js` (placeholder) |
| Business domain | **Prefixed by business module** + camelCase resource | `pmsReservation.js`, `pmsPayment.js`, `pmsListingProperty.js`, `crmCustomer.js` |

Rules:
- Only consider opening a `services/{module}/` subdirectory when a single business has more than ~15 service files; **flat by default**
- Do not stuff business endpoints into system-domain files
- Do not `import axios` inside components; HTTP goes through the services layer

### Component layering (no reverse cross-layer references)

| Layer | Directory | Who can use it |
|-------|-----------|----------------|
| Foundation UI | `src/components/ui/` (Card / Input / Select / Drawer / Dialog / Checkbox / internal parts used by DataTable, etc.) | Anyone; itself depends on no other layer |
| Shared business | `src/components/shared/` (DataTable / UserPicker / DictPicker / ConfirmDialog / IconPicker / LucideIcon / LoadingOverlay / FileDownloadLink / ExportFileButton / DateRangeSelector / SwitchField / AreaCascader / SingleImgManualUploader / ToastContainer) | Uses ui/; must not be depended on by ui/ in reverse |
| Layout | `src/components/layout/` (AppLayout / AppHeader / AppSidebar / AppTabBar / EmptyLayout / ChangePasswordDialog) | Uses ui + shared |
| Pages | `src/views/` | Uses ui + shared + layout + composables + services |

### Others

- `src/composables/` — logic reusable across pages (useDict / useConfirm / useTheme / useToast / usePopupFollowTrigger, etc.)
- `src/lib/` — pure-function utilities (cn / cva / date / download / validators)
- `src/stores/` — Pinia stores
- `src/dict/` — static dictionary data
- `src/lang/` — vue-i18n translations
- `src/router/` — **static routes only** (login / common / fallback); business routes are injected dynamically from the backend menu
- `src/styles/` — global CSS / Tailwind tokens (`main.css`)
- `ai/specs/` — spec files generated by AI Skills, organized by type (`components/`, `pages/`, `services/`, `stores/`, `composables/`)

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
| Dictionaries | **Static** (build-time bundle from `src/dict/storage.js`) — read directly via `useDict(code)`, **no HTTP** |
| User permission codes | JWT scope claim: `*:*` (super admin) or `__compact__` (others, triggers backend cache lookup) — the frontend should not parse `scope`; use `/permission/me` uniformly |
| Force logout | The backend `ForceLogoutFilter` checks globally; the axios 401 interceptor clears tokens and redirects to login |
| Date format | The backend expects `yyyy-MM-dd HH:mm:ssZZ` (e.g. `2026-04-02 00:00:00+0900`); forced Asia/Tokyo; uniformly convert via `toBackendDate(val)` from `@/lib/date` |

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

See `views/system/Dept/Dept.vue` and `views/system/Menu/Menu.vue`.

## Internationalization (i18n)

- Library: vue-i18n v9 (`legacy: false`); entry `src/lang/index.js`, registered in main.js via `app.use(i18n)`
- Languages: ja_JP / en / zh_CN / zh_TW / ko_KR; default ja_JP; persisted to `localStorage['i18n-lang']`
- File layout:
  - `src/lang/{locale}.js` — main language file; imports module sub-files
  - `src/lang/{module}/{locale}.js` — module translations
- Key naming: dot-separated, lowercase module name + camelCase fields, e.g. `user.company`, `reservation.checkInDate`, `common.button.search`
- New code: use `t()` + translation keys; do not hardcode business copy
- **Historical exception**: the six admin pages under `views/system/*` (User / UserEdit / Role / RoleEdit / Permission / Menu / Dept / OpLog) were ported from an old template and hand-tweaked, and still contain hardcoded Japanese. **Do not actively rewrite them for i18n's sake** — migrate them only when you happen to be modifying those specific pages.

## AI Skills (Slash Commands)

Invoked via `pnpm ai:*`; under the hood `Claude Code` runs them through `scripts/ai-cli.mjs`:

| Skill | Command | Purpose |
|-------|---------|---------|
| create-component | `/create-component` | Create a component from a Spec / natural language |
| create-page | `/create-page` | Create a page + route |
| update-page | `/update-page` | Modify an existing page |
| generate | `/generate` | Generate service / composable / store / utils |
| inspect | `/inspect` | Audit duplicates / violations |
| analyze | `/analyze` | Parse existing code → spec |

Spec block example (any skill takes it directly):

```yaml
component:
  name: UserAvatar
  type: shared          # ui | shared | layout
  props:
    src: { type: String, default: "" }
    size: { type: String, default: "md" }
  variants:
    size:
      sm: "w-6 h-6 text-xs"
      md: "w-10 h-10 text-sm"
```

Plain natural language also works: "create a user avatar component supporting sm/md/lg sizes".

## Registries (always check before creating)

- Component registry: `docs/component-register.md`
- Service registry: `docs/service-register.md`

Every generated component / service must be registered to avoid reinventing the wheel.

---

## Behavioral Guidelines

### 1. Think Before Coding
- State your assumptions first; ask when unsure
- When there are multiple solutions, lay out the options instead of silently picking one
- If a simpler approach exists, raise it
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
- Stay consistent with the existing style
- Clean up orphan code you produced; do not proactively delete pre-existing dead code

### 4. Goal-Driven Execution
- "Add validation" → "write a test for the invalid input, make it pass"
- "Fix the bug" → "write a reproducing test, then fix"
- For multi-step tasks, give a verifiable step plan

---

**Signs these conventions are taking hold**: fewer incidental changes in diffs, fewer rewrites caused by over-complication, and questions raised before action rather than after errors.
