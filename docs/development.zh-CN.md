# Development

[English](development.md) · **中文**

面向**开发者**：项目结构、加新功能（菜单/权限/迁移）、测试规范、代码风格。

> 安装好本地环境是前提，见 [Getting Started](getting-started.zh-CN.md)。

---

## 1. 项目结构

```
access-matrix/
├── backend/                       Spring Boot 4 多模块 (Java 25)
│   ├── core-common/              错误码 / Result / RequestContext / ULID
│   ├── core-infrastructure/      Security / Web filters / Persistence / Mail / Keycloak admin
│   ├── core-system/              Auth / RBAC / Dept / Menu / OpLog
│   ├── business-demo/            示例业务模块（task），演示 data scope
│   └── core-bootstrap/           Spring Boot 入口、application.yml、Flyway 迁移、启动 seeder
│
├── frontend/                      Vue 3 + Vite 6 (JavaScript)
│   ├── src/
│   │   ├── views/                页面（按业务领域分包）
│   │   ├── components/           UI 组件 (ui/ + shared/ + layout/)
│   │   ├── composables/          复用 hook (usePermission / useToast / …)
│   │   ├── directives/           v-permission / v-role / v-any-permission
│   │   ├── stores/               Pinia (auth / menu / tabs / theme)
│   │   ├── utils/                permission / oidc / pkce / jwt-decode
│   │   ├── router/               路由 + 守卫
│   │   └── lang/                 5 语言 i18n
│   ├── services/                 axios endpoints（按业务命名）
│   └── tests/                    vitest 单测 + Playwright e2e
│
├── infra/                         本地基础设施
│   └── keycloak/                 启动脚本 + realm export JSON
│
└── docs/                          文档（你正在看的目录）
```

### 1.1 后端模块依赖方向（严格）

```
core-bootstrap
   ↓ depends on
core-system, business-demo          (siblings; 互相不依赖)
   ↓ depends on
core-infrastructure
   ↓ depends on
core-common
```

**违反方向 = 编译失败**。这是有意的 —— 防止业务模块互相紧耦合。

---

## 2. 加一个新菜单 + 页面

完整路径示例：在 "系统" 菜单下加一个 "字典管理" 页面。

### 2.1 后端：加菜单数据（Flyway 迁移）

新建 `backend/core-bootstrap/src/main/resources/db/migration/V23__add_dict_menu.sql`：

```sql
-- 注意：V 编号必须递增且唯一
INSERT INTO core_rbac_menu (
    id, tenant_id, parent_id, name, path, component,
    icon, sort_order, menu_type, status, mark
) VALUES (
    -- ULID can be generated via IdGenerator.ulid() at build time; for
    -- migrations we hand-pick stable 26-char strings.
    '01HXXXXXXXXXXXXXXXMENU01',  -- new menu id
    'demo',
    '00000000000000000000MENU05', -- parent = 系统
    'dict',
    '/system/dict',
    'system/Dict/Dict',
    'Book',
    50,
    2,  -- 2 = menu (not directory)
    1,
    1
);

-- 同时加 i18n title (V17 引入的 menu_title_i18n 列)
UPDATE core_rbac_menu
   SET title_i18n = '{"ja_JP":"辞書管理","en":"Dictionary","zh_CN":"字典管理","zh_TW":"字典管理","ko_KR":"사전 관리"}'::jsonb
 WHERE id = '01HXXXXXXXXXXXXXXXMENU01';

-- 把菜单挂到 SUPER_ADMIN 角色上（不然超管也看不到）
INSERT INTO core_rbac_role_menu (id, tenant_id, role_id, menu_id, mark)
VALUES (
    IdGenerator.ulid_placeholder(),  -- 实际用 UUID 或固定 ULID
    'demo',
    '00000000000000000000ROLE01', -- SUPER_ADMIN
    '01HXXXXXXXXXXXXXXXMENU01',
    1
);
```

### 2.2 前端：加页面组件

`frontend/src/views/system/Dict/Dict.vue`：

```vue
<script setup>
import { ref, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import DataTable from '@/components/shared/DataTable.vue'

const { t } = useI18n()
const rows = ref([])

onMounted(async () => {
  // const r = await getDictListApi()
  // rows.value = r.data.data.records
})
</script>

<template>
  <div class="p-4">
    <h1 class="text-xl font-bold mb-4">{{ t('dict.title') }}</h1>
    <DataTable :rows="rows" />
  </div>
</template>
```

**路由不需要手写** —— 后端菜单数据返回时，前端 `router/index.js` 的 beforeEach 守卫会动态 `addRoute()`。`component` 字段指向 `frontend/src/views/<path>.vue`。

### 2.3 加 i18n key

5 个 lang 文件都要加：

```js
// frontend/src/lang/ja_JP.js
export default {
  // ...existing...
  dict: {
    title: '辞書管理',
    // ...
  }
}
```

### 2.4 重启 + 验证

后端重启 → Flyway 跑 V23 → 菜单进表 → 前端刷新 → 在系统菜单下看到 "字典管理"。

---

## 3. 加一个新权限

权限编码格式：`<resource>:<action>`，全小写 + 连字符。

### 3.1 后端：注册权限码 + 注解保护

```java
// SystemPermissions.java (或者业务模块自己的 PermissionCode 类)
public final class SystemPermissions {
    public static final String DICT_READ   = PermissionCode.register("dict:read",   "system");
    public static final String DICT_CREATE = PermissionCode.register("dict:create", "system");
    public static final String DICT_UPDATE = PermissionCode.register("dict:update", "system");
    public static final String DICT_DELETE = PermissionCode.register("dict:delete", "system");
}
```

注册后会被 `PermissionRegistry` 收集。`PermissionConsistencyGuard` 在启动时会：
- 没在 DB 里的权限码 → 自动 INSERT
- DB 有但代码删了的 → 自动 soft-delete + 撤销 role_permission 绑定

**所以加权限码 = 改代码 + 重启**。不需要手写 SQL。

### 3.2 在 Controller 里用

```java
@RestController
@RequestMapping("/admin/dict")
public class DictAdminController {

    @GetMapping
    @RequiresPermission(SystemPermissions.DICT_READ)
    public JsonResult<PageResult<Dict>> list(...) { ... }

    @PostMapping
    @RequiresPermission(SystemPermissions.DICT_CREATE)
    public JsonResult<String> create(@RequestBody Dict d) { ... }
}
```

`PermissionAspect` 切面自动校验，没权限返回 403。

### 3.3 前端：v-permission 隐藏按钮

```html
<button v-permission="'dict:create'">新建</button>
<button v-permission="'dict:delete'">删除</button>
```

### 3.4 给角色绑定

启动后进系统 → 角色 → 选角色 → 权限 tab → 勾选 dict:read / dict:create → 保存。

---

## 4. 加一个新 Flyway 迁移

### 4.1 编号规则

- `V<N>__<snake_name>.sql`
- N 必须递增、不重复、有冒号会出错
- 名字用 snake_case，简短描述

```
V23__add_dict_menu.sql
V24__core_dict_table.sql
V25__demo_dict_data.sql
```

### 4.2 内容规范

- **只用 IF NOT EXISTS / IF EXISTS** —— 重复跑也不报错
- **不要改已发布的迁移**（已经在别的环境跑过）；要改只能加新 V 文件做 ALTER
- **每个新表都要有 `tenant_id` 列 + 索引**（多租户必需）：

```sql
CREATE TABLE IF NOT EXISTS core_dict (
    id          CHAR(26)    PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL DEFAULT 'demo',
    -- ... business cols ...
    mark        SMALLINT    NOT NULL DEFAULT 1,
    create_user VARCHAR(64),
    update_user VARCHAR(64),
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_core_dict_tenant ON core_dict (tenant_id) WHERE mark = 1;
```

### 4.3 Soft-delete 约定

- 所有业务表都用 `mark SMALLINT NOT NULL DEFAULT 1`（1=alive, 0=deleted）
- 部分唯一索引必须加 `WHERE mark = 1` —— 否则软删后唯一约束会永久占位
- Entity 上加 `@TableLogic(value="1", delval="0")`
- **删行用 `UpdateWrapper.set("mark", 0)`**，不要 `setMark(0) + updateById`（@TableLogic 会把 mark 从 SET 子句剔除，导致 silent no-op）—— 见 [memory/tablelogic_updatebyid_gotcha](../memory/tablelogic_updatebyid_gotcha.md)

### 4.4 写完跑一遍

```bash
./mvnw -pl core-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=local
```

启动 log 看到 `Successfully applied migration V23` 就 OK。

---

## 加一个新业务模块（端到端 checklist）

"加订单模块 / 库存模块 / 计费模块" 的标准配方。完全跟 `TenantSchemaGuard` / `PermissionConsistencyGuard` / `core-system` 里 ArchitectureTest 的约定一致。参考实现：[`backend/business-demo/`](../backend/business-demo/)。

### 0. 推荐：用 scaffold 工具自动生成

最快路径 —— 一条命令生成下面 6 个文件，conventions 全部内置：

```bash
./mvnw -pl core-bootstrap exec:java \
    -Dexec.mainClass=com.platform.core.bootstrap.tools.BusinessModuleScaffold \
    -Dexec.args="<resource>"
```

`<resource>` 必须匹配 `[a-z][a-z0-9]*`（如 `order`、`invoice`、`salesreport`）。工具会：

- 克隆 `business-demo/task/*` 并替换标识符（`Task` → `Invoice`、`task` → `invoice`、`demo_task` → `business_invoice`）
- 自动选下一个空闲 Flyway 版本号 ≥ 1000（从 `core-bootstrap/.../db/migration/` 扫出来）
- 生成迁移：含 `tenant_id` + 审计列 + tenant 打头的唯一索引
- 打印 next-steps（你还得手动加权限码常量 —— 见下面第 5 步）
- 不覆盖已有目录 —— 想重新生成先把目录删掉

然后跳到第 5 步（加权限码）继续。下面的步骤是不用 scaffold 时的手动路径。

### 1. 选版本号 + 写迁移

业务模块从 **V1000+** 起跳（V1-V999 给框架保留）。在你的模块号段里选下一个空号：

```
backend/business-<module>/src/main/resources/db/migration/V1000__create_business_xxx.sql
```

每个 per-tenant 业务表必备字段：

```sql
CREATE TABLE business_xxx (
    id            CHAR(26)     NOT NULL PRIMARY KEY,
    tenant_id     VARCHAR(64)  NOT NULL,                   -- 必给；漏写 TenantSchemaGuard 启动期 fail-fast
    -- ... 业务字段 ...
    mark          SMALLINT     NOT NULL DEFAULT 1,
    create_user   VARCHAR(64),
    update_user   VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 业务唯一约束必须 tenant_id 打头，让多个租户能各自有同名行：
CREATE UNIQUE INDEX uk_xxx_code
    ON business_xxx (tenant_id, code) WHERE mark = 1;
```

### 2. Entity 继承 BaseEntity

```java
@TableName("business_xxx")
@Getter @Setter
public class XxxEntity extends BaseEntity {
    // 不要再声明 id / tenantId / mark / create_user / create_time
    // / update_user / update_time — BaseEntity 已经有了
    private String code;
    private String name;
    // ... 仅业务字段
}
```

INSERT 时 `AuditMetaObjectHandler` 自动从 `RequestContext.tenantId()` 填 `tenant_id`。

### 3. Mapper 继承 BaseMapper

```java
@Mapper
public interface XxxMapper extends BaseMapper<XxxEntity> { }
```

自定义 `@Select` 查询**必须显式写** `tenant_id = #{tenantId}` 条件。

### 4. Service + Controller

```java
@Service
public class XxxService { /* 业务逻辑 */ }

@RestController
@RequestMapping("/business-xxx")
public class XxxController {

    private final XxxService service;

    @GetMapping
    @RequiresPermission(XxxPermissions.XXX_READ)   // 来自 *Permissions 常量类
    public JsonResult<PageResult<XxxDto.View>> list(...) { ... }
}
```

### 5. 权限常量

```java
@Component
public final class XxxPermissions {
    public static final String XXX_READ   = "xxx:read";
    public static final String XXX_CREATE = "xxx:create";
    public static final String XXX_UPDATE = "xxx:update";
    public static final String XXX_DELETE = "xxx:delete";

    static { PermissionCode.registerAll(XxxPermissions.class, "xxx"); }
    XxxPermissions() {}
}
```

启动期 `PermissionConsistencyGuard` 自动把这些 code 写进 `core_rbac_permission`；如果哪个 `@RequiresPermission("xxx:yyy")` 用了未注册的字符串，fail-fast 拒绝启动。

### 6. 启动 — 后面 guard 接管

```bash
./mvnw -pl core-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=local
```

三层防线兜底：

| 层 | 抓什么 |
|---|---|
| 编译 (`./mvnw test`) | ArchUnit：entity 漏继承 BaseEntity、controller 漏 @RequiresPermission、mapper 不在 mapper 包 |
| 启动 | `TenantSchemaGuard`（漏 tenant_id 列）、`PermissionConsistencyGuard`（字面量/未注册 code）、`FlywayMigration`（SQL 语法错） |
| 运行时 | `TenantLineInnerInterceptor`（自动注入 `WHERE tenant_id=?`）、`@DataScope` 切面（部门范围过滤） |

三层都绿即说明模块正确接入了多租户 + RBAC 框架。

### 参考实现

抄 `backend/business-demo/task/` 的结构：
- `entity/TaskEntity.java`
- `mapper/TaskMapper.java`
- `service/TaskService.java`
- `controller/TaskController.java`
- `security/DemoPermissions.java`
- `db/migration/V10__demo_task.sql`

---

## 5. 测试规范

### 5.1 后端单测（Mockito）

放 `<module>/src/test/java/<package>/`，文件名 `*Test.java`，用 JUnit 5 + Mockito 5。

**模式**：
- @Mock mapper，@InjectMocks service
- mock 的 mapper 用 @Mock + when().thenReturn()
- 验证 SQL → captor + UpdateWrapper.getSqlSet() / getParamNameValuePairs()

参考：[`RoleAdminServiceDeleteTest.java`](../backend/core-system/src/test/java/com/platform/system/rbac/service/RoleAdminServiceDeleteTest.java)

### 5.2 后端集成测（Testcontainers）

放 `<module>/src/test/java/<package>/it/`，文件名 `*IT.java`，加 `@Testcontainers(disabledWithoutDocker = true)`。

无 Docker 时**自动跳过**，不会让 build 失败。

参考：[`OidcJitProvisioningIT.java`](../backend/core-bootstrap/src/test/java/com/platform/core/bootstrap/it/OidcJitProvisioningIT.java)

### 5.3 前端单测（Vitest）

放 `<src 文件>.test.js`（co-located）或 `tests/components/*.test.js`。

- 纯逻辑（util / composable）→ 直接调用
- 涉及 Vue 组件 → `@vue/test-utils` mount
- 涉及 Pinia store → `vi.mock('@/stores/auth')` 提供最小 stub

### 5.4 前端 e2e（Playwright）

放 `tests/e2e/*.spec.js`。

- 默认假设前后端都在跑（5273 + 9135）
- 没启动时自动 skip（`/actuator/health` 探针 fixture）
- 在 CI 里设 `E2E_REQUIRE_STACK=1` 强制必须通

### 5.5 跑测试

```bash
# 后端全跑
./mvnw test

# 单个测试类
./mvnw -pl core-system test -Dtest='RoleAdminServiceDeleteTest'

# 前端 vitest
cd frontend && npm run test

# 前端 e2e（需前后端都在跑）
cd frontend && npm run test:e2e
```

---

## 6. 代码风格

### 6.1 Java

- 用 records 而非 class for DTOs
- ULID 主键 (`CHAR(26)`)，不用自增 long
- 避免 static field + `@Value` 注入（Spring anti-pattern）
- 软删用 UpdateWrapper.set("mark", 0)，**绝不**用 setMark + updateById
- @Select 注解的 SQL 必须显式带 `tenant_id = #{tenantId}`（手写 SQL 不被 MP 拦截器自动 rewrite）

### 6.2 Vue

- Composition API + `<script setup>`
- 业务页面组件挂在 `views/<domain>/`
- 通用 UI 组件在 `components/ui/`（不掺业务）
- 共享业务组件在 `components/shared/`（如 DeptTreeDialog / UserPicker）
- service 文件命名 `services/<resource>.js`，里面是 axios 调用

### 6.3 提交规范（Conventional Commits）

```
<type>(<scope>): <short summary>

<body>

<footer with breaking changes / refs>
```

type：`feat` / `fix` / `chore` / `docs` / `test` / `refactor` / `perf` / `style`

scope：`backend` / `frontend` / `infra` / `docs` / `repo`

---

## 7. 加一个新 Keycloak protocol mapper

例：把用户的 `dept_id` 也加进 JWT 让 SPA 直接用，不需要每次调 `/me`。

1. Keycloak admin → realm → Clients → `access-matrix-backend`
2. Client scopes tab → 找 dedicated scope 或建一个新 scope
3. Add mapper → "User Attribute" 类型
4. User Attribute 选 `dept_id`，Token Claim Name 写 `dept_id`，勾选 "Add to access token"
5. 导出 realm → 替换 `infra/keycloak/realms/default-realm.json`
6. commit

前端读：`useAuthStore().claims.dept_id`（已经通过 `decodeJwt` 拆开）。

后端读：`jwt.getClaimAsString("dept_id")`，可以在 `OidcJitUserService` 把它写到 `core_auth_user.dept_id`。

---

## 8. 路线图（短期 TODO）

- [ ] User.vue 加"重发邀请邮件"按钮
- [ ] SAML 2.0 支持（Keycloak 已经支持，只需要业务侧不卡 JWT）
- [ ] 自助账号申请页面（公开注册，可选）
- [ ] @OpLog 注解扩到所有业务写操作
- [ ] 国际化邮件模板支持自定义 SMTP 模板覆盖

欢迎 PR。

---

## 9. 开发资源

- 后端：[backend/AGENTS.md](../backend/AGENTS.md) — AI 协作约定 + 模块约束
- 前端：[frontend/AGENTS.md](../frontend/AGENTS.md) — 组件分层 + services 约定
- RBAC 设计：[backend/docs/RBAC建设方案.md](../backend/docs/RBAC建设方案.md)
- 部署：[Deployment](deployment.zh-CN.md)
