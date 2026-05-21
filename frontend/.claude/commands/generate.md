# Skill: generate

你是 InnTouch UI 的通用代码生成专家。生成 services、composables、stores、utils 等非组件/非页面代码。

## Input: $ARGUMENTS

---

## 支持的生成类型

自动识别输入中的目标类型，支持 Spec 和自然语言。如果引用了 `ai/specs/` 下的已有 spec 文件，读取并使用。

### 1. Service (API 服务)

**Spec**:
```yaml
service:
  name: userService
  baseUrl: "/user"           # 相对于 /proxy_url，无 /api/v1 前缀
  endpoints:
    - name: getList
      method: GET
      path: "/index"         # 列表接口统一用 /index
      params: [page, limit, keyword]  # 分页参数: page + limit
    - name: create
      method: POST
      path: "/add"
      body: { username: String, role: String }
```

**执行**:
1. 检查 `services/` 和 `docs/service-register.md` 有无重复
2. 生成 `services/{serviceName}.js` — 使用 axios 封装
3. 生成测试 `tests/services/{serviceName}.spec.js`
4. 更新 `services/index.js` 导出
5. 更新 `docs/service-register.md`

### 2. Composable (组合式函数)

**Spec**:
```yaml
composable:
  name: useDebounce
  description: "防抖 hook"
  params: [fn, delay]
  returns: [debouncedFn, cancel]
```

**执行**:
1. 检查 `src/composables/` 有无重复
2. 生成 `src/composables/{name}.js`
3. 生成测试 `tests/composables/{name}.spec.js`

### 3. Store (Pinia 状态管理)

**Spec**:
```yaml
store:
  name: useUserStore
  state:
    users: []
    currentUser: null
    loading: false
  getters:
    - name: activeUsers
  actions:
    - name: fetchUsers
      service: userService.getList
    - name: createUser
      service: userService.create
```

**执行**:
1. 检查 `src/stores/` 有无重复
2. 生成 `src/stores/{name}.js` — 使用 Pinia `defineStore`
3. 自动关联 service 调用

### 4. Utility (工具函数)

**NL examples**:
- "生成一个日期格式化工具"
- "创建 cn 函数用于合并 tailwind 类名"

**执行**:
1. 生成 `src/lib/{name}.js`
2. 生成测试

---

## 通用规则
- JavaScript ONLY
- camelCase 命名（service、composable、util）
- useXxx 命名（composable）
- useXxxStore 命名（store）
- 所有 service 统一从 `services/index.js` 导出
- 生成前必须查重
- 生成后必须注册（service → docs/service-register.md）

## 自检 & 输出
```
✅ Generated: {type} — {name}
📁 Path: {file path}
🧪 Test: {test path}
📋 Registry: {updated | N/A}
```
