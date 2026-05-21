# Skill: update-page

你是 InnTouch UI 的页面更新专家。根据用户输入修改现有页面，保持项目规范和一致性。

## Input: $ARGUMENTS

---

## 执行流程

### Phase 1: 定位目标页面
1. 从输入中识别要修改的页面（名称 / 路由路径 / 文件路径）
2. 如果不明确 → 扫描 `src/views/` 列出所有页面，让用户选择
3. 读取目标页面当前完整代码

### Phase 2: 解析修改意图
用户可以用以下方式描述修改:

**Spec 方式** — 增量更新:
```yaml
update:
  target: PageName            # 目标页面
  add:
    sections:
      - type: chart
        description: "添加销售趋势图表"
    components: [SalesChart]
    services: [salesService.getTrend]
  modify:
    sections:
      - target: filter        # 要修改的区块
        description: "增加日期范围筛选"
  remove:
    sections: [old-section-id]
```

**自然语言方式**:
- "给用户列表页面加一个导出按钮"
- "把订单页面的表格改成卡片布局"
- "在仪表盘添加一个实时数据图表"

如果 `ai/specs/` 下有对应页面的 spec → 读取作为当前状态参考。

### Phase 3: 影响分析
1. 分析修改涉及的文件范围
2. 检查新增依赖是否存在（组件/服务/store）
3. 评估是否有破坏性变更（删除 props、改变数据结构）
4. 输出影响摘要，等待用户确认

### Phase 4: 执行修改
严格遵守:
- 保持现有代码风格一致
- JavaScript ONLY、Tailwind ONLY
- 不破坏现有功能
- 最小化变更范围
- 如果需要新组件 → 提取为独立组件（不在页面内写大段 template）
- 如果涉及新 API → 在 services/ 中创建
- **折叠面板必须使用 `Collapse` 共通组件** (`@/components/ui/Collapse.vue`)，禁止手写 `<button @click>` + `<ChevronDown :class="{'rotate-180':...}">` + `<div v-show>` 三段式。state 用 `modelValue=true=open` 正向语义（不要 `collapsed` 反向）。详见 `create-page.md` "Collapse 共通组件使用规范"
- **多 tab 页面必须使用 `Tabs` 共通组件** (`@/components/ui/Tabs.vue`)，禁止在 views 内直接 import `TabsRoot`/`TabsList`/`TabsTrigger` from 'radix-vue'（`TabsContent` 仍从 radix-vue 直接 import）。tab 定义用 `:items=[{value,label,visible?,disabled?,badge?,icon?}]` 数组驱动，不要堆 `<TabsTrigger v-if>`
- **数据表格必须使用 `DataTable` 共通组件** (`@/components/shared/DataTable`)，禁止手写 `<table>`
- **表单输入必须使用共通组件**: `Input`(`@/components/ui/Input.vue`)、`Select`(`@/components/ui/Select.vue`)、`DatePicker`(`@/components/ui/DatePicker.vue`)，禁止原生 `<input>`/`<select>`/`<input type="date">`
- **表单验证不可用 `alert()`** — 使用 `:error` prop + reactive `errors` 对象 + `data-field` 自动滚动
- **保存结果不可用 `alert()`** — 使用 `import { toast } from '@/composables/useToast'`，调用 `toast.success()` / `toast.error()`
- **API 响应 `code !== 0` 必须 `toast.error(res.data.msg)`** — 所有写操作（保存/删除/更新/销账/审批等）在判断 `if (res.data.code === 0)` 后**必须**加 `else { toast.error(res.data.msg) }`，禁止只写成功分支而忽略后端业务错误（如排他锁冲突、权限不足等）
- **日期保存到后端必须用 `@/lib/date` 的 `toBackendDate()`** — 强制 JST 时区，避免本地 TZ 偏移
- **展示和计算后端日期必须走 `@/lib/date` helper**（`toJSTDateStr` / `toJSTDateTimeStr` / `toJSTDateTimeFullStr` / `todayJSTStr`），禁止 `val.substring(0, 10)`、`dayjs(val).format()`、`new Date().toISOString().slice(0, 10)` 等无时区感知的写法 — 后端可能返回 UTC 格式，裸操作会偏一天。年龄/天数等差值计算也要先 `toJSTDateStr` 归一再传给 dayjs
- **金額字段必须用 `<Input format="money" />`** — 自动千分位 + 右寄せ、focus 時は素数字、v-model は常に Number。禁止裸 `<input type="number">`。改造遗留页面时一并迁移
- **トグルスイッチ字段必须用 `<Switch />`** — 后端 `'0'/'1'` 值用 `checked-value="1" unchecked-value="0"`、Number 值同理。禁止手写 `<button>` + bg-primary 切换样式
- **書式チェックは `@/lib/validators` ヘルパー** — `isValidEmail(val)` 等、validate() 内で呼び出して `errors[key]` にセット。alert は禁止
- **新增任何 errors[key] 时必须同步 `FIELD_TAB_MAP`** — 包括書式チェック专用字段（confirmEmail 等）和ネスト配列の動的 key（`guestEmail_${i}` → `resolveFieldTab` で前缀フォールバック）。漏配会让 validate 失败时不切 tab，用户看不到红框，误以为保存卡住
- **编辑页面必须保留 `rawData`** — 加载时 deep clone API 响应到 `rawData.value`，`buildPayload` 中 `{...rawData, ...form}` merge，避免 `createdDate`/`createUser` 等系统字段丢失
- 如果页面中有原生表单元素、手写 table、`alert()` 验证、直接 `dayjs().format()` 处理后端日期，应迁移到上述共通模式
- **`@update:model-value` 调用 handler 时必须显式传 `$event`** — 需要新值的 handler（cascade reset、change 联动等）写成 `@update:model-value="onXxxChange($event)"`，**不能**写 `onXxxChange()`（空括号不会自动传 payload，函数收到 `undefined`，会把 v-model 刚设的值覆盖成 undefined，下拉看起来无法选择）。只有 `clearError('xxx')` 这种不需要值的 handler 才能用空括号写法
- **编辑页所有保存用日期 picker 必须加 `value-format="YYYY-MM-DD HH:mm:ssZZ"`** — `DatePicker` / `DateTimePicker` / `DateRangePicker` 都支持这个 prop，设上之后 picker 会在 emit 时自动走 `toBackendDate`，form 里存的就是最终后端格式，`buildPayload` 无需再做日期转换。未设该 prop 的 picker（比如过滤条件）保持默认 `'YYYY-MM-DD'` 输出。漏配会报 `InvalidFormatException`。**特别注意嵌套数组里的 picker**（如 `form.guestInfoList[i].birthday`），很容易遗漏
- **i18n MANDATORY（新增字段时）**：新加的 label / placeholder / button text / toast / confirm 必须用 `t('xxx.yyy')` 调用，**禁止**硬编码业务字符串。`useI18n()` 在 `<script setup>` 顶层调用一次拿 `t`。新 key 同步加到 `src/lang/{module}/{locale}.js` 五个 locale 文件。详细规范见 create-page.md "i18n 使用规范" 章节。**例外**：现有 5 个 legacy 页面（User / UserEdit / Reservation / ReservationEdit / ListingProperty）整体仍是硬编码日语，update 这些页面**新增**字段时也要用 `t()`，但**不要**主动把已有字段从硬编码改成 `t()`（避免一次改动里混 i18n 迁移和业务变更，diff 不可读）
- **新增 picker 组件依赖必须用 `{Entity}Picker` 命名** —— 不得新建 `{Entity}List`/`{Entity}Select`/`{Entity}Selector`/`{Entity}Dropdown` 作为下拉组件；添加依赖前先查 `docs/component-register.md` 语义指纹（同 API + 同 props 语义 = 重复）。详见 `create-component.md` Phase 2.5
- **新增依赖异步 form 字段的 picker 时必须用 `watch(src, fn, { immediate: true })`** —— 不要用 `onMounted + watch` 的二重触发写法。编辑模式下 form 字段来自 `loadDetail()` 异步填充，老写法会先以空值发一次请求，回填后再发第二次。模板：picker 内 `if (!props.xxx) { options.value = []; return }` + `watch(() => props.xxx, load, { immediate: true })`。详见 `create-page.md` Phase 5 的事故复盘

### Phase 5: 处理依赖
1. 新增组件 → 检查是否存在，不存在则创建
2. 新增服务 → 创建并注册
3. 新增 store → 创建
4. 更新路由 meta（如需要）

### Phase 6: 自检 & 输出
验证:
- [ ] 原有功能未被破坏
- [ ] 新增代码符合规范
- [ ] 依赖已处理
- [ ] 无 TypeScript、无 inline style

输出摘要:
```
✅ Updated: {PageName}
📁 Modified files:
   - src/views/{module}/{Name}/{Name}.vue
   - ...
➕ Added:
   - {new components/services if any}
📋 Changes:
   - {change description 1}
   - {change description 2}
```
