# Access Matrix — 跨栈 Workspace 规约

[English](AGENTS.md) · **中文**

本仓是 monorepo：`backend/` 是 Spring Boot 4 后端、`frontend/` 是 Vue 3 前端。两栈强耦合，因此放在同一仓库管理 —— 单次 PR 完成"加 API + 加页面"，无版本协调成本。

## 仓库布局

```
access-matrix/
├── .editorconfig                       两栈共用的缩进/编码/换行约定
├── .gitignore                          仅跨栈条目（IDE / OS）
├── README.md                           工程门面
├── AGENTS.md                           本文件
├── .github/
│   ├── workflows/
│   │   ├── backend.yml                  paths: 'backend/**' 触发
│   │   └── frontend.yml                 paths: 'frontend/**' 触发
│   └── pull_request_template.md         PR 模板：必填影响范围
├── docs/                                跨栈协作文档（数据范围演示等）
├── backend/                             ← Spring Boot 4 + Java 25 + Maven Wrapper
│   ├── AGENTS.md                        后端开发规约
│   ├── .gitignore                       Maven target / *.class / .mvn/timing 等
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd / .mvn/
│   └── (5 个 Maven module ...)
└── frontend/                            ← Vue 3 + Vite 6 + Tailwind v4
    ├── AGENTS.md                        前端开发规约
    ├── .gitignore                       node_modules / dist / .vite 等
    ├── package.json
    └── src/ services/ public/ ...
```

各栈的开发规则（命名、文件分层、API 约定、违反硬规则）在 `backend/AGENTS.zh-CN.md` 和 `frontend/AGENTS.zh-CN.md` —— 进任何一栈写代码前**先读那个栈的 AGENTS.md**。本文件只覆盖 **跨栈** 约定。

## Commit 约定（Conventional Commits）

格式：`<type>(<scope>): <subject>`

| type | 用途 |
|------|------|
| `feat` | 新功能 |
| `fix` | bug 修复 |
| `refactor` | 不改外部行为的重写 |
| `chore` | 杂务（构建、依赖更新、目录调整） |
| `docs` | 仅文档 |
| `test` | 测试代码 |
| `ci` | GitHub Actions / 流水线 |
| `perf` | 性能优化 |
| `style` | 格式化（不影响逻辑） |

`scope` 取值：

- `backend` — 改 `backend/**`
- `frontend` — 改 `frontend/**`
- `repo` — 改根级 meta（README / AGENTS / workflows / .gitignore 等）
- `docs` — 改根级 `/docs/`

示例：

```
feat(backend): add demo_task table and DataScopeHelper walkthrough
fix(frontend): replace native confirm() with useConfirm in 6 pages
chore(repo): consolidate into monorepo
docs(repo): add data-scope-demo.md
ci(repo): split backend/frontend workflows with path filters
```

跨栈一次性提交允许，scope 用 `backend,frontend` 逗号分隔：

```
feat(backend,frontend): /demo/task CRUD end-to-end
```

## PR 约定

PR 标题跟 commit 一样格式。PR 描述用 `.github/pull_request_template.md` 的模板，**必勾**"影响范围"：

```
- [ ] backend
- [ ] frontend
- [ ] repo (workflows / docs / config)
```

跨栈 PR 必须两栈都过 CI（path filter 会按目录变化触发对应 workflow）。

## 启动顺序

1. 先后端：`cd backend && ./mvnw -pl core-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=local`
2. 再前端：`cd frontend && npm install && npm run dev`

后端 local profile 会自动跑 Flyway 迁移 + LocalAdminSeeder + DemoSeeder，无需手工 SQL。

## 跨栈契约对齐点（最常踩坑的几个）

| 维度 | 来源 |
|------|------|
| 认证头 | 后端读 `Authorization: Bearer <jwt>`；前端 axios 拦截器自动加 |
| Refresh cookie | 后端下发 HttpOnly `core_refresh`；前端 axios `withCredentials: true` |
| 多租户 header | `X-Tenant-Id`（pre-auth fallback）/ JWT `tid` claim（auth 后） |
| 分页参数 | **`page` + `size`**（不是 `pageSize`/`limit`） |
| 响应格式 | `{ code: 0, msg: "", data: {...} }`；分页时 `data = { records, total, page, limit }` |
| 时间格式 | 后端要求 `yyyy-MM-dd HH:mm:ssZZ`，前端走 `frontend/src/lib/date.js` 的 `toBackendDate` 统一转换 |
| 时区 | 强制 `Asia/Tokyo` |
| 静态路由 vs 动态路由 | 前端只静态注册公开页；业务路由由后端 `/api/menu/me` 驱动 `addRoute()` |

## 行为指南（两栈通用）

### 1. Think Before Coding
- 先表态假设，不确定就问
- 多解时把选项摊开，别静悄悄选

### 2. Simplicity First
- 最少代码解决问题
- 不为单次用法做抽象
- 不加未要求的"灵活性"

### 3. Surgical Changes
- 只动该动的
- 不顺手"改进"附近代码
- 跟现有风格保持一致

### 4. Goal-Driven Execution
- 把任务转成可验证目标
- 多步任务给出可验证步骤计划
