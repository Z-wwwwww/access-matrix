# backend — Access Matrix 基础平台

[English](README.md) · **中文**

多模块 Spring Boot 4 / JDK 25 平台基础：认证、RBAC、数据权限、多租户、审计。命名保持业务中立（`core-*`、`com.platform.core.*`、`CORE_*`），以便该基础平台可承载多个下游业务模块。

> 面向用户的文档（安装 / 使用 / 部署）位于 [/docs](../docs/)。
> 本 README 是 **后端工程师参考手册** —— 模块划分、profile、表结构、
> 接口清单、JVM 参数以及已锁定的设计决策。

---

## 技术栈

| 层            | 选型                                                                |
| ------------- | ------------------------------------------------------------------- |
| 语言          | Java 25 LTS（虚拟线程、scoped values）                              |
| GC            | Generational ZGC                                                    |
| 框架          | Spring Boot 4.0（Spring 7、Spring Security 7）                      |
| Web           | Tomcat 11（Jakarta EE 11），启用虚拟线程                            |
| 持久层        | MyBatis-Plus 3.5.16（`mybatis-plus-spring-boot4-starter`）          |
| 数据库        | PostgreSQL 15+（`new_inntouch_core`），i18n 菜单标题用 JSONB        |
| 迁移          | Flyway 11（`flyway-core` + `flyway-database-postgresql`）           |
| L1 缓存       | Caffeine 3，通过 Spring Cache                                       |
| L2 / session  | Redis 7（Lettuce），Spring Data Redis 4                             |
| 认证          | Spring Security 7 OAuth2 Resource Server。三种模式：`permit-all` / `jwt`（自建 HS256）/ `oidc`（Keycloak 或任意 OIDC IdP） |
| IdP 集成      | `keycloak-admin-client`，后端驱动用户开通                           |
| 邮件          | `spring-boot-starter-mail` + Freemarker，5 语言模板                 |
| 限流          | Bucket4j 8.10 进程内按 IP 限流，作用于 `/auth/*`                    |
| 密码          | BCrypt cost 12 + HIBP k-匿名校验                                    |
| ID            | 单调递增 ULID（CHAR(26)）                                           |
| 日志          | Log4j2 异步（Disruptor 4，256k 环形缓冲）                           |
| API 文档      | springdoc-openapi 3                                                 |
| JSON          | Jackson 3（`tools.jackson.*`）+ Blackbird                           |

---

## 模块布局

```
core-parent  (pom)
├── core-common          JsonResult / PageResult / ErrorCode / BusinessException
│                        RequestContext (ThreadLocal, virtual-thread safe) / IdGenerator
├── core-infrastructure  security / web / persistence / cache / mail / keycloak admin
│                        OidcUserResolver SPI, JwtDecoder beans, MailService,
│                        KeycloakUserService, NumberingService
├── core-system          system functions: auth / user / role / menu / dept / oplog
│                        OidcJitUserService, InviteTokenService, UserAdminService
│                        with INVITE / DIRECT provision modes
├── business-demo        示例业务模块：task — 演示 5 种 data scope 效果
└── core-bootstrap       Spring Boot entrypoint (main, ComponentScan, application*.yml,
                         log4j2-spring.xml, Flyway V1–V22, LocalAdminSeeder,
                         LocalKeycloakAdminSeeder, DemoSeeder)
```

依赖方向（严格）：

```
core-bootstrap
   ↓ depends on
core-system, business-demo          (siblings; do not depend on each other)
   ↓ depends on
core-infrastructure
   ↓ depends on
core-common
```

新增业务模块 → 作为 `business-demo` 的同级模块。新增系统功能 → `core-system` 下的子包。完整指南请参见 [../docs/development.zh-CN.md](../docs/development.zh-CN.md)。

---

## Profiles

| Profile | 默认 `security.mode` | DB / Redis / JWT 来源 |
| ------- | -------------------- | --------------------- |
| `local` | `permit-all`（可覆盖为 `oidc`） | 硬编码 localhost（PG `abcd@1234`，Redis db=1） |
| `dev`   | `jwt` 或 `oidc`     | 环境变量：`CORE_DB_URL/USERNAME/PASSWORD`、`CORE_REDIS_*`、`CORE_JWT_SECRET` |
| `test`  | `jwt`               | 环境变量（结构同 dev） |
| `prod`  | `jwt` 或 `oidc`     | 环境变量，`CORE_JWT_SECRET` ≥ 32 字节，Redis 开启 SSL |

非 `local` profile 启动时，如 `CORE_JWT_SECRET` 缺失或长度不足 32 字节，将快速失败 —— 不会静默回退到 dev 占位值。

`local` profile 额外会执行：
- `LocalAdminSeeder`（始终执行）—— 在业务侧 seed `demo-admin/demo-admin` 用户，绑定到 SUPER_ADMIN 角色 + HQ 部门
- `LocalKeycloakAdminSeeder`（仅当 `mode=oidc` 时）—— 在 Keycloak 的 `demo` realm 中 seed `demo-admin/demo-admin` 用户，与上面的业务用户成对存在，使首次 SSO 登录通过 JIT 绑定到 SUPER_ADMIN 行

完整的本地 / Keycloak 配置流程请参见 [../docs/getting-started.zh-CN.md](../docs/getting-started.zh-CN.md)。

---

## 接口清单

| 方法   | 路径                              | 鉴权        | 说明                                       |
| ------ | --------------------------------- | ----------- | ------------------------------------------ |
| GET    | `/api/health`                     | 开放        | 应用 + profile + 时间戳                    |
| POST   | `/api/auth/login`                 | 开放        | username / email / user_no（密码模式）     |
| POST   | `/api/auth/refresh`               | refresh tok | 单次使用，自动轮换                         |
| POST   | `/api/auth/logout`                | refresh tok | 撤销 + 清 cookie                           |
| GET    | `/api/auth/invite/{token}`        | 开放        | OIDC 模式 —— 邀请落地页探针                |
| POST   | `/api/auth/invite/{token}`        | 开放        | OIDC 模式 —— 消费 + 设置长期密码           |
| GET    | `/api/user/me`                    | Bearer JWT  | 完整用户信息                               |
| GET    | `/api/admin/users`                | `user:read` | 分页列表，可按部门过滤                     |
| POST   | `/api/admin/users`                | `user:create` | body 内指定 invite / direct 模式（`mode`） |
| PUT    | `/api/admin/users/{id}`           | `user:update` | …                                        |
| DELETE | `/api/admin/users/{id}`           | `user:delete` | 软删除 + Keycloak 删除 + 踢出登录        |
| PUT    | `/api/admin/users/{id}/roles`     | `user:update` | 重新分配角色                             |
| GET    | `/api/admin/roles`                | `role:read` | …                                          |
| POST   | `/api/admin/auth/unlock`          | `auth:unlock` 或 `*:*` |                                |
| POST   | `/api/admin/auth/reset-password`  | `auth:reset-password` 或 `*:*` | 强制密码策略 + HIBP |
| GET    | `/api/swagger-ui.html`            | 开放        | OpenAPI 3 UI                               |
| GET    | `/api/actuator/health`            | 开放        | 探针已启用                                 |
| GET    | `/api/v3/api-docs`                | 开放        | OpenAPI 3 JSON                             |

所有接口的错误响应结构一致：

```json
{"code": <int>, "msg": "<string>", "data": <T | null>}
```

| Code | 含义                     |
| ---- | ------------------------ |
| 0    | 成功                     |
| 400  | 请求错误                 |
| 401  | 未认证 / token 无效      |
| 403  | 拒绝访问 / 账户已禁用    |
| 404  | 未找到                   |
| 429  | 限流（`/auth/*`）        |
| 500  | 服务器错误               |
| 700  | 通用业务错误             |
| 701  | 校验失败                 |
| 702  | 乐观锁冲突               |
| 703  | 资源占用中（IN_USE）—— 调用方可附带 `?force=true` 重试 |
| 710  | 缺失 tenant 上下文       |
| 720  | 外部服务错误             |
| 730  | 账户锁定                 |

---

## Schema

Flyway 迁移位于 `core-bootstrap/src/main/resources/db/migration/`：

| 版本    | 表 / 变更                                                     |
| ------- | ------------------------------------------------------------- |
| V1      | `core_meta`                                                   |
| V2      | `core_auth_user`（username / email / user_no 登录，角色/权限 JSONB） |
| V3      | `core_auth_login_log`（异步审计）                             |
| V4      | `core_numbering_management` / `_key` + `USER` 编号种子        |
| V5      | `core_rbac_role` / `_permission` / `_role_permission` + 种子 SUPER_ADMIN |
| V6      | `core_rbac_menu` + `_role_menu` + 种子 system/oplog 菜单      |
| V7      | `core_rbac_dept` + `_role_dept` + 种子 HQ / TOKYO / OSAKA     |
| V8      | `core_oplog`（审计日志）                                      |
| V9      | RBAC 外键                                                     |
| V10     | `demo_task` + KYOTO 部门（5 种 scope 演示）                   |
| V11     | `core_rbac_menu.is_pinned`                                    |
| V12     | 菜单图标（Lucide 名称字符串）                                 |
| V13     | 清理通配符权限                                                |
| V14     | 恢复 SUPER_ADMIN 的 `*:*` 通配符绑定                          |
| V15     | demo JP 数据                                                  |
| V16     | 5 个演示用户（`tanaka_taro` …），用于 scope 演示              |
| V17     | `core_rbac_menu.title_i18n JSONB`                             |
| V18     | DROP `core_rbac_role.code`（通过 BuiltInRoles 用 ULID 查找）  |
| V19     | DROP `core_auth_user.{roles,authorities}` JSONB（改用 join）  |
| V20     | `(tenant_id, username)` 部分唯一索引（多租户）                |
| V21     | `core_auth_user.keycloak_id` 关联（OIDC JIT 绑定）            |
| V22     | `core_user_invite`（单次使用邀请 token，仅存哈希）            |

`AuthSchemaBootstrap` 会幂等地重跑 V1-V4 DDL，作为 `flyway_schema_history` 脏数据情况下的兜底保护。

### 表命名约定

| 前缀     | 归属 | 示例 |
| -------- | ---- | ---- |
| `core_`  | 平台（`core-system`、`core-infrastructure`） | `core_auth_user`、`core_rbac_role`、`core_oplog`、`core_user_invite`、`core_meta` |
| `demo_`  | 示例业务模块（`business-demo`） | `demo_task` |
| `<biz>_` | 你自己的业务模块 | `pms_reservation`、`iot_device`、… |

`flyway_schema_history` 与 `keycloak` schema 不在此约束范围内。

---

## JVM 参数

Spring Boot Maven 插件已将下列参数接入 `mvn spring-boot:run`，生产环境 `java -jar` 应保持一致：

```
-XX:+UseZGC
-XX:MaxRAMPercentage=75.0
-Xms512m
-XX:+AlwaysPreTouch
-Dfile.encoding=UTF-8
--enable-native-access=ALL-UNNAMED
-XX:+EnableDynamicAgentLoading
```

---

## 测试

```bash
./mvnw test                                                # full suite
./mvnw -pl core-system test -Dtest='RoleAdminServiceDeleteTest'
./mvnw -pl core-bootstrap test -Dtest='OidcJitProvisioningIT'   # needs Docker
```

| 模块 | 单元测试 | 集成测试（Testcontainers） |
| ---- | -------- | -------------------------- |
| core-common | 15（PermissionMatcher / BuiltInRoles / RequestContext） | — |
| core-system | 49（Role/Dept/User 删除、DataScope、PermissionGuard、OidcJit、InviteToken） | — |
| core-bootstrap | 3（LocalKeycloakAdminSeeder） | 2（MultiTenantSchemaIT、OidcJitProvisioningIT） |

集成测试通过 `@Testcontainers(disabledWithoutDocker = true)` 进行 gate —— 无 Docker → 自动跳过，构建保持绿。

---

## 已锁定的设计决策

| 编号        | 决策                                                                 |
| ----------- | -------------------------------------------------------------------- |
| AUTH-M-01   | 三种安全模式：`permit-all`、`jwt`、`oidc` —— 可按 profile 切换       |
| AUTH-M-02   | OIDC JIT 开通：未知 sub 的首个 JWT → INSERT core_auth_user；若存在历史用户名相同的行，按 username 绑定 |
| AUTH-P-01   | BCrypt cost 12（jwt 模式）                                           |
| AUTH-P-02   | 密码最小长度 8（PCI 8.3）                                            |
| AUTH-P-03   | 必须包含 4 种字符类型                                                |
| AUTH-P-04   | 启用 HIBP，HIBP 不可达时 fail-open                                   |
| AUTH-I-01   | 登录标识接受 `username` / `email` / `user_no`，可互换                |
| AUTH-B-06   | 管理员解锁接口 + 审计日志                                            |
| AUTH-P-07   | 密码自助修改委托给 Keycloak Account Console（oidc 模式）             |
| INV-01      | 邀请 token 仅以 SHA-256 哈希存储；明文只出现在邮件 URL 中            |
| INV-02      | 邀请 token 单次使用，TTL 7 天（可通过 `app.invite.token-ttl` 覆盖）  |
| RBAC-01     | 权限通配符：`*:*` / `resource:*` / 精确匹配                          |
| RBAC-02     | 多角色 data scope = UNION（取较宽松的）                              |
| MT-01       | 每张业务表均有 `tenant_id` 列 + 以 `(tenant_id, ...) WHERE mark=1` 为 scope 的部分唯一索引 |
| MT-02       | 手写的 `@Select` SQL 必须显式包含 `tenant_id = #{tenantId}` 过滤    |
| LOGIC-01    | 软删除：用 `UpdateWrapper.set("mark", 0)` —— 绝不要用 `setMark(0) + updateById` |
| naming      | `core-*` / `com.platform.core.*` / `CORE_*` —— 业务中立的基础平台    |
| ID strategy | 选用单调递增 ULID 而非 UUID，以获得 B+Tree 局部性                    |
| Storage     | `menu.title_i18n` 使用 PostgreSQL JSONB；JDBC 设置 `stringtype=unspecified` 以透明绑定 |

---

## 另请参见

- [/docs/getting-started.zh-CN.md](../docs/getting-started.zh-CN.md) —— 本地安装流程
- [/docs/user-guide.zh-CN.md](../docs/user-guide.zh-CN.md) —— 用户 / 管理员指南
- [/docs/development.zh-CN.md](../docs/development.zh-CN.md) —— 新增菜单 / 权限 / 迁移
- [/docs/deployment.zh-CN.md](../docs/deployment.zh-CN.md) —— 生产部署
- [AGENTS.md](AGENTS.md) —— 后端专属的 AI / 开发约定
- [docs/RBAC建设方案.md](docs/RBAC建设方案.md) —— RBAC 设计文档（中文）
