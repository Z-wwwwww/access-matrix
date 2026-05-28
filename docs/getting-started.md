# Getting Started

**English** · [中文](getting-started.zh-CN.md)

Get the whole project running locally from scratch. Two sign-in scenarios are covered:

- **Minimal setup** — sign in with the project's built-in password flow (5 minutes, no third-party services)
- **Full setup** — Keycloak SSO + email + invite flow (the same shape used in production)

---

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| **JDK** | 25 | The project uses Java 25 (virtual threads, scoped values). The Maven Wrapper does not auto-download a JDK. |
| **Node.js** | 20+ | Required by the Vite 6 frontend. `nvm` is recommended. |
| **PostgreSQL** | 15+ | Shared by both the main DB and the Keycloak schema. |
| **Redis** | 7+ | Permission cache, refresh tokens, force-logout set. |
| **Maven** | 3.9+ | The `mvnw` wrapper ships with the project, so a local Maven install is optional. |

**Optional**:

| Tool | Purpose |
|---|---|
| **Keycloak 26+** | Required for SSO mode ([download the ZIP](https://www.keycloak.org/downloads)). |
| **Docker Desktop** | Needed to run Testcontainers-based ITs; without it those ITs auto-skip. |
| **psql CLI** | Handy for manual DB work; the project's own `db/migration/V*.sql` runs automatically. |

---

## 2. Database setup

### 2.1 Create the main database

```bash
psql -h 127.0.0.1 -U postgres \
  -c "CREATE DATABASE new_inntouch_core WITH ENCODING 'UTF8' TEMPLATE template0;"
```

> The name `new_inntouch_core` is a historical project name. If you rename it, also update `spring.datasource.url` in `application-local.yml`.

### 2.2 (Optional) Create the Keycloak schema

Only needed if you plan to enable SSO:

```bash
psql -h 127.0.0.1 -U postgres -d new_inntouch_core \
  -c "CREATE SCHEMA IF NOT EXISTS keycloak;"
```

Keycloak stores its internal tables in this schema, physically separated from the application's business tables (`public` schema).

### 2.3 Flyway creates the tables automatically

**You do not need to create any business tables by hand.** On first backend startup, Flyway runs `backend/core-bootstrap/src/main/resources/db/migration/*.sql` in order from V1 through V29:

| Migration | Contents |
|---|---|
| V1-V4 | Metadata tables, user table, login log, numbering system |
| V5-V9 | RBAC core tables (role / permission / menu / dept / user_role / role_permission / role_menu / role_dept) |
| V10-V19 | Demo data, menu icons, multi-language menus, cleanup |
| V20 | `(tenant_id, username)` unique index (multi-tenant cutover at V20) |
| V21 | `core_auth_user.keycloak_id` link column (SSO) |
| V22 | `core_user_invite` invitation-token table |
| V24 | Password reset token + nullable `password_hash` (for the SSO-to-password migration path) |
| V25 | Rename the built-in tenant `default` to `demo` |
| V26 | Add `system` tenant + built-in `PLATFORM_ADMIN` role + MP-interceptor cross-tenant bypass |
| V27 | Central tenant registry `core_tenant`; seeded with `system` and `demo` rows |
| V28 | Platform-ops menu (`/platform/tenants`) bound to `PLATFORM_ADMIN` |
| V29 | Super-wildcard rename: `*:*` → platform super-admin, `tenant:*` → business super-admin (non-overlapping) |

---

## 3. Start the backend (local profile)

```bash
cd backend
./mvnw -pl core-bootstrap -am spring-boot:run -Dspring-boot.run.profiles=local
```

Windows PowerShell:

```powershell
cd backend
.\mvnw.cmd -pl core-bootstrap -am spring-boot:run "-Dspring-boot.run.profiles=local"
```

**Expected tail of the output**:

```
============================================================
  CORE-SERVICE is READY
------------------------------------------------------------
  profile        : local
  port           : 9135
  context-path   : /api
  security.mode  : permit-all   (or oidc, depending on application-local.yml)
============================================================
LocalAdminSeeder: ensured demo-admin user (id=...) is bound to SUPER_ADMIN role
SystemAdminSeeder: linked ops user to PLATFORM_ADMIN role
```

**What the first startup does** (`local` profile):

| Stage | Action |
|---|---|
| Flyway migrations | Runs V1-V29; creates every `core_*` table, two `core_tenant` rows (`system` / `demo`), two built-in roles (SUPER_ADMIN / Platform Admin), and five demo data-scope roles. |
| `LocalAdminSeeder` | In the demo tenant, seeds `demo-admin/demo-admin`, bound to SUPER_ADMIN + the HQ department. |
| `SystemAdminSeeder` | In the system tenant, seeds `ops/ops`, bound to PLATFORM_ADMIN. |
| `DemoSeeder` | Seeds five data-scope demo users (all with password `demo123`) plus 15 demo tasks. |
| `*KeycloakAdminSeeder` | In OIDC mode, syncs both admin accounts into the matching Keycloak realm. |

The full initial role / user matrix is in [README · Seed data](../README.md#-初始化与演示数据). Every seeder is `@Profile("local")` — **prod and dev deployments run only the migrations and seed no users**.

---

## 4. Start the frontend

```bash
cd frontend
npm install     # first time only
npm run dev
```

Open http://localhost:5273/login in the browser, sign in with `demo-admin` / `demo-admin`, and you're in.

---

## 5. Enable SSO (Keycloak mode)

Optional but strongly recommended — production deployments almost always use SSO.

### 5.1 Download Keycloak

Grab the **Server** ZIP (~200MB) from https://www.keycloak.org/downloads and extract it anywhere:

```
C:\SERVER\keycloak-26.6.2\    (Windows default path, matches the startup script)
~/tools/keycloak-26.6.2/      (mac/linux)
```

Docker is not required. Keycloak is a Quarkus Java application; just run it directly.

If you extracted to a different path, set an env var:

```powershell
setx KEYCLOAK_HOME "D:\my-path\keycloak-26.6.2"
```

### 5.2 Start Keycloak

```powershell
infra\keycloak\start-keycloak.bat
```

```bash
infra/keycloak/start-keycloak.sh
```

The startup script does a few things:
- Connects to the local Postgres using the `keycloak` schema (same DB instance as the app, schema-isolated).
- Binds HTTP to port 8180 (avoids clashing with Spring Boot's default 8080).
- `--import-realm` auto-loads `infra/keycloak/realms/*.json`: `demo-realm.json` (business demo) and `system-realm.json` (platform ops), both containing the `access-matrix-backend` client and the `tid` claim mapper.
- Comes up within 30 seconds.

**The first start runs Keycloak's own Liquibase migrations** (~30s); subsequent starts take under 10s.

Admin console: http://localhost:8180/admin (`admin` / `admin`).

For details on Keycloak configuration see [infra/keycloak/README.md](../infra/keycloak/README.md).

### 5.3 Switch to OIDC mode

Edit `backend/core-bootstrap/src/main/resources/application-local.yml`:

```diff
app:
  security:
-   mode: permit-all
+   mode: oidc
```

Edit `frontend/.env.development`:

```diff
- VITE_OIDC_ENABLED=false
+ VITE_OIDC_ENABLED=true
```

**Restart** both backend and frontend (a `.env` change requires restarting the Vite dev server — it does not hot-reload).

### 5.4 First SSO sign-in

On backend startup, `LocalKeycloakAdminSeeder` automatically creates a `demo-admin` user (password `demo-admin`, permanent) in Keycloak's `demo` realm, and `SystemKeycloakAdminSeeder` creates an `ops` user (password `ops`) in the `system` realm.

Open http://localhost:5273/login, click **"Sign in with SSO"**, you get bounced to Keycloak's login page, enter `demo-admin` / `demo-admin`, and you get redirected back to the frontend — signed in.

**What happens behind the scenes**:
1. Keycloak issues a JWT with `sub` = the Keycloak UUID and `tid` = `demo`.
2. The backend verifies the signature.
3. `OidcJitUserService` sees this UUID for the first time, takes the bind path, and writes `keycloak_id` onto the existing `core_auth_user` row seeded by `LocalAdminSeeder`.
4. `RequestContext.userId` becomes the business ULID, and the user is immediately super-admin.

Subsequent logins take the fast path (look up the business row by `keycloak_id`). The platform-ops flow is the same shape, but the frontend enters the `system` realm via `?tenant=system`, signs in as `ops/ops`, picks up PLATFORM_ADMIN, and unlocks the `/platform/tenants` menu.

---

## 6. Enable email (optional)

Unlocks the user-management **invite** mode (an invitation email is sent automatically to new users).

### 6.1 Get SMTP credentials

The default configuration targets Tencent Business Mail (`smtp.exmail.qq.com:465 SSL`). Any other SMTP service (Gmail / AWS SES / your company SMTP) works too — just change `CORE_MAIL_HOST` / `CORE_MAIL_PORT`.

Tencent Business Mail **requires a client-specific password** (not your login password):
1. Sign in at https://exmail.qq.com.
2. Settings (top right) → Security → Client-specific password → Add.
3. Copy the 16-character password (shown only once).

### 6.2 Configure

Environment variables (recommended):

```powershell
$env:CORE_MAIL_USERNAME = "your-name@your-company.com"
$env:CORE_MAIL_PASSWORD = "XXXXXXXXXXXXXXXX"  # 16-char app password
$env:CORE_MAIL_ENABLED = "true"
$env:CORE_MAIL_FROM = "your-name@your-company.com"
```

Or drop them into `application-local.yml` (**never commit the password**):

```yaml
spring:
  mail:
    username: your-name@your-company.com
    password: XXXXXXXXXXXXXXXX
app:
  mail:
    enabled: true
    from: your-name@your-company.com
```

### 6.3 Verify

Restart the backend; the startup log should contain:

```
MailHealthIndicator: Status UP
```

without anything like `AuthenticationFailedException: 500 Error: bad syntax`.

For day-to-day usage see the [User Guide](user-guide.md#2-user-management-invite--direct-modes).

---

## 7. Run the tests

### 7.1 Backend unit tests (no Docker required)

```bash
cd backend
./mvnw test
```

Expected: **49 core-system + 15 core-common = 64 unit tests pass**.

The Testcontainers ITs (`OidcJitProvisioningIT`, `MultiTenantSchemaIT`) need Docker; they auto-skip when Docker is unavailable.

### 7.2 Frontend tests

```bash
cd frontend
npm run test          # vitest runs 44 tests
npm run test:e2e      # playwright (both backend and frontend must be running)
```

---

## 8. Troubleshooting

### 8.1 Backend fails to start with `Driver org.postgresql.Driver claims to not accept jdbcUrl, ${CORE_DB_URL}`

**Cause**: `SPRING_PROFILES_ACTIVE` is unset, so the fail-closed prod default kicks in — and prod requires external env vars.

**Fix**: add `-Dspring-boot.run.profiles=local` to the startup command, or set `$env:SPRING_PROFILES_ACTIVE = "local"`.

### 8.2 SSO sign-in page says `Invalid parameter: redirect_uri`

**Cause**: the Keycloak client's Valid Redirect URIs do not include the frontend's callback path.

**Fix**: in the Keycloak admin console → `demo` realm → Clients → `access-matrix-backend` → Valid Redirect URIs, add `http://localhost:5273/*`.

The bundled `demo-realm.json` already includes this, so **you only hit this if you changed the redirect URI or rolled your own realm**.

### 8.3 Email sending fails with `500 Error: bad syntax`

**Cause**: Tencent Business Mail refuses to use your login password for SMTP auth.

**Fix**: generate a "client-specific password" (16 chars) in the ExMail console and use it as `CORE_MAIL_PASSWORD`.

### 8.4 After SSO sign-in you get bounced back to /login with `?sso_error=1`

**Cause**: `SsoCallback.vue` exchanged the authorization code for a token with Keycloak and the exchange failed.

**How to diagnose**: open DevTools → Network and find that POST to `/protocol/openid-connect/token`:
- HTTP 400 `invalid_grant` → state or code_verifier mismatch (typically caused by juggling multiple browser tabs at once).
- HTTP 401 → client misconfiguration; confirm that `VITE_OIDC_CLIENT_ID` matches the Client ID in Keycloak.
- Network error → Keycloak isn't running; check port 8180.

### 8.5 Backend fails to start with `log4j-slf4j2-impl cannot be present with log4j-to-slf4j`

**Cause**: a test bringing in `spring-boot-starter-logging` as a transitive dependency.

**Fix**: the three relevant POMs already declare the exclusion (core-common / core-system / core-bootstrap), so you should not hit this. If you add `spring-boot-starter-test` to a new module, copy the same exclusion. See the `spring-boot-starter-test` block in `core-common/pom.xml`.

### 8.6 The frontend login page does not show a "Sign in with SSO" button

**Cause**: `VITE_OIDC_ENABLED=false` (or unset).

**Fix**: edit `frontend/.env.development`, set `VITE_OIDC_ENABLED=true`, then **restart the Vite dev server** (Vite does not hot-reload `.env*` changes).

---

## 9. Next steps

- [**User Guide**](user-guide.md) — managing users / roles / permissions / data scopes / tenants
- [**Development**](development.md) — adding new menus / permissions / database migrations
- [**Deployment**](deployment.md) — getting it into production
