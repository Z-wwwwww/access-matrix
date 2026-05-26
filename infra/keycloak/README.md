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

For each tenant in the application, create a Keycloak realm with the **same
name as the tenant_id**. The `default` tenant maps to a realm named `default`.

1. Top-left realm picker → **Create realm**
2. Realm name → `default` (or the tenant id from the X-Tenant-Id header)
3. Inside the realm:
   - **Clients** → create one for the backend (`access-matrix-backend`)
     with Client authentication = OFF, Standard flow ON, root URL =
     `http://localhost:9135/api`, valid redirect URIs =
     `http://localhost:9135/api/login/oauth2/code/keycloak`.
   - **Client scopes** → ensure `email`, `profile`, `roles` are in the
     "Default" assigned scopes.
   - **Users** → create at least one test user with credentials and at
     least one role (mapped via "Realm roles" tab).

## Exporting / committing realm configuration

Once a realm is configured the way you want it to ship to other developers:

1. Admin UI → realm → **Realm Settings** → kebab menu → **Partial export**
2. Tick "Include groups and roles" and "Include clients"
3. Save the JSON into `infra/keycloak/realms/<realm-name>.json`
4. Commit it. On next start, the `--import-realm` flag in the launcher
   will rehydrate the realm from this JSON on any contributor's machine.

> The current `realms/` directory only has a `.gitkeep`; once we agree on
> the dev realm shape we'll commit the first export there.

## Backend wiring (next PR)

Not yet wired — the backend still uses the local `AdminAuthController.login`
flow. The plan once the dev realm exists:

- Add `spring-boot-starter-oauth2-resource-server` to `core-bootstrap`.
- Set `spring.security.oauth2.resourceserver.jwt.issuer-uri =
  http://localhost:8180/realms/default`.
- Map `tid` claim to `RequestContext.tenantId`, `sub` to user id.
- Keep `AdminAuthController.login` behind a feature flag for
  break-glass / no-Keycloak local runs (`CORE_AUTH_MODE=password`).

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
