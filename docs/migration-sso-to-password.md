# Migration: SSO (Keycloak / OIDC) → password

Reverse counterpart of [migration-password-to-sso.md](migration-password-to-sso.md):
take a deployment that's been running `app.security.mode=oidc` and move it
back to in-house password auth (`mode=password` or `mode=jwt`) **without
losing business data** and with **one forced password reset per user**.

The migration is **fully automated** via
`backend/.../bootstrap/migration/SsoToPasswordMigrationService`. The
operator's only job is to set two config switches, restart the backend,
and review the generated JSON report. Users complete the rest themselves
by clicking a link in an email and choosing a new password.

---

## When you'd want to do this

- Compliance pushed you toward SSO, then changed direction.
- Operational cost of maintaining Keycloak isn't worth it for your team size.
- Testing the rollback path before committing to OIDC long-term.
- You inherited a project on OIDC and want password mode for development.

If you don't have a concrete reason, **don't do this**. OIDC mode has
strictly more capability (MFA, federation, account console, audit). The
project supports the reverse only so you're not locked in.

---

## Why a forced reset (again)

Same reason as the forward direction: passwords are stored as
**argon2id** in Keycloak's `credential` table and as **bcrypt** in
`core_auth_user.password_hash`. Translation across cryptographic hash
algorithms is impossible. The only safe way to bring the user's password
across is to have them re-enter it once, into our system this time.

What carries over with no user action:

- Business `core_auth_user.id` (ULID — unchanged).
- `username`, `email`, `display_name`, `user_no`, `dept_id`, `status`.
- Role bindings (`core_user_role`).
- Audit history (`core_audit_log` / `core_op_log` all key off `user_id`).

What requires user action:

- Setting a new password via the emailed reset link.

What disappears once the reset completes:

- `core_auth_user.keycloak_id` (NULL — the row no longer claims any KC
  identity).
- The KC user is disabled (not deleted; KC-side audit retained).

The final state is byte-identical to a user born under the password
regime: `password_hash` set, `keycloak_id` NULL.

---

## Architectural pieces that make this work

| Piece | Role |
|---|---|
| `core_password_reset_token` table (V24) | single-use hash-only token, sibling of `core_user_invite` |
| `PasswordResetTokenService` | mint / peek / consume — clone of `InviteTokenService` |
| `POST /auth/password-reset/{token}` | pre-auth endpoint: bcrypt + write `password_hash`, NULL `keycloak_id`, KC.disableUser |
| `ResetPasswordAccept.vue` | frontend landing page (same styling as InviteAccept.vue) |
| `SsoToPasswordMigrationService` | batches over `keycloak_id IS NOT NULL` users, mints + emails |
| `PasswordToSsoMigrationRunner` (extended) | recognises `sso-to-password` mode |
| `user-password-reset.{en,ja_JP,zh_CN,zh_TW,ko_KR}.ftl` | email templates, 5 languages |

---

## Step-by-step

### Step 1 — Decide the cutover window

Same considerations as the forward direction. Communicate:

- When SSO becomes unavailable (you'll flip mode at the END of the window).
- That users will receive a "set your password" email during the window.
- A deadline. Token TTL defaults to 7 days (configurable via
  `app.password-reset.token-ttl`, follows the same Duration syntax as
  invite tokens).
- A fallback contact for lost emails.

### Step 2 — Trigger the migration

Keep `mode=oidc` for now (the runner is OIDC-conditional only for forward
direction; the reverse service has no such gate, but the easiest mental
model is "trigger while SSO still works, flip mode after").

```yaml
app:
  security:
    mode: oidc                          # keep SSO live during the cutover window
  migration:
    run-on-startup: sso-to-password     # NEW value, third recognised mode
    tenants: demo                    # comma-separated; one realm per item
```

Restart. The runner walks `core_auth_user` rows where
`keycloak_id IS NOT NULL`, mints a single-use reset token per user, and
fires off the templated reset email. Look for:

```
[migration] starting sso-to-password for tenants=[demo]
[rev-migration] tenant=demo found 47 candidates (still KC-bound)
[reset] minted token for user 01ULID... (tenant demo) expires 2026-06-03T14:15:00
... ×47
[rev-migration] tenant=demo emails-sent=46 skipped=1 failed=0
[migration] complete mode=sso-to-password created=46 skipped=1 failed=0 report=logs/migration-sso-to-password-20260527-141503.json
```

### Step 3 — Review the report

`logs/migration-sso-to-password-<timestamp>.json` has the same shape as
the forward direction:

| Bucket | Meaning | Action |
|---|---|---|
| `created` | Token minted + email queued — read as "emails issued" | None — users self-serve |
| `skipped[missing-email]` | DB row has no email | Fill in, re-run |
| `skipped[missing-username]` | Data quality issue | Manual fix |
| `failed[mint-token]` | DB write failed for the token row | Inspect, re-run |
| `failed[send-reset-email]` | Token minted but mail dispatch threw | Fix SMTP, re-run (a fresh token will be issued — the old one ages out) |

### Step 4 — Users complete the reset

Each email contains a link `https://<your-app>/reset-password/<TOKEN>`.

Frontend flow:

```
ResetPasswordAccept.vue mounts → GET /auth/password-reset/{token} probe
                              ↓
                         valid? render form
                              ↓
              user types new password (×2 for confirmation)
                              ↓
                  POST /auth/password-reset/{token}
                              ↓
PasswordResetController:
    1. passwordPolicy.validate(req.password())            ← length / complexity / HIBP
    2. tokens.consume(token)                              ← single-use; sets used_at
    3. UPDATE core_auth_user SET
         password_hash = bcrypt(req.password()),
         keycloak_id   = NULL,
         update_user   = 'password-reset'
       WHERE id = <token.user_id> AND tenant_id = <token.tenant_id>
    4. KeycloakUserService.disableUser(realm, kcId)       ← best-effort; logged if fails
                              ↓
                       "done" screen + link to /login
```

Once `keycloak_id` is NULL, the user drops out of any future re-run of
the candidate query. That's the "always-been-password-mode" final state.

### Step 5 — Watch progress, then flip mode

Healthcheck SQL (mirror of the forward-direction one):

```sql
SELECT
    COUNT(*) FILTER (WHERE keycloak_id IS NOT NULL)   AS still_kc,
    COUNT(*) FILTER (WHERE keycloak_id IS NULL
                       AND password_hash IS NOT NULL) AS migrated,
    COUNT(*) FILTER (WHERE keycloak_id IS NULL
                       AND password_hash IS NULL)     AS broken,
    COUNT(*)                                          AS total
FROM core_auth_user
WHERE mark = 1 AND tenant_id = 'demo';
```

`broken` (keycloak_id NULL AND password_hash NULL) is the dangerous
state — a user who has been detached from KC but never set a password.
If you see non-zero, investigate before flipping mode.

When `still_kc` reaches an acceptable residual (lagging users), flip:

```diff
 app:
   security:
-    mode: oidc
+    mode: password
   migration:
-    run-on-startup: sso-to-password
+    # run-on-startup: sso-to-password   (uncomment to re-fire reminders)
```

Restart. The legacy `/auth/login` path is now the only login route;
KC tokens stop being accepted (`DualModeJwtDecoder` falls back to HS256
only when mode != oidc).

### Step 6 — (Optional) Stragglers

Users still in the `still_kc` bucket after mode flip cannot log in.
Their options:

- They contact the admin → admin re-runs the migration in
  `sso-to-password` mode (mints a fresh token, sends a fresh email).
- Admin uses the same flow from `/admin/auth/reset-password` (legacy
  endpoint, still works for super-admin use, requires `*:*` permission).

To re-fire emails in bulk:

```yaml
app:
  security:
    mode: password         # already flipped
  migration:
    run-on-startup: sso-to-password
```

Re-running will see the same `keycloak_id IS NOT NULL` users (they
haven't reset) and mint fresh tokens. Old tokens expire on their own.

---

## Auto-on-mode-flip (opt-in)

If you'd rather have "flip the mode and that's it" with no second flag:

```yaml
app:
  security:
    mode: password       # or oidc — either direction works
  migration:
    auto-on-mode-flip: true    # off by default; opt-in per environment
    tenants: demo
```

The `ModeFlipDetector` reads the last-applied mode from
`core_meta.security.last_applied_mode`, compares to the current mode,
and dispatches the right migration. No `run-on-startup` needed.

Caveats:

- Off by default for safety — auto-firing a thousand-user email blast
  on a stray dev-environment mode flip is a worse failure mode than
  needing an extra config line in prod.
- Transitions involving `permit-all` are ignored (no migration, just
  rebaseline).
- First-ever boot is treated as "no baseline, just record current" —
  the very first migration must be triggered explicitly. After that,
  subsequent flips are auto-dispatched.
- The detector runs at `Ordered.LOWEST_PRECEDENCE` so it sees the post-
  Flyway, post-seeder state.

---

## Rollback (during the window)

The reverse migration is fully reversible UNTIL users start completing
the reset flow. Concretely:

| State | Rollback action |
|---|---|
| Migration scheduled but `app.security.mode` still oidc | remove `run-on-startup`, restart. Outstanding reset tokens age out on their own. SSO unaffected. |
| Some users have completed reset (`keycloak_id` is NULL on those rows) | those users need to be re-mirrored via the FORWARD migration to get back into KC. The KC user was DISABLED (not deleted) so it can be re-enabled in the admin console; you'll need to update `keycloak_id` manually OR let the user log in via SSO (the JIT bind path will rewire automatically). |
| Mode already flipped to password | flip back to oidc; users who completed reset have `password_hash` set and `keycloak_id` NULL — they'd see their old SSO account as "not yet bound" and JIT-create a new business row on next SSO login. **Do not flip mode back without first deciding what to do with the rows that completed reset.** |

Bottom line: rollback is cleanest during the migration window, gets
progressively harder as users complete their reset, and is essentially
"re-do the forward migration" after the mode flip.

---

## Five gotchas

### 1. SMTP must work in the backend

The reverse direction relies on `MailService.sendHtmlAsync` (which uses
the Spring Mail `JavaMailSender` configured under `spring.mail.*`). Send
a test email first; the migration won't validate SMTP at startup, it'll
just fail per-user with `stage=send-reset-email`.

### 2. KC user is disabled, not deleted

After a user completes the reset, `KeycloakUserService.disableUser` is
called against the KC user. KC-side history (`USER_INFO` event log,
login history) is preserved, but the user can't log in via KC again.

If you want full KC-side deletion, do it manually post-migration:

```sql
-- list disabled-but-still-present KC users
SELECT u.id, u.username FROM keycloak.user_entity u
WHERE u.realm_id = (SELECT id FROM keycloak.realm WHERE name = 'demo')
  AND u.enabled = false;
```

Then `kcadm delete users/<uuid>` for the ones you want gone.

### 3. password_hash NOT NULL was relaxed in V24

If you somehow have a pre-V24 deployment and skip the migration, the
reset endpoint's `UPDATE ... SET password_hash = bcrypt(...)` will succeed
but the NULL `keycloak_id` write will also succeed — the V24 constraint
drop applies symmetrically.

V24 also adds the `core_password_reset_token` table; without it the
reset endpoint will fail on its first invocation. Make sure your
Flyway state is up to V24 BEFORE running the migration.

### 4. The reset endpoint is pre-auth — register it as such if you customise security

`PasswordResetController` is mounted under `/auth/password-reset/{token}`,
which is covered by the project's default `PERMIT_PATHS = ["/auth/**", ...]`
in `SecurityConfig`. If you've customised that list, make sure
`/auth/password-reset/**` stays permitted; otherwise users hit a 401
when clicking the email link.

### 5. Email locale defaults to Japanese

The migration sends emails in `Locale.JAPAN` because at migration time
the recipient has no logged-in profile to pull a locale from. If your
user base is primarily not Japanese-speaking, you have two options:

- Add a `locale` column on `core_auth_user` (currently doesn't exist)
  and use it in `SsoToPasswordMigrationService.sendResetEmail`.
- Send a follow-up email in their preferred locale once they've logged
  in once.

The templates exist in 5 languages already; the Japanese default is
purely about which template the migration job picks.

---

## See also

- [migration-password-to-sso.md](migration-password-to-sso.md) — the forward direction
- `backend/.../bootstrap/migration/SsoToPasswordMigrationService.java` — core logic
- `backend/.../bootstrap/migration/PasswordToSsoMigrationRunner.java` — startup hook (handles both directions)
- `backend/.../bootstrap/migration/ModeFlipDetector.java` — `auto-on-mode-flip` auto-detection
- `backend/.../auth/controller/PasswordResetController.java` — the pre-auth endpoint
- `backend/core-bootstrap/src/main/resources/db/migration/V24__core_password_reset_and_nullable_pwhash.sql` — schema
- `frontend/src/views/login/ResetPasswordAccept.vue` — frontend landing page
