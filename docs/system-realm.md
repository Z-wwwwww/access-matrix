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

## Permission model — `platform:*` vs `*:*`

The two wildcards do NOT shadow each other:

| Wildcard | Who holds it | What it grants | What it does NOT grant |
|---|---|---|---|
| `*:*` | SUPER_ADMIN of a business tenant | Everything within that tenant | `platform:*` perms (can't manage tenants) |
| `platform:*` | PLATFORM_ADMIN of `system` | Tenant management, cross-tenant ops | Business-tenant data access — they can't impersonate `acme`'s SUPER_ADMIN |

This split is deliberate: a SaaS company's operations staff manage the
billing / signup / suspension of tenants, but they should NOT be able
to read a customer's business records. The two perm namespaces enforce
that boundary in code, not just by trust.

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
you create / soft-delete business tenants from there.

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

## See also

- `infra/keycloak/realms/system-realm.json` — realm template
- `backend/.../bootstrap/startup/SystemAdminSeeder.java` — DB seed
- `backend/.../bootstrap/startup/SystemKeycloakAdminSeeder.java` — KC sync
- `backend/.../core-common/security/BuiltInRoles.java` — `PLATFORM_ADMIN_ID`
- `backend/.../core-system/security/SystemPermissions.java` — `platform:*` codes
- `backend/.../core-infrastructure/config/MybatisPlusConfig.java` — the bypass
- `backend/core-bootstrap/.../db/migration/V26__system_platform_admin.sql` — role / permission seed
