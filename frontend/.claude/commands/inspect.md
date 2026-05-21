# Skill: inspect

你是 InnTouch UI 的项目审计专家。检查项目健康度、发现问题、输出诊断报告。

## Input: $ARGUMENTS

---

## 审计模式

根据用户输入选择审计范围。无参数时执行全量审计。

### 全量审计 (默认)

依次执行以下所有检查:

#### 0. 共通组件覆盖率扫描（漂移防御）

> **目的**：解决"先生成的页面不知道后生成的组件"。每次新组件加入后，旧页面里手写的等价实现就成了存量违规，必须主动找出来。

1. Glob `src/components/ui/*.vue` + `src/components/shared/*.vue` 拿到当前组件全集
2. 对每个共通组件，结合下面的"反模式映射表"在 `src/views/**/*.vue` 中扫描手写等价物
3. 命中即视为违规，按组件分组列出（file:line）

**反模式映射表**（持续维护，每次新加 ui/shared 组件应同时更新这里）：

| 组件 | 反模式正则 / 标志 |
|---|---|
| `Switch` | `relative inline-flex h-(5\|6) w-(9\|11)[^"]*translate-x-` 或 `@click="\w+\s*=\s*[^=]+ === ['"]1['"] \? ['"]0['"] : ['"]1['"]"` |
| `DataTable` | `<table\b` 在 `src/views/` 内 |
| `Input` (文本) | `<input\b[^>]+type="(text\|password\|email)"` |
| `Input format="money"` | `<input\b[^>]+type="number"` 且字段名匹配 `*Amount` / `*Fee` / `*Price` |
| `Select` | `<select\b` |
| `DatePicker` | `<input\b[^>]+type="date"` |
| `DateTimePicker` | `<input\b[^>]+type="datetime-local"` |
| `TimePicker` | `<input\b[^>]+type="time"` |
| `Dialog` | 手写 `fixed inset-0 z-\d+ bg-black/50` overlay |
| `Tabs` | `src/views/` 内直接 import `TabsRoot`/`TabsList`/`TabsTrigger` from 'radix-vue' — 应用 `@/components/ui/Tabs.vue` + `:items` 数组驱动（`TabsContent` 仍可从 radix-vue 直接导入） |
| `Collapse` | 手写 `<button @click="\w+\s*=\s*!\w+">` + `<ChevronDown[^>]+rotate-180` + `<div v-show=` 三段式折叠面板（在 `src/views/` 内）— 应用 `@/components/ui/Collapse.vue` |
| `Card` | (业务组件，按需) |

**新增组件时**：在 create-component 的 Phase 5.5 已经会做一次单组件回扫，但 inspect 这里是项目级总扫描 — 跑 `/inspect compliance` 时所有组件一起扫，避免有人忘了运行单组件回扫。

#### 1. 规范合规检查
- [ ] 扫描所有 `.vue` 和 `.js` 文件，检查是否有 TypeScript 语法 (`: string`, `interface`, `type X =`, `as X`)
- [ ] 扫描所有 `.vue` 文件，检查是否有 inline style (`style="..."`, `:style="..."`)
- [ ] 检查组件命名是否 PascalCase
- [ ] 检查 service 命名是否 camelCase
- [ ] 检查 props 是否都有默认值
- [ ] 扫描 `src/views/` 下的 `.vue` 文件，检查是否有手写 `<table>` 标签（应使用 `DataTable` 共通组件）
- [ ] 扫描 `src/views/` 下的 `.vue` 文件，检查是否有原生 `<input>`/`<select>`/`<input type="date">`（应使用 `Input`/`Select`/`DatePicker` 共通组件）
- [ ] 扫描 `src/views/` 下的 `.vue` 文件，检查是否有手写 `<button>` 实现的开关样式（`relative inline-flex h-(5|6) w-(9|11)` + `bg-primary` 切换 / `translate-x-` 滑块），都应该改用 `Switch` 共通组件（`@/components/ui/Switch.vue`，支持 `checked-value` / `unchecked-value` 任意值映射）
- [ ] 扫描 `src/views/` 下，金额字段是否使用 `<Input format="money" />`：
  - 任何 v-model 绑定到 `*Amount` / `*Fee` / `*Price` / `*Money` / `料金` 等的 `<Input>` 必须设 `format="money"`，否则没有千分位显示
  - 同上类字段的原生 `<input type="number">` 一律是违规（既缺千分位又破坏共通组件约定）
- [ ] 扫描 `src/views/` 下，邮件相关字段（v-model 绑定到 `*email` / `*Email` / `mail`）所在页面的 `validate()` 函数是否调用了 `isValidEmail`（来自 `@/lib/validators`）。绑定字段但 validate 不 check 即视为遗漏
- [ ] 扫描 `src/views/` 下，凡是页面里出现 `selection` ref / `handleBatchDelete` / `一括削除` 按钮的，对应 `<DataTable>` 必须设 `selectable` + `v-model:selection="selection"`。漏配会导致 checkbox 列不显示、批量按钮永远拿到空数组
- [ ] 多 tab 编辑页（`src/views/**/*Edit.vue`）扫描 `errors[xxx] = true` 的所有 key，对照 `FIELD_TAB_MAP`：
  - 静态 key 必须在 map 中
  - 動态 key（`errors[\`guest...${i}\`]` 模板字符串、嵌套数组生成）必须有 `resolveFieldTab` 前缀フォールバック
  - 漏配会让 validate 失败时不切 tab，用户误以为保存卡住
- [ ] 扫描 `src/views/` 下的 `.vue` 文件，检查是否有 `alert(` 调用 — 验证错误用 `:error` prop，操作结果用 `toast.success()` / `toast.error()`（来自 `@/composables/useToast`）
- [ ] 扫描 `src/views/` 下的 `.vue` 文件，检查是否直接 `import dayjs` 然后 `.format('YYYY-MM-DD HH:mm:ssZZ')`（应统一从 `@/lib/date` 引入 `toBackendDate`）
- [ ] 扫描 `src/views/` 和 `src/components/` 下是否有 **展示侧** 日期反模式：
  - `\.substring\(0,\s*10\)` / `\.slice\(0,\s*10\)` 用于截取日期前 10 位 — 后端可能返回 UTC 格式（`...+0000`），裸截取会偏一天，必须先 `toJSTDateStr(val)` 归一到 JST
  - `dayjs\(val\)\.format\('YYYY[-/]MM[-/]DD` 这种把后端日期直接 format 输出 — dayjs 默认本地时区，部署到非 JST 浏览器/服务器会错位，应用 `toJSTDateStr` / `toJSTDateTimeStr` / `toJSTDateTimeFullStr`
  - `new Date\(\)\.toISOString\(\)\.slice\(0,\s*10\)` 取"今天" — `toISOString` 是 UTC，JST 凌晨 0–9 点会跳到昨天，应用 `todayJSTStr()`
  - `dayjs\(someStr\.substring\(0,\s*10\)\)` 这种"先截再 dayjs"的两步反模式 — 同上，必须先 `toJSTDateStr`
- [ ] 扫描编辑页面是否有 `rawData` ref 保留原始 API 响应（避免保存时 `createdDate` 等系统字段丢失）
- [ ] **编辑页 `loadDetail` 必须复刻 cascade handler 的 info API 副作用**（`src/views/**/*Edit.vue`）：
  - 对每个 `onXxxChange(val)` 函数，提取它调用的 info 类 API（命名模式 `get\w+InfoApi` 或返回对象而非数组）以及赋值给 `form.*` 的字段块
  - 检查 `loadDetail()` 里是否对同一 `form.xxxId` 也调了同一个 info API 并做了相同赋值
  - 漏了即违规：直接赋值 `form.xxxId = data.xxxId` **不触发** `@update:model-value`，onXxxChange 不会被调 → 只能从 info API 读的只读字段（`companyName` / `propertyChargeName` / `contractTypeName` 等）全是空。前端看不到任何错误，用户以为数据丢了
  - 已知事故：`ReservationEdit.vue` 的 `loadDetail` 漏了 `getPropertyInfoApi` 的 7 个字段赋值，导致详细页事業者/施設担当者栏永远空白。建议在 loadDetail 里用 `if (!form.xxx) form.xxx = pi.xxx` 兜底赋值（reservation 记录本身有值就用本身的，为空才从 propertyInfo 补）
  - 规范详见 `create-page.md` "编辑模式 loadDetail 必须复刻所有 cascade handler 的 API 调用"
- [ ] 扫描 `@update:model-value="onXxxChange()"` 这种**带空括号**的 handler 调用 — 如果对应的 handler 函数签名是 `function onXxxChange(val) { ... }`（即需要新值），调用方必须改成 `onXxxChange($event)`，否则 val 收到 undefined 会把 v-model 刚设的值覆盖掉，下拉看起来无法选择。`clearError('xxx')` 等不需要值的 handler 可以不传
- [ ] 扫描编辑页（`src/views/**/*Edit.vue` 等）中所有绑定到 `form.*` 的 `<DatePicker>` / `<DateTimePicker>` / `<DateRangePicker>` 是否都加了 `value-format="YYYY-MM-DD HH:mm:ssZZ"` prop。保存用的日期字段必须加这个 prop（picker 内部会自动调 `toBackendDate` 输出后端格式），漏配会在保存时被 Jackson 反序列化拒绝报 `InvalidFormatException`。特别注意嵌套数组里的 picker（如 `form.guestInfoList[i].birthday`）。过滤条件用的 picker（绑定到 `searchForm.*`）不用加 prop，保持默认 `YYYY-MM-DD` 即可
- [ ] **i18n 硬编码业务字符串扫描**：扫描 `src/views/` 和 `src/components/shared/` 下 `.vue` 文件中模板和 `<script setup>` 里的硬编码日语/中文字符串。命中模式：
  - 模板内：`<label>...日语...</label>` / `<button>...日语...</button>` / `<h1>...日语...</h1>` 等直接的日文字面量
  - 模板内属性：`placeholder="日语..."` / `:placeholder="'日语...'"` / `title="日语..."`（应改成 `:placeholder="t('xxx.yyy')"`）
  - 脚本内：`toast.success('日语...')` / `toast.error('日语...')` / `confirm('日语...')` / `alert('日语...')`
  - DataTable columns：`{ key: ..., title: '日语...' }`（应改成 `t('xxx.yyy')`）
  - 命中规则：识别 `[\u3040-\u30ff\u4e00-\u9fff]` Unicode 区间的连续 2+ 字符
  - **白名单**：system 模块下的 legacy 管理页（`src/views/system/{User,Role,Permission,Menu,Dept,OpLog}/*.vue`）整体跳过这个检查 —— 这些是 i18n 接入前的历史包袱，按需迁移而非主动批量替换
  - **每个命中**输出：文件路径 + 行号 + 命中字符串 + 建议（"建议提取到 `src/lang/{module}/ja_JP.js` 并改用 `t('module.field')`"）
- [ ] **i18n 用法正确性扫描**：
  - 扫描所有 `.vue` 文件，看是否有 `useI18n()` 调用在 callback / 函数体内（必须在 `<script setup>` 顶层）
  - 扫描是否有 `import { i18n }` 然后 `i18n.global.t(...)` 这种走 global 的写法 —— 在 setup 里应该用 `useI18n()`，只有 setup 外（router guard、service interceptor）才用 `i18n.global.t`
  - 扫描 spec 字段同时出现 `label` 和 `labelKey` —— 应该只用 `labelKey`，`label` 是迁移残留

#### 2. 目录规范检查
- [ ] 检查 `src/components/` 下是否有页面级代码（应在 views/）
- [ ] 检查 `src/views/` 下是否有可复用组件（应在 components/）
- [ ] 检查是否有 API 调用写在组件内（应在 services/）
- [ ] **shared 组件内直接调 request 扫描**：在 `src/components/shared/*.vue` 里匹配 `import request from .*services/request` 且同时 `request\.(get\|post\|put\|delete)\(['"]\/[^'"]+['"]` 命中硬编码 URL → 违规，应提取到对应 `services/{module}.js`。例外：`ExportFileButton` / `SingleImgManualUploader` 等 URL 作为 prop 传入的通用工具组件允许保留
- [ ] 检查文件是否放在正确的目录

#### 3. 重复检查
- [ ] **命名规范扫描**：`src/components/shared/` 和 `src/components/ui/` 下是否有违规后缀的组件：
  - 命中 `(\w+)(List|Select|Selector|Dropdown|TreeSelect|PickerModal)\.vue` 的文件名 → 视为违规（正确后缀见 `create-component.md` Phase 2.5 / `docs/component-register.md` 命名规范表）
  - 例外：`List` 作为纯展示组件后缀（无选择语义）允许保留 —— 人工确认
  - 违规示例：`CustomerList.vue`（应为 `CustomerPicker`）、`OwnerSelect.vue`（应为 `OwnerPicker`）
- [ ] **语义指纹查重**：扫描所有组件，对每一对组件比对：
  - 是否调用同一后端 API
  - props 签名语义是否相同（忽略类型/默认值差异）
  - 命中即视为重复嫌疑，输出两者 file:line 让用户裁决
- [ ] **Picker 二重请求反模式扫描**：`src/components/shared/*Picker.vue` 内如果同时出现 `onMounted(loadXxx)` 和 `watch(() => props.xxx, loadXxx)`（无 `immediate: true`），是反模式，应改为 `watch(..., { immediate: true })` + 空值 guard。详见 `create-page.md` 事故复盘
- [ ] 对比 `docs/component-register.md` 与实际文件，找出:
  - 注册表中有但文件不存在的（幽灵注册）
  - 文件存在但未注册的（遗漏注册）
- [ ] 对 `docs/service-register.md` 做同样检查

#### 4. 依赖完整性检查
- [ ] 扫描所有 import，检查引用的组件/服务/store 是否存在
- [ ] 检查路由配置中引用的页面是否存在
- [ ] 检查未使用的组件/服务（没有被任何文件 import）

#### 5. 代码质量快检
- [ ] 检查是否有空文件（仅含 .gitkeep 的目录）
- [ ] 检查是否有超大组件（>300行，建议拆分）
- [ ] 检查是否有 console.log/debugger 残留

---

### 定向审计

用户可以指定范围:

```
/inspect components     → 仅审计组件
/inspect services       → 仅审计服务
/inspect page UserList  → 审计指定页面
/inspect duplicates     → 仅查重
/inspect registry       → 仅检查注册表一致性
/inspect compliance     → 仅检查规范合规
```

---

## 输出格式

```
# 🔍 InnTouch UI 审计报告

## 概览
- 组件总数: X (ui: X, shared: X, layout: X)
- 页面总数: X
- 服务总数: X
- Store 总数: X
- Composable 总数: X

## ❌ 问题 (需要修复)
1. [VIOLATION] {file}: 使用了 TypeScript 语法 (line X)
2. [VIOLATION] {file}: 使用了 inline style (line X)
3. [DUPLICATE] {CompA} 和 {CompB} 功能高度相似
4. [MISSING] {Component} 被引用但不存在
5. [ORPHAN] {file} 存在但未注册

## ⚠️ 警告 (建议修复)
1. [LARGE] {file}: 超过 300 行，建议拆分
2. [UNUSED] {Component} 未被任何文件引用
3. [DEBUG] {file}: 包含 console.log (line X)

## ✅ 通过
- 命名规范: PASS
- 目录结构: PASS
- 注册表一致性: PASS

## 📊 统计
- 合规率: X%
- 问题数: X 个
- 警告数: X 个
```

如果发现问题，主动提供修复建议。对于简单问题（如遗漏注册），询问用户是否自动修复。
