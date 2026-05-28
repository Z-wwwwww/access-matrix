# Contributing to Access Matrix

**English** · [中文](CONTRIBUTING.zh-CN.md)

Thanks for wanting to contribute. Below are the PR flow, commit conventions, and code style.

---

## 1. Before opening an issue

- Reproduction steps: every bug issue must be reproducible on `main`
- Expected vs actual: state clearly "should X, actually Y"
- Environment: JDK / Node / PG / Redis versions + your profile
- For large changes, **open an issue to discuss first** before raising a PR — avoids back-and-forth rework

---

## 2. PR flow

1. Fork → clone locally
2. Start a feature branch: `git checkout -b feat/short-name`
3. Implement + add tests (required)
4. Run tests:
   ```bash
   cd backend && ./mvnw test
   cd frontend && npm run test
   ```
5. Follow [Conventional Commits](https://www.conventionalcommits.org/) for commits
6. Push → open a PR on GitHub targeting `main`
7. CI must be green
8. Wait for review, push new commits on feedback (do not force push unless a maintainer asks you to)
9. Merging is done by a maintainer

---

## 3. Commit convention

Format:

```
<type>(<scope>): <short summary, lowercase, no trailing dot>

<body — optional, wrap at 72 cols>

<footer — optional, refs / breaking changes>
```

**type**:
| type | Meaning |
|---|---|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Docs |
| `chore` | Build / deps / miscellaneous |
| `refactor` | Refactor (behavior unchanged) |
| `perf` | Performance optimization |
| `test` | Add tests |
| `style` | Formatting (no code change) |

**scope**:
| scope | Range |
|---|---|
| `backend` | Backend code |
| `frontend` | Frontend code |
| `infra` | infra/* (Keycloak scripts, etc.) |
| `docs` | docs/* |
| `repo` | Cross-stack / root-level files |

**Examples**:

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

## 4. Code style

### 4.1 Backend (Java)

- Records over classes for DTOs
- ULID strings (CHAR(26)) for IDs, not auto-increment longs
- For soft delete use `UpdateWrapper.set("mark", 0)`, **not** `setMark(0) + updateById` (@TableLogic silently no-ops; see [details](development.md#43-soft-delete-约定))
- Every hand-written `@Select` SQL must include an explicit `tenant_id = #{tenantId}` filter
- New tables must have a `tenant_id` column + partial unique indexes with `WHERE mark = 1`
- Avoid static field + `@Value` injection (Spring anti-pattern)
- Throw `BusinessException(ErrorCode, msg)`, not `throw new RuntimeException(...)`

### 4.2 Frontend (Vue 3 + JS)

- Composition API + `<script setup>`
- ESM only (no CommonJS)
- Business pages under `views/<domain>/<PascalCase>.vue`
- Generic UI in `components/ui/` (no business logic); shared business components in `components/shared/`
- Service files `services/<resource>.js` contain all axios calls
- i18n keys must exist in all 5 lang files: `ja_JP / en / zh_CN / zh_TW / ko_KR`

### 4.3 SQL migrations (Flyway)

- Filename: `V<N>__<snake_name>.sql`
- N strictly increasing, no duplicates
- Use `IF NOT EXISTS` / `IF EXISTS` (migrations must be re-runnable)
- **Do not modify** an already-released migration; add a new V file with the ALTER instead
- New tables carry `mark / create_user / update_user / create_time / update_time / tenant_id`

### 4.4 Testing requirements

- Add unit tests for every new service / endpoint
- Bug fixes → write a **failing** test first, then fix to make it pass
- Testcontainers ITs must be marked `@Testcontainers(disabledWithoutDocker = true)`
- If a frontend component's behavior changes → add or update a vitest test

---

## 5. Don'ts

- ❌ Do not hardcode passwords / tokens / API keys in `application.yml` or `application-*.yml`
  - Use `${ENV_VAR:default}` or `application-local.yml` (gitignored)
- ❌ Do not write placeholders / fake implementations ("TODO, return fake data for now") in business code — either don't do it, or do it for real
- ❌ Do not break backward-compatible APIs (paths / params / response shape) unless the commit carries a `BREAKING CHANGE:` footer and bumps the major version
- ❌ Do not push to `main` directly; all changes go through a PR
- ❌ Do not `git push --force` to `main`
- ❌ Do not skip git hooks (`--no-verify`)

---

## 6. Adding a new module?

`access-matrix` is designed to accept new business modules at the same level as `business-demo`:

```
backend/
├── core-*               platform foundation
├── business-demo        sample
└── business-yours       ← your business module
```

New modules must:
- Not depend on other `business-*` modules
- Depend on `core-infrastructure` or `core-system`
- Use a table-name prefix (e.g. `yours_*`)
- Mirror demo's directory structure

Open an issue to discuss module boundaries and naming before raising the PR.

---

## 7. Security disclosure

If you find a security vulnerability, **do not** open a public issue. Email wen.zhang@nts-cn.com (or any subsequent security contact provided by a maintainer).

We will respond within 48 hours, confirm the issue, and coordinate the disclosure timeline.

---

## 8. License

Contributed code is released under the [MIT License](LICENSE) by default. Submitting a PR means you agree to this license.

---

Thanks for contributing 🙌
