# Access Matrix

平台级账号 + 权限 + 多租户基盘。**Spring Boot 4** 后端 + **Vue 3 + Vite + Tailwind v4** 前端，单一 monorepo 管理。

```
access-matrix/
├── backend/      Spring Boot 4 (Java 25) — Maven 多模块；auth / RBAC / 数据范围 / 审计 / JWT / 多租户
└── frontend/     Vue 3 + Vite 6 + Tailwind v4 — 管理后台 + 业务示例
```

详细架构与开发规约见各栈内的 `AGENTS.md`：
- [backend/AGENTS.md](backend/AGENTS.md) — 模块边界 / Flyway / 安全 / API 约定
- [frontend/AGENTS.md](frontend/AGENTS.md) — 组件分层 / 共享组件 / 业务前缀 services
- [AGENTS.md](AGENTS.md) — 跨栈 workspace 规则 + Conventional Commits

## 一键启动

需要本机：**JDK 25**（或 Maven Wrapper 自动下载） / **Node 20+** / **PostgreSQL 15+** / **Redis 7+**。

```bash
# 后端
cd backend
./mvnw -pl core-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=local

# 前端（另开终端）
cd frontend
npm install && npm run dev
```

默认监听：后端 `:9135`（context `/api`），前端 `:5273`。Vite 通过 `/proxy_url` 代理到后端。

local profile 自动种 `admin/admin` 超管 + 5 个 demo 用户（`demo_all` / `demo_deptsub` / `demo_dept` / `demo_self` / `demo_custom`，密码统一 `demo123`）。各 demo 用户登录后看到的 task 数据不同，演示 5 种数据范围效果，对照表见 [docs/data-scope-demo.md](docs/data-scope-demo.md)。

## 主要特性

- **RBAC**：用户 / 角色 / 权限 / 菜单 / 部门六张主表 + 通配权限匹配（`*:*` / `module:*` / `exact`）
- **5 种数据范围**：ALL / DEPT_AND_SUB / DEPT / SELF / CUSTOM，`@DataScope` 注解 + 切面强制
- **JWT + 强制下线**：scope 永不内联（让权限变更立即生效）；refresh token 走 Redis 单次使用；ForceLogoutFilter 全局拦截
- **多租户**：MyBatis-Plus 拦截器自动注入 `tenant_id`；JWT `tid` claim + `X-Tenant-Id` header 双路径
- **审计**：`@OpLog` 注解 → `core_oplog` 表异步落库
- **共享 UI**：Vue 3 + Radix Vue + Tailwind v4，DataTable / Drawer / Dialog / DatePicker / 各 Picker 一应俱全

## 开发约定

- 提交信息走 [Conventional Commits](https://www.conventionalcommits.org/)，`scope` 用 `backend` / `frontend` / `repo`，例：
  ```
  feat(backend): add /demo/task/list endpoint
  fix(frontend): drop native confirm() in OpLog page
  chore(repo): bump CI java version
  ```
- 跨栈 PR 在描述里用模板的 "影响范围" 勾选 backend / frontend / 两者
- 任何新功能在改代码前先看对应栈的 `AGENTS.md`

## License

(待补)
