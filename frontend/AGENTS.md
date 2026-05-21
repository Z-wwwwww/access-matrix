# Access Matrix — 前端 AI 开发规约

> 配套后端: `../backend/`（Spring Boot 4 + Spring Security 6 + JWT + 多租户 RBAC），监听 `:9135`。本仓是 monorepo，根级跨栈规约见 [../AGENTS.md](../AGENTS.md)。
> Vite 通过 `/proxy_url` 代理到后端，开发端口默认 `5273`。

## Project Overview
本项目是 **Access Matrix**（RBAC/権限矩阵）的管理前端：用户・ロール・権限・メニュー・部署・操作ログ 等系统功能 + 后续可挂接的业务模块（如 PMS）。视图、组件、服务采用 **AI 驱动开发流程**（Spec + Skill + 自然语言）。

## Tech Stack (MANDATORY)
- Vue 3.5+ (Composition API, `<script setup>`)
- **JavaScript ONLY**（TypeScript 禁止）
- Vite 6
- Tailwind CSS v4（`@import "tailwindcss"`、`@theme` token）
- Radix Vue（headless 基底）
- class-variance-authority (CVA), clsx, tailwind-merge
- ECharts 6 + vue-echarts
- lucide-vue-next（图标）
- Vue Router 4
- Pinia
- Axios（services/request.js 全局拦截器）
- **vue-i18n v9** (Composition API mode, `legacy: false`) — 支持 ja_JP / en / zh_CN / zh_TW / ko_KR；默认 ja_JP

## Hard Rules
1. **NO TypeScript** — 全栈 JS
2. **NO inline styles** — 仅 Tailwind class
3. **NO API calls in components** — HTTP 全部走 `services/`
4. **NO pages in components/** — 页面放 `src/views/`
5. **NO duplicate components** — 创建前先查 `docs/component-register.md`
6. **NO hand-written `<table>`** — 数据表全部用 `@/components/shared/DataTable`（含 tree 用法见 §"Tree table"）
7. **NO native form 元素**（在 views 里）—— `<input>`/`<select>`/`<textarea>`/`<input type=date>`/`<input type=checkbox>`/`<input type=radio>` 一律换成 `@/components/ui/` 下对应的 `Input` / `Select` / `Textarea` / `DatePicker` / `Checkbox` / `RadioGroup`（或 `Radio`）
8. **NO `window.confirm()` / `alert()`** — 用 `useConfirm()` + 全局挂载的 `<ConfirmDialog />`
9. 命名：组件 PascalCase；服务/composable camelCase
10. 所有 props 必须有 `default`
11. 字典数据来自 `src/dict/storage.js`（静态打包），通过 `useDict(code)` / `useDicts([code,...])` 取
12. **NO hardcoded business strings**（新代码）—— 用户可见的 label/title/placeholder/button/toast 必须 `t()`，翻译落到 `src/lang/{locale}.js` 或 `src/lang/{module}/{locale}.js`
13. **详情页模板** (`*Edit.vue` / `*Detail.vue`)：外层 `<Card>` → header (`flex justify-between p-4 border-b`) → `<Tabs v-model="activeTab" :items="tabItems">`（不要 `container-class`/`sticky`）→ 可选 footer。参考 `RoleEdit.vue` / `UserEdit.vue`

## 系统文件 vs 业务文件 — 放哪

**核心原则**：access-matrix 是平台型项目，**系统模块**（账号/权限/审计）跟**业务模块**（PMS/CRM/...）必须分目录、分服务。后端模块边界也是同样的（`core-system` vs `business-{name}`）。

### Views（页面）

| 类型 | 目录 | 例子 |
|------|------|------|
| 系统管理 | `src/views/system/{Feature}/` | `system/User/User.vue`、`system/Role/RoleEdit.vue`、`system/Menu/Menu.vue`、`system/Dept/Dept.vue`、`system/Permission/Permission.vue`、`system/OpLog/OpLog.vue` |
| 业务模块 | `src/views/{businessModule}/{Feature}/` | `{module}/{Feature}/{Feature}.vue`、`{module}/{Feature}/{Feature}Edit.vue` —— `{module}` 由各业务自取（如 `pms` / `crm`），按需新建 |
| 登录/公共 | `src/views/login/`、`src/views/404.vue`、`_iframe.vue`、`_redirect.vue` | 同左 |

> 基盘**不预建命名占位目录**。需要哪个业务模块就新建对应的 `src/views/{module}/`；多业务可以并存，规则只约束**层级和命名风格**（kebab/camel 业务名 + PascalCase Feature）。

### Services（API 封装）

**所有 `.js` 平铺在 `services/` 一级，不开子目录。** 通过文件名前缀区分归属。

| 类型 | 文件命名 | 例 |
|------|---------|----|
| 底层 | `request.js` | Axios 实例（拦截器 / token 头 / 错误统一处理）—— 任何业务文件不要绕开它直接 `import axios` |
| 系统域 | 无前缀，简单名词 | `auth.js`、`user.js`、`role.js`、`permission.js`、`menu.js`、`dept.js`、`oplog.js`、`scope.js`、`dict.js` |
| 通用下拉/辅助 | 无前缀 | `adminCommon.js`（占位） |
| 业务域 | **以业务模块名为前缀** + camelCase 拼具体资源 | `pmsReservation.js`、`pmsPayment.js`、`pmsListingProperty.js`、`crmCustomer.js` |

规则：
- 一个业务 service 文件超过 ~15 个时再考虑开 `services/{module}/` 子目录，**默认不开**
- 不要把业务接口塞进系统域文件
- 不要在组件里 `import axios`；HTTP 一律走 services 层

### 组件分层（不允许跨层引用反向依赖）

| 层 | 目录 | 谁能用 |
|----|------|--------|
| 基础 UI | `src/components/ui/` (Card / Input / Select / Drawer / Dialog / Checkbox / DataTable 用到的内部部件等) | 谁都能用，自己不依赖其他层 |
| 通用业务 | `src/components/shared/` (DataTable / UserPicker / DictPicker / ConfirmDialog / IconPicker / LucideIcon / LoadingOverlay / FileDownloadLink / ExportFileButton / DateRangeSelector / SwitchField / AreaCascader / SingleImgManualUploader / ToastContainer) | 用 ui/；不能反向被 ui 依赖 |
| Layout | `src/components/layout/` (AppLayout / AppHeader / AppSidebar / AppTabBar / EmptyLayout / ChangePasswordDialog) | 用 ui + shared |
| 页面 | `src/views/` | 用 ui + shared + layout + composables + services |

### 其他

- `src/composables/` —— 跨页可复用的逻辑（useDict / useConfirm / useTheme / useToast / usePopupFollowTrigger 等）
- `src/lib/` —— 纯函数工具（cn / cva / date / download / validators）
- `src/stores/` —— Pinia store
- `src/dict/` —— 静态字典数据
- `src/lang/` —— vue-i18n 翻译
- `src/router/` —— **仅静态路由**（登录/公共/兜底），业务路由由后端菜单驱动动态注入
- `src/styles/` —— 全局 CSS / Tailwind tokens (`main.css`)
- `ai/specs/` —— AI Skills 生成的 spec 文件，类型分子目录 (`components/`, `pages/`, `services/`, `stores/`, `composables/`)

## 路由（后端菜单驱动）

- 静态路由：`src/router/index.js` 仅注册免登录页面（`/login`、`/404`、`/forget` 等）
- 动态路由：路由守卫 `beforeEach` 内调 **`GET /api/menu/me`** 拿当前用户菜单 → `menuToRoutes()` 转换 → `router.addRoute()` 动态注入
- 业务页面只需在 `src/views/{module}/{Feature}/{Feature}.vue` 放好组件，**路径和层级由后端 `core_rbac_menu` 表控制**
- 后端 `component` 字段是相对路径（如 `/system/User/User`），前端目录大小写**不敏感**

## Backend API Conventions（access-matrix 后端）

| 项 | 实际 |
|----|------|
| Base URL | `http://127.0.0.1:9135/api`（dev），前端 Vite 通过 `/proxy_url` 代理 |
| 认证头 | **`Authorization: Bearer <jwt>`** —— 有 `Bearer ` 前缀，跟旧 PMS 后端不同 |
| 多租户 | 请求带 `X-Tenant-Id` header；JWT 已签发时后端从 `tid` claim 读 |
| Refresh token | HttpOnly Cookie `core_refresh`，axios `withCredentials: true` 自动携带 |
| 分页参数 | **`page` + `size`**（不是 `limit`/`pageSize`）。后端 `PaginationInnerInterceptor` maxLimit = 500 |
| 响应包装 | `{ code: 0, msg: "", data: ... }`，分页时 `data = { records, total, page, limit }` |
| 列表/详情/CRUD | RESTful：`GET /admin/{module}/list`、`GET /admin/{module}/{id}`、`POST /admin/{module}`、`PUT /admin/{module}/{id}`、`DELETE /admin/{module}/{id}` |
| Me-endpoints | `GET /api/menu/me` 当前用户菜单树；`GET /api/permission/me` 当前用户权限码 Set |
| 字典 | **静态**（build-time bundle from `src/dict/storage.js`）—— `useDict(code)` 直接取，**不走 HTTP** |
| 用户权限码 | JWT scope claim：`*:*`（超管）或 `__compact__`（其它，触发后端缓存查询）—— 前端不应解析 scope，统一用 `/permission/me` |
| 强制下线 | 后端 ForceLogoutFilter 全局检查；axios 401 拦截 → 清 token 跳登录 |
| 日期格式 | 后端要求 `yyyy-MM-dd HH:mm:ssZZ`（如 `2026-04-02 00:00:00+0900`）；强制 Asia/Tokyo；统一走 `@/lib/date` 的 `toBackendDate(val)` 转换 |

## Tree table（Dept / Menu 模板）

DataTable **没有原生 tree 模式**（它的 `expandable` 是行展开详情面板，不是 indent-children）。Tree 用法：

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

参考 `views/system/Dept/Dept.vue` 和 `views/system/Menu/Menu.vue`。

## Internationalization (i18n)

- 库：vue-i18n v9 (`legacy: false`)；入口 `src/lang/index.js`，main.js 中 `app.use(i18n)`
- 语言：ja_JP / en / zh_CN / zh_TW / ko_KR；默认 ja_JP；持久化到 `localStorage['i18n-lang']`
- 文件结构：
  - `src/lang/{locale}.js` —— 主语言文件，按模块 import 子文件
  - `src/lang/{module}/{locale}.js` —— 模块翻译
- key 命名：点分隔、小写模块名 + camelCase 字段，例：`user.company`、`reservation.checkInDate`、`common.button.search`
- 新代码：用 `t()` + 翻译键；不要硬编码业务文案
- **历史例外**：`views/system/*` 下的 6 个管理页 (User / UserEdit / Role / RoleEdit / Permission / Menu / Dept / OpLog) 是从老模板搬来 + 手工改造的，仍带硬编码日文，**不要为了 i18n 主动改它们**，等触发到具体页面修改时再迁

## AI Skills（Slash Commands）

通过 `pnpm ai:*` 调用，底层 `Claude Code` 执行，脚本 `scripts/ai-cli.mjs`：

| Skill | Command | Purpose |
|-------|---------|---------|
| create-component | `/create-component` | 用 Spec/自然语言创建组件 |
| create-page | `/create-page` | 创建页面 + 路由 |
| update-page | `/update-page` | 修改已有页面 |
| generate | `/generate` | 生成 service / composable / store / utils |
| inspect | `/inspect` | 审计重复/违规 |
| analyze | `/analyze` | 解析现有代码 → spec |

Spec 块示例（任何 skill 都能直接吃）：

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

也可纯自然语言："创建一个用户头像组件，支持 sm/md/lg 三个尺寸"。

## Registries（创建前必查）

- 组件注册：`docs/component-register.md`
- 服务注册：`docs/service-register.md`

每个生成的 component / service 都要登记，避免重复造轮子。

---

## Behavioral Guidelines

### 1. Think Before Coding
- 先表态假设，不确定就问
- 多解时把选项摊开，别静悄悄选
- 有更简方案就提出
- 不懂的点直接停下、命名困惑、问

### 2. Simplicity First
- 最少代码解决问题
- 不为单次用法做抽象
- 不加未要求的"灵活性"
- 不为不可能的场景写错误处理
- 写到 200 行能写成 50 行 → 重写

### 3. Surgical Changes
- 只动该动的
- 不顺手"改进"附近代码
- 不重构没坏的东西
- 跟现有风格保持一致
- 自己的改动产生的孤儿代码自己清；既有的死代码不主动删

### 4. Goal-Driven Execution
- "加校验" → "为非法输入写测试，跑通"
- "修 bug" → "写一个能复现的测试，再修"
- 多步任务给出可验证步骤计划

---

**这些规约生效的信号**：diff 里多余改动减少、过度复杂导致的重写减少、提问发生在动手前而不是错误后。
