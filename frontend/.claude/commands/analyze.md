# Skill: analyze

你是 InnTouch UI 的代码分析专家。分析指定路径下的源代码，自动生成结构化 Spec 文件，供其他 Skill（create-component / create-page / generate）直接使用。

## Input: $ARGUMENTS

输入格式：
- 文件路径: `src/views/system/menu/index.vue`
- 目录路径: `src/views/system/menu/`
- 组件路径: `src/components/ui/Button.vue`
- 服务路径: `services/menu.js`
- 外部项目路径: 任意可读的源代码路径（用于迁移分析）123

---

## 执行流程

### Phase 1: 识别目标类型

根据路径和代码内容自动判断类型：

| 路径模式 | 类型 | 输出 Spec |
|----------|------|-----------|
| `src/views/**/*.vue` | Page | `page:` spec |
| `src/components/**/*.vue` | Component | `component:` spec |
| `services/*.js` | Service | `service:` spec |
| `src/stores/*.js` | Store | `store:` spec |
| `src/composables/*.js` | Composable | `composable:` spec |
| 目录路径 | Module | 分析目录下所有文件，生成多个 spec |
| 外部项目文件 | Migration | 分析后映射到本项目的 spec 格式 |

### Phase 2: 深度分析代码

#### 对于 Vue 组件/页面:
1. 提取 `<script setup>` 中的:
   - `defineProps()` → props 及其类型、默认值
   - `defineEmits()` → 事件列表
   - `ref()` / `reactive()` / `computed()` → 状态定义
   - `import` → 依赖（组件、服务、store、composable）
   - 函数定义 → methods
   - **赋值字面映射（MANDATORY）**：函数体里所有 `form.X = response.data.Y` / `form.X = info.Y` 这类**字段名不同**的赋值，必须以 **`assignmentMap` 字段**逐条记录字面 LHS↔RHS 对，禁止用自然语言概括。详见下方"字段名映射"节。
2. 提取 `<template>` 中的:
   - `<slot>` 标签 → 插槽列表
   - 使用的组件 → 组件依赖
   - 页面区块结构 → sections（header/filter/table/form/chart/cards）
   - 路由相关（`useRoute`、`useRouter`）→ 路由信息
   - **shell 类型识别（MANDATORY for pages）**：扫模板根节点 / 最外层包裹组件，判定页面外壳形态，写到 spec 的 `page.shell`：

     | 检测到的 legacy 标签 | spec.page.shell | 备注 |
     |---|---|---|
     | `<el-dialog>` / `<a-modal>` / `<Modal>` / `<van-popup>` 居中弹窗 | `dialog` | 记录 `dialogWidth`（如 `width="50%"` / `width="800px"`） |
     | `<el-drawer>` / `<a-drawer>` / `<Drawer>` / 侧边滑出 | `drawer` | 记录 `direction`（`rtl`/`ltr`/`ttb`/`btt`，对应右/左/上/下）和 `drawerSize`（宽度或高度） |
     | 直接整页（无外壳，带 `<router-view>` 兄弟 / 路由直挂） | `page` | 默认值，省略也行 |

     示例：
     ```yaml
     page:
       name: ReservationGuestEdit
       shell: drawer                    # ← 旧项目用 el-drawer
       drawer:
         direction: rtl                 # ← 右侧滑出
         size: "60%"                    # ← 原 size="60%"
     ```
     或
     ```yaml
     page:
       name: MstCustomerEdit
       shell: dialog                    # ← 旧项目用 el-dialog
       dialog:
         width: "1200px"                # ← 原 width="1200px"
     ```
     **不要省略**：哪怕觉得"以后改成 dialog 也无所谓"，spec 也要忠实记录 legacy 形态。`/create-page` 会按 `shell` 分派模板（dialog → Dialog.vue / drawer → Drawer.vue / page → 整页）。
3. 提取 CVA variants（如有）

#### 对于 Service:
1. 提取所有导出函数 → endpoints
2. 分析 HTTP 方法（get/post/put/delete）
3. 提取 URL 路径
4. 分析参数（path params、query params、body）

#### 对于 Store:
1. 提取 `defineStore` 中的 state、getters、actions
2. 分析 service 调用关系
3. 提取 computed 属性

#### 字段名映射（MANDATORY — 曾出过 P1 事故）

LLM 天然会把 `form.X = info.Y`（X≠Y）压缩成 `form.X = info.X`（X=X），因为训练语料里同名赋值占绝对多数 —— 这是**信息压缩失真**，比纯幻觉更隐蔽，读起来"好像没毛病"。**必须强制 spec 保留字面映射**。

要求：
- 函数描述**禁止**用「自动セット propertyCharge」这种自然语言概括赋值
- 必须用 `assignmentMap` 列出每条 `LHS ← RHS` 字面对
- 凡 LHS 名 ≠ RHS 名，加 `⚠ field name mismatch` 警示

示例（基于 legacy `getMstCustomerInfo`）：
```yaml
methods:
  getMstCustomerInfo:
    api: "GET /mstcustomer/info/{ownerId}"
    description: "ownerId 切替時、および詳細画面 init 末尾で呼ぶ"
    callSites:
      - "onOwnerChange — owner 切替時"
      - "init / loadDetail 末尾 — 既存レコードの空欄補完"
    assignmentMap:
      - "form.customerType        ← info.customerType"
      - "form.personInChargeName  ← info.personInChargeName"
      - "form.ownerScore          ← info.ownerScore"
      - "form.propertyCharge      ← info.personInCharge   ⚠ field name mismatch (legacy: form.propertyCharge = response.data.data.personInCharge)"
    guards:
      - "propertyCharge: 既設の場合は上書きしない (`if (!form.propertyCharge)`)"
```

事故复盘：ListingPropertyEdit 担当者値が初期化されない事故 → 原因は spec が `assignmentMap` を持たず、関数 description だけだった → LLM が「propertyCharge 自動セット」と圧縮 → 生成コード `form.propertyCharge = info.propertyCharge`（ありえない field 名）になり実行時黙って undefined。

#### Store / Getter 嵌套字段访问（MANDATORY）

分析 page / component 时遇到对 store 的链式访问 —— 比如 legacy:
```js
store.getters.user.user.companyId      // ← 注意 .user.user 嵌套，不是 typo
authStore.userInfo.user.companyName
useAuthStore().currentUser?.dept?.deptId
```
**必须把完整路径深度记进 spec**，不能拍平成 `userInfo.companyId`。否则 `/create-page` 拿到 spec 后会写出错误的扁平化访问，运行期取到 undefined 导致下游接口 (`/getUserList?companyId=`) 拿不到数据，且**无报错难定位**（曾出过 ListingPropertyEdit 担当者下拉永远为空的事故）。

在 spec 的 `stores:` 段记录如下：
```yaml
stores:
  - name: useAuthStore
    accessedPaths:
      - "userInfo.user.companyId"     # 完整路径，含每一层
      - "userInfo.user.companyName"
      - "permission"
    notes: "legacy 用 store.getters.user.user.companyId — userInfo 是 getUserInfo 整包响应，user 字段是嵌套对象"
```

如果同一字段在多个页面被反复访问，**在 spec 末尾追加 `recommendedGetter` 字段建议**：
```yaml
recommendedGetters:
  - "companyId: () => userInfo.value?.user?.companyId || ''"
  - "companyName: () => userInfo.value?.user?.companyName || ''"
```
后续 `/create-page` 看到 `recommendedGetter` 优先去 store 里加 getter（详见 create-page skill 的 store 访问规范）。

#### 对于 Composable:
1. 提取参数和返回值
2. 分析内部 state（ref/reactive/computed）
3. 提取 methods

### Phase 2.5: 递归依赖分析（MANDATORY for pages）

分析页面时，**必须自动递归分析所有非共通依赖**，生成完整的依赖树和关联 spec。本阶段是后续 `create-page` 能"页面+组件一起生成"的前提。

#### 执行步骤:
1. **扫描两种引用来源**:
   - **`<script setup>` 的 `import` 语句**: 标准 ES module 依赖
   - **`<template>` 中使用的自定义 PascalCase 标签**: 即使没有显式 `import`（自动注册场景），也要识别
2. 将依赖分为两类:
   - **共通组件（跳过）**: 见下方"分类规则"
   - **业务依赖（递归分析）**: 读取源文件 → 生成对应类型的 spec → 递归继续分析（最多 3 层深度）
3. 在主 spec 文件中记录完整的 `dependencySpecs` 依赖树

#### 依赖分类规则:

**本地分析模式**（被分析文件在本项目 `src/` 或 `services/` 下）:
```
import Card from '@/components/ui/Card.vue'           → 跳过（本项目已有共通 UI）
import { DataTable } from '@/components/shared/DataTable' → 跳过（本项目已有共通 shared）
import EmptyLayout from '@/components/layout/...'     → 跳过（layout 基础设施）
import { Plus } from 'lucide-vue-next'                → 跳过（第三方）
import { useDict } from '@/composables/useDict'       → 跳过（共通 composable）
import { usePermission } from '@/composables/usePermission' → 跳过（共通 composable）

import { getListApi } from '@/services/xxx'            → 分析 → service spec
import { useXxxStore } from '@/stores/xxx'             → 分析 → store spec
import SubComp from './components/SubComp.vue'         → 分析 → component spec
import CustomerPicker from '@/components/shared/CustomerPicker.vue'
   → 先 ls src/components/shared/，存在就跳过；不存在就降级为占位 + 警告
```

**迁移分析模式**（被分析文件路径在本项目之外，比如 `pnpm ai:analyze /path/to/old-project/...`）:

**所有 `@/components/*` 引用都是老项目的组件，必须递归分析**，不能用本项目的共通组件清单去跳过。具体步骤:

1. 找到老项目的 root（被分析路径往上找最近的 `package.json` 或 `vite.config.*` / `vue.config.*`）
2. 解析路径别名（读老项目 `vite.config.*` 或 `tsconfig.json` 里的 `@/` 映射；找不到就默认 `@/ → {legacyRoot}/src/`）
3. 把每个 `@/components/X.vue`、`./X.vue`、`./components/X.vue` 解析成绝对路径
4. 对每个解析到的组件文件:
   - **存在** → 递归走 Phase 2 + Phase 2.5 分析它，输出 `ai/specs/components/{X}.yaml`，归到 `componentScope: "shared"`（默认）
   - **找不到** → 在主 spec 里把这个引用记录为 `unresolvedDependencies` 警告，不阻断流程
5. 老项目的第三方库（element-plus、ant-design-vue、vant 等）按 `migration_notes` 记录映射关系，不递归

**模板里的自定义组件识别（两种模式都适用）**:

扫描 `<template>` 内所有 PascalCase **和 kebab-case** 标签，过滤掉:
- HTML 标准标签
- 框架内置标签：`<router-view>`、`<router-link>`、`<keep-alive>`、`<transition>`、`<teleport>`、`<suspense>`、`<component>`、`<slot>`
- 已在 Phase 2.5 第 1 步通过 `import` 找到的组件（避免重复）

注意：旧项目（Vue 2）大量使用 kebab-case 标签如 `<user-edit>`，对应 `import UserEdit from './user-edit'`。kebab→PascalCase 后再去重。剩下的标签**很可能是被遗漏的隐式依赖**（自动注册、unplugin-vue-components、或纯模板引用）。把它们也加入待分析列表。

#### 兄弟 edit 页扫描（针对 list 页 + 路由跳转模式）

**问题**：旧项目里 list 经常通过 `router.push({ path: '/xxx/detail' })` 或 `<router-link :to="'/xxx/detail?id=...'">` 跳到一个**独立的 edit 页面**，没有 `import` 也没有自定义标签 —— 仅靠 import/template 扫描会漏掉 edit 页。

**根因**：旧项目是后端菜单驱动路由，URL → 文件的映射不在前端 router config 里，所以**无法**通过路由解析定位目标文件。

**解决**：用**目录约定回退**。当被分析的 page 文件名是 `index.vue` 时（典型 list 页特征），执行：

1. 扫描 `<script>` 里所有路由跳转语句：
   - `router.push(...)` / `router.replace(...)` / `this.$router.push(...)` / `this.$router.replace(...)`
   - `<router-link :to="...">` 中的字符串字面量或字符串拼接
2. 只要发现**任意一个**跳转目标的字面量里包含 `/detail` / `/edit` / `/add` 之一，就触发兄弟文件扫描（不要求 URL 和当前目录匹配，因为 URL 路径和目录名往往不一致）
3. **目录约定回退**：在 `index.vue` 所在目录下匹配以下文件名（按优先级）：
   - `{dirName}-edit.vue`（最常见，如 `reservation/reservation-edit.vue`）
   - `{dirName}-detail.vue`
   - `*-edit.vue` / `*-detail.vue`（兜底，匹配第一个）
4. 找到的文件**作为独立 page 递归分析**，输出到 `ai/specs/pages/{Name}Edit.yaml`：
   - 命名遵循 Phase 3 命名规则：`reservation-edit.vue` → kebab 转 PascalCase → `ReservationEdit.yaml`
   - 在主 list spec 的 `dependencySpecs` 里登记，并加 `relation: "edit-page"` 标记
5. **递归深度限制**：edit 页里如果再有 `router.push`，**不再继续**触发兄弟扫描（避免链式扩散）。普通 import 依赖仍走原 3 层规则。
6. 找不到任何兄弟 edit 文件 → 进 `unresolvedDependencies` 警告：`{ relation: "edit-page", reason: "list page navigates to /xxx/detail but no sibling *-edit.vue found" }`

**输出示例**：

分析 `legacy/src/views/{module}/{Feature}/index.vue` 时：
```
🌳 Dependency tree:
   index.vue (Feature)
   ├── services/feature.js         → ai/specs/services/featureService.yaml
   ├── stores/feature.js           → ai/specs/stores/useFeatureStore.yaml
   └── feature-edit.vue            → ai/specs/pages/FeatureEdit.yaml [relation: edit-page]
```

`ai/specs/pages/Feature.yaml` 里：
```yaml
page:
  name: Feature
  dependencySpecs:
    - { spec: ai/specs/services/featureService.yaml }
    - { spec: ai/specs/stores/useFeatureStore.yaml }
    - { spec: ai/specs/pages/FeatureEdit.yaml, relation: "edit-page" }
```

#### 字段级组件引用（针对表单页 spec）

页面 spec 的 `fields` 列表里，如果某个字段的渲染**用了不是基础 Input/Select/DatePicker 的自定义组件**（比如客户搜索器、地址选择器、级联树等），生成 spec 时必须显式记录:

```yaml
fields:
  - name: ownerId
    type: select                    # 抽象类型，给一个 fallback hint
    label: "オーナー"
    required: true
    component: "CustomerPicker"      # ← 真正的组件名（功能性命名，不是 CustomerList）
    componentScope: "shared"         # ← shared / page，决定 create-page 把它生成到哪里
    componentSpec: ai/specs/components/CustomerPicker.yaml  # ← 关联到 Phase 2.5 生成的 spec
    props: { customerKb: "owner" }   # ← 透传到组件的 props
    cascadeTo: [propertyId]
```

`componentScope` 默认值规则:
- 如果该组件被多个 page spec 引用 → `shared`
- 如果只被一个 page spec 引用 → `page`（生成到 `src/views/{module}/{Page}/components/` 下）
- 不确定时默认 `shared`，便于复用

#### 输出结构:

主页面 spec 中除原有 `dependencySpecs` 外，再新增 `unresolvedDependencies`（如有）:
```yaml
page:
  name: Reservation
  dependencySpecs:
    - { spec: ai/specs/services/reservationService.yaml }
    - { spec: ai/specs/stores/useReservationStore.yaml }
    - { spec: ai/specs/pages/ReservationEdit.yaml, relation: "edit-page" }   # ← sibling edit 是 page 不是 component
    - { spec: ai/specs/components/CustomerPicker.yaml }                       # ← 字段级组件，功能性命名
  unresolvedDependencies:                          # ← 引用了但找不到源文件的依赖
    - { component: "ZipCodePicker", referencedBy: "address field", reason: "not found in legacy project" }
```

### Phase 3: 生成 Spec

输出标准 YAML spec 文件，格式严格遵循 `ai/dsl/schema.md` 中定义的 schema。

Spec 文件保存到: `ai/specs/{type}/{name}.yaml`

#### 命名规则（MANDATORY — 保证幂等）

多次 analyze 同一个文件**必须**输出相同的 spec 文件名。`{name}` 的推导**只**依赖源文件路径，不依赖代码内容、不依赖文件被怎么使用、不允许 LLM 自由发挥（不要生成 `MenuManagement`、`UserList`、`OrderCenter` 这种"美化名"）。

##### Step 0: 分类（path-only，**不许看代码内容/使用方式**）

按下面这棵决策树**严格按顺序**走，第一条命中就立刻定型，**不再回头**。**特别注意**：哪怕这个文件实际是被某个 list 页 `import` 进来当 modal 用、哪怕模板里写的是 `<user-edit>` 看着像组件 —— 只要路径命中"页面"分支，**就是 page**，分类不可被使用方式覆盖。

```
源文件路径
│
├─ 路径包含 `/components/` 段（任意层）
│     例：src/components/ui/Button.vue
│         src/views/{module}/{Feature}/components/FeatureToolbar.vue
│         legacy/src/components/DropDownList/customerList.vue
│  → component  → 进 Step 1A
│  ⚠ 注意：page 的 sibling edit/detail（如 user-edit.vue、reservation-edit.vue）
│        位于 `views/{module}/{entity}/` 下，**不在** `components/` 子目录里 → 走下面的 page 分支
│
├─ 路径在 `views/` 下（且上一条没命中）
│     例：src/views/system/user/index.vue
│         src/views/system/user/user-edit.vue       ← page，即使被 index.vue 当 modal import
│         src/views/{module}/{feature}/{feature}-edit.vue
│         src/views/{module}/{feature}/{feature}-detail.vue
│  → page  → 进 Step 1B
│
├─ 路径在 `services/` 下
│  → service  → 进 Step 1C
│
├─ 路径在 `stores/` 下
│  → store  → 进 Step 1D
│
└─ 路径在 `composables/` 下
   → composable  → 进 Step 1E
```

**禁止的反例**（这就是上次分析 user 画面踩的坑）：

| 源文件 | ❌ 错误结果 | ✅ 正确结果 |
|---|---|---|
| `legacy/src/views/system/user/user-edit.vue` | `components/UserFormModal.yaml`（因为它在 list 里被当 modal 用 → 误判为 component → 套重命名表 → 改名 FormModal） | `pages/UserEdit.yaml`（路径在 `views/` 不在 `components/`、文件名 `*-edit.vue` → 必然 page，重命名规则不适用） |
| `legacy/src/views/{module}/{feature}/{feature}-edit.vue` | `components/{Feature}FormModal.yaml` | `pages/{Feature}Edit.yaml` |

如果一个文件在新项目里你想把它实现成共通的 modal 组件（放到 `src/components/shared/`），那是**实现层的决策**，不是 spec 分类决策。spec 仍然按源路径归到 `pages/`，create-page 在生成时再决定要不要把它实现成共通组件。

##### Step 1A: component → 命名

`{name}` 推导：文件 basename（去 `.vue`），kebab-case 转 PascalCase。
- `src/components/ui/Button.vue` → `Button.yaml`
- `src/components/DropDownList/customerList.vue` → 见下方"组件命名约束"，**禁止**直接用 `CustomerList.yaml`

##### Step 1B: page → 命名

1. 文件名是 `index.vue` → `{name}` = 父目录名
   - `src/views/system/User/index.vue` → `User.yaml`
   - `src/views/{module}/{feature}/index.vue` → `{Feature}.yaml`
2. 否则 → `{name}` = 文件 basename（去 `.vue`），kebab-case 转 PascalCase
   - `src/views/system/user/user-edit.vue` → `UserEdit.yaml`
   - `src/views/{module}/{feature}/{feature}-edit.vue` → `{Feature}Edit.yaml`
   - `src/views/{module}/{Feature}/{Feature}Edit.vue` → `{Feature}Edit.yaml`
3. 父目录名小写开头时，首字母转大写

**page 永远不走"组件命名约束"的重命名表**。`UserEdit` 是合法的 page 名字，`ReservationDetail` 也是。重命名规则只适用于 Step 1A 分类出来的 component。

##### 组件命名约束（MANDATORY，**仅适用于 Step 1A 分类出来的 component**）

**前置条件**：本节规则**只**对 Step 0 路径树判定为 `component` 的文件生效。如果文件已经被 Step 0 判为 `page`（包括 `*-edit.vue` / `*-detail.vue`），**直接跳过本节**，page 名永远不重命名。

组件 spec 的 `{name}` **必须体现组件的功能**（"它是什么交互"），**禁止**使用和 page 撞型的 `{Entity}{List|Edit|Detail|Add|Create|Form}` 格式 —— 那些词代表"页面级动作"，看到 `UserList.yaml` / `CustomerList.yaml` 的人会误以为是列表页 spec。

**判定流程**：
1. 取源文件 basename 转 PascalCase 得到候选名 `X`
2. 如果 `X` 匹配 `^[A-Z][a-z]+(List|Edit|Detail|Add|Create|Form)$` → **必须重命名**，按下面的"功能后缀表"挑一个反映真实交互的后缀
3. 否则保留 `X`

**功能后缀对照表**（按组件实际行为选）：

| 组件做什么 | 用什么后缀 | 例 |
|---|---|---|
| 下拉选择某条主数据（单选） | `Picker` | `CustomerPicker`、`UserPicker` |
| 下拉多选 | `MultiPicker` | `PropertyMultiPicker` |
| 弹窗式选择/搜索 | `SearchModal` | `CustomerSearchModal` |
| 行内输入 + 搜索建议 | `Autocomplete` | `CustomerAutocomplete` |
| 树形选择 | `TreePicker` | `DeptTreePicker` |
| 级联选择 | `Cascader` | `RegionCascader` |

**禁用后缀**（与 `create-component.md` Phase 2.5 保持一致）：`List` / `Select` / `Selector` / `Dropdown` / `TreeSelect` / `PickerModal` 作为下拉组件后缀 —— 与 `Picker` / `SearchModal` / `TreePicker` 同义、会造成重复。legacy 文件（如 `customerList.vue`、`ownerSelect.vue`）**必须**在 spec 阶段按上表重命名。
| 状态徽标 | `Badge` / `Tag` | `OrderStatusBadge` |
| 头像 | `Avatar` | `UserAvatar` |
| 卡片 | `Card` | `ReservationSummaryCard` |
| 自定义表格行/单元格 | `Row` / `Cell` | `MenuTreeRow` |

**故意不在表里的后缀**：`FormModal` / `EditModal` / `DetailModal` —— 这些是 page 级语义，旧项目里被当 modal 使用的"编辑/详情"文件**几乎都**位于 `views/{module}/{entity}/{entity}-edit.vue`，按 Step 0 已经被判为 page，不会走到这里。如果你确实分析到了一个共通的、跨实体复用的 modal-form 组件（比如 `src/components/shared/GenericFormModal.vue`），那是真正的 component，按它的实际功能命名（`GenericFormModal` 已经是功能性命名了，因为没和具体 entity 绑死）。

**spec 注释**：组件 spec 顶部注释里**必须**写明这次重命名（如果发生过），方便回查：
```yaml
# Spec: CustomerPicker
# Analyzed from: legacy/src/components/DropDownList/customerList.vue
# Renamed: customerList → CustomerPicker (原文件名与 page 命名格式冲突)
# Generated: 2026-04-11
# Type: component
```

**幂等性保障**：因为重命名是规则驱动（候选名匹配正则就改），同一个文件多次 analyze 一定得到同一个目标名 —— 但为了避免新组件的"挑后缀"步骤产生分歧，**先查 `ai/specs/components/` 里是否已有对应 `Analyzed from:` 来源相同的 yaml，存在就沿用其文件名**（覆盖更新内容，不改名字）。

**Service (`services/*.js`)** → `ai/specs/services/{name}.yaml`
- `{name}` = 文件 basename（去 `.js`） + `Service` 后缀
- `services/menu.js` → `menuService.yaml`
- `services/user.js` → `userService.yaml`
- 已经以 `Service` 结尾的不重复加：`services/authService.js` → `authService.yaml`

**Store (`src/stores/*.js`)** → `ai/specs/stores/{name}.yaml`
- `{name}` = 文件 basename（去 `.js`），如果不以 `use` 开头则补 `use` 前缀并首字母大写后接 `Store`
- `src/stores/user.js` → `useUserStore.yaml`
- `src/stores/useAuthStore.js` → `useAuthStore.yaml`

**Composable (`src/composables/*.js`)** → `ai/specs/composables/{name}.yaml`
- `{name}` = 文件 basename（去 `.js`），保留原大小写
- `src/composables/useDict.js` → `useDict.yaml`

**外部项目（迁移分析）**
- 路径不在本项目时，按"映射到本项目后的等价路径"套用上面的规则
- 例如老项目 `/path/to/old/src/views/user/list.vue`：先确定它在新项目对应 `src/views/system/User/index.vue`（页面，父目录 `User`）→ `User.yaml`
- 如果实在无法判断对应位置，按文件 basename 走（`list.vue` → `List.yaml`），但要在 spec 顶部注释里写明 `# Note: name derived from file basename, may need rename`

#### 覆盖策略

如果目标 spec 文件**已存在**：
1. 不要换文件名绕开（这正是要消除的不确定性）
2. 直接覆盖写入新内容
3. 在终端输出里提示 `🔄 Overwrote existing spec: {path}`

目录结构:
```
ai/specs/
├── components/     # 组件 spec
├── pages/          # 页面 spec
├── services/       # 服务 spec
├── stores/         # Store spec
└── composables/    # Composable spec
```

文件格式:
```yaml
# Spec: {Name}
# Analyzed from: {source file path}
# Generated: {YYYY-MM-DD}
# Type: {component|page|service|store|composable}

{type}:
  name: {Name}
  # ... 完整的结构化字段
```

### Phase 4: 迁移映射（外部项目分析时）

当分析外部项目代码时，额外执行:
1. 识别外部框架（Vue 2 / React / Angular / 其他）
2. 将外部框架的模式映射到本项目的等价实现:
   - Options API → Composition API (`<script setup>`)
   - TypeScript → JavaScript
   - SCSS/CSS Modules → Tailwind CSS
   - Vuex → Pinia
   - Element Plus / Ant Design → Radix Vue + CVA + Tailwind
   - 其他图标库 → lucide-vue-next
3. **表单字段渲染映射**（spec 字段的 `type` / `multiple` 必须按下表填写，create-page 才能正确生成模板）：

| 旧项目写法 | spec 字段 | 新项目渲染 |
|---|---|---|
| `<a-input>` / `<el-input>` | `type: input` | `<Input>` |
| `<a-input-password>` | `type: password` | `<Input type="password">` |
| `<a-input-number format="money">` | `type: input`, `format: money` | `<Input format="money">` |
| `<a-textarea>` | `type: textarea`, `rows: N` | `<textarea>`（Tailwind 样式） |
| `<a-select>` 单选 | `type: select` | `<Select>` |
| `<a-select mode="multiple">` / `<el-select multiple>` | `type: select`, **`multiple: true`** | `<Select :multiple="true">` |
| `<a-radio-group>` | `type: radio` | `<RadioGroup>` |
| `<a-checkbox>` 单个 | `type: checkbox` | `<Checkbox>` |
| `<a-switch>` | `type: switch`, `checkedValue` / `uncheckedValue` | `<Switch>` |
| `<a-date-picker>` | `type: date` | `<DatePicker>` |
| `<a-range-picker>` | `type: dateRange` | `<DateRangePicker>` |
| `<a-cascader>` | `type: cascader` | （未实现，记到 `unresolvedDependencies`） |

**判定多选的关键信号**（任一即可）：
- 模板上有 `mode="multiple"` / `:multiple="true"` 属性
- 后端 entity 字段是数组类型（看 `roleIds: []` / `String[]` 等）
- JS 里有 `xxx.map(d => d.id)` 把对象数组拍平成 ID 数组的回填逻辑
- form 默认值写成 `[]`

→ 命中任一就在 spec 字段里写 `multiple: true`，**不要**写 `renderAs: "multiSelect"` 这种自创类型（旧 spec 残留），统一用 `type: select` + `multiple: true`。

4. **i18n 处理（MANDATORY — 不许幻觉翻译）**

旧项目大量使用 vue-i18n：模板里写 `:label="$t('user.company')"`、JS 里写 `this.$t('common.button.search')`，**真实文案在 lang 文件里**，不在 .vue 里。模型不能凭语感造翻译。

**强制规则**：

1. 扫描被分析文件，找出所有 i18n 调用：
   - 模板：`$t('xxx.yyy')` / `$tc(...)` / `:label="$t(...)"` / `:placeholder="$t(...)"` / `:title="$t(...)"`
   - 脚本：`this.$t('xxx.yyy')` / `i18n.t(...)` / `t('xxx.yyy')`（Composition API 风格）
2. **不许把 key 翻译成字面量写入 spec**。spec 里**保留 i18n key**，用 `labelKey` / `titleKey` / `placeholderKey` 字段：

   ```yaml
   # ❌ 错（旧规则下踩的坑 —— 模型自由发挥造翻译）
   fields:
     - name: companyId
       label: "会社"          # ← 模型猜的，旧项目实际是 "所属会社"

   # ✅ 对（新规则）
   fields:
     - name: companyId
       labelKey: user.company  # ← 保留 i18n key，create-page 渲染时用 t('user.company')
   ```

   适用所有用户可见文案字段：`labelKey` / `titleKey` / `placeholderKey` / `buttonTextKey` / `confirmMessageKey` / `tooltipKey` / `optionLabelKey`

3. **新项目本地已经有完整的 lang 文件副本** 在 `src/lang/`，结构和旧项目一致。analyze 时**不需要去解析 lang 文件**也不需要去查文案 —— 只要把 i18n key 原样保留进 spec，create-page 生成代码时调 `t(key)` 即可。lang 文件是单一信源，spec 不重复存翻译。

4. **如果旧项目的某段是硬编码字符串而不是 `$t()`**（少数情况，比如 alert 信息或 console.log）：
   - 在 spec 里用 `label: "字面量"` 标注
   - 在 spec 顶部 `migration_notes` 加一条 `# Hardcoded string: "xxx" — should be migrated to i18n key`
   - 不要替模型猜对应的 i18n key（猜错了下游全错）

5. **旧项目用了 i18n key 但 key 里嵌套了对象的 message 字段**（如 `user.message.resetPassword`）：原样保留，create-page 生成 `t('user.message.resetPassword')`。

6. spec 顶部注释里加一行 `# i18n: keys preserved as labelKey / titleKey / etc., resolved at runtime via vue-i18n`，提醒后续 reviewer。

5. 在 spec 中添加 `migration_notes` 字段记录关键迁移点

### Phase 5: 输出

```
✅ Analyzed: {source path}
📄 Spec: ai/specs/{type}/{name}.yaml
📊 Summary:
   - Type: {component|page|service|store|composable}
   - Props: {count}
   - Events: {count}
   - Dependencies: {list}
   - Sections: {list} (页面类型)
   - Endpoints: {count} (服务类型)
```

如果分析了目录（多个文件），列出所有生成的 spec:
```
✅ Analyzed: {directory path} ({N} files)
📄 Specs generated:
   - ai/specs/pages/MenuManagement.yaml
   - ai/specs/services/menuService.yaml
   - ai/specs/components/MenuTreeRow.yaml
```

如果递归分析了依赖，额外输出依赖树:
```
🌳 Dependency tree:
   reservation/index.vue (Reservation)
   ├── services/reservation.js              → ai/specs/services/reservationService.yaml
   ├── stores/reservation.js                → ai/specs/stores/useReservationStore.yaml
   ├── reservation/reservation-edit.vue     → ai/specs/pages/ReservationEdit.yaml [relation: edit-page]
   └── components/DropDownList/customerList.vue
                                            → ai/specs/components/CustomerPicker.yaml [field: ownerId, renamed]
   (Skipped: Card, DataTable, DateRangePicker, Input, useDict, usePermission — shared/common)

⚠️  Unresolved (placeholder will be generated by create-page):
   - ZipCodePicker (referenced by address field, not found in legacy project)
```

---

## 使用场景示例

### 1. 分析现有页面，生成 spec 后用于迭代
```bash
pnpm ai:analyze src/views/system/menu/index.vue
# → 生成 ai/specs/pages/Menu.yaml
pnpm ai:update-page "基于 ai/specs/pages/Menu.yaml 添加批量删除功能"
```

### 2. 分析外部旧项目代码，生成迁移 spec
```bash
pnpm ai:analyze /path/to/old-project/src/views/user/list.vue
# → 生成 ai/specs/pages/UserList.yaml (含 migration_notes)
pnpm ai:create-page "基于 ai/specs/pages/UserList.yaml 生成"
```

### 3. 分析整个目录
```bash
pnpm ai:analyze src/views/system/
# → 为目录下每个文件生成对应 spec
```

### 4. 分析服务层
```bash
pnpm ai:analyze services/menu.js
# → 生成 ai/specs/services/menuService.yaml
```
