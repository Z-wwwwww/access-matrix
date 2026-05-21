# Skill: create-component

你是 InnTouch UI 的组件生成专家。根据用户输入（Spec / 自然语言 / 混合）创建 Vue 3 组件。

## Input: $ARGUMENTS

---

## 执行流程

### Phase 1: 解析输入
1. 检测输入中是否包含 Spec（YAML `component:` 块）
2. 如果有 Spec → 解析结构化字段
3. 如果有自然语言 → 提取: 组件名、类型(ui/shared/layout)、props、slots、events、样式需求
4. 如果引用了 `ai/specs/` 下的已有 spec 文件 → 读取并使用
5. 合并 Spec + NL 信息，生成完整组件规格

### Phase 2: 查重检查 (MANDATORY)
1. 扫描 `src/components/ui/`、`src/components/shared/`、`src/components/layout/`
2. 读取 `docs/component-register.md`
3. **按"语义指纹"而不是"名字字面"查重** —— 匹配任一条即视为重复：
   - 调用同一个后端 API（如两个组件都用 `getCustomerListApi`）
   - props 签名语义相同（例：`{modelValue, customerKb}` 都是"按 customerKb 过滤的客户选择器"，无论叫 `CustomerList`/`CustomerPicker`/`CustomerSelect`/`CustomerSelector`）
   - 命名是同义词替换（`List` ↔ `Picker` ↔ `Select` ↔ `Selector` ↔ `Dropdown`，在"下拉选择"语义下这些全是同一类）
4. 如果重复:
   - **停止生成**
   - 输出已有组件路径和使用方式
   - 提供扩展方案（如需要增强现有组件 —— 比如补 `disabled` / `error` prop）

**事故复盘**：`CustomerList.vue` 和 `CustomerPicker.vue` 曾并存 —— 相同 API、相同 props、只是命名不同，注册表还把 `CustomerList` 标注成"ReservationEdit 使用中"但实际代码里已无任何引用。根因：查重只看字面名字而非语义指纹。

### Phase 2.5: 命名规范 (MANDATORY)

避免同一业务实体出现多份同功能、不同名的组件。强制命名后缀：

| 后缀 | 语义 | 示例 |
|------|------|------|
| `{Entity}Picker` | **单选**下拉选择器（modelValue 为单值） | `CustomerPicker` / `UserPicker` / `DictPicker` |
| `{Entity}MultiPicker` | 多选下拉选择器（modelValue 为数组） | `PropertyMultiPicker` |
| `{Entity}Cascader` | 级联选择器 | `AreaCascader` |
| `{Entity}SearchModal` | 弹窗式搜索+选择 | `CustomerSearchModal` |
| `{Entity}Table` | 表格展示组件（非页面） | `RoomRateTable` |

**禁用**:
- `{Entity}List` / `{Entity}Select` / `{Entity}Selector` / `{Entity}Dropdown` 作为下拉组件名 —— 与 `Picker` 同义、造成重复
- `List` 后缀保留给列表**页面**（`src/views/` 下的列表页，如 `UserList.vue`）、或代表"多条数据渲染"的展示组件（纯 UI、不含选择语义）

从 legacy 项目迁移的组件若旧名违反规范（如 `customerList.vue`），**必须**在 spec 阶段按功能重命名（见 `analyze.md` 的"DropDownList 组件重命名"规则），不要保留旧名。

### Phase 3: 生成组件
严格遵守:
- `<script setup>` + Composition API
- JavaScript ONLY（禁止 TypeScript）
- Tailwind CSS ONLY（禁止 inline style）
- Props 必须有默认值
- **API 调用必须走 `services/` 模块**（MANDATORY、CLAUDE.md rule #3）：禁止在组件内直接 `import request from 'services/request'` 然后 `request.get('/some/url')`。shared 组件需要调 API 时，先去 `services/{module}.js` 找已有函数；没有就**先建/补充 service**（如 `services/adminCommon.js` 的 `getUserListApi`/`getDictListApi`/`getCityListApi`），再 import 进组件。共通下拉类组件（Picker/Cascader）的 endpoint 通常属于 `services/adminCommon.js`（`/adminCommon/*`）。例外：`ExportFileButton` / `SingleImgManualUploader` 这类通过 `url` prop 接受任意 endpoint 的**通用工具组件**可直接用 `request`，因为调用方不知道会打哪个 URL
- PascalCase 组件命名
- 如果有 variants → 使用 CVA (class-variance-authority)
- 如果需要样式合并 → 使用 cn() (clsx + tailwind-merge)
- **i18n MANDATORY**：组件内的任何用户可见文案（默认 placeholder、aria-label、内部按钮文字、空状态提示等）**禁止硬编码**业务字符串。
  - **共通 UI 组件**（`src/components/ui/`）：尽量**不**自带文案 —— placeholder / 标签等通过 props 由调用方传入（`:placeholder="t('user.keywordPlaceholder')"`）。如果组件确实需要内置 fallback 文字（如 Dialog 默认确认按钮），用 `t('common.button.ok')` 这种通用 key
  - **shared 业务组件**（`src/components/shared/`）：在 `<script setup>` 顶层 `import { useI18n } from 'vue-i18n'; const { t } = useI18n()`，然后用 `t('xxx.yyy')` 调用。新 key 同步加到 `src/lang/{module}/{locale}.js` 五个 locale。详细规范见 create-page.md "i18n 使用规范"
- 组件文件结构（**flat**，每个组件就是一个 .vue 文件，不要建子目录、不要 index.js）：
  ```
  src/components/ui/Button.vue
  src/components/ui/Card.vue
  src/components/shared/DataTable/DataTable.vue   # 例外：DataTable 因为有内部子组件 ColumnFilter，所以用了子目录
  ```
  实际项目里 99% 的组件都是 `src/components/{type}/{Name}.vue` 一个文件搞定。只有当组件**确实**有需要分文件的内部结构（比如 DataTable + ColumnFilter）时才建子目录，并且也**不写 index.js**（直接 `import { DataTable } from '@/components/shared/DataTable'` 走的是子目录里的 `index.js` 才需要 —— 这种情况极少，按需添加，不要默认生成）。

### Phase 4: 生成测试
- 路径: `tests/components/{ComponentName}.spec.js`
- 覆盖: props 默认值、slot 渲染、event 触发、variant 切换

### Phase 5: 注册
1. 追加到 `docs/component-register.md`:
   ```
   | {Name} | src/components/{type}/{Name} | {用途} | AI | {当前日期} |
   ```
2. 更新 `src/components/{type}/index.js` 导出

### Phase 5.5: Back-fill 扫描（MANDATORY for ui/ and shared/ components）

**根本目的**：解决"先生成的页面不知道后生成的组件"的漂移问题。新组件诞生的瞬间，存量代码里一定有人在"手写它"，必须主动找出来。

#### 触发条件
- 组件类型是 `ui` 或 `shared`（layout 组件不需要回扫）
- **跳过**纯新业务组件（如 `CustomerList` / `AreaList` 这类没有"反模式对应物"的组件）

#### 模式来源（按优先级）
1. **组件 spec 中的 `replaces` 字段**（如果有）— 显式列出该组件要替代的反模式正则：
   ```yaml
   component:
     name: Switch
     type: ui
     replaces:
       - pattern: 'relative inline-flex h-(5|6) w-(9|11)[^"]*translate-x-'
         hint: 手写 toggle button + Tailwind 滑块
       - pattern: '@click="[^"]*= [^"]*=== ''1'' \? ''0'' : ''1''"'
         hint: 内联 0/1 翻转 handler
   ```

2. **没有 `replaces` 字段时按组件名/功能推断**（用 LLM 常识）：
   | 组件 | 典型反模式 |
   |---|---|
   | `Switch` | `relative inline-flex h-5 w-9` / `relative inline-flex h-6 w-11` + `translate-x-` |
   | `DataTable` | 手写 `<table>` / `<tbody>` |
   | `Input` (含 `format="money"`) | 原生 `<input type="text/number">`、`type="number"` 处理金额 |
   | `Select` | 原生 `<select>` |
   | `DatePicker` / `DateTimePicker` / `TimePicker` | `<input type="date/datetime-local/time">` |
   | `Dialog` / `Modal` | 手写 `fixed inset-0` + `bg-black/50` overlay |

#### 执行流程
1. Glob `src/views/**/*.vue` 拿到所有视图文件
2. 对每个反模式 pattern 用 Grep 扫描
3. 收集命中并**按文件分组**（同一文件多个命中聚合成一个迁移项）
4. 同时检查命中文件**是否已经 import 该新组件**（已 import 的可能是部分迁移，仍然要列出未迁移的位置）
5. 输出回扫报告：
   ```
   🔄 Back-fill scan ({ComponentName}):
   发现 N 个文件含可迁移到 <{ComponentName}> 的存量代码:
     1. src/views/xxx/Foo.vue          — 2 处 (line 120, 145)  手写 toggle button
     2. src/views/xxx/Bar.vue          — 1 处 (line 45)        内联 0/1 翻转 handler
     3. src/views/yyy/Baz.vue          — 1 处 (line 230)       手写 toggle button
   ```
6. 如果**零命中**，输出 `🔄 Back-fill scan: clean ✓` 让用户知道扫过了，**结束 Phase 5.5**

#### 交互式迁移决策（B 模式 — 默认）

零命中时跳过这一步。命中 ≥ 1 时，**必须**用 `AskUserQuestion` 工具让用户做选择：

**第一轮：批量决策**

```
question: "发现 {N} 个文件含可迁移到 <{ComponentName}> 的存量代码，如何处理？"
multiSelect: false
options:
  - label: "全部迁移（推荐）"
    description: "对 {N} 个文件批量调用 update-page 迁移"
  - label: "逐个确认"
    description: "对每个文件单独询问是否迁移"
  - label: "暂不迁移"
    description: "只输出报告，留待将来 /inspect 或手动 /update-page"
```

**根据选择分支**：

- **「全部迁移」**：直接进入 Phase 5.6，对所有文件调用 update-page
- **「暂不迁移」**：在最终摘要里把列表标记为 ⚠ DEFERRED，结束 Phase 5.5
- **「逐个确认」**：对每个文件用 `AskUserQuestion` 单独问一次：
  ```
  question: "迁移 src/views/xxx/Foo.vue (2 处, line 120, 145) 到 <{ComponentName}>?"
  multiSelect: false
  options:
    - label: "迁移"
    - label: "跳过"
    - label: "跳过剩余全部（中断）"
  ```
  收集所有「迁移」的文件 → 进入 Phase 5.6
  - 选「中断」时立刻退出循环，把已选 + 未问的都按当前已选集合处理

#### Phase 5.6: 执行迁移（B 模式选择"迁移"时触发）

对每个被选中的文件：

1. 调用 `update-page` skill，命令格式：
   ```
   /update-page "{filepath} 把手写 {antipattern_hint} 迁移到 <{ComponentName}>，参考 {newComponentPath} 的 props 接口"
   ```
2. 收集每个文件的迁移结果（成功 / 失败 / 部分成功）
3. 失败的不阻断后续文件
4. 在最终摘要 Phase 6 里展示迁移汇总：
   ```
   🔄 Back-fill migration: 2 success / 1 failed
      ✅ src/views/xxx/Foo.vue
      ✅ src/views/xxx/Bar.vue
      ❌ src/views/yyy/Baz.vue — diff conflict, 需手动处理
   ```

#### 不在 B 模式中自动跳过 review 的理由
即使用户选「全て迁移」，每个 update-page 调用本身仍然遵循正常的 update-page 流程（影响分析、依赖检查），只是免去了用户手动复制命令的步骤。这保证了：
- **响应速度**：常见情况一键完成
- **安全性**：每个文件独立调用 update-page，单文件失败不会污染其他文件
- **可审阅**：所有 diff 在最后摘要里列出，用户事后可查 git diff 回滚

### Phase 6: 自检 & 输出
验证:
- [ ] 无 TypeScript
- [ ] 无 inline style
- [ ] Props 有默认值
- [ ] 注册表已更新
- [ ] 测试已生成
- [ ] 无重复组件
- [ ] **回扫报告已输出**（ui/shared 类型）
- [ ] **如果回扫有命中，AskUserQuestion 已被调用**（B 模式契约）

输出摘要（按 Phase 5.5 决策分支选其一）:

**clean 时**:
```
✅ Component: {Name}
📁 Path: src/components/{type}/{Name}/
🧪 Test: tests/components/{Name}.spec.js
📋 Registry: updated
🔄 Back-fill scan: clean ✓
```

**有命中 + 用户选了「全て迁移」**:
```
✅ Component: {Name}
📁 Path: src/components/{type}/{Name}/
📋 Registry: updated
🔄 Back-fill scan: 3 hits → migrated
   ✅ src/views/system/User/UserEdit.vue                    (2 处)
   ✅ src/views/system/Foo/Foo.vue                          (1 处)
```

**有命中 + 用户选了「今は迁移しない」**:
```
✅ Component: {Name}
...
🔄 Back-fill scan: 3 hits ⚠ DEFERRED
   - src/views/xxx/Foo.vue (line 120, 145)
   - src/views/xxx/Bar.vue (line 45)
   未来运行 /inspect compliance 会再次提醒
```

**部分迁移失败**:
```
🔄 Back-fill scan: 3 hits → 2 migrated / 1 failed
   ✅ src/views/xxx/Foo.vue
   ✅ src/views/xxx/Bar.vue
   ❌ src/views/yyy/Baz.vue — {失败原因}, 建议手动处理
```

回扫结果是**第一公民**，必须出现在摘要里 — 不能淹没在中间日志中。
