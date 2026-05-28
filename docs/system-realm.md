# The `system` realm — platform operations

This document describes the `system` realm: a hidden Keycloak realm
reserved for the SaaS operator's own staff, used to manage business
tenants and run cross-tenant administration.

It is **not** for business customers. Their data and login flows live
under `demo` / `acme` / `<their-tenant-id>` realms; the `system` realm
sits alongside as the "control plane."

## Why a separate realm

Three concerns drove the separation:

1. **Realm-level isolation**. Keycloak realms isolate signing keys,
   session storage, user tables, and policies. Putting platform staff
   in the same realm as a business tenant would mean a misconfiguration
   in one could leak into the other.

2. **Different security posture**. The system realm runs stricter
   defaults (see below) — brute-force lockout at 5 failures, shorter
   sessions, no self-registration. Business tenants typically can't
   afford to be that strict (their helpdesk burden would explode);
   platform-ops staff can.

3. **Cross-tenant authority needs a bearing surface**. The MyBatis-Plus
   tenant interceptor injects `WHERE tenant_id = ?` on every business
   query, scoped by the caller's JWT `tid`. A system-realm caller has
   `tid='system'`, which the interceptor recognises as "bypass scoping
   for this caller" — letting them SELECT/UPDATE across all business
   tenants. Without a dedicated tenant id, this bypass would have no
   clean trigger.

## Realm config — what differs from `demo`

| Setting | demo | system |
|---|---|---|
| `displayName` | "Demo Tenant" | "Platform Operations" |
| `accessTokenLifespan` | 1800 (30 min) | 900 (15 min) |
| `ssoSessionMaxLifespan` | 36000 (10 h) | 14400 (4 h) |
| `bruteForceProtected` | false | true |
| `failureFactor` | 30 | 5 |
| `waitIncrementSeconds` | 60 | 300 |
| `maxFailureWaitSeconds` | 900 | 3600 |
| `tid` mapper claim.value | `demo` | `system` |

Everything else (client config, default scopes, theme, login flow)
mirrors `demo`. Strict bits live exclusively on session/lockout.

## Identities

| Construct | Where it lives | Notes |
|---|---|---|
| `system` realm | Keycloak | Imported from `infra/keycloak/realms/system-realm.json` |
| `tid` claim on JWTs from this realm | Keycloak protocol mapper | Always `system` |
| `core_auth_user.tenant_id='system'` rows | Postgres | Platform-ops users live here, one row per staff member |
| `PLATFORM_ADMIN` role | `core_rbac_role` under tenant `system` | Seeded by V26 with ULID `00000000000000000000ROLE50` |
| `platform:*` wildcard permission | `core_rbac_permission` under tenant `system` | Seeded by V26 |
| Default ops user (dev only) | `ops` / `ops` | Seeded by `SystemAdminSeeder` + `SystemKeycloakAdminSeeder` under `@Profile("local")` |

## Permission model — `*:*` vs `tenant:*`

The system has two super-wildcards, one per scope, and they do NOT
shadow each other:

| Wildcard | Who holds it | What it grants | What it does NOT grant |
|---|---|---|---|
| `*:*` | PLATFORM_ADMIN (in `system` realm) | The full `platform:` namespace — tenant management, cross-tenant ops | Business-tenant data — they can't impersonate `acme`'s SUPER_ADMIN |
| `tenant:*` | SUPER_ADMIN of a business tenant (e.g. `demo`, `acme`) | Everything within that tenant — `user:*`, `role:*`, `auth:*`, `dept:*`, etc. | The `platform:*` namespace — they can't reach `POST /platform/tenants` |

The symbol assignment maps the "looks most powerful" wildcard (`*:*`)
to the "most powerful in scope" role (PLATFORM_ADMIN); the business
super-admin gets `tenant:*`, which reads as "I'm the boss within this
tenant" — the scope is right there in the name.

This split is deliberate: a SaaS company's operations staff manage the
billing / signup / suspension of tenants, but they should NOT be able
to read a customer's business records. The two scopes enforce that
boundary in code (PermissionMatcher rejects shadow attempts both ways),
not just by trust.

### Implications

- Granting "godmode" requires BOTH wildcards explicitly (rare, but
  auditable when needed). No single wildcard covers everything.
- `platform:*` still works as a narrower delegation (resource wildcard)
  if you want a junior platform-ops role with all platform perms but
  no `*:*` reserved-for-PLATFORM_ADMIN signal.
- `tenant:*` matching naturally extends to any future business
  permission added in any module — no need to update the wildcard
  registration as the perm catalogue grows.

## The MyBatis-Plus bypass

`MybatisPlusConfig.mybatisPlusInterceptor()` configures a
`TenantLineInnerInterceptor` whose `ignoreTable(...)` returns true
when `RequestContext.tenantId == "system"`. This bypasses the
`WHERE tenant_id = ?` injection on ALL tables for system-realm callers.

Why blanket-skip rather than per-table:

- The role check (`platform:*`) happens at the controller layer via
  `@RequiresPermission`. By the time SQL is being emitted, the
  authorization decision is already made — the SQL layer doesn't need
  to second-guess it.
- The alternative (`@InterceptorIgnore` on every cross-tenant mapper
  method) scales poorly and leaks intent into hundreds of files.

Implication: **a bug that lets a non-PLATFORM_ADMIN caller arrive with
`tid='system'` would let them read all tenants**. Defense in depth lives
at the realm boundary (Keycloak refuses to issue a `tid='system'` token
without the user existing in that realm) and at the role check
(controllers refuse without the `platform:*` permission).

## Local dev setup

After cloning the repo and running the backend with `local` profile:

```
SystemAdminSeeder         → inserts ops user into core_auth_user (tenant=system)
SystemKeycloakAdminSeeder → mirrors the user into Keycloak's system realm (mode=oidc only)
V26                       → seeds PLATFORM_ADMIN role + platform:* permission
```

Sign in as `ops` / `ops` and navigate to `/platform/tenants` — the
tenant management console (see "Managing business tenants" below) lets
you create / suspend / hard-delete business tenants from there.

## Production setup

Provision the system realm via:

```
$KEYCLOAK_HOME/bin/kc.sh start --import-realm
  --hostname=https://idp.your-saas.com
```

The committed `system-realm.json` is the template. Tighten further:

- Set a real `smtpServer` block so admin password resets work.
- Enable MFA (`CONFIGURE_TOTP` in `requiredActions`) for every ops user.
- Use a private network for the realm's admin console — platform ops
  shouldn't be reachable from public internet.
- Rotate the ops users' passwords / tokens on the org's own schedule;
  the `SystemAdminSeeder` is `@Profile("local")` and won't run in prod.

## Adding a platform-ops user

Until the tenant-management UI lands:

```
1. KC admin console → realms picker → system → Users → Add user
2. Set username, email, enabled, emailVerified
3. Credentials tab → set permanent password
4. Insert matching row into core_auth_user with tenant_id='system'
5. Link the row to PLATFORM_ADMIN role via core_rbac_user_role
   (also tenant_id='system')
6. On first SSO login, OidcJitUserService binds the rows
```

After the tenant-management PR lands, this becomes a single REST call
plus an email.

## Managing business tenants

The tenant lifecycle from the `/platform/tenants` console mirrors a
"recycle bin" model — every destructive step needs an explicit
predecessor, and the most destructive step needs a typed confirmation:

```
                    suspend             hard delete
   ┌──────────┐  ─────────────►  ┌──────────┐  ────────────►  ☒ gone
   │  active  │                  │ suspended │   (typed conf.)   forever
   └──────────┘  ◄─────────────  └──────────┘
                     resume
```

### Create (POST /platform/tenants)

End-to-end onboarding inside one `@Transactional`:
1. Create Keycloak realm from the demo template (KC first → no orphan rows)
2. Insert `core_tenant` registry row
3. Seed `core_numbering_management` (per-tenant counter)
4. Seed RBAC: tenant:* permission + SUPER_ADMIN role + role-permission
   binding + clone of demo's menu tree
5. Create first admin user (no password) + bind to SUPER_ADMIN
6. Create matching Keycloak user (no credentials)
7. Mint an invite token and email the link to `contactEmail`

The invite recipient hits `/invite/<token>`, sets a password, and is
the new tenant's first SUPER_ADMIN.

### Suspend (POST /platform/tenants/{id}/suspend)

- `status` flips 1 → 0; `mark` stays 1
- KC realm `enabled` flips to false (active sessions get kicked out
  on next refresh; new logins refused)
- Tenant stays visible in the list with a "Suspended" badge
- Reversible via Resume

### Resume (POST /platform/tenants/{id}/resume)

- `status` flips 0 → 1; KC realm `enabled` back to true
- Idempotent (already-active resume is a no-op)

### Hard delete (DELETE /platform/tenants/{id})

"Empty recycle bin" — the only destructive operation.

**Prerequisites**:
- Tenant must currently be suspended (`status=0`). Active tenants are
  refused — the UI doesn't even show a delete button on active rows.
- Body must include `confirmCode` matching the tenant's `tenant_code`
  exactly. The frontend gates on the same typed match; the backend
  re-validates so a curl-armed caller can't slip a path-id past.
- Built-in tenants (`system`, `demo`) refused.

**Order (DB-first)**:
1. `DELETE FROM <every per-tenant table> WHERE tenant_id = ?` in
   FK-safe order (junction tables → parents → users → auxiliaries).
   Currently hardcoded; adding a new per-tenant table for a future
   business module means adding one line to `TenantAdminService.hardDelete`.
2. `kc.realm(code).remove()` — drops realm + users + sessions + clients.
3. `DELETE FROM core_tenant WHERE id = ?` (registry row last).

DB-first is deliberate: a DB failure leaves the realm intact and the
registry row intact for retry. The reverse would leave data unreachable
but still consuming space, requiring manual realm recreation to recover.

**Irreversible**. No undo. Recovery is "restore from backup", which
requires DB + KC backups timed before the delete.

### Support sessions

See [§ Support sessions](#support-sessions-platform-ops-impersonation)
below — short-lived JWT impersonation for ticket triage, with audit.

## Support sessions (platform-ops impersonation)

When a tenant raises a support ticket — "my report shows wrong numbers"
— the platform ops team needs to act inside that tenant briefly. Without
a tool, they either ssh into the DB (no UI, no audit) or borrow a
customer admin's credentials (no accountability). The "support session"
feature is the official path.

### What it does

`POST /platform/tenants/{id}/support-session` (gated by
`platform:tenant:impersonate`, body `{ reason }`) mints a 30-minute
HS256 JWT with:

- `sub` = the target tenant's oldest active SUPER_ADMIN user id
- `tid` = the target tenant code
- `scope` = `tenant:*` (full SUPER_ADMIN authority within the tenant)
- `preferred_username` = `"[support] <ops>"` — visible audit prefix
- `act` claim — RFC 8693-style actor record:
  ```json
  {
    "sub":        "<ops user id in system tenant>",
    "tid":        "system",
    "username":   "<ops username>",
    "session_id": "<uuid>",
    "reason":     "<text from the request body>",
    "mode":       "FULL"
  }
  ```

The frontend stashes the ops token + tenant_id under separate localStorage
keys before overwriting with the support-session values, so on terminate
the original session restores cleanly. A persistent red banner at the
top of every page shows the active session, countdown, and a Terminate
button.

### Audit trail — two layers

1. **Username prefix**. The downstream oplog aspect reads
   `RequestContext.username()` (populated from JWT
   `preferred_username`), so every write made during the session lands
   in `core_oplog` as `[support] ops`. A routine "what did ops do in
   acme today?" query surfaces these immediately, no JWT decoding needed.

2. **Platform-side oplog row at session start**. The
   `@OpLog(action="tenant.impersonate.start")` annotation on the
   controller writes a separate row in the system tenant's audit log
   with `target_id = <acme registry id>` and `request_body` = the
   reason. So "I started a support session for OS-1234 at HH:MM" is
   captured before the actual support work even begins.

3. **(Optional, future)** Anyone wanting the full forensic trail can
   decode the JWT and read the `act` claim — the structured record is
   there even though no current code projects it into a separate column.

### Why we use the target's SUPER_ADMIN as the JWT subject

The alternative is a per-tenant shadow `__support__` user (cleaner
audit, since the tenant's own SUPER_ADMIN actions wouldn't be confused
with the platform-ops support work). But that adds DDL + a confusing
extra row in `core_auth_user`. For v1 we use the existing SUPER_ADMIN —
the `[support]` prefix + `act` claim carry the disambiguation. The
shadow-user refactor is a follow-up if the audit ambiguity becomes a
problem in practice.

### Known limitations (v1)

- **FULL-mode only** — the minted token has `tenant:*`, including writes.
  A READ_ONLY mode (scope reduced to `*:read`) is tracked as a follow-up;
  today the audit trail is the sole protection against bad writes.
- **No server-side revoke list**. Terminating in the UI just discards
  the token client-side. The token remains valid on the backend until
  its `exp` (30 min). Adding the `session_id` to `ForceLogoutService`'s
  Redis kickout set would close this — small follow-up.
- **Built-in tenants refused**. `system` and `demo` cannot be the target
  of a support session — no operational reason, and the blast radius if
  it ever went wrong is high. Hard-coded refusal.
- **No FE auto-refresh**. 30 minutes is meant to cover one triage cycle;
  there is no extend/renew endpoint. If a session genuinely needs more,
  terminate + restart (which generates a fresh `session_id` and a new
  oplog row, preserving accountability).

### Operator runbook

```
1. ops logs into ?tenant=system, navigates to /platform/tenants
2. Find the tenant row → click the LifeBuoy (lifesaver) icon
3. Enter a non-trivial reason (≥5 chars; "test" gets rejected client-side)
4. Click "Start support session" → page reloads under the new identity
5. Red banner at top shows: "Acting as <displayName> (<code>) — MM:SS"
6. Do the work. Every write lands in core_oplog as "[support] ops".
7. Click Terminate in the banner OR wait for the 30-min auto-expire.
8. Page reloads back to /platform/tenants under the ops identity.
```

## See also

- `infra/keycloak/realms/system-realm.json` — realm template
- `backend/.../bootstrap/startup/SystemAdminSeeder.java` — DB seed
- `backend/.../bootstrap/startup/SystemKeycloakAdminSeeder.java` — KC sync
- `backend/.../core-common/security/BuiltInRoles.java` — `PLATFORM_ADMIN_ID`
- `backend/.../core-system/security/PlatformPermissions.java` — `platform:*` codes
- `backend/.../core-infrastructure/config/MybatisPlusConfig.java` — the bypass
- `backend/.../core-system/platform/service/TenantImpersonationService.java` — support-session mint
- `backend/.../core-infrastructure/security/JwtIssuer.java` — `issueSupportSession`
- `frontend/src/stores/auth.js` — `enterSupportSession` / `terminateSupportSession`
- `frontend/src/components/layout/SupportSessionBanner.vue` — red banner
- `backend/core-bootstrap/.../db/migration/V26__system_platform_admin.sql` — role / permission seed
