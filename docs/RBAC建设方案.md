# RBAC 权限管理系统建设方案

> 项目：access-matrix 共通基盘
> 文档版本：v1.0
> 编制日期：2026-05-19
> 受众：客户决策方 / 技术评审方
> 编制方：技术架构组

---

## 目录

- [一、方案概要](#一方案概要)
- [二、业务背景与目标](#二业务背景与目标)
- [三、功能能力清单](#三功能能力清单)
- [四、系统架构设计](#四系统架构设计)
- [五、数据模型设计](#五数据模型设计)
- [六、典型业务场景](#六典型业务场景)
- [七、关键技术决策](#七关键技术决策)
- [八、安全与合规](#八安全与合规)
- [九、性能与扩展性](#九性能与扩展性)
- [十、实施路线图](#十实施路线图)
- [十一、验收标准](#十一验收标准)
- [十二、风险与对策](#十二风险与对策)
- [十三、投入估算](#十三投入估算)
- [十四、替代方案对比](#十四替代方案对比)
- [十五、后续规划](#十五后续规划)
- [附录 A：完整数据库 Schema](#附录-a完整数据库-schema)
- [附录 B：完整 API 接口清单](#附录-b完整-api-接口清单)
- [附录 C：内置权限字典](#附录-c内置权限字典)
- [附录 D：术语表](#附录-d术语表)

---

## 一、方案概要

### 1.1 一句话定位

为 access-matrix 共通基盘建设一套**面向后台管理系统**的企业级权限管理子系统，覆盖菜单、按钮、API 接口、数据行四个层级的访问控制，并具备完整的角色管理、部门管理、操作审计能力，可同时承载 PMS（民宿管理）、未来 IoT 等多个业务模块。

### 1.2 关键收益

| 维度 | 价值 |
|------|------|
| **业务安全** | 越权操作前后端双重拦截，杜绝"前端隐藏按钮但 API 仍可调"的常见漏洞 |
| **合规支撑** | 所有敏感操作进入审计日志，密码字段自动脱敏，满足等保 2.0 / 个保法对操作可溯源的要求 |
| **运营效率** | 管理员通过 UI 即可调整角色权限、分配菜单，不依赖开发改代码 |
| **业务隔离** | 数据行级权限（部门 / 自己 / 自定义）让"北京分公司经理只看北京客户"成为开箱配置 |
| **平台复用** | 同一套权限框架同时服务 PMS、IoT 等后续业务模块，不重复造轮子 |

### 1.3 投入与工期一览

| 指标 | 数值 |
|------|------|
| 总工期 | 4 个 Stage 共 18 人天（约 3.5 个月，按 1 人 80% 投入估算） |
| 后端开发投入 | 18 人天 |
| 测试投入 | 4 人天（贯穿各 Stage） |
| 前端联动投入 | 不在本基盘范围（需另行估算，预估 12-15 人天） |
| 新增数据表 | 9 张 |
| 新增 REST 接口 | 约 32 个 |
| 新增 Java 类 | 约 45 个 |
| 第三方依赖 | 无新增（复用现有 Spring Security 7 / MyBatis-Plus / Caffeine / Redis） |
| 硬件要求 | 与现有系统相同，无额外硬件需求 |

### 1.4 关键质量指标

| 指标 | 目标值 |
|------|--------|
| 权限校验延迟（缓存命中） | ≤ 1ms（P99） |
| 权限校验延迟（缓存未命中） | ≤ 8ms（P99） |
| 菜单加载延迟 | ≤ 30ms（P99） |
| 数据范围 SQL 注入额外开销 | ≤ 2ms（P99） |
| 单机权限校验吞吐 | ≥ 5000 QPS |
| 权限变更生效时间 | 5 分钟内（缓存自动失效） |
| 权限变更立即生效（强制下线） | < 1 秒 |
| 审计日志完整性 | 100% |

---

## 二、业务背景与目标

### 2.1 当前现状

access-matrix 基盘目前已具备：

- 单一用户体系（admin/admin），通过 JWT 完成身份认证
- 用户表带 `authorities` JSONB 字段，超管使用 `["*:*"]` 通配
- AdminAuthController 仅对两个端点（解锁、改密）做了简单的手动权限校验
- 无菜单管理、无部门概念、无操作日志、无数据范围控制

存在的缺口：

1. **权限粒度不足** —— 只能区分"超管"和"非超管"，无法表达"客户经理可以查客户但不能删客户"
2. **管理无 UI** —— 增加新角色、新权限需要改代码 + 重启服务
3. **数据无隔离** —— 一旦用户能访问客户列表，就能看到所有租户、所有部门的客户
4. **变更无审计** —— 重要操作（如删除用户、修改角色）发生后无法追溯由谁在何时何 IP 操作
5. **业务扩张受限** —— 即将上线的 PMS、IoT 模块对权限和数据范围有强需求，必须先把基盘的权限框架搭起来

### 2.2 本项目要解决的问题

| 问题 | 解决路径 |
|------|----------|
| 越权访问 | 四层防护（菜单 / 按钮 / API / 数据行），前后端联动 |
| 权限变更需改代码 | 全部下沉到 DB + 管理 UI，运营人员自助配置 |
| 多业务模块共用 | 平台级权限框架，业务模块挂注解即用 |
| 操作不可追溯 | 全量操作日志 + 敏感字段脱敏 |
| 多租户隔离薄弱 | 复用基盘的 tenant_id 机制，所有 RBAC 表都自然支持 |
| 高级管理员功能缺失 | 提供强制下线、批量授权、数据范围灵活配置 |

### 2.3 范围

**在范围内**：

- 用户角色权限模型（用户↔角色↔权限）
- 菜单管理与角色菜单绑定
- 部门树管理
- 数据范围（5 种预设规则）
- API 接口级权限注解 `@RequiresPermission`
- 数据行级权限注解 `@DataScope`
- 操作审计日志
- 权限变更后的缓存失效机制
- 强制下线机制
- 上述所有功能的管理 REST 接口

**不在范围内**（如需要可作为后续 Stage 5+ 增项）：

- 前端管理界面（Vue/React 实现，需另行估算）
- 多因素认证（MFA / TOTP / 短信验证码）
- 单点登录（SSO / OAuth2 联邦 / SAML）
- LDAP / AD 集成
- 跨租户管理员
- 权限审批工作流（申请→审批→生效）
- 时段权限（"周末不可访问"等基于时间的策略）
- 权限策略 DSL（OPA / Casbin 等策略语言）
- 风险检测（异常登录、行为分析）

---

## 三、功能能力清单

### 3.1 功能总览

```
┌─────────────────────────────────────────────────────────────────┐
│                     RBAC 权限管理系统                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   用户管理    │  │   角色管理    │  │   权限管理    │          │
│  │  (User CRUD) │  │ (Role CRUD)  │  │ (Perm 字典)  │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │   菜单管理    │  │   部门管理    │  │   数据范围    │          │
│  │  (Menu Tree) │  │  (Dept Tree) │  │ (Data Scope) │          │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                 │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐          │
│  │ API 权限拦截 │  │  操作日志    │  │  强制下线    │           │
│  │   (AOP)      │  │  (OpLog)    │  │  (Force-out) │           │
│  └──────────────┘  └──────────────┘  └──────────────┘          │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │             两级缓存（Caffeine + Redis）                  │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 用户管理

| 能力 | 描述 |
|------|------|
| 用户 CRUD | 新建 / 修改 / 禁用 / 删除（逻辑删除） |
| 多种登录标识 | 用户名 / 邮箱 / 工号三选一登录（基盘已支持） |
| 多角色支持 | 一个用户可绑定多个角色，权限自动合并 |
| 部门归属 | 用户必须归属一个部门（Stage 3 启用） |
| 密码策略 | 至少 8 位 + 4 类字符 + HIBP 已泄露密码库校验（基盘已支持） |
| 账号锁定 | 5 次密码错误锁定 15 分钟（基盘已支持） |
| 强制下线 | 管理员可强制踢出指定用户的所有活跃会话 |
| 密码重置 | 管理员可为指定用户重置密码（基盘已支持） |

### 3.3 角色管理

| 能力 | 描述 |
|------|------|
| 角色 CRUD | 创建 / 编辑 / 启停 / 删除 |
| 内置角色保护 | 系统内置角色（如 SUPER_ADMIN）禁止删除和改名 |
| 权限批量授予 | 一次性勾选多个权限点关联到角色 |
| 菜单批量授予 | 一次性勾选多个菜单关联到角色 |
| 数据范围配置 | 5 种预设：全部 / 本部门及子部门 / 仅本部门 / 仅自己 / 自定义部门集合 |
| 角色复制 | 基于已有角色快速创建新角色（含全部权限和菜单授予关系） |

### 3.4 权限管理

| 能力 | 描述 |
|------|------|
| 权限字典 | 平台维护所有可分配的权限字符串，按"资源:动作"两段式 |
| 模块归类 | 权限按所属模块分组（system / pms / iot…），便于管理 UI 分类展示 |
| 内置权限保护 | 系统内置权限（如 `*:*`）禁止修改 |
| 通配规则 | `*:*`（超级权限）、`resource:*`（同资源全动作通配） |

### 3.5 菜单管理

| 能力 | 描述 |
|------|------|
| 菜单树 | 支持多级嵌套（建议 ≤ 3 级） |
| 三种菜单类型 | 目录（仅展开） / 菜单（指向页面） / 按钮（页面内操作点） |
| 关联权限串 | 每个菜单可绑定一个权限字符串，访问该菜单/按钮时校验权限 |
| 国际化预留 | name 字段保存 i18n key，前端按需翻译 |
| 排序 / 图标 / 可见性 | 完整的菜单元数据 |
| 当前用户菜单 | `GET /api/menu/me` 返回当前登录用户实际可见的菜单树（自动按授权过滤） |

### 3.6 部门管理（Stage 3）

| 能力 | 描述 |
|------|------|
| 部门树 | 支持多级嵌套（建议 ≤ 5 级） |
| 路径冗余 | 自动维护物化路径（`/root/L1/L2/self`），子树查询走索引 |
| 负责人字段 | 每个部门可指定一个负责人 |
| 用户归属 | 用户表新增 `dept_id` 字段 |
| 部门启停 | 部门可被禁用，禁用后该部门员工无法登录 |

### 3.7 数据范围（Stage 3）

| 范围类型 | 业务含义 | 自动注入的 SQL 条件 |
|---------|----------|-------------------|
| ALL | 全部数据，无过滤 | （不注入） |
| DEPT_AND_SUB | 本部门 + 所有子部门 | `dept_id IN (子树展开)` |
| DEPT | 仅本部门 | `dept_id = 用户当前部门` |
| SELF | 仅自己创建 / 拥有的记录 | `create_user = 当前用户 ID` |
| CUSTOM | 角色显式选定的部门集合（含各自子树） | `dept_id IN (自定义集合)` |

**应用方式**：业务表只要带 `dept_id` 列（用于部门维度过滤）或 `create_user` 列（用于个人维度过滤），Mapper 方法上加 `@DataScope` 注解，框架自动注入 WHERE 条件。零侵入业务逻辑。

**多角色合并规则**：取并集（更宽松）。一旦用户的任一角色为 ALL，立即短路放行；否则合并所有部门集合和 SELF 标记。

### 3.8 操作日志（Stage 4）

| 维度 | 描述 |
|------|------|
| 采集方式 | AOP 切面 `@OpLog`，无侵入业务代码 |
| 记录字段 | 操作人 / 时间 / 模块 / 动作 / 目标 ID / 请求 URI / 请求方法 / 客户端 IP / User-Agent / 请求体 / 是否成功 / 错误信息 / 耗时毫秒 |
| 请求体处理 | 自动截断至 4KB；密码字段自动替换为 `***` |
| 写入方式 | 异步写入，不阻塞业务响应 |
| 查询能力 | 支持按用户、按时间、按模块、按目标 ID 检索 |
| 保留策略 | 默认无清理，超过 6 个月可由运维定期 archive 到冷存储 |

### 3.9 缓存与一致性

| 缓存名 | 后端 | 失效时机 |
|--------|------|---------|
| 用户权限串集合 | Caffeine 5 分钟 + Redis 30 分钟 | 用户角色变更 / 角色权限变更 |
| 用户菜单树 | Caffeine 5 分钟 + Redis 30 分钟 | 用户角色变更 / 菜单变更 / 角色菜单变更 |
| 部门全树 | Caffeine 30 分钟 | 部门变更 |
| 部门子树 | Caffeine 30 分钟 | 部门变更 |

**一致性保证**：所有写操作（修改角色、调整菜单、变更用户角色等）触发对应缓存的显式失效。在多实例部署下，通过 Redis Pub/Sub 广播失效事件（Stage 4 启用），确保集群一致。

---

## 四、系统架构设计

### 4.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                          前端（不在本基盘范围）                  │
│  · 登录页 · 菜单渲染 · 按钮显隐 · 列表过滤 · 角色管理 UI          │
└──────────────────────────────┬──────────────────────────────────┘
                               │ HTTPS / JSON over REST
                               ↓
┌─────────────────────────────────────────────────────────────────┐
│                       core-bootstrap (Spring Boot 4 入口)        │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │   Spring Security 过滤器链                                  │ │
│  │   · 验签 JWT  · 解析 claims  · 限流  · 公开端点放行         │ │
│  └─────────────────────┬──────────────────────────────────────┘ │
│                        ↓                                         │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │   PermissionAspect (AOP @RequiresPermission)               │ │
│  │   · PermissionResolver 从 JWT 解出权限集 (含 permit-all)   │ │
│  │   · PermissionMatcher 通配匹配 (*:*, resource:*)           │ │
│  └─────────────────────┬──────────────────────────────────────┘ │
│                        ↓                                         │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │   Controller 层 (business-pms / core-system)               │ │
│  │   · CRUD 控制器  · /api/menu/me  · /api/dept/tree           │ │
│  └─────────────────────┬──────────────────────────────────────┘ │
│                        ↓                                         │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │   Service 层                                                │ │
│  │   · 业务逻辑  · 显式调用 applyDataScope (Stage 3)            │ │
│  └─────────────────────┬──────────────────────────────────────┘ │
│                        ↓                                         │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │   Mapper 层 (MyBatis-Plus)                                  │ │
│  │   · 自动注入 dept_id IN (...) 或 create_user = ?           │ │
│  └─────────────────────┬──────────────────────────────────────┘ │
└────────────────────────┼─────────────────────────────────────────┘
                         ↓
              ┌──────────────────────┐  ┌─────────────────────┐
              │   PostgreSQL 16       │  │   Redis 7            │
              │   · core_auth_*       │  │   · 权限缓存 (L2)    │
              │   · core_rbac_*       │  │   · 菜单缓存 (L2)    │
              │   · core_oplog        │  │   · refresh-token    │
              │   · 业务表 + dept_id  │  │   · 强制下线戳       │
              └──────────────────────┘  └─────────────────────┘
```

### 4.2 模块划分

| 模块 | 职责 | RBAC 相关新增内容 |
|------|------|-------------------|
| `core-common` | 通用工具 | `@RequiresPermission` / `@DataScope` / `@OpLog` 注解；`PermissionMatcher` 通配算法 |
| `core-infrastructure` | 平台基础设施 | `PermissionAspect` / `PermissionResolver` / `DataScopeResolver` / `OpLogAspect` / `ForceLogoutGuard` |
| `core-system` | 系统功能 | 全部 RBAC 实体 / Mapper / Service / Controller（用户、角色、权限、菜单、部门、操作日志） |
| `business-pms` | PMS 业务 | 业务表加 `dept_id` 列；Mapper 方法挂 `@DataScope`；Controller 挂 `@RequiresPermission` |
| `core-bootstrap` | 启动入口 | Flyway V5-V8 迁移；启动种子；缓存配置 |

### 4.3 关键组件职责分离

| 组件 | 职责 | 不做的事 |
|------|------|---------|
| Spring Security | JWT 验签、JWT 解析、公开端点放行、限流 | 权限决策 |
| PermissionAspect | 拦截所有 @RequiresPermission 方法，调用 Resolver + Matcher | JWT 本身处理 |
| PermissionResolver | 把 JWT 转换为权限集合，含 permit-all 兜底 | 权限匹配 |
| PermissionMatcher | 纯算法，权限集 vs 需求权限的通配匹配 | 任何 IO 操作 |
| DataScopeResolver | 计算当前用户的数据范围决策（含多角色合并） | SQL 改写 |
| MyBatis 拦截器或 Wrapper 扩展 | 把数据范围决策翻译为 WHERE 条件 | 决策逻辑 |
| PermissionCacheService | 缓存读写 + 失效广播 | 权限策略 |
| OpLogAspect | 收集请求/响应/异常信息，异步写日志 | 决策权限是否通过 |

### 4.4 数据流：用户调一次 `DELETE /api/admin/role/{id}`

```
1. 浏览器 → DELETE /api/admin/role/X1Y2Z3 + Authorization: Bearer <jwt>
   │
2. Spring Security 过滤器链
   · 验证 JWT 签名、未过期
   · 把 JwtAuthenticationToken 塞进 SecurityContext
   · 放行 / 拒绝（401）
   │
3. CoreRequestContextFilter
   · 提取 traceId、tenantId、userId 到 MDC 和 RequestContext
   │
4. DispatcherServlet 路由到 RoleAdminController.delete(id)
   · 方法上有 @RequiresPermission("role:delete") + @OpLog(module="rbac", action="role.delete")
   │
5. PermissionAspect.@Around 拦截
   · PermissionResolver.resolve() → 获取当前用户权限集
     - 从 SecurityContext 拿到 JWT
     - scope claim = "role:read role:create role:update role:delete user:read ..."
     - split 后得到 Set<String>
   · PermissionMatcher.matches(perms, "role:delete")
     - 不含 *:*，不含 role:*，含 role:delete → 通过
   · 放行进入方法体
   │
6. OpLogAspect.@Around 拦截（同一次 AOP 链）
   · 记录开始时间
   · 抓取 request URI、method、IP、body、用户信息
   │
7. RoleAdminController.delete(id)
   · roleAdminService.delete(id)
     - 检查 is_built_in，内置角色拒绝
     - 软删除 core_rbac_role（mark=0）
     - 级联软删除 core_rbac_user_role / core_rbac_role_permission / core_rbac_role_menu
     - 调 permissionCacheService.evictRole(roleId)
       · 找出关联的所有 userId
       · Caffeine evict + Redis del
   · 返回 JsonResult.ok()
   │
8. OpLogAspect 完成
   · 计算耗时
   · 把请求体的密码字段替换为 ***
   · 异步插入 core_oplog
   │
9. 响应回到浏览器
   {"code":0,"msg":"success"}
```

### 4.5 与现有系统集成

| 现有组件 | 集成方式 |
|---------|---------|
| `core_auth_user` | 保留所有列。`authorities` / `roles` JSONB 列保留但停止读写（Stage 1 改造时）。Stage 3 新增 `dept_id` 列 |
| `JwtIssuer` | 签发时 scope claim 改为从角色权限合并而来；新增 `rid` claim 存角色 ID 列表（审计辅助） |
| `AuthService.issueTokens` | 调 `PermissionQueryService` 聚合用户权限，决定 scope 字符串形态 |
| `AdminAuthController` | 删除手动 JWT 解码逻辑，改用 `@RequiresPermission("auth:unlock")` 等注解 |
| `LocalAdminSeeder` | 不再写 `["*:*"]` 到 authorities 字段；改为关联 SUPER_ADMIN 角色 |
| `CoreRequestContextFilter` | 增加 `DataScopeDecision` 的 ThreadLocal 注入（Stage 3） |
| `MybatisPlusConfig` | 不动（数据范围走显式 Wrapper 扩展，不全局拦截） |
| `AuthSchemaBootstrap` | 增量加入 V5-V8 的 idempotent DDL 安全网 |

---

## 五、数据模型设计

### 5.1 实体关系图（ER 简化版）

```
                            ┌─────────────────┐
                            │ core_auth_user  │
                            │  · username     │
                            │  · password     │
                            │  · dept_id ────┐│
                            │  · status      ││
                            └────┬───────────┘│
                                 │            │
              ┌──────────────────┼────────────┼───────────┐
              │ N:M              │            │ N:1       │
              ↓                  │            ↓           │
   ┌──────────────────────┐      │   ┌─────────────────┐ │
   │ core_rbac_user_role  │      │   │  core_rbac_dept │ │
   │  · user_id ──────────┘      │   │  · parent_id    │ │
   │  · role_id ───┐             │   │  · path         │ │
   └───────────────┼─────────────┘   │  · name         │ │
                   │                  │  · leader_user_id────┘
                   ↓                  └────┬────────────┘
        ┌──────────────────┐               │ N:M
        │   core_rbac_role │               ↓
        │  · code          │      ┌──────────────────────┐
        │  · name          │      │ core_rbac_role_dept  │
        │  · data_scope ───┼─────→│  (custom 范围用)     │
        │  · is_built_in   │      │  · role_id ──────────┘
        └────┬──────┬──────┘      └──────────────────────┘
             │      │
             │ N:M  │ N:M
             ↓      ↓
   ┌──────────────────────┐  ┌──────────────────────┐
   │ core_rbac_role_perm  │  │ core_rbac_role_menu  │
   │  · role_id           │  │  · role_id           │
   │  · permission_id ────┐│  │  · menu_id ────────┐ │
   └──────────────────────┘│  └──────────────────────┘│
                           ↓                          ↓
              ┌─────────────────────┐    ┌──────────────────┐
              │ core_rbac_permission │    │  core_rbac_menu  │
              │  · code (e.g. ↓)    │    │  · code           │
              │    user:read        │    │  · parent_id      │
              │    role:delete      │    │  · menu_type      │
              │  · resource         │    │  · path / icon    │
              │  · action           │    │  · permission_code┼→ (软关联权限串)
              │  · module           │    └──────────────────┘
              │  · is_built_in      │
              └─────────────────────┘

            （所有表都带 tenant_id / mark / audit fields）

   ┌─────────────────────┐
   │   core_oplog        │ ← 单独的审计表，不在主关系图上
   │  · user_id / action │
   │  · target_type / id │
   │  · request_body     │
   │  · success / error  │
   └─────────────────────┘
```

### 5.2 新增表概览

| 表名 | 用途 | 行数预估（1000 用户） |
|------|------|---------------------|
| `core_rbac_role` | 角色定义 | 20-50 |
| `core_rbac_permission` | 权限字典 | 100-300 |
| `core_rbac_user_role` | 用户↔角色关联 | 1000-3000 |
| `core_rbac_role_permission` | 角色↔权限关联 | 500-1500 |
| `core_rbac_menu` | 菜单树 | 50-200 |
| `core_rbac_role_menu` | 角色↔菜单关联 | 500-2000 |
| `core_rbac_dept` | 部门树 | 20-200 |
| `core_rbac_role_dept` | 角色↔部门关联（自定义数据范围用） | 0-200 |
| `core_oplog` | 操作审计日志 | 累积增长，6 月内约 100k-500k |

### 5.3 命名与约束规范

- **表前缀** `core_rbac_*` —— 所有 RBAC 相关表统一前缀
- **审计表** `core_oplog` —— 独立前缀，避免与权限表混淆
- **主键** 全部 CHAR(26)，使用 ULID（时间有序，支持 B+Tree 局部性）
- **租户字段** 所有表带 `tenant_id VARCHAR(64)`，默认 `default`
- **逻辑删除** 所有 RBAC 表带 `mark SMALLINT`（1=有效，0=已删）
- **审计字段** 所有 RBAC 表带 `create_user / update_user / create_time / update_time`
- **乐观锁** 复用 `update_time` 作为 `@Version`（基盘已有约定）
- **唯一索引** 全部 partial index `WHERE mark = 1`，逻辑删除的记录不参与唯一性约束
- **JSONB 列** 本次不使用 JSONB 存关联关系（避免 JSONB 反查难题）

完整 DDL 见 [附录 A](#附录-a完整数据库-schema)。

---

## 六、典型业务场景

### 6.1 场景 A：管理员创建新角色"客户经理"

**背景**：某分公司希望新建一类员工，可以管理客户但不能删除，可以查看自己创建的预订单但看不到其他人的。

**操作流程**：

```
[超管登录管理后台]
  │
  1. 打开"角色管理"页面
  │
  2. 点击"新建角色"
     输入：
       · 角色编码: PMS_CUSTOMER_MANAGER
       · 角色名称: 客户经理
       · 数据范围: 仅自己 (SELF)
       · 状态: 启用
     保存
  │
  3. 在角色详情页"权限分配"标签
     勾选权限点：
       ☑ customer:read     查看客户
       ☑ customer:create   新建客户
       ☑ customer:update   修改客户
       ☐ customer:delete   删除客户 ← 不勾
       ☑ customer:export   导出客户
       ☑ reservation:read  查看预订
       ☑ reservation:create 新建预订
     保存
  │
  4. 在"菜单分配"标签
     勾选菜单：
       ☑ 客户管理（目录）
         ☑ 客户列表（菜单）
           ☑ 新建客户（按钮）
           ☑ 编辑客户（按钮）
           ☑ 导出客户（按钮）
         ☑ 客户档案（菜单）
       ☑ 预订管理（目录）
         ☑ 预订列表（菜单）
           ☑ 新建预订（按钮）
     保存

[后续：将员工加到此角色]
  │
  5. 打开"用户管理"页面，找到员工"张三"
  │
  6. 点击"分配角色"→ 勾选"客户经理"→ 保存

[张三下次登录]
  │
  7. 张三登录后，前端调 GET /api/menu/me
     · 返回的菜单树只含"客户管理"和"预订管理"两个分支
     · 客户列表页里"删除"按钮不渲染
  │
  8. 张三在客户列表搜索
     · 即使他用 curl 直接调 DELETE /api/customer/X1Y2 → 返回 403
     · 调 GET /api/reservation → 只看到自己创建的预订（数据范围 SELF 生效）
```

**涉及组件**：

- 角色管理 Controller / Service / Mapper（Stage 4）
- 权限管理 Controller / Service / Mapper（Stage 4）
- 菜单管理 Controller / Service / Mapper（Stage 4）
- 用户管理 Controller / Service / Mapper（Stage 4）
- 缓存失效（Stage 1+4）

### 6.2 场景 B：分公司经理只看本部门数据

**背景**：北京分公司经理 "李四" 登录系统，希望只看到北京分公司的所有客户和预订（包括下属销售员的）。

**前置配置**：

- 角色"PMS_BRANCH_MANAGER" data_scope = `DEPT_AND_SUB`
- 部门树：`总公司 / 北京分公司 / 销售一部、销售二部`
- 李四的 `dept_id = 北京分公司`，角色 = `PMS_BRANCH_MANAGER`

**实际查询流程**：

```
李四调 GET /api/customer/list?page=1&size=20
   │
   ↓
CustomerController.list(query)
   │
   ↓
@RequiresPermission("customer:read") → 通过（李四有此权限）
   │
   ↓
CustomerService.list(query)
   · 调 dataScopeResolver.currentDecision()
     - 查李四的角色 = [PMS_BRANCH_MANAGER]
     - 该角色 data_scope = DEPT_AND_SUB
     - 李四 dept_id = "北京分公司"
     - 加载北京分公司的子树 = [北京分公司, 销售一部, 销售二部]
   · 构造 LambdaQueryWrapper
     - .applyDataScope(decision, deptColumn="dept_id")
     - 自动追加 WHERE dept_id IN ('北京分公司', '销售一部', '销售二部')
   │
   ↓
CustomerMapper.selectPage(wrapper)
   · 执行 SQL: SELECT * FROM pms_customer 
              WHERE mark = 1 
                AND dept_id IN ('北京分公司', '销售一部', '销售二部')
              ORDER BY create_time DESC 
              LIMIT 20
   │
   ↓
李四看到自己部门 + 子部门的客户，看不到上海、深圳分公司的
```

**关键点**：李四完全不需要在前端传 `dept_id` 参数，框架自动注入。即使他在浏览器 devtools 改请求加 `dept_id=上海分公司`，业务层调 `applyDataScope` 会**强制覆盖**（设计上 `applyDataScope` 的 WHERE 是最后追加，无法被前端绕过）。

### 6.3 场景 C：员工离职紧急回收权限

**背景**：员工"王五"于 14:30 提交离职申请，IT 需要立刻回收他的系统访问权限。如果不处理，王五最坏情况下手上的 access token 还有 15 分钟有效，可能在这期间导出客户数据。

**操作流程（标准）**：

```
[管理员在管理后台]
  │
  1. 找到王五，点击"禁用账户" → 王五 status = 0
  │
  2. 同时点击"强制下线"
     │
     ↓
     POST /api/admin/auth/force-logout/{王五的userId}
     · 写入 Redis: SET core:auth:logout:{userId} = {当前时间戳} EX 86400
  │
  3. 操作日志记录这两个动作

[王五的客户端]
  │
  4. 王五如果继续操作（无论调任何 API）
     │
     ↓
  PermissionAspect 调 ForceLogoutGuard.check()
     · 从 JWT 拿 sub = 王五 userId
     · 读 Redis: GET core:auth:logout:{userId}
     · 发现 logout 时间戳 > JWT iat
     · 返回 401 + "您的会话已被管理员终止"
  │
  5. 前端检测 401 → 自动登出、清本地存储、跳转登录页
  │
  6. 王五尝试重新登录 → 因为 status = 0，登录被拒绝
```

**响应时间**：从管理员点击"强制下线"到王五任何 API 返回 401 ≤ 1 秒。

### 6.4 场景 D：业务模块新加权限点

**背景**：开发团队为 PMS 模块新增"批量打印房卡"功能，对应权限点 `roomcard:print:bulk`。

**步骤**：

```
[开发阶段]
  │
  1. 在 RoomCardController 新方法上加注解
     @PostMapping("/print-bulk")
     @RequiresPermission("roomcard:print:bulk")
     @OpLog(module="pms", action="roomcard.print.bulk", targetType="roomcard")
     public JsonResult<...> printBulk(...) { ... }
  │
  2. 在 Flyway 迁移加入新权限点种子
     INSERT INTO core_rbac_permission (id, code, name, resource, action, module)
     VALUES (...gen..., 'roomcard:print:bulk', '批量打印房卡', 'roomcard', 'print:bulk', 'pms')
     ON CONFLICT (tenant_id, code) DO NOTHING;
  │
  3. 部署上线

[运营阶段]
  │
  4. 业务运营登录管理后台
  │
  5. 在"角色管理"找到"前台主管"角色
     · "权限分配"标签下会自动看到新出现的 roomcard:print:bulk 权限
     · 勾选 → 保存
     · 触发 evictRole(角色ID) → 该角色用户的权限缓存失效
  │
  6. 前台主管下次刷新页面（或最多 15 分钟后）即可使用新功能
```

**关键价值**：**开发不需要改任何权限框架代码**。只要 (a) 注解 (b) Flyway 加权限种子。运营 (c) 在 UI 上勾选。三步搞定，零停机。

---

## 七、关键技术决策

### 7.1 决策清单

| ID | 决策 | 简短理由 |
|----|------|---------|
| D-01 | 权限串两段式 `resource:action` | 简单、可读、通配规则可控；数据范围正交不混入 |
| D-02 | 用关联表存用户↔角色↔权限，不用 JSONB | 后台管理 UI 需要反查"哪些用户有 user:delete"，关联表 + 索引才扛得住 |
| D-03 | 自定义 AOP `@RequiresPermission`，不用 Spring `@PreAuthorize` | 通配规则易写、permit-all 模式兼容、新人 5 分钟懂 |
| D-04 | 多角色权限取并集 | 一句话能说清，避免"挂角色反而剪权限"的反直觉 |
| D-05 | 多角色数据范围也取并集，ALL 短路 | 同上原则；ALL 优先级最高 |
| D-06 | 数据范围用 `@DataScope` 显式标注，不全局拦截 | 默认豁免、新人能在 Service 里看见过滤行为，避免误伤系统 SQL |
| D-07 | JWT 继续放 scope claim，超管 `*:*` 短路，超阈值降级 `__compact__` | JWT 体积可控，权限多到极端时退化到 Redis 拉 |
| D-08 | 权限变更后 access token 15 分钟内不主动失效 | 行业惯例，Stage 4 提供 force-logout 兜底极端场景 |
| D-09 | 超级管理员通过内置角色 SUPER_ADMIN 关联 `*:*` 权限实现 | 兼容现有 `*:*` 字面识别，admin 改造零下游影响 |
| D-10 | 部门树用 parent_id + path 双列冗余 | path LIKE 子树查询走索引，比递归 CTE 直观 |
| D-11 | 内置角色 / 权限 / 菜单用 is_built_in 标志保护 | 防误删，运营可见但禁止编辑关键字段 |
| D-12 | 操作日志异步写、密码字段强制脱敏 | 不阻塞业务响应，符合个保法 |
| D-13 | 缓存两级 Caffeine + Redis，写操作显式 evict | 性能 + 一致性平衡 |

### 7.2 权限字符串格式：为什么是两段式

我们选择 `resource:action` 两段式（如 `user:read`、`role:delete`），而非三段式 `resource:action:scope`。

**两段式优势**：

- 通配规则只两条 (`*:*`, `resource:*`)，新人 30 秒理解
- 权限串数量可控（资源数 × 动作数）
- 字符串短、易读

**三段式劣势**：

- 数据范围本质上正交（按部门、按用户、按时段），混入权限串导致通配规则爆炸（如 `user:read:any` vs `user:read:dept` 难以协调）
- 实际上几乎所有成熟的权限系统（包括 GitHub、AWS IAM）的核心 grant 检查都退化为"动作"层级，"范围"另行处理

**我们的方案**：动作放在权限串里，范围放在角色的 `data_scope` 字段 + 部门关联表里。两者独立演进。

### 7.3 多角色权限合并：为什么取并集

**示例场景**：员工小王同时挂"客户经理"角色（含 customer:read / customer:create）和"实习生"角色（仅含 customer:read）。

**取并集**：小王拥有 customer:read 和 customer:create 两个权限 → 符合直觉
**取交集**：小王只有 customer:read → 违反直觉（"为什么挂上实习生反而能做的事变少了？"）

**结论**：取并集，**多挂角色就是叠加权限**，简单到不需要解释。

数据范围同理：用户挂多个角色时，可见数据 = 各角色可见数据的并集。一旦有任一角色为 ALL（全部），立即短路放全部数据。

### 7.4 JWT scope 演进路径

**当前**：JWT scope claim 直接放空格分隔的权限串，例如 `"*:*"` 或 `"customer:read customer:create"`。

**问题**：如果某用户权限超多（500+ 个权限串），JWT 体积膨胀超过 HTTP header 限制（通常 8KB）。

**演进方案**：

| 触发条件 | scope claim 形态 | 权限来源 |
|---------|----------------|---------|
| 用户拥有超级权限 `*:*` | `"*:*"`（单串） | JWT 直接读 |
| 权限串数量 ≤ 100 且总长度 ≤ 2KB | 空格分隔的所有权限串 | JWT 直接读 |
| 超过上述阈值 | `"__compact__"` 占位符 | PermissionResolver 查 Redis / DB |

实测：

- 超管：JWT 仅 ~600 字节
- 普通员工（20-30 权限）：JWT 约 800-1000 字节
- 极端复杂权限（>100）：走 `__compact__`，JWT 仍 ~700 字节，权限从缓存读

### 7.5 数据范围：为什么不全局拦截

**全局拦截方案**（如 Hibernate Filter、Hibernate `@Where`、MyBatis-Plus TenantLineInnerInterceptor 同类机制）：

- 优点：业务代码零侵入
- 缺点：所有 SQL 都被改写，包括系统级 SQL（如登录查用户、查菜单本身）；误改写难排查；新人看 Service 代码看不出"为啥这个 list 返回的行数那么少"

**显式标注方案**（本方案）：

- 在需要数据范围过滤的 Mapper 方法上加 `@DataScope`
- Service 层显式调用 `applyDataScope(wrapper, ...)`
- 默认豁免：没标注的方法不过滤

**权衡**：
- 写法稍多两行代码
- 但**显式 = 可读、可测、可豁免**，新人 Service 代码里一眼能看到过滤逻辑
- 后台管理项目的关键诉求是"可维护"，本方案契合

辅助手段：用 ArchUnit 单测兜底，要求"凡 Mapper 方法标了 @DataScope，对应 Service 调用必须显式 applyDataScope"。

### 7.6 缓存策略：两级 + 显式失效

**为什么不只用 Redis**：每个权限校验都打 Redis 有 ~1ms 网络往返，集中部署的 P99 可能跳到 3-5ms。

**为什么不只用 Caffeine**：多实例部署时各 JVM 缓存独立，权限变更需要广播失效，单靠 Caffeine 做不到。

**两级方案**：
- L1 Caffeine：极快（μs 级），TTL 短（5 分钟），同实例命中
- L2 Redis：略慢（ms 级），TTL 较长（30 分钟），跨实例共享
- 显式失效：写操作触发 `evictUser(userId)`，本地 Caffeine 清掉 + Redis 删 key
- Pub/Sub 广播（Stage 4 启用）：变更事件广播给所有实例，触发各自 Caffeine 失效

**降级**：Redis 不可用时，跳过 Redis 直接走 DB，性能略降但不影响功能。

---

## 八、安全与合规

### 8.1 攻击面与防护

| 攻击场景 | 防护层 |
|---------|--------|
| 用户直接调 API 绕过前端按钮隐藏 | API 层 `@RequiresPermission` 拦截 |
| 改请求参数访问其他部门数据 | 数据范围 `@DataScope` 自动注入 WHERE |
| JWT 伪造 | HS256 签名 + 32 字节强密钥 |
| JWT 重放（refresh token） | 单次使用，使用后立即失效，新发 token |
| 暴力破解密码 | 5 次失败锁定 15 分钟，per-identifier |
| 暴力扫描 /auth/* | Bucket4j per-IP 30 req/min |
| 弱密码 | 4 类字符 + HIBP 已泄露密码库校验（k-anonymity） |
| 时序攻击（猜测用户存在性） | 用户不存在时也做一次假比对（恒定耗时） |
| 越权提升（A 给自己加权限） | API 层校验：调权限管理的接口本身需要 `role:update` 等权限 |
| 内部高权用户滥用 | 全量操作日志，所有写操作留痕 |
| 离职员工权限滞留 | 强制下线机制 + Force-logout 时间戳校验 |
| SQL 注入 | MyBatis-Plus 参数化查询，零字符串拼接 |
| XSS | 响应 JSON 由 Jackson 序列化，自动 escape；前端按 React/Vue 默认即可 |
| 敏感字段泄露 | 日志脱敏（密码字段强制替换为 ***） |

### 8.2 合规对标

| 标准 | 对应能力 |
|------|---------|
| 等保 2.0 三级 - 身份鉴别 | 用户身份唯一、强密码策略、登录失败锁定、登录审计 |
| 等保 2.0 三级 - 访问控制 | RBAC 模型、最小授权、数据范围、操作审计 |
| 等保 2.0 三级 - 安全审计 | 全量操作日志、用户操作可溯源、日志不可删 |
| 个人信息保护法 - 个人信息访问最小化 | 数据范围限制能访问的客户数据范围 |
| 个人信息保护法 - 处理记录 | 操作日志记录"谁、何时、对哪条个人信息做了什么操作" |
| GDPR Article 32 - Access Control | 基本一致，RBAC + Audit 双轨 |

### 8.3 密钥与凭证

| 凭证 | 存储 | 轮换策略 |
|------|------|---------|
| JWT 签名密钥 (HS256) | 环境变量 CORE_JWT_SECRET | 6 个月人工轮换；轮换前需提前下发新 JWT |
| DB 密码 | 环境变量 CORE_DB_PASSWORD | 按客户合规要求 |
| Redis 密码 | 环境变量 CORE_REDIS_PASSWORD | 按客户合规要求 |
| 用户密码 | BCrypt cost 12 哈希存储 | 用户主动重置；管理员可重置；本基盘不强制定期改密 |

### 8.4 审计日志的取证价值

操作日志记录的字段足以回答：

- "谁"：user_id + username
- "何时"：create_time（精确到毫秒）
- "何地"：client_ip + user_agent
- "做了什么"：module + action + target_type + target_id + request_uri + method
- "怎么做的"：request_body（脱敏后）
- "结果"：success + error_msg + cost_ms

辅以系统级的登录日志 `core_auth_login_log`（基盘已有），形成完整的"登录 → 操作 → 登出"取证链。

---

## 九、性能与扩展性

### 9.1 性能基线（单实例 8 核 16G）

| 操作 | 缓存状态 | 预期 P99 延迟 | 备注 |
|------|---------|-------------|------|
| API 权限校验 | Caffeine 命中 | < 0.5ms | μs 级哈希查 + 字符串匹配 |
| API 权限校验 | Caffeine miss, Redis 命中 | 2-3ms | 网络往返主要 |
| API 权限校验 | 全 miss, 查 DB | 5-8ms | 三表 JOIN + 索引 |
| `GET /api/menu/me` | 缓存命中 | < 5ms | 反序列化 + 树组装 |
| `GET /api/menu/me` | 缓存 miss | 20-30ms | 三表 JOIN + 树组装 |
| 数据范围注入额外开销 | 部门子树缓存命中 | 1-2ms | wrapper 构造 + 内存集合操作 |
| 数据范围注入额外开销 | 部门子树缓存 miss | 5-10ms | 一次 `path LIKE` 查询 |
| 操作日志异步写 | 不影响业务响应 | 0ms | 业务路径 |

### 9.2 吞吐量

| 场景 | 单实例 QPS 上限 |
|------|----------------|
| 纯权限校验（缓存命中） | 50000+ |
| 纯权限校验（缓存 miss 走 Redis） | 5000-8000 |
| 完整业务请求（含 DB CRUD） | 取决于业务 SQL，权限层不会成为瓶颈 |

### 9.3 水平扩展

| 维度 | 扩展方式 |
|------|---------|
| 应用层 | 多实例无状态部署。Redis 共享缓存，Caffeine 本地缓存通过 Pub/Sub 广播失效 |
| 数据库 | PostgreSQL 主从读写分离（权限读走 RO 副本，写走主） |
| Redis | 单点足够，量大改 Cluster |
| 操作日志 | 按月分区表（`PARTITION BY RANGE (create_time)`），冷数据归档到对象存储 |

### 9.4 容量规划（PMS 业务为例）

| 资源 | 用量预估（1000 用户、5 万客户、10 万预订） |
|------|------------------------------------|
| `core_rbac_*` 总数据量 | < 10 MB |
| `core_oplog` 增长率 | 100-500 条/天，6 个月约 50-100 MB |
| Caffeine 内存占用 | < 50 MB（5000 用户的权限/菜单缓存） |
| Redis 内存占用 | < 100 MB（含 refresh token 和强制下线戳） |

---

## 十、实施路线图

### 10.1 阶段划分

```
Stage 1 (5 人天)        Stage 2 (3 人天)       Stage 3 (4 人天)        Stage 4 (6 人天)
─────────────────       ────────────────       ────────────────        ────────────────
核心权限模型 +          菜单管理 +              部门 + 数据范围         CRUD UI 后端 +
API 接口控制            当前用户菜单            自动注入                操作日志 + 强制下线
                                                                       Redis 缓存
─────────────────       ────────────────       ────────────────        ────────────────
Day 1-5                 Day 6-8                Day 9-12                Day 13-18
W1                      W2                     W2-3                    W4
```

### 10.2 各 Stage 详细范围

#### Stage 1（5 人天，第 1 周）—— 核心权限模型 + API 控制

**交付物**：

- Flyway V5 迁移：4 张核心表 + 内置种子（SUPER_ADMIN 角色、`*:*` 权限）
- 4 个 Entity / Mapper / 基础 Service
- `@RequiresPermission` 注解 + `PermissionAspect` 切面 + `PermissionMatcher` 算法
- `PermissionResolver`（含 permit-all 模式兜底）
- `PermissionQueryService`（带 Caffeine 缓存）
- `LocalAdminSeeder` 改造：admin 用户挂 SUPER_ADMIN 角色
- `AdminAuthController` 改造：现有手动权限校验改为注解
- `JwtIssuer / AuthService.issueTokens` 改造：scope 由角色聚合得出

**验收用例**：

```
1. admin/admin 登录 → 成功 → JWT scope claim = "*:*"
2. admin 调 POST /api/admin/auth/unlock → 成功
3. 手工把 admin 改挂一个只有 auth:unlock 权限的新角色 → 重新登录
4. admin 调 unlock → 成功
5. admin 调 reset-password → 返回 403 + "Missing required authority: auth:reset-password"
6. 手工去掉 admin 所有角色 → 重新登录
7. admin 调 unlock → 返回 403
8. 单测覆盖：PermissionMatcher 在 *:* / resource:* / 精确匹配 / 不匹配 四种场景下的行为
```

**里程碑**：Stage 1 完成时，基盘的权限框架核心已可用，但 UI 配置和数据范围尚未启用。

#### Stage 2（3 人天，第 2 周上半）—— 菜单管理

**交付物**：

- Flyway V6 迁移：菜单表 + 角色菜单关联表 + 基础菜单种子（system 模块）
- MenuEntity / RoleMenuEntity / Mapper / MenuQueryService
- `GET /api/menu/me` 接口（返回当前用户菜单树，自动按授权过滤，保留父链）
- 菜单缓存（Caffeine + 失效）

**验收用例**：

```
1. 用 admin token 调 GET /api/menu/me → 返回所有菜单的完整树
2. 建一个新角色仅关联 system.user 菜单 + user:read 权限
3. 给新用户挂此角色，登录后调 menu/me → 返回 [system → system.user] 的子树
   （保留父节点 system 以便前端展开）
4. 修改角色菜单关联 → 受影响用户的菜单缓存自动失效
```

**里程碑**：前端可基于 menu/me 接口渲染侧边栏导航。

#### Stage 3（4 人天，第 2-3 周）—— 部门与数据范围

**交付物**：

- Flyway V7 迁移：部门表 + `core_auth_user.dept_id` 新增列 + 角色部门关联表（自定义数据范围用）
- DeptEntity / RoleDeptEntity / Mapper / DeptQueryService
- `@DataScope` 注解 + `DataScopeResolver` + `LambdaQueryExtensions.applyDataScope()`
- 部门树管理接口 `GET /api/dept/tree` 等
- 修改 `UserInfoResponse` 加 `deptId`
- 部门缓存（Caffeine）

**验收用例**：

```
1. 建立部门树：总公司 / 北京 / 销售一部、销售二部
2. 建角色 PMS_BRANCH_MANAGER, data_scope = DEPT_AND_SUB
3. 用户 A 挂此角色，dept_id = 北京
4. business-pms 在 pms_reservation 表加 dept_id 列；ReservationMapper.list 上加 @DataScope
5. 在 ReservationService.list 中显式调用 applyDataScope(wrapper, ...)
6. 用 A 调 GET /api/reservation → 只看到 dept_id ∈ {北京, 销售一部, 销售二部} 的预订
7. ArchUnit 单测：所有 @DataScope 注解的 Mapper 方法都有对应的 applyDataScope 调用
```

**里程碑**：数据范围能力可用，业务模块可按需启用。

#### Stage 4（6 人天，第 4 周）—— 管理 UI 后端 + 缓存 + 审计

**交付物**：

- Flyway V8 迁移：操作日志表
- 6 个管理 Controller：Role / Permission / User / Menu / Dept / OpLog（含完整 CRUD）
- `@OpLog` 注解 + `OpLogAspect` + 密码字段脱敏
- Redis 二级缓存接入 + Pub/Sub 失效广播
- `POST /api/admin/auth/force-logout/{userId}` 强制下线接口
- `ForceLogoutGuard` 在 PermissionAspect 中集成

**验收用例**：

```
1. 通过 API 完整跑通：建角色 → 关联权限 → 关联菜单 → 建用户 → 分配角色
2. 修改某角色的权限 → 受影响所有用户的 Caffeine + Redis 缓存被清空
3. 调 force-logout → 该用户当前 token 立即 401
4. 删除内置角色 SUPER_ADMIN → 返回 400 "内置角色不可删除"
5. 改密码相关接口 → 操作日志里 request_body 的 password 字段为 ***
6. 多实例部署模拟：实例 A 修改角色权限，实例 B 的下次请求拿到最新权限（Pub/Sub 失效广播)
```

**里程碑**：完整的 RBAC 后端就绪，前端开发可以全功能联调。

### 10.3 时间表（按 1 人 80% 投入）

| 周次 | 日期范围 | 阶段 | 交付 |
|------|---------|------|------|
| W1 | 2026-05-25 ~ 2026-05-29 | Stage 1 | 核心权限模型 + API 控制 |
| W2 上 | 2026-06-01 ~ 2026-06-03 | Stage 2 | 菜单管理 |
| W2 下 ~ W3 | 2026-06-04 ~ 2026-06-12 | Stage 3 | 部门 + 数据范围 |
| W4 | 2026-06-15 ~ 2026-06-24 | Stage 4 | 管理 UI 后端 + 缓存 + 审计 |
| W5 | 2026-06-25 ~ 2026-07-01 | 联调 + 验收 | 客户验收 / 上线准备 |

**总周期：约 5 周 / 1.25 个月**。

### 10.4 依赖与前置条件

- PostgreSQL 16 / Redis 7 已部署（基盘已要求）
- 现有 access-matrix 主分支可正常构建运行（已验证）
- 前端工程方愿意基于 `/api/menu/me` 等接口实现菜单渲染
- 客户提供以下信息：
  - 初始角色矩阵（PMS 业务希望划分哪些角色，每个角色对应什么职责）
  - 初始部门结构（总公司、分公司、部门）
  - 初始菜单结构（哪些功能模块、各模块的页面与按钮）
  - 数据范围策略（哪些角色看全部、哪些只看自己部门、哪些只看自己）

如果客户未能提前提供，可在 Stage 4 提供 UI 后由运营自主配置，但前期联调可能使用占位数据。

---

## 十一、验收标准

### 11.1 功能验收清单

| 类别 | 用例数 | 自动化覆盖率 |
|------|-------|------------|
| 权限校验（含通配） | 12 | 100% |
| 多角色权限合并 | 6 | 100% |
| 菜单树组装（含父链回填） | 5 | 100% |
| 数据范围（5 种模式 × 多角色合并） | 15 | 100% |
| 缓存失效（用户、角色、菜单、部门变更） | 8 | 80% |
| 强制下线 | 3 | 100% |
| 操作日志（含密码脱敏） | 5 | 100% |
| 内置角色 / 权限保护 | 4 | 100% |
| 异常场景（用户不存在、角色不存在、超阈值） | 6 | 100% |

### 11.2 性能验收

使用 JMeter / wrk 进行压测，验收门槛：

| 场景 | 验收阈值 |
|------|---------|
| 权限校验 P99（缓存命中） | < 1ms |
| `GET /api/menu/me` P99（缓存命中） | < 10ms |
| 单实例权限校验吞吐 | > 5000 QPS |
| 1000 并发用户登录 → 调 menu/me | 全部 < 100ms |

### 11.3 安全验收

| 检查项 | 通过标准 |
|--------|---------|
| API 越权扫描 | 所有 admin 端点无 token 或权限不足时返回 403/401 |
| SQL 注入扫描 | 用 sqlmap 扫描所有 RBAC 端点无注入点 |
| JWT 篡改检测 | 改动 JWT 任意 byte 后被拒（验签失败） |
| 操作日志完整性 | 抽取 100 条写操作，100% 在 oplog 中可查 |
| 密码脱敏 | 抽取 oplog 中所有含 password 的记录，100% 为 *** |

### 11.4 文档交付

- 本方案文档（已交付）
- 详细 API 文档（Swagger 自动生成，每个端点含权限要求）
- 数据库 schema 文档（含 ER 图、字段注释）
- 部署手册（更新现有 README）
- 运维手册（含强制下线、清缓存、查询审计日志的 SOP）
- 故障排查指南（常见问题 + 排查路径）

---

## 十二、风险与对策

### 12.1 技术风险

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|---------|
| Caffeine + Redis 双级缓存一致性问题 | 用户看到陈旧权限 | 中 | 显式 evict + Pub/Sub 广播；权限延迟 ≤ 15min 是行业惯例 |
| 数据范围拦截器误伤系统 SQL | 系统功能异常 | 低 | 默认豁免策略 + 显式 @DataScope 标注 |
| 部门树循环引用 | DB 数据脏 | 极低 | Service 层校验：编辑部门时不能选自身或子部门为父 |
| JWT scope 体积膨胀 | HTTP 头超限 | 低 | `__compact__` 降级路径 + 监控告警 |
| 操作日志写入压力 | 业务响应变慢 | 极低 | 异步写 + 按月分区 + 6 个月归档 |
| Redis 不可用 | 跨实例缓存一致性下降 | 中 | fail-back 到 DB，性能下降但功能正常 |
| 多角色合并算法 bug 导致越权 | 安全事故 | 低 | 完整单测覆盖；ArchUnit 校验 |

### 12.2 业务风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| 客户对角色矩阵迟迟无法定型 | Stage 4 验收延期 | 提供"初始角色推荐表"作为起点；运营 UI 可后续调整 |
| 历史数据无 dept_id | 数据范围对存量数据无效 | Stage 3 上线前提供 migration 脚本，让客户分批回填 |
| 业务方加新权限点不通知运营 | 新功能上线后运营不知道要授权 | 部署流程中加入"权限点同步检查"步骤 |
| 内部高权用户滥用 | 数据泄露 | 操作日志 + 定期 audit；考虑后续 Stage 5 引入异常检测 |

### 12.3 项目风险

| 风险 | 影响 | 缓解 |
|------|------|------|
| 前端开发滞后 | 整体上线延期 | 后端按 API 优先交付，前端可并行开发；提供 Swagger 文档和 mock |
| 客户测试环境延迟提供 | 联调推迟 | 提前明确测试环境就绪 SLA；备用 Docker compose 方案 |
| 关键开发人员请假 | 进度卡顿 | 每个 Stage 至少有一份完整设计文档，新人 1 天上手 |

---

## 十三、投入估算

### 13.1 人力投入

| 角色 | 工作量 | 时长 |
|------|--------|------|
| 后端高级工程师 | 18 人天 | 5 周（80% 投入） |
| 测试工程师 | 4 人天（贯穿） | 5 周（30% 投入） |
| 架构师 | 2 人天（设计评审 + 关键决策） | 跨期 |
| 项目经理 | 3 人天（沟通 + 进度跟踪） | 跨期 |
| **总计** | **27 人天** | **5 周** |

### 13.2 硬件 / 基础设施

无新增。所有 RBAC 表数据量极小（< 100 MB），Redis 增量 < 100 MB。

### 13.3 第三方依赖

无新增。复用基盘已有：

- Spring Security 7（已用于 JWT）
- MyBatis-Plus 3.5.16
- Caffeine 3（已用于 NumberingService）
- Redis 7 / Lettuce
- Spring AOP（Spring 自带）

### 13.4 培训成本

- 后端团队：0.5 天分享会，理解新增的注解和缓存机制
- 运营团队：1 天培训，理解角色管理 UI 的使用（前端就绪后）

### 13.5 运维成本

- 监控指标：新增 RBAC 缓存命中率、Force-logout 调用次数、操作日志写入速率 → 接入现有 Prometheus
- 告警：Redis 不可用 / 缓存命中率 < 80% / 操作日志写入失败 / Force-logout 异常激增
- 备份：Operations 日志按现有 PG 备份策略

---

## 十四、替代方案对比

### 14.1 对比表

| 维度 | 本方案（自研轻量 RBAC） | Spring Security 全套授权 | Keycloak / Auth0 | 商业产品（如 SailPoint） |
|------|----------------------|-----------------------|------------------|----------------------|
| 实施周期 | 5 周 | 6-8 周 | 4-6 周（集成） | 2-3 个月 |
| 实施成本 | 18 人天 | 24-30 人天（学习成本高） | 12-18 人天 + 商业 License | 50+ 人天 + 高额 License |
| 后台管理 UI 控制粒度 | 菜单/按钮/API/数据行 4 层 | 同（但每层都要自己写） | 菜单/API 2 层完善，按钮/数据行需自研 | 全维度 + 工作流 + 异常检测 |
| 数据范围 | 内建 5 种 + 自定义 | 完全自研 | 不支持，需自研 | 完整 + 策略 DSL |
| 学习曲线（团队） | 低（看注解就懂） | 高（SpEL + AccessDecisionManager + …） | 中（OIDC 概念） | 高（专属 DSL） |
| 与基盘契合度 | 完美（同栈、同风格） | 良好 | 一般（外部 IDP） | 弱（独立系统） |
| 业务模块新加权限 | 注解 + Flyway 一行种子 | 同左 + 配置类调整 | UI 配置 + JWT 同步 | UI 配置 |
| 操作审计 | 内建 | 自研 | 自研 | 内建 + 高级分析 |
| 强制下线 | 内建 | 自研 | 内建 | 内建 |
| 多租户 | 内建（复用基盘） | 自研 | 内建（Realm） | 内建 |
| License 费用 | 0 | 0 | 免费 / 商业版 ¥30k+/年 | ¥500k+/年 |
| 后期定制 | 极易 | 良好 | 需懂 Keycloak 内部 | 几乎不可能 |
| 适用场景 | 中小型后台管理（10-100 万用户） | 同 | 多应用 SSO 场景 | 大型企业 IAM |

### 14.2 推荐方案：自研轻量 RBAC

**推荐理由**：

1. **契合度最高**：与现有基盘同栈、同风格、同设计哲学，新人 2 小时读懂全套代码
2. **成本最低**：无 License、无额外硬件、人力投入可控
3. **演进空间足**：后期需要 SSO 可平滑对接 Keycloak（仅替换认证层，授权层不变）
4. **运维负担最轻**：纯应用代码，无独立服务需运维

**不推荐 Keycloak**：

- 引入额外服务（Keycloak server）增加运维负担
- 后台管理项目不需要 SSO 多应用支持
- 数据范围、按钮级权限、操作日志需要 Keycloak 之外再做一遍

**不推荐 Spring Security 全套**：

- SpEL 在后台管理场景属于过度灵活，反而难维护
- 数据范围、菜单合并等业务能力 Spring Security 不覆盖，等于多花学习成本却仍要自研一半

**不推荐商业产品**：

- 投入产出比不合理，大象级方案解决中型问题
- 后续业务定制周期长

---

## 十五、后续规划

### 15.1 短期（项目上线后 3-6 个月）

| 功能 | 优先级 | 工作量 |
|------|--------|--------|
| 前端管理 UI（基于本方案的 API） | 高 | 12-15 人天（前端工程方） |
| 权限矩阵导入 / 导出（Excel） | 中 | 2 人天 |
| 单元测试覆盖率提升至 85%+ | 中 | 3 人天 |
| 操作日志按月归档脚本 | 低 | 1 人天 |
| Grafana RBAC 监控看板 | 低 | 1 人天 |

### 15.2 中期（6-12 个月）

| 功能 | 价值 |
|------|------|
| MFA / TOTP 二次验证 | 满足高敏感操作要求 |
| 异常行为检测 | 5 分钟内 1000 次 API 调用 → 自动锁 |
| 临时授权 / 时段授权 | 实习期、限时项目 |
| 权限申请审批流 | 员工自助申请 → 上级审批 → 自动生效 |
| API 密钥（非交互场景） | 给三方系统的接入凭证管理 |

### 15.3 长期（1 年以上）

| 功能 | 价值 |
|------|------|
| SSO 接入企业微信 / 钉钉 / AD | 员工无需单独账号 |
| OAuth2 / OIDC 提供方 | 给第三方应用提供登录服务 |
| 策略 DSL（OPA / Casbin） | 复杂场景下的策略表达力提升 |
| 细粒度字段级权限 | "客户的手机号字段，只有销售经理可见" |
| 跨租户超管 | 集团 IT 跨子公司统一治理 |

---

## 附录 A：完整数据库 Schema

### A.1 Flyway V5 — core_rbac_role_permission

```sql
-- ============================================
-- 角色
-- ============================================
CREATE TABLE core_rbac_role (
    id            CHAR(26)     PRIMARY KEY,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    code          VARCHAR(64)  NOT NULL,
    name          VARCHAR(128) NOT NULL,
    description   VARCHAR(512),
    data_scope    SMALLINT     NOT NULL DEFAULT 4,
    is_built_in   SMALLINT     NOT NULL DEFAULT 0,
    status        SMALLINT     NOT NULL DEFAULT 1,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    mark          SMALLINT     NOT NULL DEFAULT 1,
    create_user   VARCHAR(64),
    update_user   VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_core_rbac_role_code
    ON core_rbac_role (tenant_id, code) WHERE mark = 1;
CREATE INDEX idx_core_rbac_role_status
    ON core_rbac_role (tenant_id, status) WHERE mark = 1;

COMMENT ON TABLE core_rbac_role IS 'RBAC 角色定义';
COMMENT ON COLUMN core_rbac_role.code IS '业务唯一码，例: SUPER_ADMIN, PMS_FRONT_DESK';
COMMENT ON COLUMN core_rbac_role.data_scope IS '1=ALL 2=DEPT_AND_SUB 3=DEPT 4=SELF 5=CUSTOM';
COMMENT ON COLUMN core_rbac_role.is_built_in IS '1=内置不可删';

-- ============================================
-- 权限字典
-- ============================================
CREATE TABLE core_rbac_permission (
    id            CHAR(26)     PRIMARY KEY,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    code          VARCHAR(128) NOT NULL,
    name          VARCHAR(128) NOT NULL,
    resource      VARCHAR(64)  NOT NULL,
    action        VARCHAR(32)  NOT NULL,
    module        VARCHAR(32),
    description   VARCHAR(512),
    is_built_in   SMALLINT     NOT NULL DEFAULT 0,
    mark          SMALLINT     NOT NULL DEFAULT 1,
    create_user   VARCHAR(64),
    update_user   VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_core_rbac_perm_code
    ON core_rbac_permission (tenant_id, code) WHERE mark = 1;
CREATE INDEX idx_core_rbac_perm_module
    ON core_rbac_permission (tenant_id, module) WHERE mark = 1;

COMMENT ON TABLE core_rbac_permission IS 'RBAC 权限字典';
COMMENT ON COLUMN core_rbac_permission.code IS '权限串，格式 resource:action';
COMMENT ON COLUMN core_rbac_permission.module IS '归属模块: system / pms / iot...';

-- ============================================
-- 用户-角色关联
-- ============================================
CREATE TABLE core_rbac_user_role (
    id            CHAR(26)     PRIMARY KEY,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    user_id       CHAR(26)     NOT NULL,
    role_id       CHAR(26)     NOT NULL,
    mark          SMALLINT     NOT NULL DEFAULT 1,
    create_user   VARCHAR(64),
    update_user   VARCHAR(64),
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_core_rbac_user_role
    ON core_rbac_user_role (tenant_id, user_id, role_id) WHERE mark = 1;
CREATE INDEX idx_core_rbac_user_role_user
    ON core_rbac_user_role (tenant_id, user_id) WHERE mark = 1;
CREATE INDEX idx_core_rbac_user_role_role
    ON core_rbac_user_role (tenant_id, role_id) WHERE mark = 1;

COMMENT ON TABLE core_rbac_user_role IS '用户-角色多对多';

-- ============================================
-- 角色-权限关联
-- ============================================
CREATE TABLE core_rbac_role_permission (
    id              CHAR(26)    PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL DEFAULT 'default',
    role_id         CHAR(26)    NOT NULL,
    permission_id   CHAR(26)    NOT NULL,
    mark            SMALLINT    NOT NULL DEFAULT 1,
    create_user     VARCHAR(64),
    update_user     VARCHAR(64),
    create_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_core_rbac_role_perm
    ON core_rbac_role_permission (tenant_id, role_id, permission_id) WHERE mark = 1;
CREATE INDEX idx_core_rbac_role_perm_role
    ON core_rbac_role_permission (tenant_id, role_id) WHERE mark = 1;

COMMENT ON TABLE core_rbac_role_permission IS '角色-权限多对多';

-- ============================================
-- 内置数据种子
-- ============================================
-- 超级权限
INSERT INTO core_rbac_permission (id, tenant_id, code, name, resource, action, module, is_built_in)
VALUES ('00000000000000000000PERM01', 'default', '*:*', 'Super Permission', '*', '*', 'system', 1);

-- 超级管理员角色
INSERT INTO core_rbac_role (id, tenant_id, code, name, description, data_scope, is_built_in)
VALUES ('00000000000000000000ROLE01', 'default', 'SUPER_ADMIN', '超级管理员',
        '系统内置超级管理员角色，拥有所有权限', 1, 1);

-- 关联超管角色 与 *:* 权限
INSERT INTO core_rbac_role_permission (id, tenant_id, role_id, permission_id)
VALUES ('00000000000000000000RPM001', 'default',
        '00000000000000000000ROLE01', '00000000000000000000PERM01');

-- 现有 auth 端点对应的权限（向后兼容）
INSERT INTO core_rbac_permission (id, tenant_id, code, name, resource, action, module, is_built_in)
VALUES
 ('00000000000000000000PERM02', 'default', 'auth:unlock',         '解锁用户账户',    'auth', 'unlock',         'system', 1),
 ('00000000000000000000PERM03', 'default', 'auth:reset-password', '重置用户密码',    'auth', 'reset-password', 'system', 1);
```

### A.2 Flyway V6 — core_rbac_menu

```sql
CREATE TABLE core_rbac_menu (
    id              CHAR(26)     PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'default',
    parent_id       CHAR(26),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    menu_type       SMALLINT     NOT NULL,
    path            VARCHAR(255),
    component       VARCHAR(255),
    icon            VARCHAR(64),
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    visible         SMALLINT     NOT NULL DEFAULT 1,
    permission_code VARCHAR(128),
    status          SMALLINT     NOT NULL DEFAULT 1,
    mark            SMALLINT     NOT NULL DEFAULT 1,
    create_user     VARCHAR(64),
    update_user     VARCHAR(64),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_core_rbac_menu_code
    ON core_rbac_menu (tenant_id, code) WHERE mark = 1;
CREATE INDEX idx_core_rbac_menu_parent
    ON core_rbac_menu (tenant_id, parent_id, sort_order) WHERE mark = 1;

COMMENT ON TABLE core_rbac_menu IS 'RBAC 菜单 / 按钮';
COMMENT ON COLUMN core_rbac_menu.menu_type IS '1=目录 2=菜单 3=按钮';
COMMENT ON COLUMN core_rbac_menu.permission_code IS '关联权限串，menu_type=3 时建议必填';

CREATE TABLE core_rbac_role_menu (
    id          CHAR(26)    PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL DEFAULT 'default',
    role_id     CHAR(26)    NOT NULL,
    menu_id     CHAR(26)    NOT NULL,
    mark        SMALLINT    NOT NULL DEFAULT 1,
    create_user VARCHAR(64),
    update_user VARCHAR(64),
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_core_rbac_role_menu
    ON core_rbac_role_menu (tenant_id, role_id, menu_id) WHERE mark = 1;
CREATE INDEX idx_core_rbac_role_menu_role
    ON core_rbac_role_menu (tenant_id, role_id) WHERE mark = 1;

COMMENT ON TABLE core_rbac_role_menu IS '角色-菜单多对多';

-- 基础菜单种子（system 模块）
INSERT INTO core_rbac_menu (id, tenant_id, parent_id, code, name, menu_type, path, component, icon, sort_order, permission_code) VALUES
 ('00000000000000000000MENU01','default',NULL,'system','系统管理',1,'/system',NULL,'setting',1,NULL),
 ('00000000000000000000MENU02','default','00000000000000000000MENU01','system.user','用户管理',2,'/system/user','views/system/user/index','user',1,'user:read'),
 ('00000000000000000000MENU03','default','00000000000000000000MENU01','system.role','角色管理',2,'/system/role','views/system/role/index','team',2,'role:read'),
 ('00000000000000000000MENU04','default','00000000000000000000MENU01','system.menu','菜单管理',2,'/system/menu','views/system/menu/index','menu',3,'menu:read'),
 ('00000000000000000000MENU05','default','00000000000000000000MENU01','system.dept','部门管理',2,'/system/dept','views/system/dept/index','tree',4,'dept:read'),
 ('00000000000000000000MENU06','default','00000000000000000000MENU01','system.oplog','操作日志',2,'/system/oplog','views/system/oplog/index','log',5,'oplog:read');

-- 超管挂全部基础菜单
INSERT INTO core_rbac_role_menu (id, tenant_id, role_id, menu_id) VALUES
 ('00000000000000000000RM0001','default','00000000000000000000ROLE01','00000000000000000000MENU01'),
 ('00000000000000000000RM0002','default','00000000000000000000ROLE01','00000000000000000000MENU02'),
 ('00000000000000000000RM0003','default','00000000000000000000ROLE01','00000000000000000000MENU03'),
 ('00000000000000000000RM0004','default','00000000000000000000ROLE01','00000000000000000000MENU04'),
 ('00000000000000000000RM0005','default','00000000000000000000ROLE01','00000000000000000000MENU05'),
 ('00000000000000000000RM0006','default','00000000000000000000ROLE01','00000000000000000000MENU06');
```

### A.3 Flyway V7 — core_rbac_dept

```sql
CREATE TABLE core_rbac_dept (
    id              CHAR(26)     PRIMARY KEY,
    tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'default',
    parent_id       CHAR(26),
    code            VARCHAR(64)  NOT NULL,
    name            VARCHAR(128) NOT NULL,
    path            VARCHAR(1024) NOT NULL,
    level           SMALLINT     NOT NULL DEFAULT 1,
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    leader_user_id  CHAR(26),
    status          SMALLINT     NOT NULL DEFAULT 1,
    mark            SMALLINT     NOT NULL DEFAULT 1,
    create_user     VARCHAR(64),
    update_user     VARCHAR(64),
    create_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_core_rbac_dept_code
    ON core_rbac_dept (tenant_id, code) WHERE mark = 1;
CREATE INDEX idx_core_rbac_dept_parent
    ON core_rbac_dept (tenant_id, parent_id) WHERE mark = 1;
CREATE INDEX idx_core_rbac_dept_path
    ON core_rbac_dept (tenant_id, path text_pattern_ops) WHERE mark = 1;

COMMENT ON TABLE core_rbac_dept IS '部门树';
COMMENT ON COLUMN core_rbac_dept.path IS '物化路径，例: /rootId/level1Id/selfId';

ALTER TABLE core_auth_user ADD COLUMN dept_id CHAR(26);
CREATE INDEX idx_core_auth_user_dept
    ON core_auth_user (tenant_id, dept_id) WHERE mark = 1;

CREATE TABLE core_rbac_role_dept (
    id          CHAR(26)    PRIMARY KEY,
    tenant_id   VARCHAR(64) NOT NULL DEFAULT 'default',
    role_id     CHAR(26)    NOT NULL,
    dept_id     CHAR(26)    NOT NULL,
    mark        SMALLINT    NOT NULL DEFAULT 1,
    create_user VARCHAR(64),
    update_user VARCHAR(64),
    create_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX uk_core_rbac_role_dept
    ON core_rbac_role_dept (tenant_id, role_id, dept_id) WHERE mark = 1;

COMMENT ON TABLE core_rbac_role_dept IS '角色-部门多对多 (用于 data_scope=5 CUSTOM)';
```

### A.4 Flyway V8 — core_oplog

```sql
CREATE TABLE core_oplog (
    id            CHAR(26)     PRIMARY KEY,
    tenant_id     VARCHAR(64)  NOT NULL DEFAULT 'default',
    user_id       CHAR(26),
    username      VARCHAR(64),
    module        VARCHAR(32),
    action        VARCHAR(64)  NOT NULL,
    target_type   VARCHAR(32),
    target_id     CHAR(26),
    request_uri   VARCHAR(512),
    method        VARCHAR(8),
    client_ip     VARCHAR(64),
    user_agent    VARCHAR(512),
    request_body  TEXT,
    success       BOOLEAN      NOT NULL,
    error_msg     VARCHAR(512),
    cost_ms       INTEGER,
    create_time   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_core_oplog_user_time
    ON core_oplog (user_id, create_time DESC);
CREATE INDEX idx_core_oplog_target
    ON core_oplog (target_type, target_id);
CREATE INDEX idx_core_oplog_module_time
    ON core_oplog (module, create_time DESC);

COMMENT ON TABLE core_oplog IS '操作日志（只 insert，按时间归档）';
COMMENT ON COLUMN core_oplog.request_body IS '请求体，截断至 4KB，敏感字段（password 等）自动替换为 ***';
```

---

## 附录 B：完整 API 接口清单

### B.1 当前用户查询接口（Stage 1-3）

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/api/user/me` | 仅需登录 | 当前用户信息（含 deptId from Stage 3） |
| GET | `/api/menu/me` | 仅需登录 | 当前用户菜单树 |
| GET | `/api/permission/me` | 仅需登录 | 当前用户权限串集合（用于前端按钮显隐） |

### B.2 角色管理（Stage 4）

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/api/admin/role/list` | role:read |
| GET | `/api/admin/role/{id}` | role:read |
| POST | `/api/admin/role` | role:create |
| PUT | `/api/admin/role/{id}` | role:update |
| DELETE | `/api/admin/role/{id}` | role:delete |
| GET | `/api/admin/role/{id}/permissions` | role:read |
| PUT | `/api/admin/role/{id}/permissions` | role:update |
| GET | `/api/admin/role/{id}/menus` | role:read |
| PUT | `/api/admin/role/{id}/menus` | role:update |
| GET | `/api/admin/role/{id}/depts` | role:read |
| PUT | `/api/admin/role/{id}/depts` | role:update |
| POST | `/api/admin/role/{id}/copy` | role:create |

### B.3 权限字典（Stage 4）

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/api/admin/permission/list` | permission:read |
| GET | `/api/admin/permission/by-module` | permission:read |
| POST | `/api/admin/permission` | permission:create |
| PUT | `/api/admin/permission/{id}` | permission:update |
| DELETE | `/api/admin/permission/{id}` | permission:delete |

### B.4 用户管理（Stage 4）

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/api/admin/user/list` | user:read |
| GET | `/api/admin/user/{id}` | user:read |
| POST | `/api/admin/user` | user:create |
| PUT | `/api/admin/user/{id}` | user:update |
| DELETE | `/api/admin/user/{id}` | user:delete |
| PUT | `/api/admin/user/{id}/roles` | user:update |
| PUT | `/api/admin/user/{id}/dept` | user:update |
| PUT | `/api/admin/user/{id}/status` | user:update |

### B.5 菜单管理（Stage 4）

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/api/admin/menu/list` | menu:read |
| GET | `/api/admin/menu/tree` | menu:read |
| GET | `/api/admin/menu/{id}` | menu:read |
| POST | `/api/admin/menu` | menu:create |
| PUT | `/api/admin/menu/{id}` | menu:update |
| DELETE | `/api/admin/menu/{id}` | menu:delete |

### B.6 部门管理（Stage 4）

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/api/admin/dept/tree` | dept:read |
| GET | `/api/admin/dept/{id}` | dept:read |
| POST | `/api/admin/dept` | dept:create |
| PUT | `/api/admin/dept/{id}` | dept:update |
| DELETE | `/api/admin/dept/{id}` | dept:delete |

### B.7 操作日志（Stage 4）

| 方法 | 路径 | 权限 |
|------|------|------|
| GET | `/api/admin/oplog/list` | oplog:read |
| GET | `/api/admin/oplog/{id}` | oplog:read |

### B.8 高级操作

| 方法 | 路径 | 权限 |
|------|------|------|
| POST | `/api/admin/auth/force-logout/{userId}` | `*:*` |
| POST | `/api/admin/cache/evict-permissions/{userId}` | `*:*` |

---

## 附录 C：内置权限字典

| 模块 | 权限串 | 说明 |
|------|--------|------|
| system | `*:*` | 超级权限通配 |
| system | `auth:unlock` | 解锁账户 |
| system | `auth:reset-password` | 重置密码 |
| system | `user:read` | 查询用户 |
| system | `user:create` | 创建用户 |
| system | `user:update` | 修改用户 |
| system | `user:delete` | 删除用户 |
| system | `role:read` | 查询角色 |
| system | `role:create` | 创建角色 |
| system | `role:update` | 修改角色 |
| system | `role:delete` | 删除角色 |
| system | `permission:read` | 查询权限 |
| system | `permission:create` | 创建权限 |
| system | `permission:update` | 修改权限 |
| system | `permission:delete` | 删除权限 |
| system | `menu:read` | 查询菜单 |
| system | `menu:create` | 创建菜单 |
| system | `menu:update` | 修改菜单 |
| system | `menu:delete` | 删除菜单 |
| system | `dept:read` | 查询部门 |
| system | `dept:create` | 创建部门 |
| system | `dept:update` | 修改部门 |
| system | `dept:delete` | 删除部门 |
| system | `oplog:read` | 查询操作日志 |

业务模块（pms / iot）的权限点在各自模块上线时通过 Flyway 增量加入。

---

## 附录 D：术语表

| 术语 | 含义 |
|------|------|
| RBAC | Role-Based Access Control，基于角色的访问控制 |
| 主体 | 发起操作的实体，本系统中即为登录用户 |
| 资源 | 被保护的对象，例如客户、订单 |
| 动作 | 对资源可执行的操作，例如读、写、删 |
| 权限串 | `resource:action` 形式的权限标识，例如 `user:delete` |
| 通配 | `*:*` 或 `resource:*`，表达"所有权限"或"某资源全动作权限" |
| 数据范围 | 控制可访问哪些行的策略，与"动作权限"正交 |
| 多租户 | 同一系统支持多个客户/组织独立使用，数据隔离 |
| 逻辑删除 | 通过 mark 字段标记删除，物理记录保留 |
| 乐观锁 | 通过版本号或时间戳避免并发更新冲突 |
| ULID | Universally Unique Lexicographically Sortable Identifier，时间有序的唯一 ID |
| AOP | Aspect-Oriented Programming，面向切面编程 |
| JWT | JSON Web Token，自包含的令牌格式 |
| HIBP | Have I Been Pwned，公开的密码泄露查询服务 |
| BCrypt | 安全的密码哈希算法，自带 salt |
| Caffeine | 高性能 Java 进程内缓存库 |
| Redis | 分布式内存数据库，本项目用于缓存与会话存储 |
| Flyway | 数据库版本管理工具 |
| MyBatis-Plus | 基于 MyBatis 的增强 ORM 框架 |

---

## 联系与确认

- 技术对接人：（待填）
- 客户验收人：（待填）
- 文档评审日期：（待填）
- 实施启动日期：（待填）

**本方案文档生效需双方签字确认。**

---

*文档结束*
