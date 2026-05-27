# Break-glass credentials

Operational guide for the "break-glass password" mechanism — the
emergency credential that lets a super-admin log in when Keycloak (SSO)
is unavailable.

---

## What it is

A separate, independently-managed password stored in
`core_auth_user.password_hash` (bcrypt) for super-admin users only.
Used exclusively against `POST /auth/login` — the legacy password
path. `DualModeJwtDecoder` accepts the resulting HS256 token alongside
RS256 tokens from Keycloak, so this credential keeps working regardless
of OIDC mode.

## What it is NOT

- **NOT** your daily SSO password. That password lives in Keycloak.
  The two are not synced and should not match.
- **NOT** something normal users have. Regular users authenticate
  exclusively via SSO; when KC is down, they wait.
- **NOT** a fallback admin can use to reset other users' passwords.
  It only authenticates the holder; for resetting another user's KC
  password use the Keycloak admin console.

## Why a separate credential

Keycloak stores passwords as argon2id; we store break-glass as bcrypt.
The two algorithms can't be translated, and the SSO login path doesn't
check our DB. If we tried to keep them in sync, every KC password change
would either:

- propagate cleartext from KC to our backend (cleartext exposure, bad), or
- fail silently (divergence, breaks the recovery path).

Keeping them independent — and rotating each on its own cadence — is
the only design that doesn't require trust assumptions one of those
systems can't satisfy.

---

## Setting / rotating

### From the UI (recommended)

1. Log in via SSO as a super-admin user.
2. Top-right user menu → **Break-glass password**. (Hidden for
   non-super-admin users; only visible when `VITE_OIDC_ENABLED=true`.)
3. The dialog shows whether you currently have one configured, plus a
   form to set a new value.
4. Save. Store the value in your password manager or organisational
   vault — there is no recovery path.

### From the API

```bash
# Get status — { configured: true|false }
curl -X GET https://<host>/api/me/break-glass-password/status \
  -H "Authorization: Bearer $SSO_TOKEN"

# Set / rotate
curl -X POST https://<host>/api/me/break-glass-password \
  -H "Authorization: Bearer $SSO_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"password": "<your new break-glass password>"}'
```

Both endpoints require an authenticated session (so a SUPER_ADMIN role
binding for the caller is required) — they live under `/me/...`.

### From SQL (genuine last resort)

```sql
-- The hash is bcrypt strength 12. Generate one with any bcrypt tool;
-- the JVM equivalent is org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).
UPDATE core_auth_user
   SET password_hash = '$2a$12$...',
       update_user   = 'manual-sql-recovery',
       update_time   = NOW()
 WHERE tenant_id = 'default'
   AND username  = 'admin';
```

Don't use this unless the API path is unreachable (full SPA outage AND
backend somehow up). Audit log will not capture the rotation.

---

## Using it

Only when KC is unreachable:

1. Navigate to `/login`. Five rapid clicks on the application logo (or
   header brand mark) unlocks the legacy password form — the SPA
   normally auto-redirects to KC, this hot-zone bypasses it.
2. Enter your super-admin username + break-glass password.
3. You are now logged in with an HS256 token. Do what you came for.

Once KC is back, you should:

1. Log out.
2. Log in via SSO normally.
3. Consider rotating the break-glass credential if you suspect it was
   needed because of a credential issue rather than infrastructure.

## Best practices

| Practice | Why |
|---|---|
| Rotate every quarter. | Limits the blast radius if the credential leaks via a screenshot / email / commit. |
| Store in a password manager, not a sticky note. | If two super-admins rotate independently, the manager is the source of truth. |
| Don't reuse your SSO password. | A KC compromise should not also compromise the break-glass path. |
| Rotate immediately after every use. | The use itself may have been logged / visible to onlookers. |
| At least two super-admins per environment. | If one loses their credential, another can use the admin console to grant them a new one. |

---

## Disabled paths in OIDC mode

| Path | OIDC behavior | Reason |
|---|---|---|
| `POST /admin/auth/reset-password` | rejects with 400 | Writes local password_hash only; never propagates to KC; can also undo the JIT-bind cleanup invariant on non-super-admin rows. |
| User management page → "Reset password" button | grey, hover tooltip explains | Same reason; UI gates the trigger so the operator gets feedback without round-tripping to a rejecting endpoint. |
| `POST /me/break-glass-password` | active, super-admin only | This is the only supported way to set a break-glass credential. |

If you really do need to set a password for a normal user (e.g.
debugging a stuck account), use the Keycloak admin console — that
writes argon2id into KC's `credential` table, which is the credential
the SSO path actually checks.

---

## Self-alert on use

Every successful `/auth/login` in OIDC mode fires a fire-and-forget
email to the user's own address with:

- timestamp of the login
- client IP and user agent
- tenant id
- a prominent "Sign in via SSO and rotate" button linking to the SPA

The semantics: in OIDC mode, `/auth/login` is the **only** path that
takes a username + password. A successful one means break-glass was
used. The alert lets the legitimate owner detect "wait, that wasn't me"
within minutes:

```
T0  attacker uses leaked break-glass credential
T1  AuthService.login succeeds → MailService.sendHtmlAsync dispatched
T2  owner's inbox dings ~seconds later (provided SMTP is healthy)
T3  owner sees "From IP 198.51.100.x" — not them — opens SPA, rotates
```

The alert is **self-only** (sent to the user who authenticated, not all
super-admins). Two reasons:

1. **Targeting**: a "someone is attacking my account" notification
   should go where the legitimate owner reads mail, not where the
   org-wide noise floor swallows it.
2. **Privacy**: super-admin login events aren't broadcast across the
   admin pool for free; lateral surveillance is a different feature
   request with different trust assumptions.

If you want broader broadcasting (e.g. PagerDuty webhook, Slack
channel, every super-admin), add a downstream consumer of the audit
log — `LoginAuditService.record(...)` writes every login attempt to
`core_auth_login_log` with `success` and `failure_reason`. A scheduled
job that watches for `success=true AND mode=oidc` rows on accounts
with SUPER_ADMIN role gives you the same signal at any granularity.

Mail dispatch is **best-effort** — if SMTP is down (likely in the same
incident that drove the operator to break-glass in the first place),
the login still succeeds. Audit log + console log still capture the
event regardless of email health.

## Threat model

| Attack | Mitigation |
|---|---|
| Initial break-glass password leaks (committed to git, sent over Slack, etc.) | Rotate immediately via UI/API. SSO is unaffected — login as super-admin, open Break-Glass dialog, set new value. |
| Attacker has SSO session of a super-admin and rotates break-glass behind their back | All `setBreakGlassPassword` calls are op-logged via `@OpLog(action="auth.breakGlassSet")`. Regular review of the audit log surfaces unexpected rotations. |
| Attacker brute-forces /auth/login | `AccountLockoutService` (5 failures / 15 min window) gates the username. Also `AuthRateLimitFilter` is per-IP. |
| KC compromised → all SSO accounts owned | Break-glass independent → attacker can't ride the KC compromise into our system unless they also have the bcrypt hash. |

## See also

- `backend/.../auth/controller/BreakGlassController.java` — endpoints
- `backend/.../auth/service/AuthService.java` — the legacy login path
- `backend/.../security/DualModeJwtDecoder.java` — accepts both HS256
  (break-glass) and RS256 (SSO)
- `frontend/src/components/layout/BreakGlassPasswordDialog.vue` — UI
- `docs/migration-password-to-sso.md` — context for why super-admin
  rows preserve `password_hash` during SSO migration
