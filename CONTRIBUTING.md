# Contributing to Access Matrix

谢谢愿意贡献。下面是 PR 流程、commit 规范、代码风格。

---

## 1. Issue 之前

- 复现步骤：每个 bug issue 必须能在 main 上复现
- 期望 vs 实际：明确说"应该 X，实际 Y"
- 环境信息：JDK / Node / PG / Redis 版本 + 你的 profile
- 大改动**先开 issue 讨论** 再 PR，避免来回返工

---

## 2. PR 流程

1. Fork → 本地 clone
2. 起 feature 分支：`git checkout -b feat/short-name`
3. 实现 + 加测试（必须）
4. 跑测试：
   ```bash
   cd backend && ./mvnw test
   cd frontend && npm run test
   ```
5. Commit 遵循 [Conventional Commits](https://www.conventionalcommits.org/)
6. Push → 在 GitHub 上开 PR 指向 `main`
7. CI 必须绿
8. 等 review，反馈后 push 新 commit（不要 force push 除非 maintainer 让你做）
9. Merge 由 maintainer 操作

---

## 3. Commit 规范

格式：

```
<type>(<scope>): <short summary, lowercase, no trailing dot>

<body — optional, wrap at 72 cols>

<footer — optional, refs / breaking changes>
```

**type**：
| type | 含义 |
|---|---|
| `feat` | 新功能 |
| `fix` | bug 修复 |
| `docs` | 文档 |
| `chore` | 构建 / 依赖 / 杂项 |
| `refactor` | 重构（行为不变） |
| `perf` | 性能优化 |
| `test` | 加测试 |
| `style` | 格式化（无代码改动） |

**scope**：
| scope | 范围 |
|---|---|
| `backend` | 后端代码 |
| `frontend` | 前端代码 |
| `infra` | infra/* (Keycloak 脚本等) |
| `docs` | docs/* |
| `repo` | 跨栈 / 根目录文件 |

**示例**：

```
feat(backend): add SAML 2.0 protocol mapper

Adds SAML support alongside OIDC for enterprises that haven't moved off SAML.
Wires up Spring Security's SAML2 RelyingParty and a new
SamlAuthenticationConverter mirroring OidcUserResolver semantics.

Closes #42
```

```
fix(frontend): drop native confirm() in OpLog page

Replaces window.confirm with our shared ConfirmDialog so the page works in
embedded iframes (window.confirm is blocked there).
```

```
docs(getting-started): clarify Keycloak port conflict with Spring Boot default
```

---

## 4. 代码规范

### 4.1 Backend (Java)

- Records over classes for DTOs
- ULID strings (CHAR(26)) for IDs, not auto-increment longs
- 软删用 `UpdateWrapper.set("mark", 0)`，**不要** `setMark(0) + updateById`（@TableLogic 会 silently no-op，见 [details](development.md#43-soft-delete-约定)）
- 所有 hand-written `@Select` SQL 必须显式带 `tenant_id = #{tenantId}` filter
- 新表必须有 `tenant_id` 列 + 部分唯一索引带 `WHERE mark = 1`
- 避免 static field + `@Value` 注入 (Spring anti-pattern)
- 异常用 `BusinessException(ErrorCode, msg)`，不 `throw new RuntimeException(...)`

### 4.2 Frontend (Vue 3 + JS)

- Composition API + `<script setup>`
- ESM only (no CommonJS)
- 业务页面在 `views/<domain>/<PascalCase>.vue`
- 通用 UI 在 `components/ui/`（无业务）；共享业务在 `components/shared/`
- service 文件 `services/<resource>.js` 全 axios 调用
- 多语言 key 必须 5 个 lang 文件都有：`ja_JP / en / zh_CN / zh_TW / ko_KR`

### 4.3 SQL Migrations (Flyway)

- 文件名：`V<N>__<snake_name>.sql`
- N 递增不重复
- 用 `IF NOT EXISTS` / `IF EXISTS`（迁移要可重跑）
- 已发布的 migration **不要改**，加新 V 文件做 ALTER
- 新表都有 `mark / create_user / update_user / create_time / update_time / tenant_id`

### 4.4 测试要求

- 每个新 service / endpoint 加单测
- 修 bug → 先写**会失败**的测试，然后 fix 让它过
- Testcontainers IT 要标 `@Testcontainers(disabledWithoutDocker = true)`
- 前端组件改了行为 → 加或更新 vitest 测试

---

## 5. 不要做的事

- ❌ 不要在 `application.yml` 或 `application-*.yml` 里硬编码密码 / token / API key
  - 用 `${ENV_VAR:default}` 或 `application-local.yml`（已 gitignored）
- ❌ 不要在业务代码里写 placeholder / 假实现（"TODO 后面实现，先返回假数据"）—— 要么不做，要么真做
- ❌ 不要破坏向后兼容的 API（路径 / 参数 / 返回格式），除非 commit 带 `BREAKING CHANGE:` footer 并升大版本
- ❌ 不要直接 push 到 `main`，所有 change 走 PR
- ❌ 不要 `git push --force` 到 `main`
- ❌ 不要跳过 git hooks (`--no-verify`)

---

## 6. 添加新模块？

`access-matrix` 设计支持加新业务模块，跟 `business-demo` 平级：

```
backend/
├── core-*               平台基盘
├── business-demo        示例
└── business-yours       ← 你的业务模块
```

新模块必须：
- 不依赖其他 `business-*` 模块
- 依赖 `core-infrastructure` 或 `core-system`
- 表名前缀（如 `yours_*`）
- 跟 demo 类似的目录结构

提 PR 前先开 issue 讨论模块边界和命名。

---

## 7. 安全披露

发现安全漏洞**不要**直接开 public issue。请发邮件到 wen.zhang@nts-cn.com（或 maintainer 后续提供的安全联系方式）。

我们会在 48 小时内回复，确认问题后协调披露时间。

---

## 8. License

贡献的代码默认按 [MIT License](LICENSE) 发布。提 PR 即视为你同意这个 license。

---

谢谢贡献 🙌
