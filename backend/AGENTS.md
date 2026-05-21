# Access Matrix — 后端 AI 开发规约

> 配套前端: `../frontend/`（Vue 3 + Vite + Tailwind v4）。本仓是 monorepo，根级跨栈规约见 [../AGENTS.md](../AGENTS.md)。
> 后端默认监听 `:9135`，context-path `/api`，dev profile 强制 `Asia/Tokyo` 时区。

## Project Overview

**Access Matrix** 是平台级账号 + 权限 + 多租户基盘。一个 Spring Boot 4 多模块 Maven 项目，按"系统域"/"业务域"严格分模块：

- **system features** —— 账号 / 角色 / 权限 / 菜单 / 部门 / 操作日志 / 审计 / 强制下线 —— 由 `core-system` 模块提供
- **business features** —— 各业务系统（如 PMS）—— 各自起一个 `business-{module}` 模块，**不允许**侵入 `core-*` 包
- **基础设施** —— 跨域共用的安全 / 缓存 / 持久化 / Web 切面 —— 在 `core-infrastructure`
- **可复用类型** —— Result / 错误码 / 注解 / 上下文 —— 在 `core-common`
- **启动器** —— `main()` + Flyway 迁移 + 全局配置 —— 在 `core-bootstrap`

## Tech Stack

| 类别 | 选型 |
|------|------|
| Java | **25** |
| 框架 | Spring Boot 4.0.6 + Spring Security 6 (OAuth2 Resource Server) |
| ORM | MyBatis-Plus 3.5.16 |
| DB | PostgreSQL |
| 迁移 | Flyway 11（`repair-on-migrate` 通过 `FlywayMigrationStrategy` bean 实现） |
| 缓存 | Caffeine（L1）；Redis（refresh token / lockout / force-logout 状态） |
| 鉴权 | JWT（HS256）+ HttpOnly Cookie refresh token |
| 密码 | BCrypt（cost = 12） |
| 限流 | bucket4j |
| ID | ULID Creator（CHAR(26) PK） |
| 时区 | `Asia/Tokyo` |

## 模块边界（最重要的规则）

```
core-bootstrap        ─┐ 启动 + Flyway + 全局配置 (application*.yml)
                       │
core-system          ─┤ 系统域: auth / rbac / menu / dept / oplog (controller/service/mapper/entity/dto)
business-pms         ─┤ 业务域: PMS (目前为空 package-info.java 占位)
business-{module}    ─┤ 后续业务: 各自一个 maven 模块
                       │
core-infrastructure  ─┤ 横切: security 切面 / 审计 / web filter / 缓存配置 / MybatisPlusConfig
                       │
core-common         ──┘ 纯类型: BusinessException / ErrorCode / JsonResult / PageResult / @OpLog / @RequiresPermission / @DataScope / PermissionMatcher / IdGenerator / RequestContext
```

依赖方向**只能往下**：

```
core-bootstrap → core-system & business-* → core-infrastructure → core-common
```

绝不允许：
- `core-common` 反向依赖任何其它模块
- `core-infrastructure` 依赖 `core-system` 或 `business-*`
- `core-system` 依赖 `business-*`，或反过来
- 跨业务模块互引（`business-pms` 不能依赖 `business-crm`，反之亦然）—— 跨业务协作走事件/接口/HTTP

## 系统文件 vs 业务文件 — 放哪

### 系统功能（账号/RBAC/审计） → `core-system`

```
core-system/src/main/java/com/platform/system/
  auth/
    controller/   AuthController (POST /auth/login, /refresh, /logout)
                  AdminAuthController (POST /admin/auth/unlock, reset-password, force-logout/{id})
    service/      AuthService / LoginAuditService
    mapper/       UserMapper / LoginLogMapper
    entity/       UserEntity / LoginLogEntity
    dto/          LoginRequest / TokenResponse / RefreshRequest / ResetPasswordRequest / UnlockRequest
  rbac/
    controller/
      admin/      RoleAdminController / UserAdminController / MenuAdminController / DeptAdminController
                  PermissionAdminController / OpLogQueryController
      MeMenuController / MePermissionController / ScopeMeController / DeptController
    service/      RoleAdminService / UserAdminService / MenuQueryService / DeptAdminService
                  PermissionQueryService / PermissionCacheService / DataScopeQueryService / OpLogService
    mapper/       RoleMapper / PermissionMapper / MenuMapper / DeptMapper
                  UserRoleMapper / RolePermissionMapper / RoleMenuMapper / RoleDeptMapper
    entity/       RoleEntity / PermissionEntity / MenuEntity / DeptEntity / 链接表 entity / OpLogEntity
    dto/          RoleDto / UserDto / MenuNode / DeptNode / DeptAdminDto / OpLogQuery 等
```

### 业务功能（PMS / CRM / 等）→ `business-{module}`

```
business-pms/src/main/java/com/platform/business/pms/
  {feature}/
    controller/   GET /pms/reservation/list, POST /pms/reservation, ...
    service/
    mapper/
    entity/
    dto/
```

约定：
- 业务接口路径：`/{businessModule}/{feature}/...`（**不要**用 `/admin/` 前缀，那是系统域 RBAC 写接口的命名）
- 业务表名：`{businessModule}_{feature}_{noun}`，例：`pms_reservation`
- 业务字段要参与数据范围：表带 `dept_id` (部门维度) 和/或 `create_user` (个人维度)，Mapper 方法挂 `@DataScope`，Service 调 `DataScopeHelper.apply`（详见 §"数据范围"）

### 横切（多业务公用）→ `core-infrastructure`

```
core-infrastructure/src/main/java/com/platform/core/infrastructure/
  security/
    JwtIssuer / JwtDecoder 配置
    AccountLockoutService / ForceLogoutService / ForceLogoutFilter
    PasswordPolicyService / RefreshTokenStore / RefreshCookieService
    AuthRateLimitFilter / SecurityConfig
    rbac/
      PermissionAspect / PermissionResolver / UserPermissionsLookup
      DataScopeAspect / DataScopeContext / DataScopeResolver / DataScopeHelper / DataScopeDecision / UserDataScopeLookup
  audit/
    OpLogAspect / OpLogRecord / OpLogSink
  web/
    CoreRequestContextFilter（traceId / tenantId / userId 注入 RequestContext + MDC）
  persistence/
    BaseEntity / AuditMetaObjectHandler（create_user / update_time 自动填充）
  config/
    MybatisPlusConfig（TenantLineInnerInterceptor / PaginationInnerInterceptor / OptimisticLockerInnerInterceptor / BlockAttackInnerInterceptor）
    properties/ AppSecurityProperties / AppMybatisProperties
  numbering/ NumberingService（编号生成器，跨业务复用）
```

### 共享 API 类型 → `core-common`

```
core-common/src/main/java/com/platform/core/common/
  audit/        @OpLog 注解
  context/      RequestContext (ThreadLocal: tenantId / userId / username / locale / traceId)
  error/        BusinessException / ErrorCode
  id/           IdGenerator (ULID)
  result/       JsonResult / PageResult
  security/     @RequiresPermission / @DataScope / PermissionMatcher
```

`core-common` **绝对纯净**：不能依赖任何 Spring 上下文 / DB / Redis / Web。

### 启动 + 全局配置 → `core-bootstrap`

```
core-bootstrap/
  src/main/java/com/platform/core/bootstrap/
    CoreApplication.java       @SpringBootApplication，main()
    startup/
      AuthSchemaBootstrap      启动期 sanity check
      LocalAdminSeeder         dev 环境种 admin/admin 用户 (@Profile("local"))
      FlywayRepairConfig       FlywayMigrationStrategy bean，repair()+migrate()
  src/main/resources/
    application.yml            通用配置 (mybatis-plus / management / actuator / springdoc)
    application-local.yml      本地 (security.mode=permit-all, tenant.enabled=false, log expose-error-details=true)
    application-dev.yml        测试环境 (security.mode=jwt, tenant.enabled=true)
    application-prod.yml       生产 (同 dev + Redis SSL)
    application-test.yml       junit 用
    db/migration/V*.sql        Flyway 迁移 (规则见 §"Flyway")
    log4j2-spring.xml          日志配置 (MDC: traceId / tenantId / userId)
```

## Hard Rules

1. **NO business code in core-system** —— `core-system` 只放系统域。新业务必开新模块
2. **NO system code leaks into business modules** —— `business-pms` 不允许定义 user / role / permission 表，使用现有 `core_*` 表
3. **NO cross-business deps** —— business 模块互相不能 import
4. **NO raw SQL outside `@Select` / `@Update` / `@Delete` annotations OR `V*__*.sql` migrations** —— 临时调试除外
5. **NO new tenant-bypassing query without justification** —— 默认所有查询走 MyBatis-Plus 租户拦截器；手写 `@Select` 必须显式带 `tenant_id` 条件（参考 `UserMapper.findByIdentifier`）
6. **NO `@PreAuthorize`** —— 接口鉴权统一用 `@RequiresPermission`（自定义 AOP，可读、支持通配）
7. **NO inline permission checks in controllers** —— 鉴权切面 + `RequestContext` 用户身份；controller 里不要 `if (currentUser.isAdmin())`
8. **NO `BaseMapper.selectList` 不带 `@DataScope`** 跨部门数据查询 —— 任何按用户视角的列表查询都要走 `@DataScope` + `DataScopeHelper.apply`
9. **NO `confirm()` 风格的命令式审批跳过** —— 风险动作（删除 SUPER_ADMIN / 改租户 / 改密码策略）必须走 `core_oplog` 审计 + 二级确认
10. **NO unchecked `selectById` after JWT** in 多租户开启路径 —— 注意 refresh token 路径使用 `findByIdAndTenant`，避免 MyBatis-Plus 租户拦截器把 `X-Tenant-Id` header 误用到 token 持有者
11. **NEVER 改已上线的 `V*__*.sql`** —— 加列/改列必须新建 `V{N+1}__*.sql`。`FlywayRepairConfig` 容忍 checksum 漂移，但 schema 历史可读性仍要靠 append-only 维持

## Flyway 约定

- 迁移文件命名：`V{N}__{snake_case_description}.sql`，版本号连续
- 全部脚本必须**幂等**：
  - 建表：`CREATE TABLE IF NOT EXISTS`
  - 索引：`CREATE [UNIQUE] INDEX IF NOT EXISTS`
  - 约束：包在 `DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = '...') THEN ALTER TABLE ... ADD CONSTRAINT ... END IF; END $$;`
  - 种子：`INSERT ... ON CONFLICT DO NOTHING`
- 软删除：所有业务表带 `mark SMALLINT NOT NULL DEFAULT 1`（1=有效，0=删除）；唯一索引带 `WHERE mark = 1`
- 多租户：所有租户级表带 `tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'`，唯一索引前置 `tenant_id`；非租户表（如 `core_numbering_*`）要加入 `MybatisPlusConfig.TENANT_EXCLUDED_TABLES`
- 审计列：业务表带 `create_user / update_user / create_time / update_time`，由 `AuditMetaObjectHandler` 自动填充
- 链接表（user_role / role_permission 等）FK 用 `ON DELETE RESTRICT`（参考 `V9__core_rbac_fk.sql`）
- 启动期 `FlywayRepairConfig` 会先 `repair()` 再 `migrate()`，吸收开发期 checksum 漂移；**但仍不允许故意修改老 V 文件**

## 安全 & 认证

| 主题 | 实现 | 文件 |
|------|------|------|
| JWT 签发 | HS256，载荷 `sub`(userId) / `tid`(tenant) / `preferred_username` / `scope` / `roles`(JSONB) / `iat` / `exp` | `JwtIssuer` |
| JWT 校验 | Spring Security `oauth2.jwt()` 自动；permit-all profile 走手动 decode | `SecurityConfig` + `PermissionResolver` |
| scope claim | **只用 `*:*`（超管）或 `__compact__`（其它）**，绝不 inline 权限码 → 解析时走 `UserPermissionsLookup`（Caffeine 缓存）实现"权限变更立即生效" | `AuthService.chooseScopeClaim` |
| refresh token | Redis key `auth:refresh:{token}` → value `userId\|tenantId\|issuedAtSec`，TTL 7d；rotate 用 `GETDEL` 原子操作 | `RefreshTokenStore` |
| refresh tenant 解耦 | refresh 路径用 `UserMapper.findByIdAndTenant`（手写 SQL 不被租户拦截器改写） | `AuthService.refresh` |
| force-logout | Redis key `core:auth:logout:{userId}` → epoch sec，TTL 8d（> refresh 7d） | `ForceLogoutService` |
| force-logout 全局 | `ForceLogoutFilter`（OncePerRequestFilter, order = HIGHEST + 30）每个带 JWT 的请求都校验 `iat <= kickOutAt` | `ForceLogoutFilter` + `SecurityConfig` |
| 账号锁定 | Redis key `auth:fail:{tenant}:{id}` + `auth:lock:{tenant}:{id}`，**租户隔离** | `AccountLockoutService` |
| 密码策略 | 长度/字符类 + HIBP 远端检查（可降级 fail-open） | `PasswordPolicyService` |
| 限流 | bucket4j；登录路径前置 | `AuthRateLimitFilter` |

## API 约定

| 项 | 规则 |
|----|------|
| context-path | 全局 `/api` |
| 系统管理写接口 | `/admin/{module}/...`，例：`POST /admin/role`, `PUT /admin/user/{id}/roles` |
| 系统管理读接口 | `/admin/{module}/list` 或 `/admin/{module}/{id}`（受 `@RequiresPermission` 保护） |
| Me-endpoints | `GET /menu/me`、`GET /permission/me`、`GET /scope/me`、`GET /dept/tree`（仅需登录，不需要细粒度权限） |
| 业务接口 | `/{businessModule}/{feature}/...`（**不要**滥用 `/admin/`） |
| 鉴权 | 接口方法挂 `@RequiresPermission("module:action")` 或 `@RequiresPermission(anyOf={...})` |
| 审计 | 写接口挂 `@OpLog(module, action, targetType)`，自动落 `core_oplog` |
| 分页 | `page` (1-based) + `size`（max 500）；返回 `PageResult<T>(records, total, page, limit)` |
| 响应 | 全部包 `JsonResult<T>`：`{ code, msg, data }`；错误用 `BusinessException(ErrorCode.X, msg)` |
| 时间 | `LocalDateTime`，Jackson 配置写 ISO + `Asia/Tokyo` 时区 |

## 多租户

- 开关：`app.mybatis.tenant.enabled`；local profile = false，dev/prod = true
- 解析：
  - 已登录请求：`CoreRequestContextFilter` 从 JWT `tid` claim 取 → 写入 `RequestContext`
  - 未登录请求：fallback 到 `X-Tenant-Id` header，再 fallback `default`
- 拦截器：`TenantLineInnerInterceptor` 自动给 MyBatis-Plus 生成的 SQL 追加 `tenant_id = ?` 条件
- 例外表：`MybatisPlusConfig.TENANT_EXCLUDED_TABLES` —— flyway_*、core_meta、core_numbering_*
- 手写 SQL（`@Select` / `@Update`）**不受拦截器影响**，必须显式带 `tenant_id` 条件
- 跨租户操作（如平台超管）—— 暂未支持，需要时新加一个 `app.mybatis.tenant.bypass-role` 配置 + 切面

## 数据范围（@DataScope）

5 种预设：

| value | 名称 | SQL 条件 |
|------:|------|----------|
| 1 | ALL | 无 |
| 2 | DEPT_AND_SUB | `dept_id IN (本部门子树)` |
| 3 | DEPT | `dept_id = 当前部门` |
| 4 | SELF | `create_user = 当前用户` |
| 5 | CUSTOM | `dept_id IN (角色显式选定部门子树)` |

**使用方式**：
1. 业务表带 `dept_id` 列和/或 `create_user` 列
2. Mapper 方法挂 `@DataScope`（注解本身是信息性的）
3. Service 内调用：
   ```java
   DataScopeDecision dec = dataScopeResolver.currentDecision();
   LambdaQueryWrapper<Foo> w = new LambdaQueryWrapper<>();
   DataScopeHelper.apply(w, dec, Foo::getDeptId, Foo::getCreateUser);
   return mapper.selectPage(page, w);
   ```
4. `DataScopeAspect` 在 Mapper 调用前校验本请求是否调过 `apply()` —— **没调就 local/dev/test 抛 500，prod 写 WARN**

参考：`DataScopeHelper` / `DataScopeContext` / `DataScopeAspect`。

## 审计（@OpLog）

- 写接口（POST/PUT/DELETE）挂 `@OpLog(module="system", action="user.delete", targetType="user")`
- `OpLogAspect` (order=50, 在 `PermissionAspect` order=10 之后) 自动落库 `core_oplog`：操作人 / 时间 / 模块 / 动作 / 目标 ID / 请求 URI / 请求体（密码字段强制脱敏）/ 客户端 IP / UA / 是否成功 / 错误信息 / 耗时毫秒
- 异步写：`@Async`，失败仅 WARN，不阻塞业务
- 登录审计单独走 `LoginAuditService.record(tenantId, ...)`（注意：参数必须显式传 tenantId，因为 worker 线程不继承 ThreadLocal）

## 错误码 & 异常

- 业务异常：`throw new BusinessException(ErrorCode.X, "msg")`
- 全局异常处理器（在 `core-infrastructure.web` 或 `core-common`）把 `BusinessException` 转成 `JsonResult.error(code, msg)`，HTTP 400/401/403
- 不要 `throw new RuntimeException(...)`；不要 catch + 包装 + 抛新
- 校验：`@Valid` + `@NotBlank` / `@Size` / `@Email`；DTO 用 Java record

## 测试

目前无 `src/test`。新加测试约定：
- 单元：`{Module}ServiceTest` 在该模块的 `src/test/java/`，覆盖核心 service
- 集成：`@SpringBootTest` 走 `test` profile + Testcontainers PostgreSQL/Redis
- ArchUnit：在 `core-bootstrap/src/test/java/` 写模块边界守卫（禁止反向依赖、禁止 `business-pms` 用 `core_*` 表的 Mapper 等）

## 命名规范

| 类型 | 规则 | 例 |
|------|------|----|
| 表 | snake_case，前缀 `core_` (系统域) / `{module}_` (业务域) | `core_auth_user`、`pms_reservation` |
| 列 | snake_case | `tenant_id` / `created_at` |
| Java 类 | PascalCase，模块包内不重名 | `UserAdminService` |
| Java 字段 | camelCase | `tenantId` |
| API 路径 | kebab-case 段；动作用 RESTful 动词 + 资源名 | `/admin/user/list`、`/auth/force-logout/{id}` |
| 权限码 | `resource:action`，支持 `*:*` / `resource:*` 通配 | `user:delete`、`auth:unlock`、`*:*` |
| Bean 注入 | 构造器注入（无 `@Autowired` field）；私有 final 字段 | 参考所有 `*Service` |

## Profile 矩阵

| profile | security.mode | tenant | refresh-cookie.secure | debug.expose-error-details |
|---------|---------------|--------|------------------------|----------------------------|
| local   | permit-all    | off    | false                  | true                       |
| dev     | jwt           | on     | true                   | false                      |
| prod    | jwt           | on     | true (Redis SSL)       | false                      |
| test    | (junit)       | -      | -                      | -                          |

---

## Behavioral Guidelines

### 1. Think Before Coding
- 先表态假设，不确定就问
- 多解时把选项摊开，别静悄悄选
- 看到现成简方案就建议
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
- 自己产生的孤儿代码自己清；既有的死代码不主动删

### 4. Goal-Driven Execution
- "加校验" → "为非法输入写测试，跑通"
- "修 bug" → "写一个能复现的测试，再修"
- 多步任务给出可验证步骤计划

---

**这些规约生效的信号**：模块边界整洁、新功能能用现有 Aspect 落地（@RequiresPermission/@OpLog/@DataScope）、Flyway 历史 append-only。
