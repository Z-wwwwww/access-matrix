# Access Matrix — Cross-Stack Workspace Guide

This repo is a monorepo: `backend/` is the Spring Boot 4 backend and `frontend/` is the Vue 3 frontend. The two stacks are tightly coupled, which is why they live together — a single PR can "add an API + add a page" without any version-coordination overhead.

## Repository layout

```
access-matrix/
├── .editorconfig                       indentation/encoding/EOL conventions shared by both stacks
├── .gitignore                          cross-stack entries only (IDE / OS)
├── README.md                           project front page
├── AGENTS.md                           this file
├── .github/
│   ├── workflows/
│   │   ├── backend.yml                  triggers on paths: 'backend/**'
│   │   └── frontend.yml                 triggers on paths: 'frontend/**'
│   └── pull_request_template.md         PR template: impact scope is required
├── docs/                                cross-stack collaboration docs (data-scope demo, etc.)
├── backend/                             ← Spring Boot 4 + Java 25 + Maven Wrapper
│   ├── AGENTS.md                        backend dev guide
│   ├── .gitignore                       Maven target / *.class / .mvn/timing etc.
│   ├── pom.xml
│   ├── mvnw / mvnw.cmd / .mvn/
│   └── (5 Maven modules ...)
└── frontend/                            ← Vue 3 + Vite 6 + Tailwind v4
    ├── AGENTS.md                        frontend dev guide
    ├── .gitignore                       node_modules / dist / .vite etc.
    ├── package.json
    └── src/ services/ public/ ...
```

The development rules for each stack (naming, file layering, API conventions, hard-rule violations) live in `backend/AGENTS.md` and `frontend/AGENTS.md` — **read that stack's AGENTS.md before writing code there**. This file covers **cross-stack** conventions only.

## Commit conventions (Conventional Commits)

Format: `<type>(<scope>): <subject>`

| type | Use |
|------|-----|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Rewrite without changing external behavior |
| `chore` | Chores (build, dependency updates, directory shuffles) |
| `docs` | Docs only |
| `test` | Test code |
| `ci` | GitHub Actions / pipelines |
| `perf` | Performance optimization |
| `style` | Formatting (no logic change) |

Allowed `scope` values:

- `backend` — changes under `backend/**`
- `frontend` — changes under `frontend/**`
- `repo` — changes to root meta (README / AGENTS / workflows / .gitignore, etc.)
- `docs` — changes under root `/docs/`

Examples:

```
feat(backend): add demo_task table and DataScopeHelper walkthrough
fix(frontend): replace native confirm() with useConfirm in 6 pages
chore(repo): consolidate into monorepo
docs(repo): add data-scope-demo.md
ci(repo): split backend/frontend workflows with path filters
```

One-shot cross-stack commits are allowed; use comma-separated scope `backend,frontend`:

```
feat(backend,frontend): /demo/task CRUD end-to-end
```

## PR conventions

PR titles follow the commit format. PR descriptions use the `.github/pull_request_template.md` template, **with the "impact scope" checkbox required**:

```
- [ ] backend
- [ ] frontend
- [ ] repo (workflows / docs / config)
```

Cross-stack PRs must pass CI on both sides (the path filter triggers the corresponding workflow based on which directory changed).

## Startup order

1. Backend first: `cd backend && ./mvnw -pl core-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=local`
2. Then frontend: `cd frontend && npm install && npm run dev`

The backend's local profile automatically runs Flyway migrations + LocalAdminSeeder + DemoSeeder, so no manual SQL is required.

## Adding a new business module (AI quick reference)

When the task is "add a new business module / table / endpoint":

```bash
# Fastest path — generates 5 Java files + 1 SQL migration, conventions baked in
./mvnw -pl core-bootstrap exec:java \
    -Dexec.mainClass=com.platform.core.bootstrap.tools.BusinessModuleScaffold \
    -Dexec.args="<resource>"      # e.g. order, invoice, salesreport
```

Then edit the placeholder business columns + add 4 permission constants to `backend/business-demo/src/main/java/com/platform/business/demo/security/DemoPermissions.java` (the scaffold prints the exact lines to add). Full conventions live at [backend/AGENTS.md § Business code recipe](backend/AGENTS.md#business-code-recipe--adding-a-new-table--endpoint); the boot guards (`TenantSchemaGuard` + `PermissionConsistencyGuard`) + `ArchitectureTest` enforce them.

## Cross-stack contract alignment points (most common pitfalls)

| Dimension | Source |
|-----------|--------|
| Auth header | Backend reads `Authorization: Bearer <jwt>`; the frontend axios interceptor adds it automatically |
| Refresh cookie | Backend issues HttpOnly `core_refresh`; the frontend axios uses `withCredentials: true` |
| Multi-tenant header | `X-Tenant-Id` (pre-auth fallback) / JWT `tid` claim (after auth) |
| Pagination params | **`page` + `size`** (not `pageSize` / `limit`) |
| Response shape | `{ code: 0, msg: "", data: {...} }`; for pagination `data = { records, total, page, limit }` |
| Date format | Backend expects `yyyy-MM-dd HH:mm:ssZZ`; the frontend converts uniformly via `toBackendDate` in `frontend/src/lib/date.js` |
| Timezone | Forced `Asia/Tokyo` |
| Static vs dynamic routes | The frontend registers public pages statically only; business routes are driven by the backend `/api/menu/me` via `addRoute()` |

## Behavioral guidelines (both stacks)

### 1. Think Before Coding
- State your assumptions first; ask when unsure
- When there are multiple solutions, lay out the options instead of silently picking one

### 2. Simplicity First
- Solve the problem with the least code
- Do not abstract for single-use cases
- Do not add unrequested "flexibility"

### 3. Surgical Changes
- Touch only what needs to change
- Do not casually "improve" nearby code
- Stay consistent with the existing style

### 4. Goal-Driven Execution
- Turn the task into a verifiable goal
- For multi-step tasks, give a verifiable step plan
