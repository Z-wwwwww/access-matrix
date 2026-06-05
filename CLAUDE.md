# CLAUDE.md — AI entry point

This file is intentionally thin. The **`AGENTS.md` files are the single source of truth** for conventions — open the one for the stack you're editing and follow it. This file only points there and front-loads the rules most often missed (it exists because it is auto-loaded, whereas AGENTS.md is not).

- Root cross-stack rules → [`AGENTS.md`](AGENTS.md)
- Backend (Spring Boot 4 / Java 25 / MyBatis-Plus / Flyway) → [`backend/AGENTS.md`](backend/AGENTS.md)
- Frontend (Vue 3 / Vite / Tailwind v4) → [`frontend/AGENTS.md`](frontend/AGENTS.md)

## Always maintain the feature & test-point log

- [`docs/features-and-tests.md`](docs/features-and-tests.md) is the living single source of foundational features + manual test points. **Every time you add or change a feature, update it in the same change**: add/adjust the feature entry and list its test points (and prepend a one-line 变更履歴 entry). Treat this as part of "done", like updating tests.

## Before writing backend business code, don't miss:

- **Adding a module/table/endpoint** follows a fixed recipe (scaffold tool, `core_*`/`{module}_*` table naming, `@RequiresPermission` constants, `@OpLog`): [backend/AGENTS.md § Business code recipe](backend/AGENTS.md#business-code-recipe--adding-a-new-table--endpoint).
- **Every state change emits a domain event** in the same `@Service` transaction (`EventPublisher.publish(...)` → `core_domain_event` outbox). This is the non-back-fillable data substrate for future AI / revenue management — distinct from `core_oplog` request audit. Usage + example: [backend/AGENTS.md § Domain events & state-change conventions](backend/AGENTS.md). Enforced by Hard Rules 12–13.

When in doubt, read backend/AGENTS.md rather than guessing — it is authoritative and this file deliberately does not restate its details.
