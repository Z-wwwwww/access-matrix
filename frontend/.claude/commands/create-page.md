# Skill: create-page

你是 InnTouch UI 的页面生成专家。根据用户输入（Spec / 自然语言 / 混合）创建完整的页面视图。

## Input: $ARGUMENTS

---

## 执行流程

### Phase 1: 解析输入
1. 检测 Spec（YAML `page:` 块）和自然语言
2. 如果引用了 `ai/specs/` 下的已有 spec 文件 → 读取并使用
3. 提取: 页面名称、所属模块、路由配置、使用的组件/服务/store、页面区块
4. 合并 Spec + NL，生成完整页面规格

### Phase 2: 依赖检查 & 自动加载 spec

**起手必做**：在解析任何 spec 之前，先扫描 `src/components/ui/` 和 `src/components/shared/` 拿到**当前可用的共通组件全集**（不要只信任本文档列出的清单 — 项目会增长）。这一步是为了避免"清单是写死的，新加的组件不知道"的漂移问题。生成代码时遇到任何字段，先在这个动态全集里找匹配，找不到再考虑生成原生 / 占位。

1. 如果输入引用了 `ai/specs/pages/*.yaml` → 读取该 spec
2. 检查 spec 中的 `dependencySpecs` 字段 → 自动读取所有关联 spec 文件，**按 spec 路径前缀分类**（这一步极其关键，分类错会导致文件落到错位置）：

   | `spec:` 字段路径前缀 | 分类 | 目标 |
   |---|---|---|
   | `ai/specs/services/` | service | `services/{name}.js` |
   | `ai/specs/stores/` | store | `src/stores/{name}.js` |
   | `ai/specs/composables/` | composable | `src/composables/{name}.js` |
   | `ai/specs/components/` | component | 按 `componentScope` 决定路径（见第 3 步） |
   | **`ai/specs/pages/`** | **sibling page** | **`src/views/{module}/{ParentPage}/{SiblingName}.vue`** —— 和当前主页面**同级**，**绝不**落到 `components/` 子目录 |

   **特别提醒 sibling page 的情况**：当主页面是 list（如 `User.yaml`）且 dependencySpecs 里有 `{ spec: ai/specs/pages/UserEdit.yaml, relation: "edit-page" }` → 这是和 list 同级别的"兄弟编辑页"（旧项目里典型的 `views/{module}/{entity}/{entity}-edit.vue` 模式），新项目实现时也保持同级布局：

   ```
   src/views/system/User/
     ├── User.vue          ← list（主页面）
     ├── UserEdit.vue      ← sibling edit page（不放 components/）
     └── index.js
   ```

   即便 sibling page 实际上是被 list 当 modal 用（`<UserEdit v-model:visible="..." />`），它的**文件位置**仍然是 list 的兄弟，**不是** list 的子目录组件。这是 spec 分类（path-only）和实现层（如何渲染）的解耦。

3. **扫描字段级 component 引用**（针对表单页）:
   - 遍历 `fields[]` 中每一项，找出有 `component: "X"` 的字段
   - 跳过本项目已有的共通组件（`Input`/`Select`/`DatePicker`/`DateTimePicker`/`TimePicker`/`DateRangePicker`/`Switch`/`Checkbox`/`Radio`/`RadioGroup`/`Badge` 等基础 UI）
   - 剩下的组件名加入待处理列表，按 `componentScope` 分类:
     - `componentScope: "shared"` → 目标路径 `src/components/shared/{X}.vue`
     - `componentScope: "page"` → 目标路径 `src/views/{module}/{Page}/components/{X}.vue`
     - 未指定时默认 `shared`
4. 对照项目中实际文件，把所有依赖（包括字段级组件、sibling page）分类:
   - **已存在** — 项目中已有，直接引用
   - **有 spec 无代码** — `ai/specs/` 中有 spec（component 路径优先取 `componentSpec` 字段，sibling page 取 `pageSpec` 字段，都没写就按 `ai/specs/{type}/{X}.yaml` 约定查找）但尚未生成代码 → 标记为待生成
   - **无 spec 无代码** — 既无 spec 也无代码 → 输出明显警告，生成占位 + TODO 注释（不阻断流程）
5. 输出依赖清单，**自动生成所有有 spec 的缺失依赖**（不需要用户确认）

#### 字段级 component 引用解析示例

输入 spec:
```yaml
fields:
  - name: ownerId
    type: select
    component: "CustomerPicker"
    componentScope: "shared"
    componentSpec: ai/specs/components/CustomerPicker.yaml
    props: { customerKb: "owner" }
```

Phase 2 决策:
- `CustomerPicker` 不在共通 UI 列表 → 进入待处理
- 检查 `src/components/shared/CustomerPicker.vue` 是否存在
- 不存在 → 检查 `ai/specs/components/CustomerPicker.yaml` 是否存在
- 存在 → 添加到「待生成依赖」队列，目标路径 `src/components/shared/CustomerPicker.vue`，由 Phase 5 在生成主页面之前调用 create-component skill 生成
- 模板渲染该字段时使用 `<CustomerPicker v-model="form.ownerId" customerKb="owner" />` 而**不是** `<Input>` 占位

注意：组件名遵循 analyze.md 的「组件命名约束」—— 必须是功能性命名（`Picker` / `Modal` / `Selector` 等），禁止 `{Entity}{List|Edit|Detail}` 这种和 page 撞型的格式。

### Phase 3: 生成页面
严格遵守:
- 页面放在 `src/views/{module}/` 下
- `<script setup>` + Composition API
- JavaScript ONLY
- Tailwind CSS ONLY
- **i18n MANDATORY**：所有用户可见文案（label / title / placeholder / button text / toast 信息 / 确认对话框等）必须用 `t()` 调用，**禁止硬编码**业务字符串。详见下方"i18n 使用规范"。
- 页面文件结构:
  ```
  src/views/{module}/{PageName}/
    ├── {PageName}.vue          # 主页面（list / 主入口）
    ├── {PageName}Edit.vue      # sibling edit page（如有；relation: edit-page 的 dependency）
    ├── {PageName}Detail.vue    # sibling detail page（如有；relation: detail-page 的 dependency）
    └── components/             # 页面私有"组件"（仅放真正的子组件，不放 sibling page）
        └── {SubComp}.vue
  ```
  **重要**：sibling page（`*Edit.vue` / `*Detail.vue`）属于 page 级别，**必须**放在 `{PageName}/` 目录下与主页面同级，**禁止**放进 `components/` 子目录。即便它在主页面里是被当 modal 使用，也不算"私有组件"。

  **不要生成 `index.js` re-export 文件**。本项目的动态路由通过 `import.meta.glob('/src/views/**/*.vue')` 直接解析 .vue 文件（见 `src/utils/menu-to-routes.js`），index.js 不在解析链路上；其他业务代码也都直接 `import X from './X.vue'`，不会绕一层 re-export。生成 index.js 只会增加 dead code。
- 页面组成区块（根据 Spec sections 或 NL 描述）:
  - **header**: 页面标题、操作按钮
  - **filter**: 搜索/筛选区域（**必须使用共通表单组件**，见下方 UI 共通组件规范）。**栅格默认 `grid grid-cols-3 gap-4`**，字段少可用 2 列，禁止用 4 列（单列过窄，DateRangePicker / 多选等会挤压）
  - **table**: **必须使用 `DataTable` 共通组件**（见下方 DataTable 使用规范）
  - **form**: 表单（弹窗或内联）（**必须使用共通表单组件**）
  - **chart**: 图表区域
  - **cards**: 卡片列表
  - **custom**: 自定义区块

### i18n 使用规范（MANDATORY — 禁止硬编码业务字符串）

本项目用 vue-i18n v9 + Composition API，lang 文件在 `src/lang/`。所有用户可见文案**必须**走 `t()`，**禁止**直接写 `"会社"` / `"検索"` / `"削除しますか？"` 等字面量。

#### 引入方式

```vue
<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
// 之后所有需要文案的地方都用 t('xxx.yyy')
</script>
```

注意：`useI18n()` 必须在 `<script setup>` 顶层调用一次，**不能**在函数体内或 callback 里调用（会拿到 wrong instance）。

#### 模板里使用

```vue
<template>
  <h1>{{ t('user.title') }}</h1>

  <!-- 共通 UI 组件的 props 都用 :prop="t(...)" 绑定 -->
  <Input
    v-model="form.keyword"
    :placeholder="t('user.keywordPlaceholder')"
  />

  <Select
    v-model="form.companyId"
    :options="companyOptions"
    :placeholder="t('common.label.allOptions')"
  />

  <!-- 按钮文案 -->
  <button @click="handleSearch">{{ t('common.button.search') }}</button>

  <!-- DataTable 列定义里的 title 也要用 t() -->
  <DataTable :columns="columns" ... />
</template>
```

```js
// columns 定义
const columns = computed(() => [
  { key: 'companyName', title: t('user.company'), minWidth: '150px' },
  { key: 'realname', title: t('user.realName'), minWidth: '150px' },
  { key: 'createTime', title: t('user.createTime'), minWidth: '160px', format: 'datetime' },
  { key: 'actions', title: t('common.label.action'), minWidth: '120px', sticky: 'right' }
])
// ⚠ columns 必须包成 computed —— 切语言时 t() 返回值变化、computed 才能 reactively 更新表头
```

#### Toast / 确认对话框

```js
import { toast } from '@/composables/useToast'
import { confirm } from '@/composables/useConfirm'

// ✅ 对
toast.success(t('common.message.saved'))
toast.error(t('user.message.deleteFailed'))

// 确认框用自定义 useConfirm（Dialog 样式），禁止用浏览器原生 window.confirm()
if (!(await confirm(t('common.message.delConfirm')))) return

// 危险操作（删除 / 清空 / 切断连接）用 destructive 变体，按钮变红
if (!(await confirm({ message: t('common.message.delConfirm'), variant: 'destructive' }))) return

// 自定义标题、按钮文字
await confirm({
  title: t('xx.title'),
  message: t('xx.message.confirm'),
  confirmText: t('common.button.delete'),
  cancelText: t('common.button.cancel'),
  variant: 'destructive'
})

// ❌ 错（硬编码 / 用原生 confirm）
toast.success('保存しました')
if (!confirm(t('common.message.delConfirm'))) return   // 浏览器原生弹窗，UI 难看
if (!confirm('削除してもよろしいですか？')) return         // 字面量硬编码
```

**硬性要求**：
- `confirm` 从 `@/composables/useConfirm` 引入，**禁止**使用 `window.confirm` / 全局 `confirm`
- 调用必须 `await`（返回 Promise<boolean>）
- 危险不可逆操作（删除、清除 token、解约、一括上書き）必须带 `variant: 'destructive'`

#### 从 spec 渲染：`labelKey` / `titleKey` / `placeholderKey` 字段

analyze 生成的 spec 把 i18n key 保存在带 `Key` 后缀的字段里。create-page 渲染时按下表映射：

| spec 字段 | 含义 | 渲染 |
|---|---|---|
| `labelKey: user.company` | 表单字段标签 | `<label>{{ t('user.company') }}</label>` |
| `titleKey: user.title` | 页面标题 / DataTable 列标题 | `t('user.title')` |
| `placeholderKey: user.keywordPlaceholder` | 输入框占位 | `:placeholder="t('user.keywordPlaceholder')"` |
| `buttonTextKey: common.button.search` | 按钮文字 | `{{ t('common.button.search') }}` |
| `confirmMessageKey: common.message.delConfirm` | 确认对话框文案 | `await confirm(t('common.message.delConfirm'))` （useConfirm，非原生） |
| `tooltipKey: ...` | tooltip / title 属性 | `:title="t('...')"` |
| `optionLabelKey: ...` | option 列表的 label 字段 | option 渲染时调 `t(...)` |

**spec 里如果同时出现 `label` 和 `labelKey`**：优先 `labelKey`（i18n），把 `label` 当成迁移残留警告记录到生成报告里。

**spec 里如果只有 `label: "字面量"` 没有 `labelKey`**：
- 生成时**仍然**用硬编码字面量渲染（兼容 5 个 legacy 页面）
- 但在生成报告里加 ⚠ 警告："field xxx in {Page} uses hardcoded label, should be migrated to labelKey + i18n entry"
- 不要替模型猜对应的 i18n key（猜错了下游全错）

#### 添加新 key 到 lang 文件

如果 spec 里出现的 i18n key 在 `src/lang/{locale}.js` 里**不存在**（典型场景：业务方临时加字段），生成代码时：
1. 把 key 加到 `src/lang/{module}/ja_JP.js`（按 namespace 选模块文件，找不到对应模块就建一个新的）
2. 同步把 key + 占位翻译加到 en/zh_CN/zh_TW/ko_KR 对应文件，值用 `'TODO: ja_value here'` 标注待翻译
3. 在生成报告里列出新增的 key，提醒用户后续补翻译
4. **绝对不要**只加一个 locale 不加其他 locale —— 缺 locale 会让 fallback 走 ja_JP，但其他语言切换时显示成日语，体验割裂

#### Legacy 5 个页面例外

User / UserEdit / Reservation / ReservationEdit / ListingProperty 这五个 spec 里全是 `label: "字面量"`，没有 `labelKey`。**不要**主动重写它们，按上面的"只有 label 没有 labelKey"分支走（硬编码渲染 + 警告）。这些是 i18n 接入前的历史包袱，等业务真要用其他语言时再批量迁移。

### UI 共通表单组件使用规范（MANDATORY）

所有表单输入**必须**使用以下共通组件，禁止使用原生 `<input>`、`<select>`、`<input type="date">`。
所有组件均支持 hover 显示清除按钮（X），统一交互体验。

#### Input — 文本入力 / 金額入力
```vue
import Input from '@/components/ui/Input.vue'

<!-- 通常文本 -->
<Input v-model="form.keyword" placeholder="キーワード..." @keyup.enter="handleSearch" />

<!-- 金額（必須）— format="money" で千分位カンマ表示、focus 中は素数字、v-model は常に Number -->
<Input v-model="form.accommodationAmount" format="money" />
```
Props: `modelValue`, `placeholder`, `type`(default:'text'), `format`(`''`|`'money'`), `disabled`, `error`, `class`
- `format="money"`：金額字段必须使用，自动千分位 + 右寄せ。v-model 始终是 Number（保存时无需任何转换），focus 时显示纯数字便于编辑，blur 时整形显示
- **禁止用 `type="number"` 的原生 `<input>` 处理金额**，inspect 会捕捉这类违规

#### Select — 下拉选择（单选 / 複数選択）
```vue
import Select from '@/components/ui/Select.vue'

<!-- 単一選択：options 格式 [{ label: '表示名', value: '値' }] -->
<Select v-model="form.status" :options="statusOptions" placeholder="全て" />

<!-- 複数選択：multiple=true、modelValue は Array -->
<Select
  v-model="form.roleIds"
  :options="roleOptions"
  :multiple="true"
  placeholder="ロールを選択"
/>
```
Props: `modelValue`(String|Number|**Array**), `options`([{label,value}]), `placeholder`, `disabled`, `clearable`, `error`, `searchable`, `multiple`, `class`
- 字典选项用 `useDict`/`useDicts` composable 获取（来源: `src/dict/storage.js`）
- 需要「全て」选项时，在 computed 中 prepend `{ label: '全て', value: '' }`
- **`options.length > 10` 时自动启用模糊检索**（在下拉面板顶部显示搜索框，按 label 不分大小写过滤），不需要业务代码自己实现。可通过 `searchable="true"` 强制开启 / `searchable="false"` 强制关闭，默认 `'auto'`。
- **`multiple="true"`**: `modelValue` 必须是 `Array`（form 初始化时写 `roleIds: []`），点击选项是 toggle（已选→移除、未选→追加），面板**不**自动关闭，按钮显示为逗号拼接的 label 列表，清除按钮 emit `[]`。**不要**为多选场景自己手写 checkbox 列表或寻找 `MultiSelect` 组件 —— 本项目只有 `Select` 这一个组件，多选靠 `multiple` prop 切换

**⚠ 编辑模式 `loadDetail` 必须复刻所有 cascade handler 的 API 调用（必须遵守）**

`loadDetail` 直接赋值 `form.propertyId = data.propertyId`，**不触发** `@update:model-value` → `onPropertyChange` 不被调用 → 只读显示字段（companyName / propertyChargeName / channelFeeRate 等）就是空的。

每层 cascade（ownerId → propertyId → roomTypeId → planId）必须对比 `onXxxChange()` 里调用的 API 列表，**完整复刻 dropdown + info/detail 两类 API**：

```js
// ❌ 漏 getPropertyInfoApi → companyName / propertyChargeName 为空
if (form.propertyId) {
  await getRoomTypeListApi(...)
  await getListingChannelListApi(...)
}

// ✅ 完整复刻 onPropertyChange 的所有 API
if (form.propertyId) {
  await Promise.all([
    getRoomTypeListApi({ propertyId: form.propertyId }),
    getPropertyInfoApi(form.propertyId),           // ← 不能漏：companyName / propertyChargeName / contractTypeName
    getListingChannelListApi({ propertyId: form.propertyId })
  ])
}
```

**⚠ cascade `@update:model-value` 必须传 `$event`（必须遵守）**

v-model 和 handler 都监听同一事件按订阅顺序执行；handler 不传参 → `val === undefined` → 把 v-model 刚设的值覆盖为 undefined → 表现为「下拉一选中就清空」。

```vue
<!-- ✅ handler 需要新值（cascade / setFormField / 调 API） -->
<Select v-model="form.propertyId" @update:model-value="onPropertyChange($event); clearError('propertyId')" />

<!-- ❌ 漏传 $event：onPropertyChange 收到 undefined -->
<Select v-model="form.propertyId" @update:model-value="onPropertyChange(); clearError('propertyId')" />

<!-- ✅ handler 不需要值（只 clearError）可省略 -->
<Select v-model="form.status" @update:model-value="clearError('status')" />
```

#### DatePicker — 单日期选择
```vue
import DatePicker from '@/components/ui/DatePicker.vue'

<!-- ① 过滤条件等、需要输出纯日期（YYYY-MM-DD）的场景 — 默认行为 -->
<DatePicker v-model="searchForm.dateFrom" placeholder="開始日" />

<!-- ② 编辑页保存表单 — 必须设 value-format="YYYY-MM-DD HH:mm:ssZZ" -->
<DatePicker
  v-model="form.checkIn"
  value-format="YYYY-MM-DD HH:mm:ssZZ"
  placeholder="チェックイン日"
/>
```
Props: `modelValue`, `placeholder`, `valueFormat`, `disabled`, `class`
- `valueFormat` 默认 `'YYYY-MM-DD'`（向后兼容，过滤条件用）
- `valueFormat="YYYY-MM-DD HH:mm:ssZZ"` 自动走 `toBackendDate` 把日期补成 JST 0 时 + offset（`2026-04-10 00:00:00+0900`），**编辑页保存表单的日期字段必须用这个**，否则后端 Jackson 反序列化会报 `InvalidFormatException`
- 自定义日历面板，点击整个区域弹出
- 内置「クリア」「今日」快捷按钮

#### DateTimePicker — 日期时间选择
```vue
import DateTimePicker from '@/components/ui/DateTimePicker.vue'

<!-- 默认输出 'YYYY-MM-DD HH:mm' -->
<DateTimePicker v-model="form.reservationDatetime" placeholder="日時を選択" />

<!-- 包含秒 -->
<DateTimePicker v-model="form.timestamp" :show-seconds="true" />

<!-- 编辑页保存表单 — 必须设 value-format -->
<DateTimePicker
  v-model="form.reservationDatetime"
  value-format="YYYY-MM-DD HH:mm:ssZZ"
  placeholder="予約日時を選択"
/>
```
Props: `modelValue`, `placeholder`, `showSeconds`(默认 false), `valueFormat`, `disabled`, `class`
- `valueFormat` 默认 `''`（按 `showSeconds` 决定是 `'YYYY-MM-DD HH:mm'` 还是 `'YYYY-MM-DD HH:mm:ss'`）
- 设为 `'YYYY-MM-DD HH:mm:ssZZ'` → 自动走 `toBackendDate` 输出 JST 后端形式，编辑页保存表单必须用这个
- 日历 + 时刻输入框（时/分/秒），点击「確定」提交
- 「今すぐ」按钮一键填入当前日期+时刻
- 凡是 datetime / 日時 字段**必须使用此组件**，禁止 `<input type="datetime-local">`

#### Switch — トグルスイッチ
```vue
import Switch from '@/components/ui/Switch.vue'

<!-- Boolean 値 -->
<Switch v-model="form.isActive" />

<!-- 文字列/数字値（後端が "0"/"1" や 1/2 を返す場合） -->
<Switch v-model="form.flag" checked-value="1" unchecked-value="0" />
<Switch v-model="row.status" :checked-value="1" :unchecked-value="2" @change="onChange" />
```
Props: `modelValue`, `checkedValue`(default `true`), `uncheckedValue`(default `false`), `disabled`, `class`
- `checkedValue` / `uncheckedValue` で任意の型・値ペアに対応（Boolean / Number / String）
- on/off 状態は型を緩く判定（`String(modelValue) === String(checkedValue)`）
- `@change` で新值を取得可能（一覧の即時更新用）
- **凡是 ON/OFF / 有効/無効 / フラグ系字段必须用此组件**，禁止手写 `<button>` + `bg-primary` 切换样式

#### Checkbox — チェックボックス
```vue
import Checkbox from '@/components/ui/Checkbox.vue'

<!-- Boolean 単体 -->
<Checkbox v-model="form.agree" label="同意する" />

<!-- 複数（配列モード：modelValue が配列なら自動で配列モード） -->
<Checkbox v-model="form.tags" :value="1" label="PMS" />
<Checkbox v-model="form.tags" :value="2" label="SCS" />

<!-- カスタムラベル（アイコン等） -->
<Checkbox v-model="form.vip"><Crown :size="14" /> VIP</Checkbox>
```
Props: `modelValue`(Boolean/Array), `value`(配列モード時の項目値), `label`, `disabled`, `indeterminate`, `error`, `class`
- **凡是 チェック / 選択 / 複数選択系字段必须用此组件**，禁止手写 `<input type="checkbox">`

#### RadioGroup / Radio — ラジオボタン
```vue
import RadioGroup from '@/components/ui/RadioGroup.vue'
import Radio from '@/components/ui/Radio.vue'

<!-- options 駆動（dicts と直結、最も一般的） -->
<RadioGroup
  v-model="form.listingType"
  :options="dicts.listing_type.options.value"
  name="listingType"
  :error="!!errors.listingType"
  @change="clearError('listingType')"
/>

<!-- 縦並び -->
<RadioGroup v-model="form.type" :options="options" direction="vertical" />

<!-- カスタムラベル（アイコン混在）は Radio を直接使用 -->
<Radio v-model="form.type" :value="0" name="type"><FolderOpen :size="14" /> メニュー</Radio>
<Radio v-model="form.type" :value="1" name="type"><ShieldCheck :size="14" /> 権限</Radio>
```
RadioGroup Props: `modelValue`, `options: [{label, value, disabled?}]`, `name`, `direction`('horizontal'|'vertical', default horizontal), `disabled`, `error`, `class`
Radio Props: `modelValue`, `value`(必須), `label`, `name`, `disabled`, `error`, `class`
- **凡是 単一選択系字段（ラジオ）必须用此组件**，禁止手写 `<input type="radio">`
```vue
import TimePicker from '@/components/ui/TimePicker.vue'

<TimePicker v-model="form.checkInTime" placeholder="時刻を選択" />
<!-- 包含秒: -->
<TimePicker v-model="form.startTime" :show-seconds="true" />
```
Props: `modelValue`("HH:mm[:ss]"), `placeholder`, `showSeconds`(默认 false), `disabled`, `class`
- 时/分/秒下拉列表（自动判断上下展开），可直接键盘输入
- 「今すぐ」一键填入当前时刻
- 凡是 time / 時刻 字段**必须使用此组件**，禁止 `<input type="time">`

#### DateRangePicker — 日期期间选择（PREFERRED for date ranges）
```vue
import DateRangePicker from '@/components/ui/DateRangePicker.vue'

<!-- 过滤条件：默认纯日期输出 -->
<DateRangePicker
  v-model:start-date="searchForm.dateFrom"
  v-model:end-date="searchForm.dateTo"
/>

<!-- 编辑页保存表单：设 value-format -->
<DateRangePicker
  v-model:start-date="form.checkIn"
  v-model:end-date="form.checkOut"
  value-format="YYYY-MM-DD HH:mm:ssZZ"
/>
```
Props: `startDate`, `endDate`, `startPlaceholder`, `endPlaceholder`, `valueFormat`, `disabled`, `class`
- `valueFormat` 默认 `'YYYY-MM-DD'`；设为 `'YYYY-MM-DD HH:mm:ssZZ'` 时两个 v-model 都会自动走 `toBackendDate`
- 開始日~終了日 一体型 UI（一个输入框内含两个日期）
- 点击左侧选开始日，自动切换到终了日选择
- **终了日不能早于开始日**（日历面板中禁用）
- 选中范围内的日期高亮显示
- 日期期间的检索条件**必须使用此组件**，不要用两个独立的 DatePicker

### 日期格式转换（MANDATORY for forms with date fields）

后端所有 `Date` 字段统一要求 `yyyy-MM-dd HH:mm:ssZZ` 格式（如 `2026-04-02 00:00:00+0900`，JST 时区）。**编辑页所有保存用的日期 picker 必须加 `value-format="YYYY-MM-DD HH:mm:ssZZ"` prop**，这样 picker 会自动用 `toBackendDate` 把值补成后端要求的格式，form 里存的就是最终形态，`buildPayload` 不用再做任何日期转换。

```vue
<!-- ✅ 正确 — 编辑页所有日期 picker 加 value-format -->
<DatePicker v-model="form.checkIn" value-format="YYYY-MM-DD HH:mm:ssZZ" />
<DateTimePicker v-model="form.reservationDatetime" value-format="YYYY-MM-DD HH:mm:ssZZ" />
<DatePicker v-model="form.guestInfoList[i].birthday" value-format="YYYY-MM-DD HH:mm:ssZZ" />

<!-- ❌ 错误 — 不加 value-format 会 emit 'YYYY-MM-DD'，后端报 InvalidFormatException -->
<DatePicker v-model="form.checkIn" />
```

```js
// 编辑页面要保留原始加载数据，避免 createdDate/createUser 等系统字段丢失
const rawData = ref({})

async function loadDetail() {
  const res = await getInfoApi(id)
  rawData.value = JSON.parse(JSON.stringify(res.data.data))  // ← 保存原始
  // 然后把字段映射到 form...
}

function buildPayload() {
  // 1. 原始数据为底（含 createdDate/createUser/mark 等系统字段）
  const base = JSON.parse(JSON.stringify(rawData.value || {}))
  // 2. form 编辑值覆盖（form 里的日期已经是后端格式，因为 picker value-format 配好了）
  const formCopy = JSON.parse(JSON.stringify(form))
  const payload = { ...base, ...formCopy }
  // 嵌套对象需要单独 merge
  if (base.subInfo && formCopy.subInfo) {
    payload.subInfo = { ...base.subInfo, ...formCopy.subInfo }
  }
  // 日期字段不需要再转 — picker 已经 emit 了后端格式
  return payload
}

async function handleSave() {
  if (!validate()) return
  const payload = buildPayload()
  await xxxApi(payload)
}
```

**编辑页 ↔ 过滤页的区分**：

| 场景 | picker 用法 | 原因 |
|---|---|---|
| 编辑页保存表单 | `<DatePicker value-format="YYYY-MM-DD HH:mm:ssZZ" />` | POST body 走 Jackson 严格反序列化，必须全格式 |
| 列表页过滤条件 | `<DatePicker />`（默认 `'YYYY-MM-DD'`） | GET query 后端通常用宽松 `@DateTimeFormat`，纯日期能解析 |

### 展示后端日期 / 日期计算（MANDATORY）

后端可能返回 **UTC 格式**（`2026-04-10 15:00:00+0000` = JST `2026-04-11 00:00:00`），所有展示和计算**必须先归一到 JST**，统一走 `@/lib/date` helper：

| 用途 | helper | 输出 |
|---|---|---|
| 列表/单元格展示日期 | `toJSTDateStr(val)` | `2026-04-11` |
| 列表/单元格展示日期时间 | `toJSTDateTimeDisp(val)` | `2026/04/11 00:00` |
| 含秒的日期时间（更新履历等） | `toJSTDateTimeFullStr(val)` | `2026-04-11 00:00:00` |
| 取"今天"作为默认值 | `todayJSTStr()` | `2026-04-11` |
| 保存到后端 | `toBackendDate(val)` | `2026-04-11 00:00:00+0900` |

```js
// ✅ 正确 — 展示后端日期
import { toJSTDateStr, toJSTDateTimeFullStr } from '@/lib/date'
function formatDate(val) {
  if (!val) return ''
  const pure = toJSTDateStr(val)
  return pure ? pure.replace(/-/g, '/') : ''
}

// ❌ 错误 — 任何带时间/时区的后端日期都不能用以下方式：
val.substring(0, 10)                       // 没归一 → UTC 会偏一天
dayjs(val).format('YYYY/MM/DD')            // dayjs 默认本地时区 → 非 JST 环境错位
dayjs(val.substring(0, 10))                // 同上 + 截断陷阱
new Date().toISOString().slice(0, 10)      // toISOString 是 UTC → 凌晨变昨天

// ✅ 日期差值/年龄计算 — 也必须先归一
const ci = dayjs(toJSTDateStr(form.checkIn))
const co = dayjs(toJSTDateStr(form.checkOut))
const days = co.diff(ci, 'day')
```

**唯一可以用 `substring(0, 10)` 的场景**：值已经是 10 位纯日期字符串（picker 默认输出 / 后端返回的 LocalDate），且用途与时区无关。否则一律走 helper。

**漏点自查**：所有绑定到 form（会被保存的字段）的 picker 都应该检查是否加了 `value-format`：
- 顶层字段：`form.checkIn` / `form.checkOut` / `form.reservationDatetime` ✓
- 嵌套对象：`form.accommodationAmountInfo.withdrawScheduleDate` ✓
- 嵌套数组：`form.guestInfoList[i].birthday` / `form.adjustAmountInfoList[i].accountDate` ✓

第三种（嵌套数组）最容易遗漏 —— 写完顶层和嵌套对象后，忘了给数组里的 picker 也加 prop。只要某一个字段漏配，后端就会拒绝整个请求，报 `InvalidFormatException`。

参考实现: `src/views/system/User/UserEdit.vue` —— buildPayload 只直接传业务字段，日期 / 状态不在客户端做格式转换。

### API 响应 code 分支规范（MANDATORY — 禁止忽略非 0 响应）

所有 API 调用在判断 `res.data.code === 0` 成功分支后，**必须**添加 `else` 分支用 `toast.error(res.data.msg)` 显示后端返回的错误消息。禁止只写成功分支而忽略失败情况。

```js
// ✅ 正确 — 成功和失败都处理
const res = await saveApi(payload)
if (res.data.code === 0) {
  toast.success(t('common.message.saveSuccessful'))
  // ... 刷新数据等
} else {
  toast.error(res.data.msg)
}

// ❌ 错误 — 缺少 else 分支，后端返回业务错误（如排他锁冲突）时用户无感知
const res = await saveApi(payload)
if (res.data.code === 0) {
  toast.success(t('common.message.saveSuccessful'))
}
```

**适用范围**：所有写操作（保存/删除/更新/销账/审批等）的 API 调用都必须有 else 分支。列表查询（fetchData）可以只在 catch 里处理异常。

### Toast 通知规范（MANDATORY — 禁止 alert）

**禁止使用 `alert()` / `confirm()` 显示操作结果**。所有保存成功/失败、提示信息必须用 `toast` API：

```js
import { toast } from '@/composables/useToast'

// 保存成功
toast.success('保存しました')

// 保存失败
toast.error('保存に失敗しました: ' + e.message)

// 警告
toast.warning('入力内容を確認してください')

// 信息
toast.info('処理中...')

// 自定义持续时间（毫秒）
toast.success('完了', 2000)
toast.error('エラー', 0)  // 0 = 不自动消失
```

特性：
- 右上角滑入，自动消失（默认 success/info/warning 3秒，error 5秒）
- 成功/错误/警告/信息四种类型，分别用绿/红/橙/蓝色 + 对应图标
- 圆润边框，左侧色条，毛玻璃背景
- 支持多个 toast 堆叠
- 可手动关闭（X 按钮）
- 全局可用（在 AppLayout 中已挂载 ToastContainer）

### フォームバリデーション规范（MANDATORY for forms）

**禁止使用 `alert()` 显示验证错误**。必须使用以下模式：

1. 所有共通 UI 组件（Input/Select/DatePicker/DateTimePicker/TimePicker/DateRangePicker）都支持 `:error` prop，传 `true` 时输入框边框变红

2. 在页面中维护一个 reactive `errors` 对象，key 对应字段名：
   ```js
   const errors = reactive({})
   function clearError(key) { if (errors[key]) delete errors[key] }
   ```

3. validate 函数将无效字段的 key 设置到 `errors` 对象，**不要 alert**：
   ```js
   // 多 tab 表单：定义字段 → tab 映射，验证失败时自动切换到错误所在 tab
   const FIELD_TAB_MAP = {
     fieldA: 'basic',
     fieldB: 'amount',
   }
   // 動的 key（ネスト配列の guestEmail_0 など）は前缀フォールバック
   function resolveFieldTab(key) {
     if (FIELD_TAB_MAP[key]) return FIELD_TAB_MAP[key]
     if (key.startsWith('guest')) return 'guest'
     return null
   }

   function validate() {
     Object.keys(errors).forEach((k) => delete errors[k])
     const checks = [
       ['fieldA', !form.fieldA],
       ['fieldB', !form.fieldB],
     ]
     for (const [key, isInvalid] of checks) {
       if (isInvalid) errors[key] = true
     }
     if (Object.keys(errors).length > 0) {
       const firstKey = Object.keys(errors)[0]
       // 切换到错误所在 tab（v-show 隐藏的字段无法 scrollIntoView）
       const targetTab = resolveFieldTab(firstKey)
       if (targetTab && activeTab.value !== targetTab) {
         activeTab.value = targetTab
       }
       nextTick(() => {
         const el = document.querySelector(`[data-field="${firstKey}"]`)
         if (el) el.scrollIntoView({ behavior: 'smooth', block: 'center' })
       })
       return false
     }
     return true
   }
   ```

   **⚠ 漏点**：`FIELD_TAB_MAP` 必須**所有可能进入 errors 的 key 都覆盖**，包括：
   - 顶层固定字段（ownerId, propertyId 等）
   - **書式チェック走的字段**（confirmEmail 等 — 容易漏，因为它们不在 required checks 列表里）
   - **ネスト配列の動的 key**（guestEmail_${i}, adjustItem_${i} 等 — 用 `resolveFieldTab` 前缀フォールバック）

   漏配的后果：firstKey 找不到 tab → 不切换 → 用户看不到红框 → **誤って "保存が固まった" と感じる**。

4. 模板中绑定 `:error` 和 `data-field`，并在 `@update:model-value` 时清除错误：
   ```vue
   <div data-field="fieldA">
     <label>...</label>
     <Input
       v-model="form.fieldA"
       :error="!!errors.fieldA"
       @update:model-value="clearError('fieldA')"
     />
   </div>
   ```

5. **書式チェック**（メールなど）は `@/lib/validators` のヘルパーを呼ぶ。空値は許容、書式違反のみ `errors[key] = true`：
   ```js
   import { isValidEmail } from '@/lib/validators'
   if (!isValidEmail(form.email)) errors.email = true
   ```

#### 参考实现
- 表单组件使用: `src/views/system/Role/RoleEdit.vue`
- 表单验证: `src/views/system/User/UserEdit.vue`

### Collapse 共通组件使用规范（MANDATORY）

折叠面板**必须**使用 `@/components/ui/Collapse.vue`，禁止手写 `<button @click="x = !x">` + `<ChevronDown :class="{'rotate-180': x}">` + `<div v-show="x">` 这种三段式结构。模型每次重新生成都会给出略有差异的 class（`border rounded-lg mb-4` vs `border-b` vs 不同 padding），tab 间、页面间外观不统一。

```vue
<script setup>
import Collapse from '@/components/ui/Collapse.vue'

// 一次初始化多个面板的展开状态，true = 打开（不要用"collapsed"反向语义）
const panelsOpen = reactive({ base: true, business: true, score: false })
</script>

<template>
  <Collapse v-model="panelsOpen.base" :title="t('x.header.baseInfo')" class="mb-4">
    <div class="grid grid-cols-3 gap-x-6 gap-y-4">
      ...
    </div>
  </Collapse>

  <!-- 数据表格等自带 padding 的内容用 body-class="p-0" 取消内边距 -->
  <Collapse v-if="list.length > 0" v-model="panelsOpen.history" :title="t('x.history')" body-class="p-0">
    <DataTable ... />
  </Collapse>
</template>
```

要点：
- **`modelValue=true` = 打开**，不要用 `collapsed`/`collapsedPanels` 这种反向布尔，state 名建议 `panelsOpen` / `collapses`（初值 `true`）
- 面板标题用 `title` prop；需要富文本/图标用 `#title` slot；右上角按钮（如"添加"）用 `#extra` slot
- `variant`: `card`（默认、带阴影）/ `bordered`（纯边框）/ `plain`（无边框）
- DataTable、自带 padding 内容用 `body-class="p-0"`
- 条件显示用 `v-if` 在 `<Collapse>` 上，不要把条件 v-show 放在外层再嵌套一层 div
- **禁止 pattern**：`<div class="border rounded-lg"><button @click="x = !x">...<ChevronDown :class="{'rotate-180': x}" /></button><div v-show="x">...</div></div>`

### Tabs 共通组件使用规范（MANDATORY）

多 tab 页面**必须**使用 `@/components/ui/Tabs.vue`，禁止在 `src/views/` 内直接 import `TabsRoot`/`TabsList`/`TabsTrigger` from 'radix-vue'（这会导致每个页面自己写一套样式 class、tab 外观不统一）。

```vue
<script setup>
import { TabsContent } from 'radix-vue'
import Tabs from '@/components/ui/Tabs.vue'

const activeTab = ref('1')

// 用 computed 定义 items，把 v-if 条件放到 `visible` 字段
const tabItems = computed(() => [
  { value: '1', label: t('x.tabs.basic') },
  { value: '2', label: t('x.tabs.permission'), visible: isUpdate.value },
  { value: '3', label: t('x.tabs.rooms'), visible: isUpdate.value && hasPermission('sys:room:index'), badge: roomCount.value },
])
</script>

<template>
  <Tabs v-model="activeTab" :items="tabItems">
    <TabsContent value="1" class="p-4">...</TabsContent>
    <TabsContent v-if="isUpdate" value="2" class="p-4">...</TabsContent>
  </Tabs>
</template>
```

要点：
- **`Tabs` 组件**只封装 `TabsRoot + TabsList + TabsTrigger`（列表栏），`TabsContent` 仍从 `radix-vue` 直接 import 使用（保留 Radix 原生灵活性）
- **`items` 数组驱动**，不要在模板里写一堆 `<TabsTrigger v-if="...">` —— 条件走 `visible` 字段
- **value 用 string**（Radix 要求），`computed` 里写成 `'1'`/`'2'`
- 支持的 item 字段：`value` / `label` / `visible` / `disabled` / `icon` / `badge`
- `variant` 可选 `underline`（默认）/ `pill` / `segmented`；`size` 可选 `sm`/`md`/`lg`
- **`sticky` 勿用** —— 会触发 tab 栏浮动定位，并且在带 `overflow-hidden` 的容器里会失效。保留该 prop 是为向后兼容，新页面一律不传
- **详细页里不要给 Tabs 设 `container-class`** —— 卡片外观由外层 `<Card>` 提供（见下方"详细页面模板规范"）

### 列表页 Header 按钮排列规范（MANDATORY — list / search 页面）

所有列表 / 检索页面（`{Module}.vue` 主入口）的 header 右侧 action 按钮组**必须**遵循以下顺序，确保所有页面交互一致：

```
检索 (Search) → 业务操作（新規 / 削除 / Export / Sync / 一括操作 …） → リセット (Reset)
```

**核心规则**：
1. **`@click="handleSearch"`（検索）放在最左**，主按钮样式 `bg-primary text-primary-foreground`
2. **`@click="resetSearch"`（リセット）放在最右**，次要按钮样式 `bg-muted text-foreground`，图标 `<RotateCcw :size="14" />`
3. **业务操作按钮（新規 / 削除 / Export / 同期 等）放在中间**，按重要性 / 频率从左到右排列
4. **不要**把 Reset 紧贴 Search 放在第二位 —— 旧布局是 `Search → Reset → 业务操作`，**已废弃**；新布局把 Reset 推到行尾，让用户视线先扫过常用业务按钮，最不常用的"重置"在最远端

**示例**（标准模板）：

```vue
<template>
  <Card>
    <!-- Header -->
    <div class="flex items-center justify-between p-4 pb-3 border-b border-border">
      <h1 class="text-base font-semibold text-foreground">{{ t('xxx.title') }}</h1>
      <div class="flex items-center gap-2">
        <!-- 1. 検索（最左，primary） -->
        <button
          class="inline-flex items-center gap-1.5 h-9 px-3 rounded-lg text-sm font-medium bg-primary text-primary-foreground hover:bg-primary/90 transition-colors"
          @click="handleSearch"
        >
          <Search :size="14" />
          {{ t('common.button.search') }}
        </button>
        <!-- 2. 业务操作（中间，按需排列） -->
        <button
          v-if="hasPermission('xxx:add')"
          class="inline-flex items-center gap-1.5 h-9 px-3 rounded-lg text-sm font-medium bg-primary text-primary-foreground hover:bg-primary/90 transition-colors"
          @click="openEdit()"
        >
          <Plus :size="14" />
          {{ t('common.button.new') }}
        </button>
        <button
          v-if="hasPermission('xxx:delete')"
          class="inline-flex items-center gap-1.5 h-9 px-3 rounded-lg text-sm font-medium bg-destructive text-destructive-foreground hover:bg-destructive/90 transition-colors"
          @click="handleBatchDelete"
        >
          <Trash2 :size="14" />
          {{ t('common.button.delete') }}
        </button>
        <!-- 3. リセット（最右，muted） -->
        <button
          class="inline-flex items-center gap-1.5 h-9 px-3 rounded-lg text-sm font-medium bg-muted text-foreground hover:bg-muted/80 transition-colors"
          @click="resetSearch"
        >
          <RotateCcw :size="14" />
          {{ t('common.button.reset') }}
        </button>
      </div>
    </div>
    <!-- ... Filter / DataTable ... -->
  </Card>
</template>
```

参考实现：`src/views/system/User/User.vue`、`src/views/system/Role/Role.vue`、`src/views/system/Permission/Permission.vue`

### 详细页面模板规范（MANDATORY — `*Edit.vue` / `*Detail.vue`）

所有详细/编辑页面**必须**使用以下统一模板，确保视觉一致：

```vue
<script setup>
import Card from '@/components/ui/Card.vue'
import Tabs from '@/components/ui/Tabs.vue'
import { TabsContent } from 'radix-vue'

const isUpdate = ref(false)  // edit=true / new=false
// ...
</script>

<template>
  <Card>
    <!-- Header -->
    <div class="flex items-center justify-between p-4 border-b border-border">
      <h1 class="text-base font-semibold text-foreground">
        {{ isUpdate ? 'XX詳細' : 'XX新規' }}
      </h1>
      <!-- Action 按钮（可选）；没有时整个 right div 可省略 -->
      <div class="flex items-center gap-2">
        <button v-if="条件" class="inline-flex items-center gap-1.5 h-9 px-3 rounded-lg text-sm font-medium bg-muted text-foreground hover:bg-muted/80 transition-colors" @click="...">
          <IconXxx :size="14" />
          {{ t('xx.button.copyUrl') }}
        </button>
      </div>
    </div>

    <!-- Tabs -->
    <Tabs v-model="activeTab" :items="tabItems">
      <TabsContent value="1" force-mount v-show="activeTab === '1'" class="p-4">...</TabsContent>
      <TabsContent v-if="isUpdate" value="2" class="p-4">...</TabsContent>
    </Tabs>

    <!-- Footer（Save 按钮；如 Save 放在 tab 内则省略此块） -->
    <div v-if="hasPermission('xxx:save')" class="flex justify-end p-4 border-t border-border">
      <button class="inline-flex items-center gap-1.5 h-9 px-6 rounded-lg text-sm font-medium bg-primary text-primary-foreground hover:bg-primary/90 transition-colors" @click="handleSave">
        {{ t('common.button.save') }}
      </button>
    </div>
  </Card>
</template>
```

**硬性要求**：
1. **外壳用 `<Card>`** —— 禁止自己套 `<div class="space-y-4">` + 给 Tabs 加 `container-class` 这种组合（背景/边框会双重渲染）
2. **Header 行固定 3 要素**：左侧 h1 标题 + 右侧 action 按钮区（可省略）；底边 `border-b border-border`
3. **h1 标题动态切换**：`isUpdate ? '{module}詳細' : '{module}新規'`（如 `予約詳細`/`予約新規`、`施設詳細`/`施設新規`、`ユーザー詳細`/`ユーザー新規` 等）。legacy 迁移页面可硬编码日文（符合 CLAUDE.md 的 5 页例外）；非 legacy 页面应走 i18n key
4. **Tabs 不加 `container-class`**、不加 `sticky` —— 外层 Card 已提供卡片外观，sticky 在 Card 内也没用
5. **Action 按钮统一样式**：`h-9 px-3 rounded-lg text-sm font-medium bg-muted text-foreground hover:bg-muted/80`（次要操作）；主按钮（Save）用 `bg-primary text-primary-foreground`

参考实现：`src/views/system/User/UserEdit.vue`、`src/views/system/Role/RoleEdit.vue`

### 编辑页 Shell 选型（MANDATORY — 三选一，按 spec.page.shell 分派）

生成 `*Edit.vue` 之前**必须**先读 spec 的 `page.shell` 字段，按"旧用什么新用什么"的原则保真迁移：

| `spec.page.shell` | 生成模板 | 何时使用 |
|---|---|---|
| `dialog` | 用 `<Dialog>` 居中弹窗（见下方"Dialog 弹窗编辑页规范"） | legacy 用 `el-dialog` / `Modal` / 居中弹窗 |
| `drawer` | 用 `<Drawer>` 侧边滑出（见下方"Drawer 弹窗编辑页规范"） | legacy 用 `el-drawer` / `Drawer` / 抽屉形态 |
| `page`（或缺省） | 用 Card + Tabs 整页（见上方"详细页面模板规范"） | legacy 是路由直挂的整页详情 |

**禁止**根据"我觉得字段多用 drawer 比较好" / "这个 Dialog 太挤" 这类**实现层的主观判断**改 shell。要保持 legacy 形态。如果确实想从 dialog 升级 drawer，让用户在 spec 里手动改 `shell: drawer` 后重跑。

如果 spec 没有 `shell` 字段（旧 spec 没标注 / 内联 spec 没给）：
- sibling edit 文件名带 `*Edit.vue` / `*Detail.vue` → 默认 `dialog`（兼容现有约定）
- 跑完后在生成报告里 ⚠ 警告"未声明 shell，已默认 dialog；如 legacy 是 drawer 请重跑 analyze"

---

### Dialog 弹窗编辑页规范（MANDATORY — 弹窗形态的 `*Edit.vue`）

当编辑页以弹窗（`<Dialog>`）形式而非整页形式呈现时（例如 Tab 里的子表 `TabXxxEdit.vue`、`MstCustomerEdit.vue`），**必须**使用以下统一结构：

```vue
<template>
  <Dialog
    :open="visible"
    :title="isUpdate ? t('common.button.edit') : t('common.button.new')"
    width="max-w-3xl"
    @update:open="$event ? null : handleCancel()"
  >
    <!-- 默认插槽：表单内容。Dialog.vue 的 body 已内置 `px-6 py-4 max-h-[70vh] overflow-y-auto`，禁止再套 <div class="p-6"> 否则边距会翻倍 -->
    <div class="grid grid-cols-2 gap-x-6 gap-y-4">
      ...
    </div>

    <!-- footer 插槽：取消 + 保存两个按钮，由 Dialog 负责 border-t / justify-end / gap-2 布局 -->
    <template #footer>
      <button
        class="inline-flex items-center gap-1.5 h-9 px-4 rounded-lg text-sm font-medium bg-muted text-foreground hover:bg-muted/80 transition-colors"
        @click="handleCancel"
      >
        {{ t('common.button.cancel') }}
      </button>
      <button
        v-if="hasPermission('xxx:save')"
        class="inline-flex items-center gap-1.5 h-9 px-4 rounded-lg text-sm font-medium bg-primary text-primary-foreground hover:bg-primary/90 transition-colors"
        :disabled="saving"
        @click="handleSave"
      >
        {{ t('common.button.save') }}
      </button>
    </template>
  </Dialog>
</template>
```

**硬性要求**：
1. **按钮必须放在 `<template #footer>` 插槽**，禁止在默认插槽里自己用 `<div class="... border-t ...">` 拼 footer（Dialog 已内置 footer 区域 `flex items-center justify-end gap-2 px-6 py-4 border-t`）
2. **按钮尺寸统一用 `h-9 px-4`**（与 `MstCustomerEdit.vue` 一致），禁止用 `px-6` 或不一致的尺寸
3. **按钮顺序固定**：左 Cancel（`bg-muted`）→ 右 Save（`bg-primary`）
4. **Save 按钮用 `:disabled="saving"`（或 `loading`）+ 权限码 `v-if`** 控制，不使用 `disabled:opacity-50` 这类自定义类
5. **不给按钮加图标**（Cancel/Save 保持纯文本）
6. **新增时 payload 不带空串 id**：`{...form}` 后用 `if (!payload.id) delete payload.id` 避免后端把 `""` 当作有主键

参考实现：`src/views/system/User/ResetPasswordDialog.vue`

### Drawer 弹窗编辑页规范（MANDATORY — 抽屉形态的 `*Edit.vue`）

当 `spec.page.shell === 'drawer'`（legacy 用 `el-drawer` 等抽屉组件），**必须**使用 `<Drawer>` 组件，结构与 Dialog 规范基本一致，差异点：

```vue
<template>
  <Drawer
    :open="visible"
    :title="isUpdate ? t('common.button.edit') : t('common.button.new')"
    :side="drawerSide"          
    :width="drawerWidth"
    @update:open="$event ? null : handleCancel()"
  >
    <!-- 默认插槽：表单内容。Drawer.vue 的 body 已内置 `px-6 py-5 overflow-y-auto`，禁止再套 <div class="p-6"> 否则边距会翻倍 -->
    <div class="grid grid-cols-2 gap-x-6 gap-y-4">
      ...
    </div>

    <!-- footer 插槽：取消 + 保存按钮，由 Drawer 负责 border-t / justify-end / gap-2 布局 -->
    <template #footer>
      <button class="inline-flex items-center gap-1.5 h-9 px-4 rounded-lg text-sm font-medium bg-muted text-foreground hover:bg-muted/80 transition-colors" @click="handleCancel">
        {{ t('common.button.cancel') }}
      </button>
      <button
        v-if="hasPermission('xxx:save')"
        class="inline-flex items-center gap-1.5 h-9 px-4 rounded-lg text-sm font-medium bg-primary text-primary-foreground hover:bg-primary/90 transition-colors"
        :disabled="saving"
        @click="handleSave"
      >
        {{ t('common.button.save') }}
      </button>
    </template>
  </Drawer>
</template>
```

**props 映射规则（从 spec 的 `page.drawer.*` 翻译过来）**：

| spec | Drawer prop | 默认值 |
|---|---|---|
| `drawer.direction: rtl` | `side="right"` | right（最常见） |
| `drawer.direction: ltr` | `side="left"` | — |
| `drawer.direction: ttb` / `btt` | 暂不支持，记 ⚠ 警告，按 `right` 处理 | — |
| `drawer.size: "60%"` | `width="w-[60vw]"` | `max-w-md` |
| `drawer.size: "640px"` | `width="w-[640px] max-w-full"` | `max-w-md` |

**硬性要求**（与 Dialog 规范对齐，唯一差异是组件名）：
1. 按钮放 `<template #footer>` 插槽，禁止自拼 footer
2. 按钮尺寸 `h-9 px-4`、顺序 Cancel → Save、纯文本无图标
3. 新增时 `if (!payload.id) delete payload.id`
4. **不得**把 legacy 的 drawer 改成 dialog 来"简化"，shell 必须保真

参考：`src/components/ui/Drawer.vue`（已有的 UI 基础组件）

### DataTable 共通组件使用规范（MANDATORY）

所有包含数据表格的页面**必须**使用 `import { DataTable } from '@/components/shared/DataTable'`，禁止手写 `<table>` 标签。

```vue
<DataTable
  :columns="columns"
  :data="dataList"
  :loading="loading"
  :page="pagination.current"
  :page-size="pagination.pageSize"
  :total="pagination.total"
  @update:page="handlePageChange"
  @update:page-size="handlePageSizeChange"
  @update:filter="handleFilterUpdate"
>
  <!-- 自定义列渲染 -->
  <template #cell-{key}="{ row, value }">...</template>
  <!-- 自定义列头 -->
  <template #header-{key}="{ column }">...</template>
</DataTable>
```

**行選択（一括削除など）が必要な場合**：
```vue
<DataTable
  v-model:selection="selection"
  selectable
  :columns="columns"
  :data="dataList"
  ...
/>
```
- `selectable` を有効にすると先頭にチェックボックス列が自動追加され、列ヘッダーは「当前页全選択」（インディタミネート対応）
- `v-model:selection` の値は**行オブジェクトそのものの配列**（id ではない）— 一括削除时 `selection.value.map(r => r.id).join(',')` で API に渡す
- ページ遷移しても他ページの選択は保持される（rowKey で同一性判定）
- **一括削除ボタンと `selection` ref を持つページは必ず `selectable` + `v-model:selection` を設定すること** — 設定漏れだとボタンが何も選択しないまま実行されて空削除になる

**columns 配置**:
```js
const columns = computed(() => [
  { key: 'name', title: '名前', minWidth: '120px' },
  { key: 'status', title: 'ステータス', minWidth: '100px',
    filter: { modelValue: columnFilters.status, options: dicts.xxx.options.value }
  },
  { key: 'actions', title: '操作', align: 'center', sticky: 'right', minWidth: '120px' }
])
```

**关键约定**:
- `columns` 必须是 `computed`（filter options 依赖响应式数据）
- 列头筛选用 `filter: { modelValue, options }`，选项来自 `useDict` composable（读 `src/dict/storage.js` 静态字典）
- 筛选参数通过 `@update:filter` 事件传到 `buildSearchParams` → 发送到服务端（数组参数由 qs `arrayFormat:'repeat'` 展开）
- 分页参数: `page` + `limit`（不是 pageNo/pageSize）
- 默认 pageSize: 10，可选 [10, 20, 50, 100]（pageSize Select 设置 `:clearable="false"`）
- 分页区域总件数使用 `text-foreground font-medium` 加重显示
- 冻结列: `sticky: 'right'`（操作列）或 `sticky: 'left'`
- 操作列 minWidth `120px`（3 个图标按钮足够）
- 操作列按钮统一样式 — **仅图标 + hover Tooltip**:
  ```vue
  <div class="flex items-center justify-center gap-0.5 flex-nowrap">
    <button
      title="編集"
      class="group/btn relative inline-flex items-center justify-center h-8 w-8 rounded-md text-foreground hover:bg-muted transition-colors shrink-0"
      @click="handleEdit(row)"
    >
      <Pencil :size="14" />
      <span class="pointer-events-none absolute bottom-full left-1/2 -translate-x-1/2 mb-1 px-2 py-0.5 rounded-md text-xs font-medium bg-foreground text-background whitespace-nowrap opacity-0 group-hover/btn:opacity-100 transition-opacity z-10">編集</span>
    </button>
    <!-- 删除按钮用 text-destructive hover:bg-destructive/10 -->
  </div>
  ```
  关键点:
  - 正方形图标按钮 `h-8 w-8`，图标 `size=14`
  - `group/btn` + `group-hover/btn:opacity-100` 显示 Tooltip
  - Tooltip 用 `bg-foreground text-background` 反色
  - `pointer-events-none` 防止 Tooltip 拦截点击
  - `title` 属性提供原生 fallback / 无障碍支持
- 参考实现: `src/views/system/Permission/Permission.vue`

### Phase 4: 路由说明（动态路由，无需前端配置）
本项目使用**后端菜单驱动的动态路由**，前端无需手动注册路由。
路由守卫在用户登录后自动请求 `GET /api/menu/me`，
由 `menuToRoutes()` 将菜单数据转为路由配置并 `router.addRoute()` 动态注册。

前端只需确保:
1. 页面组件存在于 `src/views/{module}/{PageName}/{PageName}.vue`
2. 后端 `core_rbac_menu` 表中配置了对应菜单项:
   - `path`: 路由路径（如 `/system/user`）
   - `component`: 组件路径，相对于 `src/views/`（如 `/system/User/User`）
   - 组件路径大小写不敏感（后端通常存小写，前端目录可为 PascalCase）
3. **不要在 `src/router/index.js` 中添加业务路由**，静态路由仅用于免登录公开页面

### Phase 5: 自动生成依赖（依赖优先，页面最后）

按照依赖顺序自动生成，**无需用户逐个确认**。整体顺序由"被谁依赖"决定：底层先生成、上层后生成。

1. **Services** — 读取 `ai/specs/services/*.yaml`，生成 `services/{name}.js`，更新 `services/index.js` + `docs/service-register.md`
2. **Stores** — 读取 `ai/specs/stores/*.yaml`，生成 `src/stores/{name}.js`，自动关联已生成的 service。

   **嵌套字段访问 → 必须封装 getter（MANDATORY，P1 事故防护）**

   spec 的 `stores[].accessedPaths` 里出现深度 ≥ 2（如 `userInfo.user.companyId`），或 spec 含 `recommendedGetters` 时，store 必须暴露 `computed` getter，**禁止页面写嵌套访问**。每个 getter 兜回退 shape：

   ```js
   const currentUser = computed(() => userInfo.value?.user || userInfo.value || {})
   const companyId = computed(() => currentUser.value.companyId || '')
   const companyName = computed(() => currentUser.value.companyName || '')
   return { token, userInfo, currentUser, companyId, companyName, /* ... */ }
   ```

   - ✅ `form.companyId = authStore.companyId`
   - ❌ `form.companyId = authStore.userInfo?.companyId`（shape 一变全炸，后端可能返回 `{user:{...}}` 嵌套）

   **事故复盘**：ListingPropertyEdit 担当者下拉永远空，因 `authStore.userInfo?.companyId` 是 undefined（实际 shape 是 `{user:{companyId}}`）；UserPicker 拿不到 companyId → 后端返回空数组，无报错。后端按 companyId 过滤为空是合法状态（该公司无担当者），别把它当 bug 修。

   **异步 store getter → form 必须用 watch 兜底（MANDATORY）**

   `userInfo` 可能 `onMounted` 时还没到（直 URL 进 / 刷新后路由守卫还在跑 `fetchUserInfo`），一次性同步赋值会读到空。

   ```js
   onMounted(() => {
     if (!isUpdate.value) {
       form.companyId = authStore.companyId
       form.companyName = authStore.companyName
       if (!form.companyId) {
         const stop = watch(() => authStore.companyId, (v) => {
           if (v) { form.companyId = v; form.companyName = authStore.companyName; stop() }
         })
       }
     }
   })
   ```

   适用：所有 store getter → form 的拷贝（companyId / userId 等）。直接 v-bind 给子组件不需要（Vue 响应式自动跟）。

   **picker 用 `watch(immediate:true)`，禁止 `onMounted + watch`（MANDATORY）**

   `onMounted(loadOptions) + watch(props.xxx, loadOptions)` 在编辑模式下会发**两次请求**（挂载时 form.companyId 还空 → 第一次参数丢失；loadDetail 回填后 → 第二次）。正确：

   ```js
   async function loadOptions() {
     if (!props.companyId) { options.value = []; return }   // 空值 guard
     const res = await request.get('/adminCommon/getUserList', { params: { companyId: props.companyId } })
     options.value = res.data.code === 0 ? (res.data.data || []) : []
   }
   watch(() => props.companyId, loadOptions, { immediate: true })
   ```
3. **Composables** — 读取 `ai/specs/composables/*.yaml`，生成 `src/composables/{name}.js`
4. **shared 业务组件** — 读取 Phase 2 第 3/4 步标记为待生成的、`componentScope: "shared"` 的组件 spec → 调 `create-component` 生成到 `src/components/shared/{X}.vue` → 同步注册到 `docs/component-register.md`
5. **页面私有组件** — 读取 `dependencySpecs` 中、`componentScope: "page"` 的**组件** spec → 生成到 `src/views/{module}/{PageName}/components/`。**注意**：本步只处理 `ai/specs/components/*.yaml` 路径下的 spec；**不**处理 sibling page（见下一步）。
6. **Sibling pages（MANDATORY — 不可跳过）** — 读取 `dependencySpecs` 中、spec 路径前缀是 `ai/specs/pages/` 的条目（典型场景：`relation: "edit-page"` / `"detail-page"`）。

   **这一步不可省略、不可推迟、不可留 TODO**。list 页 + edit 页是一个整体，只生成 list 不生成 edit 等于半成品 —— list 页面上的"新規追加"/"編集"按钮会跳转到一个不存在的页面，直接 crash。

   执行方式：读取 sibling page spec → 在同一次 create-page 调用中**直接生成**到 `src/views/{module}/{ParentPage}/{SiblingName}.vue`（与主页面同级，**不**进 `components/`）。生成时把 sibling 自己 spec 里的 dependencySpecs 也走完整 Phase 2-5 流程。

   如果 sibling page spec 很大（如 ReservationEdit.yaml 有 800+ 行 spec），**仍然必须生成**，不能因为复杂就跳过。宁可生成一个不完美的版本（部分字段用 TODO 占位），也不能不生成。
7. **主页面** — 最后生成。模板渲染字段时:
   - 字段 spec 有 `component: "X"` 且 X 已存在或刚生成 → 用 `<X v-model="form.fieldId" v-bind="props" />`
   - 字段 spec 有 `component: "X"` 但 X 既无代码也无 spec → 生成 `<Input v-model="form.fieldId" />` 占位 + `<!-- TODO: implement X component -->` 注释 + 控制台警告
   - 字段 spec 没有 `component:` 但有 `type:` → 按 `type` 选基础 UI（select → Select、date → DatePicker 等）
   - sibling page 的 import 路径用相对路径同级引用：`import UserEdit from './UserEdit.vue'`（**不**写成 `./components/UserEdit.vue`）

生成顺序总览: service → store → composable → shared 组件 → 页面私有组件 → sibling pages → 主页面（每一层依赖前一层）

如果某个依赖既无 spec 也无法推断 → 生成占位文件（含 TODO 注释），不中断整体流程，但在最终输出报告里**显著标记**警告项。

### Phase 5.5: 父详细页面 Tab 自动挂载（MANDATORY）

子 Tab 组件（`TabXxx*` / `PriceInventory` 等挂在 `ListingPropertyEdit` 某个 Tab 下）生成后**必须**回写父详情页，禁止留 TODO 给用户手挂。事故案例：曾出现新组件已生成但父 Tab 仍是 `— TODO` 占位，用户看到空白不知道。

**判定**（满足任一即子 Tab 组件）：
1. spec 有 `page.parentTab: { file, tabValue, propName, propExpr }`（显式，最稳）
2. 文件名 `Tab*` 开头 → 同目录 `{EntityName}Edit.vue` 是父（命名约定回退）
3. 父 `*Edit.vue` 内有 `<!-- TODO: XxxComponent --> ... — TODO` 占位

**执行**：
1. 在父文件 script 加 `import {ComponentName} from '{relative-path}'`，紧邻其他 Tab import
2. 父模板把整个 `<!-- TODO -->` + `— TODO` 的 `<div>` 替换为 `<{ComponentName} :{propName}="{propExpr}" />`
3. `TabsContent` 外壳（`v-if` / `value` / `class="p-4"` / `hasPermission(...)`）不动
4. propName/propExpr 默认：`propertyId` + `form.id`（spec 没给时）

**Guard**：父文件找不到 → blocker 警告；占位结构变异 → 列出当前代码让用户确认，不强改；子组件无 `defineProps` → `<Component />` 挂。

### Phase 6: 自检 & 输出
验证:
- [ ] 页面在正确的 views/{module}/ 下
- [ ] 路由已配置
- [ ] 无 TypeScript、无 inline style
- [ ] 依赖组件/服务都存在
- [ ] 页面不在 components/ 目录中
- [ ] **dependencySpecs 中所有 `ai/specs/pages/*.yaml` 的 sibling page 都已生成**（Phase 5 第 6 步）—— 如果有未生成的，这是一个 **blocker 级别**错误，必须立刻生成后再输出报告，不能标记为 TODO 或"下次再做"
- [ ] **如果页面是子 Tab 组件（Phase 5.5 判定条件命中），父详细页 `*Edit.vue` 必须已完成 import + 占位替换**，不能留 `— TODO` 在父页面。这一项是 **blocker 级别**

输出摘要:
```
✅ Page: {Name}
📁 Path: src/views/{module}/{Name}/
🛣️ Route: 动态路由（后端 sys_menu 配置）

🔗 Auto-generated dependencies:
   - ✅ services/{name}.js (from spec)
   - ✅ src/stores/{name}.js (from spec)
   - ✅ src/components/shared/CustomerPicker.vue (from spec, used by field: ownerId)
   - ✅ src/views/{module}/{Name}/components/{SubComp}.vue (from spec)
   - ⏭️ @/components/ui/Card.vue (existing, skipped)
   - ⏭️ @/components/shared/DataTable (existing, skipped)

⚠️  Placeholders generated (require manual implementation):
   - src/composables/useXxx.js — no spec, placeholder generated
   - field: zipCode → ZipCodePicker — no spec/code, rendered as <Input> placeholder + TODO
```

**警告项必须列在报告末尾**，不能淹没在普通日志里。每个警告项要标明:
- 文件路径或字段名
- 缺失原因（no spec / no code / unresolved dependency）
- 当前的占位实现
- 修复建议（手动创建组件 / 补 spec 后重跑 create-component / 改用其他字段类型）
