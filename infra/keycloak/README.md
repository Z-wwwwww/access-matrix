# Keycloak — local SSO/IdP for access-matrix

**English** · [中文](README.zh-CN.md)

We use **Keycloak 26+** as the OIDC identity provider during development.
It runs as a standalone Java process (no Docker required) and talks to the
same Postgres instance the application uses, isolated by schema.

## Why local Keycloak

The backend's `app.security.mode: jwt` was designed against an OIDC-shaped
JWT (`tid` / `sub` / `preferred_username` / `scope` claims — see
`application.yml`). Keycloak is the easiest open-source IdP that emits
exactly that shape and supports multi-tenancy out of the box (one **realm**
per tenant).

For dev, running Keycloak locally:

- gives every contributor a real OIDC server to authenticate against;
- keeps the multi-tenant chain (`X-Tenant-Id` header → realm → `tid` claim
  → `RequestContext.tenantId`) end-to-end testable without a shared service;
- works offline.

## Prerequisites

| Requirement | Notes |
| --- | --- |
| **JDK 17+** | You already have JDK 25 for the backend. |
| **Postgres 17+** | Same instance the application uses (`127.0.0.1:5432`, database `new_inntouch_core`). |
| **`keycloak` schema** | Created with `CREATE SCHEMA IF NOT EXISTS keycloak;` — already done. |
| **Keycloak 26+ ZIP** | Download from <https://www.keycloak.org/downloads>. |

> No Docker needed. Keycloak ships as a Quarkus app; just extract the ZIP.

## One-time setup

1. **Download & extract Keycloak**

   ```powershell
   # Windows — script default path:
   #   C:\SERVER\keycloak-26.6.2\
   # Extract somewhere else? Set KEYCLOAK_HOME or edit start-keycloak.bat.
   ```

   ```bash
   # macOS / Linux — script default path (Git Bash / WSL):
   #   /c/SERVER/keycloak-26.6.2/
   # Or set KEYCLOAK_HOME explicitly.
   ```

2. **(Already done) Verify the `keycloak` schema exists**

   ```sql
   -- run as postgres against new_inntouch_core
   CREATE SCHEMA IF NOT EXISTS keycloak;
   ```

3. **(Optional) Set `KEYCLOAK_HOME`** if you extracted somewhere other than
   the default the launcher expects:

   ```powershell
   setx KEYCLOAK_HOME "C:\SERVER\keycloak-26.6.2"
   ```

## Running

```powershell
# Windows
infra\keycloak\start-keycloak.bat
```

```bash
# macOS / Linux
infra/keycloak/start-keycloak.sh
```

Then open:

- Admin UI — <http://localhost:8180/admin>  (user `admin`, password `admin`)
- Realm UI — <http://localhost:8180/>

> Port 8180 deliberately avoids the Spring Boot default 8080.

The first boot lets Keycloak run its own Liquibase migrations against the
`keycloak` schema (takes ~30 s). Subsequent boots are < 10 s.

## Defining a tenant (realm)

Multi-tenancy convention:

> **realm name == tenant id == subdomain label**

so adding tenant "acme" means there's a realm `acme` in Keycloak, every JWT
out of that realm has `tid="acme"`, and the SPA reaches it at
`https://acme.access-matrix.com/`. The frontend's `utils/tenant.js` and the
backend's MyBatis `TenantLineInnerInterceptor` both pivot off this convention.

### Adding a new tenant (recommended)

Use the committed helper that clones `demo-realm.json` and retargets the
realm name + `tid` hardcoded-claim-mapper:

```powershell
# Windows
.\infra\keycloak\new-tenant.ps1 -Name acme
```

```bash
# macOS / Linux
infra/keycloak/new-tenant.sh acme
```

Then restart Keycloak — `start-keycloak.{bat,sh}` already passes
`--import-realm`, so the new file is picked up on next boot. Verify in the
admin console (realm picker → `acme`), then provision the first admin user
via the Users tab.

### Adding a tenant manually via the admin UI

For one-offs without going through the helper:

1. Top-left realm picker → **Create realm**.
2. Realm name → the tenant id (e.g. `acme`).
3. Inside the realm:
   - **Clients** → create `access-matrix-backend` with Client authentication
     = OFF, Standard flow ON, valid redirect URIs =
     `https://acme.access-matrix.com/sso/callback` (or
     `http://localhost:5273/sso/callback` for dev). A wildcard registration
     `https://*.access-matrix.com/sso/callback` on every realm's client
     covers every tenant in one entry.
   - **Client scopes** → on the `access-matrix-backend`-dedicated scope, add
     a hardcoded-claim mapper named `tid`, claim name `tid`, claim value
     equal to the realm name. This is what wires `tenant_id` through to
     every downstream API call.
   - **Client scopes** → ensure `email`, `profile`, `roles` are in the
     "Default" assigned scopes.
   - **Users** → create at least one admin with credentials, password set
     to non-temporary, email verified.
   - **Realm settings → Themes → Login theme** → `access-matrix` (the
     branded theme committed under `infra/keycloak/themes/`).

## Exporting / committing realm configuration

Once a realm is configured the way you want it to ship to other developers:

1. Admin UI → realm → **Realm Settings** → kebab menu → **Partial export**
2. Tick "Include groups and roles" and "Include clients"
3. Save the JSON into `infra/keycloak/realms/<realm-name>.json`
4. Commit it. On next start, the `--import-realm` flag in the launcher
   will rehydrate the realm from this JSON on any contributor's machine.

> `realms/` 已经committed `demo-realm.json`（业务示例租户）和
> `system-realm.json`（平台运营租户）。新增租户请用 `new-tenant.ps1`
> 克隆 `demo-realm.json` 并改名，避免手编 JSON。

## Custom theme (access-matrix branding)

The committed login theme under `infra/keycloak/themes/access-matrix/`
restyles Keycloak's login pages to match the access-matrix Vue UI:

- Slate-50 page background (matches the SPA's `bg-background`)
- Tailwind blue-600 primary button + focus ring
- Rounded inputs (8 px) / card (12 px) / subtle shadow
- System UI font stack (matches the SPA)

The theme extends `keycloak.v2` (PatternFly-based) so we get every
flow's template for free (login / forgot-password / update-password /
verify-email / required-action / …) and only override colors / fonts /
spacing via CSS custom properties. Future Keycloak upgrades that touch
template HTML are absorbed automatically; only PatternFly version
bumps need attention here.

**Sync**: `start-keycloak.bat` / `.sh` `xcopy` / `rsync` the theme into
`$KEYCLOAK_HOME/themes/` on every launch so edits to
`infra/keycloak/themes/access-matrix/` take effect after a Keycloak
restart (or `Ctrl+Shift+R` in the login page if `--spi-theme-cache-themes=false`).

**Activation per realm**: the committed `demo-realm.json` sets
`"loginTheme": "access-matrix"`, so a fresh `--import-realm` picks it
up automatically. For a realm that already exists (created before this
theme was committed), apply the change one of two ways:

```bash
# Option A: admin console
#   Realm settings → Themes → Login theme → access-matrix → Save

# Option B: one-liner via kcadm
$KEYCLOAK_HOME/bin/kcadm.sh config credentials \
    --server http://localhost:8180 --realm master --user admin --password admin
$KEYCLOAK_HOME/bin/kcadm.sh update realms/demo -s 'loginTheme=access-matrix'
```

Adding more themes: drop a sibling directory under
`infra/keycloak/themes/<name>/` and set the realm's `loginTheme` /
`accountTheme` / `adminTheme` / `emailTheme` to that name.

## Multi-tenant routing (frontend ↔ realm)

The SPA picks "which realm am I logging into right now" at runtime, in
`frontend/src/utils/tenant.js`. Resolution priority:

1. `?tenant=<name>` query string (dev override on localhost, sticky to
   localStorage for subsequent reloads).
2. Subdomain of `window.location.hostname` — e.g. `acme.access-matrix.com`
   → realm `acme`. Reserved labels like `www`, `app`, `api`, `kc`, etc.
   fall through to the next source.
3. `localStorage.tenant_id` (carry-over from a previous explicit pick).
4. `"demo"`.

Once resolved, the same value drives two things in lockstep:

- `oidcConfig().issuer` becomes `${VITE_OIDC_ISSUER_BASE}/realms/<tenant>`,
  i.e. which realm the OIDC redirect targets.
- `X-Tenant-Id: <tenant>` rides on every axios request as a pre-auth fallback
  (post-auth the backend prefers the JWT's `tid` claim).

The redirect URI is derived from `window.location.origin` so subdomain
hosts come back to themselves. Register a wildcard pattern such as
`https://*.access-matrix.com/sso/callback` in the Keycloak client config so
adding a new tenant doesn't require touching valid-redirect-uri lists.

## Backend wiring (already done — `app.security.mode=oidc`)

Wired across two beans:

- `MultiRealmJwtDecoder` (in `core-infrastructure/security`) accepts tokens
  from any realm under `app.security.oidc.issuer-base-uri`. JWKS for each
  realm is fetched lazily on first sighting and cached. Falls back to
  single-realm `issuer-uri` for hardened single-tenant deploys.
- `OidcJitUserService` provisions a business `core_auth_user` row on first
  SSO login per tenant — gates JIT on the same trust prefix so an HS256
  break-glass token signed by `AdminAuthController.login` is correctly
  passed through.

The `AdminAuthController.login` password path stays available as
break-glass (`app.security.mode=jwt` or HS256 tokens accepted alongside
RS256 via `DualModeJwtDecoder`) so Keycloak being unavailable doesn't
lock everyone out.

## Troubleshooting

| Symptom | Likely cause |
| --- | --- |
| `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${...}` | Env vars not exported — run via the launcher script, not bare `kc.bat`. |
| `permission denied for schema keycloak` | Postgres user is not the schema owner. Fix with `ALTER SCHEMA keycloak OWNER TO postgres;`. |
| Admin UI shows "Update profile" loop | Forgot to mark the dev user's email as verified — toggle it in Users → Details. |
| 8180 already in use | Set `KC_HTTP_PORT=8280` (or similar) before running. |

## Production posture

For prod we run Keycloak in production mode (`kc.sh start`) behind a
TLS-terminating reverse proxy, with `hostname-strict=true`,
`proxy-headers=xforwarded`, and its own dedicated DB user — not the
application's `postgres` superuser.

### Network segmentation — the auth face vs. the admin face

This is the single most important production hardening decision, so it gets
its own section. Keycloak serves two categories of endpoint with **opposite**
exposure requirements:

| Category | Path prefix | Who uses it | Public reachability |
| --- | --- | --- | --- |
| **Auth face** | `/realms/<realm>/*`, `/resources/*` | End-user **browsers** (login, token, account, **tenant switching**) | **Must be public** |
| **Admin face** | `/admin/*` (admin console SPA **and** the Admin REST API) | Operators only | **Internal-only** |

The auth face *has* to be public: OIDC Authorization-Code flow redirects the
user's browser to `/realms/<tenant>/protocol/openid-connect/auth`. Tenant
switching (the realm-pill switcher in the login theme) is the same mechanism —
it navigates to the SPA which then hits `/realms/<new-tenant>/...`. So free
realm switching **requires** `/realms/*` to be proxied publicly. That is by
design and safe, because the auth face only exposes login/token endpoints that
users are supposed to reach.

The admin face does **not** need to be public. The KC admin console is a SPA
under `/admin/<realm>/console/` that does all its real work by calling the
**Admin REST API** under `/admin/realms/*`. Block `/admin/*` at the proxy and
the console is unusable from the internet **even with a correct admin
password** — the API it depends on is unreachable. This is why network
isolation, not password secrecy, is the *primary* control. Password secrecy is
the second layer (defense in depth), not the only line.

> **Why the two faces can be split cleanly:** they live under different path
> prefixes (`/realms/` vs `/admin/`), so a reverse proxy can allow one and deny
> the other with simple location rules — without affecting login or tenant
> switching.

### Recommended reverse-proxy rules

```nginx
# ── Auth face: public ──────────────────────────────────────────────
# Business users log in and switch tenants through these.
location /realms/    { proxy_pass http://keycloak_upstream; }
location /resources/ { proxy_pass http://keycloak_upstream; }

# ── Block the master realm specifically (see reasoning below) ───────
# Must come BEFORE the generic /realms/ rule, or place it as a more
# specific regex match. No legitimate business flow targets master.
location ~ ^/realms/master(/|$) { deny all; return 404; }

# ── Admin face: internal-only ───────────────────────────────────────
# Admin console SPA + Admin REST API. Operators reach these via VPN /
# bastion / internal network, never the public listener.
location /admin/     { deny all; return 404; }
```

Operators access the admin console from inside the network (VPN, bastion, or
an internal-only listener). Keycloak 26 also supports a dedicated admin
hostname so the console's own links never point at the public URL:

```bash
KC_HOSTNAME=https://auth.yourcompany.com          # public auth face
KC_HOSTNAME_ADMIN=https://kc-admin.internal:8443  # admin face, internal DNS only
```

### Recommendation: block the `master` realm at the public edge

**What:** in addition to denying `/admin/*`, explicitly deny
`/realms/master/*` on the public listener (the regex rule above).

**Why this is worth a dedicated rule:**

1. **`master` has no business purpose.** No tenant maps to it; no application
   client authenticates against it. The realm-name == tenant-id convention
   means every *real* tenant is `demo`, `system`, `acme`, … — never `master`.
   So blocking it publicly costs nothing functionally.

2. **`master` is the cross-realm super-realm.** Its admins can manage *every*
   other realm. It holds the bootstrap `admin` super-user. It is the highest-
   value target in the whole IdP. Keeping its login page off the public
   internet removes it from the attack surface entirely (no credential
   stuffing, no brute force, no exposure of its login flow / MFA config).

3. **Path uniformity makes it easy to forget.** `master` is reached through the
   exact same `/realms/<name>/*` structure as business realms — it is *not*
   special at the URL layer. That uniformity is precisely why an allow-all
   `/realms/*` rule silently exposes `master` too. The explicit deny rule makes
   the intent visible and auditable instead of relying on everyone remembering
   that `/realms/*` quietly includes the admin super-realm.

4. **Defense in depth, not the only defense.** Even if this rule is missing,
   `master` stays protected by (a) realm-scoped credentials — business users
   have no `master` account — and (b) the `/admin/*` block, which makes a
   `master` token useless. This rule is the cheap, explicit outer layer that
   means a single misconfiguration elsewhere doesn't expose the super-realm.

> The frontend `BLOCKED_REALMS` denylist in the login theme's `login.js`
> (`master`, `admin`, `www`, …) is a **UX guard, not a security control** — it
> only stops a confused user from typing `master` into the tenant switcher and
> hitting a "client not found" dead-end. Real protection is the proxy rules
> above plus realm-scoped credentials. Don't conflate the two.

### Backend → Keycloak admin credential (auto-bootstrapped service account)

When ops creates a tenant, the backend calls the Keycloak Admin REST API to
create the realm + first admin user. The backend authenticates for **all** such
calls as a dedicated **service-account client** (`access-matrix-provisioner`)
using the `client_credentials` grant — **one path, dev and prod identical, no
username/password at runtime**. See `KeycloakAdminClientFactory.runtimeClient()`.

**That provisioner client is auto-created at startup** by
`KeycloakProvisionerSeeder` — you never click around the Keycloak console. On
boot (when `app.keycloak.bootstrap.enabled=true`) it uses a one-time bootstrap
admin credential to:

1. create the `access-matrix-provisioner` client (confidential, service accounts
   on) and sync its secret to `CORE_KEYCLOAK_PROVISIONER_SECRET`;
2. grant its service account the `master` realm role **`create-realm`**;
3. grant **`manage-users`/`view-users`** on each pre-existing managed realm
   (`demo`, `system`) — those were imported, not created by the provisioner, so
   Keycloak's create-realm auto-grant doesn't cover them.

Realms the provisioner later creates itself (acme, …) are auto-granted
management by Keycloak, so they need no extra setup.

**Why a service-account client (not the `master` admin user):** the runtime
credential is a machine secret with **least privilege** (`create-realm` + manage
on known realms — not the omnipotent `master` `admin`), it isn't subject to
human-account lifecycle hazards (password policy/expiry/"update password"/MFA —
the "invalid_grant 400" you hit when the admin password drifts), it's
rotatable, and a leak is bounded + revocable (delete/rotate one client) rather
than a full IdP-root compromise.

#### Operator runbook (prod)

First deploy — set in the backend env (see `backend/.env.example`):

```bash
APP_SECURITY_MODE=oidc
CORE_OIDC_ISSUER_BASE_URI=https://auth.yourcompany.com
CORE_KEYCLOAK_SERVER_URL=https://auth.yourcompany.com

CORE_KEYCLOAK_PROVISIONER_SECRET=<strong secret from vault>   # the runtime identity

# one-time bootstrap (reuses your existing KC root admin):
CORE_KEYCLOAK_BOOTSTRAP_ENABLED=true
CORE_KEYCLOAK_BOOTSTRAP_USERNAME=<KC root admin>
CORE_KEYCLOAK_BOOTSTRAP_PASSWORD=<KC root password>
CORE_KEYCLOAK_BOOTSTRAP_MANAGED_REALMS=demo,system
```

Boot once → watch for `[kc-provisioner] provisioner ready …`. Then **harden**:

```bash
# the provisioner client now persists in Keycloak — drop the one-time root cred
unset CORE_KEYCLOAK_BOOTSTRAP_USERNAME CORE_KEYCLOAK_BOOTSTRAP_PASSWORD
CORE_KEYCLOAK_BOOTSTRAP_ENABLED=false
```

Steady state: the only Keycloak secret the backend holds is
`CORE_KEYCLOAK_PROVISIONER_SECRET`.

**Rotation:** routine rotation = rotate the secret. Update
`CORE_KEYCLOAK_PROVISIONER_SECRET`, re-enable bootstrap for one boot (it re-syncs
the client's secret to the new value), then disable again — or rotate it in the
KC console and update the env. The client object, its service account, and all
role grants are unaffected by a secret change.

**Caveat (DR / migration only):** if the provisioner *client* is deleted or you
stand up a fresh Keycloak, the recreated client is a new identity and won't hold
Keycloak's auto-granted management on previously-created tenant realms — re-run
bootstrap and re-grant those realms. This does not happen on normal secret
rotation.

**Hygiene:** keep the provisioner secret in a secrets manager (Vault / AWS
Secrets Manager / sealed secret). For the strongest posture, replace the shared
secret with mTLS or signed-JWT client auth (`private_key_jwt`).
