# Migration: password-only → SSO (Keycloak / OIDC)

**English** · [中文](migration-password-to-sso.zh-CN.md)

This runbook walks an operator through switching a running access-matrix
deployment from `app.security.mode=password` (HS256, `AuthController.login`
validates against `core_auth_user.password_hash`) to
`app.security.mode=oidc` (RS256, Keycloak owns credentials), with **zero
business-data loss** and **one forced password reset per user**.

The migration is **fully automated by the code under
`backend/.../bootstrap/migration/`** — there is no shell script to babysit
and no SQL to hand-edit. The operator's job is to set two config switches,
restart the backend, and review a generated JSON report.

---

## Why a forced reset

Passwords are stored as **bcrypt** hashes in `core_auth_user.password_hash`
in the password-mode era and as **argon2id** hashes in Keycloak's
`credential` table once OIDC takes over. Bcrypt → argon2id translation is
mathematically impossible (cryptographic hash functions are one-way). The
only way to bring the user's password across is to have the user re-enter
it once, which is exactly what Keycloak's `UPDATE_PASSWORD` required-action
email does.

Nothing else needs to be re-entered. The user keeps their:

- Business `core_auth_user.id` (ULID — unchanged across the migration)
- `username`, `email`, `display_name`, `user_no`
- Role assignments (`core_user_role`)
- Department (`core_dept_user`)
- Audit history (`core_audit_log` / `core_op_log` all key off `user_id`)

---

## Architectural pieces that make this work

| Piece | Role |
|---|---|
| `core_auth_user.keycloak_id` column (V21) | nullable FK to KC user UUID, partial-unique on `(tenant_id, keycloak_id)` |
| `OidcJitUserService` "bind path" | on first SSO login, finds the legacy row by `(tenant, username)`, writes its `keycloak_id`, AND clears `password_hash` (non-super-admin) so the row ends up in the same shape as a JIT-provisioned user |
| `KeycloakUserService.createUser` + `executeActionsEmail` | mirror DB user into KC realm and trigger the reset-password email |
| `PasswordToSsoMigrationService` | batches the above over every legacy row, idempotent, multi-tenant aware |
| `PasswordToSsoMigrationRunner` | `ApplicationRunner` triggered by `app.migration.run-on-startup=password-to-sso` |
| `DualModeJwtDecoder` | accepts both HS256 (legacy) and RS256 (KC) — gives you a graceful overlap window |

---

## Prerequisites

- [ ] Keycloak ≥ 26 reachable from the backend (see `infra/keycloak/README.md`).
- [ ] The realm whose name matches your tenant id exists (`demo` for
      single-tenant deploys). Generate with
      `infra/keycloak/new-tenant.ps1 -Name <tenant>` if needed.
- [ ] The realm has a working **SMTP** configuration. Without it the
      reset-password emails fail and users get stuck. Verify by manually
      triggering an "execute actions email" from the admin console first.
- [ ] You have a snapshot / point-in-time backup of `core_auth_user`. The
      migration **does not modify** this table, but a backup costs nothing.
- [ ] `app.security.oidc.issuer-base-uri` points at the Keycloak you intend
      to use (default: `http://localhost:8180`). Verify in `application.yml`
      or via env override.

---

## Step-by-step

### Step 1 — Decide your cutover window

Communicate to users:
- The exact day SSO becomes mandatory.
- That they'll receive a "set your password" email between now and then.
- A deadline by which they must click the link.
- A fallback contact (admin who can resend) if the email is lost.

Keycloak's `executeActionsEmail` links expire in **12 hours** by default
(`actionTokenGeneratedByAdminLifespan` in realm settings). Adjust if your
user base needs longer.

### Step 2 — Enable the migration flag

In the active `application*.yml` (or via environment variable on a managed
deploy):

```yaml
app:
  security:
    mode: oidc                       # required — migration is OIDC-conditional
  migration:
    run-on-startup: password-to-sso  # opt-in trigger
    tenants: demo                 # comma-separated; one realm per item
    report-dir: logs                 # where the JSON report lands
```

Equivalent env-var form:

```sh
CORE_MIGRATION_RUN_ON_STARTUP=password-to-sso
CORE_MIGRATION_TENANTS=demo,acme,beta
```

The flag is **fail-safe by default** (`""` ⇒ nothing happens). Forgetting
to set it is harmless; forgetting to UNSET it after success is also
harmless (re-runs are idempotent — see below).

### Step 3 — Restart the backend

```sh
# Kubernetes
kubectl rollout restart deployment/backend

# bare metal
systemctl restart access-matrix-backend
```

On the next startup the runner fires. Look for:

```
[migration] starting password-to-sso for tenants=[demo]
[migration] tenant=demo found 47 candidates
[migration] tenant=demo created=47 skipped=0 failed=0
[migration] complete: created=47 skipped=0 failed=0 report=logs/migration-password-to-sso-20260527-141503.json
```

### Step 4 — Review the report

The runner writes `logs/migration-password-to-sso-<timestamp>.json` with
three buckets per tenant:

| Bucket | Meaning | Action |
|---|---|---|
| `created` | KC user provisioned, reset email sent | None — users will self-serve |
| `skipped` | Already in KC, OR missing email/username | Investigate `skipped[*].reason` |
| `failed` | KC create or email send threw | Read `failed[*].errorMessage`, fix, re-run |

Common `skipped.reason` values:

- `kc-user-already-exists` — idempotent re-run, nothing to do
- `missing-email` — fill in `core_auth_user.email` for the user and re-run
- `missing-username` — data quality issue, investigate

Common `failed.stage` values:

- `create-kc-user` — KC unreachable, admin credentials wrong, or KC user
  with that username exists with a different identity. Look at
  `errorMessage`.
- `send-reset-email` — KC user IS created, but the email leg threw.
  Usually realm SMTP is misconfigured. Fix SMTP, then re-run — the
  service will see the KC user exists, skip create, AND will NOT re-trigger
  the email (so users don't get pestered). For these users, use the KC
  admin console to manually trigger a `Credential Reset` from the user
  detail page.

### Step 5 — Disable the trigger

Once the report shows all green, remove the migration flag so subsequent
restarts don't pointlessly hammer the Keycloak admin API:

```yaml
app:
  migration:
    # run-on-startup: password-to-sso     ← remove or comment out
    tenants: demo
```

Restart. Normal startup resumes.

### Step 6 — Watch the bind path land (and clean state along the way)

The bind path in `OidcJitUserService` does more than just write
`keycloak_id`. On each user's first SSO login it ALSO clears their
`password_hash` (unless they hold `SUPER_ADMIN`, in which case the hash
is preserved so they can still break-glass when KC is unreachable).
This means migrated users end up byte-identical to a fresh JIT user —
the "as if always OIDC" final state is reached automatically as people
log in, without a separate cleanup step.



As users complete the reset-password flow and log in via SSO,
`OidcJitUserService.bind path` writes `keycloak_id` back to their row.

Healthcheck SQL to see migration progress:

```sql
SELECT
    COUNT(*) FILTER (WHERE keycloak_id IS NULL)       AS not_yet_bound,
    COUNT(*) FILTER (WHERE keycloak_id IS NOT NULL)   AS bound,
    COUNT(*)                                          AS total
FROM core_auth_user
WHERE mark = 1
  AND tenant_id = 'demo';
```

Run daily during the cutover window. When `not_yet_bound` reaches an
acceptable residual (people on holiday, etc.), enter cleanup.

### Step 7 — (Optional) Cleanup of dead `password_hash` values for non-logged-in users

Step 6 covers users who actually log in via SSO. For laggards who have
been migrated to KC but never logged in (their `password_hash` is still
the stale bcrypt from the password era, and they haven't completed SSO
so the bind path hasn't fired yet), you can NULL out their hash explicitly:

```sql
-- DRY RUN — count first
SELECT COUNT(*) FROM core_auth_user u
WHERE mark = 1
  AND keycloak_id IS NOT NULL
  AND password_hash IS NOT NULL
  AND id NOT IN (
      SELECT user_id FROM core_user_role
       WHERE role_id = '<SUPER_ADMIN role id>' AND mark = 1
  );

-- Then run the update
UPDATE core_auth_user
   SET password_hash = NULL,
       update_user   = 'sso-migration-cleanup',
       update_time   = NOW()
 WHERE mark = 1
   AND keycloak_id IS NOT NULL
   AND password_hash IS NOT NULL
   AND id NOT IN (
       SELECT user_id FROM core_user_role
        WHERE role_id = '<SUPER_ADMIN role id>' AND mark = 1
   );
```

(V24 already relaxed `password_hash` to NULL — no separate ALTER needed.)

---

## Email expired — bulk resend (mode: `password-to-sso-resend`)

Keycloak's reset-credentials link expires after 12 hours by default
(controlled by `actionTokenGeneratedByAdminLifespan` on the realm).
Users who don't click in time can't recover on their own. For one or
two laggards, ask them to ping you and re-issue from the KC admin
console one at a time. For a meaningful batch (dozens of users) use
the dedicated resend mode:

```yaml
app:
  security:
    mode: oidc
  migration:
    run-on-startup: password-to-sso-resend   # NOT "password-to-sso"
    tenants: demo
```

Restart. The runner walks `core_auth_user` rows where `keycloak_id IS NULL`
(== users who haven't completed SSO yet), looks up the existing KC user
by username, and fires a fresh `executeActionsEmail` against them. Users
whose link is still valid get a duplicate email — Keycloak handles that
gracefully by invalidating the old token in favor of the new.

Report lands at `logs/migration-password-to-sso-resend-<timestamp>.json`
with the same bucket shape as the initial migration:

| Bucket | Meaning |
|---|---|
| `created` | Re-sent successfully (the bucket name is reused — read as "emails issued") |
| `skipped[reason=no-kc-user-yet-run-migration-first]` | The row was never run through the initial migration. Run `password-to-sso` first. |
| `skipped[reason=missing-username]` | Data quality — investigate manually. |
| `failed[stage=send-reset-email]` | SMTP failure on that specific user. |
| `failed[stage=lookup-kc-user]` | Keycloak unreachable. |

### Safety properties of resend mode

- **Does NOT create KC users** — that's the initial migration's job.
  Catching the misconfig case ("row exists in DB but never had its KC
  side mirrored") explicitly lands users in a skipped bucket so the
  operator notices.
- **Does NOT touch already-bound users** — the candidate query filters
  on `keycloak_id IS NULL`, so once a user has completed SSO, they fall
  out of every subsequent resend run automatically.
- **Idempotent** — re-running fires the same emails again. Useful for
  multi-batch communication (e.g. day-7 reminder).
- **Same trace pattern as initial migration** — synthetic
  `RequestContext` per tenant, MP tenant interceptor scopes correctly,
  multi-tenant deployments use the same `app.migration.tenants` list.

### When to use which mode

| Situation | Mode |
|---|---|
| First time switching to OIDC | `password-to-sso` |
| Operator added a new column to `core_auth_user.email`, retry the few `skipped[missing-email]` rows | `password-to-sso` (idempotent — others get skipped as `kc-user-already-exists`) |
| 12 h passed, ~30% of users haven't clicked their link | `password-to-sso-resend` |
| SMTP went down during initial run — KC users created but no emails went out | `password-to-sso-resend` (the resend re-fires email against the now-existing KC users) |
| One specific user lost their email | KC admin console → user → Credentials → Credential Reset → check UPDATE_PASSWORD → Send email (faster than a restart) |

---

## Rollback

If something goes badly wrong:

1. Set `app.security.mode: password` (or `jwt`) back.
2. Restart.
3. The old `/auth/login` path comes back online; users with intact
   `password_hash` can log in as before.

This works **only** if you haven't run the optional Step 7 cleanup yet.
Once `password_hash` is NULLed, the only roll-forward is to fix whatever
is wrong with the OIDC side and let users complete enrollment.

---

## Five gotchas to know up front

### 1. SMTP must work in the realm

`executeActionsEmail` lands users in the `failed` bucket if KC can't talk
SMTP. Send a test email from the KC admin console before kicking off the
migration.

### 2. Don't let users change their KC username during the window

`OidcJitUserService.bind path` finds the legacy row by
`(tenant, preferred_username)`. If a user changes their username in KC
account console **before** their first SSO login, the bind path misses and
they get a brand-new `core_auth_user` row — orphaning the original. The
default realm config has `editUsernameAllowed: false` which prevents this;
verify yours does too.

### 3. Profile fields don't sync going forward

The bind path only writes `keycloak_id`. If a user later changes their
email in KC, `core_auth_user.email` keeps the old value. Decide who owns
profile updates — either lock down KC and edit only in admin UI, or write
a KC EventListener to push UPDATE_PROFILE events into the backend.

### 4. JIT new-user trap

After the migration, if someone is created **directly in Keycloak** (e.g.
through the KC admin console) without going through your admin UI, their
first login takes the OidcJitUserService **provision path** — a brand-new
`core_auth_user` row with no role, no department, no `user_no`. They log
in but see nothing. Forbid creating users in KC after Step 5; use the
backend's admin UI exclusively.

### 5. Schema drift: `password_hash NOT NULL`

V2 migration declared `password_hash VARCHAR(255) NOT NULL`. OIDC JIT and
INVITE-mode user creation both leave it NULL, relying on MyBatis-Plus's
`NOT_NULL` field strategy to omit the column from `INSERT` statements
entirely. This works today but is brittle:

- Any future code path that uses `entity.setPasswordHash(null)` and writes
  with a `DEFAULT` field strategy will fail.
- The Step 7 cleanup `UPDATE ... SET password_hash = NULL` will fail
  against the `NOT NULL` constraint.

Fix with a small migration (suggested ID: `V23`):

```sql
ALTER TABLE core_auth_user
  ALTER COLUMN password_hash DROP NOT NULL;
COMMENT ON COLUMN core_auth_user.password_hash IS
  'NULLABLE — set only when the row represents a break-glass admin or a legacy password-mode user. Normal OIDC users carry NULL here, with credentials owned by Keycloak.';
```

---

## Why a startup runner and not an HTTP endpoint?

Considered, rejected: an `@PostMapping("/admin/migration/run")`. Reasons:

1. **Single audit trail**: triggering migration via a deploy gives you the
   same paper trail (deploy log, change ticket) as any other prod change.
   An HTTP endpoint adds a separate audit surface that has to be guarded
   against compromised admin sessions.
2. **Determinism**: same property, same restart, same behavior across
   every environment.
3. **Idempotency is enough**: the migration is naturally one-shot; the
   "run once" mechanism doesn't need to be richer than "set property →
   restart". The cost (one restart) is small for an operation an org
   does once in its lifetime.

If you really need to trigger without restart, instantiate
`PasswordToSsoMigrationService` directly from a one-off Spring-Boot CLI
sub-command — but at that point the restart path is simpler.

---

## See also

- `backend/.../bootstrap/migration/PasswordToSsoMigrationService.java` —
  the core logic
- `backend/.../bootstrap/migration/PasswordToSsoMigrationRunner.java` —
  startup hook
- `backend/.../system/auth/service/OidcJitUserService.java` — the bind
  path that does the second half of the migration on first SSO login
- `backend/core-bootstrap/src/test/.../PasswordToSsoMigrationIT.java` —
  end-to-end smoke against real KC + Postgres
- `infra/keycloak/README.md` — Keycloak setup that this migration assumes
- `backend/core-bootstrap/src/main/resources/db/migration/V21__core_auth_user_keycloak_link.sql`
  — the schema column / index that makes the bind path work
