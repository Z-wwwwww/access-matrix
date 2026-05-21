# Data Scope Walkthrough — `business-demo`

`business-demo` 模块演示 access-matrix 的 5 种 **数据范围**（`role.data_scope`）实际效果：同一张表、同一条 SQL，不同身份登录看到的行数不同。

## 一键启动

```bash
# 后端（local profile 会同时跑 LocalAdminSeeder + DemoSeeder）
cd access-matrix
mvn -pl core-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=local

# 前端
cd access-matrix-front
npm run dev
```

后端首次启动后会自动种好：
- 1 个新部门 `KYOTO`（TOKYO 的子部门）
- 5 个 demo 用户（密码统一 `demo123`）
- 5 个 demo 角色（各对应一种 `data_scope`）
- 15 条 `demo_task` 数据，跨 HQ / TOKYO / OSAKA / KYOTO 4 个部门

## 部门树

```
HQ (Head Office)
 ├── TOKYO Branch
 │    └── KYOTO Branch        ← V10 新加，用于演示 DEPT_AND_SUB
 └── OSAKA Branch
```

## 5 种 Scope vs 5 个 demo 用户

| 用户名 | 密码 | 所属部门 | 角色 | `data_scope` | 视角说明 |
|--------|------|---------|------|:------------:|---------|
| `demo_all`      | `demo123` | HQ    | DEMO_ALL      | **1 ALL**          | 看到所有部门所有人的 task |
| `demo_deptsub`  | `demo123` | TOKYO | DEMO_DEPT_SUB | **2 DEPT_AND_SUB** | 本部门 + 所有子部门（TOKYO + KYOTO） |
| `demo_dept`     | `demo123` | OSAKA | DEMO_DEPT     | **3 DEPT**         | 仅本部门（OSAKA） |
| `demo_self`     | `demo123` | TOKYO | DEMO_SELF     | **4 SELF**         | 只看自己创建的 task |
| `demo_custom`   | `demo123` | HQ    | DEMO_CUSTOM   | **5 CUSTOM**       | role_dept 表显式绑了 KYOTO，所以只看 KYOTO 的 |

> 顺便：`admin` / `admin` 仍是超级管理员（SUPER_ADMIN，`*:*`），会看到全部 15 条。

## 预期可见行数

| 登录身份 | 可见 task 条数 | 说明 |
|---------|:---:|---|
| `admin` (SUPER_ADMIN) | 15 | `*:*` 短路，所有数据 |
| `demo_all` | 15 | ALL scope 等同放行 |
| `demo_deptsub` | 8  | TOKYO 5 条 + KYOTO 3 条 |
| `demo_dept` | 4 | OSAKA 4 条 |
| `demo_self` | 3 | 自己（TOKYO 用户）创建的 2 条 + 在 KYOTO 创建的 1 条 |
| `demo_custom` | 3 | KYOTO 3 条（CUSTOM 绑定的部门） |

> SELF 是按"我创建的"过滤，跟所属部门无关 —— `demo_self` 在 TOKYO，但他在 KYOTO 创的一条也能看到。

## 验证步骤

打开两个无痕浏览器窗口，分别登录两个 demo 用户：

1. 用 `demo_all` 登录 → 看到 15 条
2. 用 `demo_dept` 登录 → 看到 4 条（全是 OSAKA）
3. 试着用 `demo_dept` 创建一条 HQ 的 task → **行能写进去**（写不走 scope 过滤），但回到列表 **看不到自己刚建的那条**（因为它落到 HQ，OSAKA 用户的 DEPT scope 排除了）。这是预期行为，体现"可写、不可见"

## 关键代码路径

- 切面定义：`core-infrastructure/.../security/rbac/DataScopeAspect.java`
- 实际改写 SQL：`core-infrastructure/.../security/rbac/DataScopeHelper.java`
- 决策构造：`core-system/.../rbac/service/DataScopeQueryService.java`（5 种 mode 取并集，ALL 短路）
- **demo 使用范式**（业务方照抄这一段）：

```java
// business-demo/.../task/service/TaskService.java
DataScopeDecision decision = dataScopeResolver.currentDecision();
LambdaQueryWrapper<TaskEntity> w = new LambdaQueryWrapper<TaskEntity>()
        .eq(TaskEntity::getMark, 1)
        .orderByDesc(TaskEntity::getCreateTime);
DataScopeHelper.apply(w, decision, TaskEntity::getDeptId, TaskEntity::getCreateUser);
Page<TaskEntity> result = taskMapper.selectPage(p, w);
```

`@DataScope` 注解只是**门禁**：service 忘了调 `apply` 时，dev/local/test profile 会抛 `INTERNAL_ERROR` 把问题在第一次集成时暴露出来；prod 仅 WARN 不中断业务。

## 复位

清空 demo 数据：

```sql
DELETE FROM demo_task WHERE id LIKE 'DEMOTASK%';
DELETE FROM core_rbac_user_role WHERE user_id LIKE '00000000000000000000USER1%';
DELETE FROM core_auth_user WHERE id LIKE '00000000000000000000USER1%';
```

下次启动 DemoSeeder 会重建。
