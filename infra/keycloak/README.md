# Keycloak — local SSO/IdP for access-matrix

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

Use the committed helper that clones `default-realm.json` and retargets the
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

> The current `realms/` directory only has a `.gitkeep`; once we agree on
> the dev realm shape we'll commit the first export there.

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

**Activation per realm**: the committed `default-realm.json` sets
`"loginTheme": "access-matrix"`, so a fresh `--import-realm` picks it
up automatically. For a realm that already exists (created before this
theme was committed), apply the change one of two ways:

```bash
# Option A: admin console
#   Realm settings → Themes → Login theme → access-matrix → Save

# Option B: one-liner via kcadm
$KEYCLOAK_HOME/bin/kcadm.sh config credentials \
    --server http://localhost:8180 --realm master --user admin --password admin
$KEYCLOAK_HOME/bin/kcadm.sh update realms/default -s 'loginTheme=access-matrix'
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
4. `"default"`.

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

## Production posture (out of scope here)

For prod we will run Keycloak in production mode (`kc.sh start`) behind a
TLS-terminating reverse proxy, with `hostname-strict=true`,
`proxy-headers=xforwarded`, and its own dedicated DB user — not the
application's `postgres` superuser.
