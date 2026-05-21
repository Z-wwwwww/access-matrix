# Access Matrix Spec Schema

Spec 用于精确描述要生成的代码结构。所有 Skill 都支持 Spec 输入和自然语言输入，也可以混合使用。

Spec 文件存储在 `ai/specs/{type}/{name}.yaml`，可通过 `/analyze` 从源代码自动生成。

---

## Component DSL

```yaml
component:
  name: ComponentName        # PascalCase, required
  type: ui | shared | layout # where to place it
  description: "..."         # purpose description
  props:
    propName:
      type: String | Number | Boolean | Array | Object | Function
      default: value         # required — all props must have defaults
      required: false        # optional, default false
  slots: [default, header, footer]   # named slots
  emits: [eventName]                 # custom events
  variants:                          # CVA variant definitions
    variantName:
      optionA: "tailwind classes"
      optionB: "tailwind classes"
  dependencies: [ComponentA, ComponentB]  # internal component deps
  composables: [useTheme]                 # composable deps
```

---

## Page DSL

```yaml
page:
  name: PageName             # PascalCase
  module: system | {businessModule}    # system or one of the project's business modules (e.g. pms when merged)
  route:                       # 动态路由 — 前端不需要手动注册
    path: "/module/page"       # 后端 sys_menu.path 字段
    component: "/module/page"  # 后端 sys_menu.component 字段（相对于 src/views/）
    meta:
      title: "页面标题"        # 后端 sys_menu.title 字段
  components: [CompA, CompB]       # components used in this page
  services: [serviceA, serviceB]   # API services used
  stores: [useXxxStore]            # Pinia stores used
  sections:                        # page layout sections
    - type: header | filter | table | form | chart | cards | custom
      description: "..."
  dependencySpecs:                 # /analyze 自动生成，/create-page 自动读取
    - ai/specs/services/xxxService.yaml
    - ai/specs/stores/useXxxStore.yaml
    - ai/specs/components/SubComp.yaml
  # table section 必须使用 DataTable 共通组件
  # columns 定义示例:
  table:
    columns:
      - key: name
        title: "名前"
        minWidth: "120px"
      - key: status
        title: "ステータス"
        filter: { dictCode: "operating_status" }   # 列头筛选（字典来源: src/dict/storage.js）
      - key: actions
        title: "操作"
        align: center
        sticky: right                               # 冻结列
    defaultFilters:                                  # 初始列头筛选值
      salesStatus: ["1"]
    pagination: { pageSize: 10 }                     # 默认分页
```

---

## Service DSL

```yaml
service:
  name: serviceName          # camelCase
  baseUrl: "/module"         # 相对于 /proxy_url，无 /api/v1 前缀
  endpoints:
    - name: getList          # 列表接口
      method: GET
      path: "/index"         # 约定: 列表用 /index
      params: [page, limit, keyword]  # 分页参数: page + limit（非 pageNo/pageSize）
    - name: getById
      method: GET
      path: "/info/:id"      # 约定: 详情用 /info/:id
    - name: create
      method: POST
      path: "/add"           # 约定: 新增用 /add
      body: { name: String, type: String }
    - name: update
      method: PUT
      path: "/update"        # 约定: 更新用 /update
      body: { name: String, type: String }
    - name: remove
      method: DELETE
      path: "/delete/:id"    # 约定: 删除用 /delete/:id
```

---

## Composable DSL

```yaml
composable:
  name: useXxxName           # must start with "use"
  description: "..."
  state:
    - name: varName
      type: ref | reactive | computed
      initial: value
  methods: [methodA, methodB]
  returns: [varName, methodA, methodB]
```

---

## Store DSL

```yaml
store:
  name: useXxxStore          # must start with "use", end with "Store"
  description: "..."
  state:
    items: []
    loading: false
    current: null
  getters:
    - name: activeItems
      description: "filter active items"
  actions:
    - name: fetchItems
      service: serviceA.getList
      description: "load items from API"
    - name: createItem
      service: serviceA.create
```

---

## Mixing Spec + Natural Language

You can provide partial Spec and describe the rest in natural language:

```bash
pnpm ai:create-component DataTable
```

传入混合内容：

```yaml
component:
  name: DataTable
  type: shared
```

> 需要支持分页、排序、列自定义、loading状态、空数据提示。
> 列配置通过 columns prop 传入，格式参考 Element Plus 的 Table。

The skill will merge the Spec structure with the NL description to generate complete code.

---

## Spec from Code Analysis

使用 `/analyze` 从现有代码自动生成 Spec：

```bash
pnpm ai:analyze src/views/system/menu/index.vue
# → ai/specs/pages/MenuManagement.yaml

pnpm ai:analyze services/menu.js
# → ai/specs/services/menuService.yaml
```

生成的 Spec 可直接用于 create-page、generate 等 Skill 的输入。
