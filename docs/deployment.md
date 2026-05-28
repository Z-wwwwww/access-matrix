# Deployment

**English** · [中文](deployment.zh-CN.md)

Production deployment guide — taking access-matrix to staging / prod environments.

> For the local development setup, see [Getting Started](getting-started.md).

---

## 1. Recommended deployment architecture

```
                       ┌──────────────────┐
                       │  CDN / static    │  index.html + assets
                       │  hosting         │  (vite build → dist/)
                       │  (Cloudflare /   │
                       │   AWS CloudFront)│
                       └────────┬─────────┘
                                │
                                ▼  HTTPS
                       ┌──────────────────┐
                       │  Nginx / ALB     │  TLS termination
                       │  Reverse Proxy   │  /api  → backend
                       │                  │  /sso  → keycloak
                       └────────┬─────────┘
                                │
              ┌─────────────────┼─────────────────┐
              ▼                 ▼                 ▼
       ┌────────────┐   ┌────────────┐   ┌──────────────┐
       │ Backend    │   │ Backend    │   │  Keycloak    │
       │ pod×2~N    │   │ pod×2~N    │   │  pod×2       │
       │ :9135      │   │ :9135      │   │  :8180       │
       └─────┬──────┘   └─────┬──────┘   └──────┬───────┘
             │                │                  │
             └──┬──────────┬──┘                  │
                ▼          ▼                     ▼
        ┌────────────┐ ┌──────────┐      ┌──────────────┐
        │ PostgreSQL │ │  Redis   │      │  PostgreSQL  │
        │ (primary + │ │ (cluster │      │  schema      │
        │  replicas) │ │  / HA)   │      │  "keycloak"  │
        └────────────┘ └──────────┘      └──────────────┘
            ▲                                    │
            └──────── same PG cluster ───────────┘
                (separate schemas; can scale independently)
```

The backend is stateless and scales horizontally. Sessions, refresh tokens, and forced-logout sets all live in Redis, so the pods are interchangeable.

---

## 2. Environment variable reference

### 2.1 Backend

| Variable | Required | Description | Example |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Required | `prod` / `dev` / `staging`. Defaults to `prod` (fail-closed) | `prod` |
| `CORE_DB_URL` | Required | JDBC URL | `jdbc:postgresql://pg-prod:5432/access_matrix?stringtype=unspecified` |
| `CORE_DB_USERNAME` | Required | DB username | `access_matrix_app` |
| `CORE_DB_PASSWORD` | Required | DB password (inject via a secret manager) | |
| `CORE_REDIS_HOST` | Required | Redis host | `redis-prod.svc.cluster.local` |
| `CORE_REDIS_PORT` | Defaults to 6379 | | |
| `CORE_REDIS_PASSWORD` | Recommended | Redis AUTH password | |
| `CORE_REDIS_DB` | Defaults to 0 | DB index | |
| `CORE_JWT_SECRET` | Required in jwt mode | HS256 key, >= 32 bytes | 32+ character random string |
| `CORE_OIDC_ISSUER_URI` | Required in oidc mode | Keycloak realm URL | `https://sso.example.com/realms/acme` |
| `CORE_KEYCLOAK_SERVER_URL` | Required in oidc mode | Keycloak root URL | `https://sso.example.com` |
| `CORE_KEYCLOAK_ADMIN_REALM` | oidc mode | Realm holding the admin credentials | `master` |
| `CORE_KEYCLOAK_ADMIN_CLIENT_ID` | oidc mode | Service account client | `access-matrix-admin` |
| `CORE_KEYCLOAK_ADMIN_CLIENT_SECRET` | Required in oidc mode (production) | Service account secret | |
| `CORE_KEYCLOAK_ADMIN_USERNAME` | Dev only | Not recommended for production | |
| `CORE_KEYCLOAK_ADMIN_PASSWORD` | Dev only | Same as above | |
| `CORE_MAIL_HOST` | When email is enabled | SMTP host | `smtp.exmail.qq.com` or `email-smtp.ap-northeast-1.amazonaws.com` |
| `CORE_MAIL_PORT` | When email is enabled | 465 (SSL) or 587 (STARTTLS) | |
| `CORE_MAIL_USERNAME` | When email is enabled | SMTP user | |
| `CORE_MAIL_PASSWORD` | When email is enabled | SMTP password / app password | |
| `CORE_MAIL_ENABLED` | Defaults to false | Master switch | `true` |
| `CORE_MAIL_FROM` | When email is enabled | From address (must be on the same domain as the SMTP user) | `noreply@your-domain.com` |
| `CORE_MAIL_FROM_NAME` | Recommended | Display name for the sender | `Access Matrix` |
| `CORE_APP_BASE_URL` | When email is enabled | External URL of the app, used for links in emails | `https://app.example.com` |
| `CORE_INVITE_TOKEN_TTL` | Defaults to 7d | Invitation token lifetime | `7d` / `48h` |
| `CORE_CORS_ALLOWED_ORIGINS` | Required | Comma-separated list of allowed CORS origins | `https://app.example.com` |

### 2.2 Frontend (build time)

Configure these via `.env.production` when building the frontend:

```dotenv
# frontend/.env.production
VITE_API_BASE_URL=https://app.example.com/api

VITE_OIDC_ENABLED=true
VITE_OIDC_ISSUER=https://sso.example.com/realms/acme
VITE_OIDC_CLIENT_ID=access-matrix-frontend
VITE_OIDC_REDIRECT_URI=https://app.example.com/sso/callback
VITE_OIDC_SCOPES=openid profile email
```

Build:

```bash
cd frontend
npm ci
npm run build           # output in dist/
```

Upload `dist/` to a CDN or drop it into nginx's static directory.

---

## 3. Database preparation

### 3.1 Create a dedicated DB user (don't use the `postgres` superuser)

```sql
CREATE USER access_matrix_app WITH PASSWORD '<long-random>';
CREATE DATABASE access_matrix OWNER access_matrix_app
    ENCODING 'UTF8' TEMPLATE template0;
GRANT ALL PRIVILEGES ON DATABASE access_matrix TO access_matrix_app;
```

### 3.2 Keycloak schema

```sql
\c access_matrix
CREATE SCHEMA IF NOT EXISTS keycloak;
ALTER SCHEMA keycloak OWNER TO keycloak_user;  -- a dedicated DB user for KC
```

For the cleanest isolation: Keycloak gets its own DB user that can only access the `keycloak` schema. The business user has no access to the `keycloak` schema.

### 3.3 Flyway runs automatically

The first backend startup runs every `V*.sql` migration automatically. No manual schema initialisation is needed.

**Caveat**: the business DB user needs DDL permissions (CREATE TABLE / ALTER). The production-preferred pattern is to run DDL with a separate privileged account (as a CI step) and grant only DML to the runtime user:

```yaml
spring:
  flyway:
    enabled: false                  # turn off auto-migration
    # run `mvn flyway:migrate -D flyway.user=ddl_user -D ...` as a CI step instead
```

---

## 4. Keycloak production deployment

### 4.1 Don't use dev mode

Start with `start` (not `start-dev`):

```bash
kc.sh build                         # one-off optimised build
kc.sh start --optimized \
  --hostname=sso.example.com \
  --hostname-strict=true \
  --hostname-backchannel-dynamic=false \
  --proxy-headers=xforwarded \
  --http-enabled=false \
  --https-port=8443 \
  --https-certificate-file=/path/to/fullchain.pem \
  --https-certificate-key-file=/path/to/privkey.pem
```

Alternatively, terminate TLS at the reverse proxy and run Keycloak over plain HTTP behind it:

```bash
kc.sh start --optimized \
  --hostname=sso.example.com \
  --hostname-strict=true \
  --proxy-headers=xforwarded \
  --http-enabled=true \
  --http-port=8180
```

### 4.2 Use PostgreSQL, not the embedded H2

```bash
KC_DB=postgres \
KC_DB_URL=jdbc:postgresql://pg-prod:5432/access_matrix?currentSchema=keycloak \
KC_DB_USERNAME=keycloak_user \
KC_DB_PASSWORD=<secret> \
kc.sh start --optimized ...
```

### 4.3 Service account (production admin credentials)

Don't use `admin-cli + admin/admin`. The production approach:

1. In the master realm (or whichever realm you want to manage), create a client `access-matrix-admin`
2. Set Client authentication = ON and grab the secret
3. Set Service accounts roles = ON
4. Grant this service account only the `realm-management/manage-users` role (least privilege)
5. Inject the secret into the backend:

```yaml
CORE_KEYCLOAK_ADMIN_REALM=acme        # or master — wherever you created the client
CORE_KEYCLOAK_ADMIN_CLIENT_ID=access-matrix-admin
CORE_KEYCLOAK_ADMIN_CLIENT_SECRET=<secret>
# leave username / password unset → KeycloakUserService falls back to client_credentials grant
```

### 4.4 Version-control the realm configuration

Keep `infra/keycloak/realms/<env>-realm.json` in the repo and load it on startup with `--import-realm`:

```bash
kc.sh start --optimized --import-realm
```

Make sure the file name matches what gets deployed into `$KEYCLOAK_HOME/data/import/`.

### 4.5 SMTP configuration (so Keycloak can send its own emails)

Go to Realm settings → Email:
- Host, Port, From, Username, Password
- Enable "Enable SSL" or "Enable StartTLS"
- Enable all actions (reset password / verify email / …)

The user "Forgot password" flow relies on this SMTP. It's independent of the business-side `MailService` SMTP (they can share an account or be different ones).

---

## 5. Backend JVM configuration

```
-XX:+UseZGC
-XX:MaxRAMPercentage=75.0
-Xms512m
-XX:+AlwaysPreTouch
-Dfile.encoding=UTF-8
--enable-native-access=ALL-UNNAMED
-XX:+EnableDynamicAgentLoading
-Xlog:gc*:file=/var/log/access-matrix/gc.log:time,uptime,level,tags:filecount=10,filesize=100M
-XX:HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/var/dumps/access-matrix
```

The Spring Boot Maven plugin already writes the first six lines into the `repackage` manifest, so `java -jar` picks them up automatically.

In containers, set `MaxRAMPercentage` to 75% so the Kubernetes / Docker resource limits actually take effect.

---

## 6. Sample Nginx configuration

```nginx
upstream access_matrix_backend {
    server backend-1:9135;
    server backend-2:9135;
    keepalive 32;
}

upstream keycloak {
    server keycloak-1:8180;
    server keycloak-2:8180;
}

server {
    listen 443 ssl http2;
    server_name app.example.com;

    ssl_certificate     /etc/ssl/app.example.com/fullchain.pem;
    ssl_certificate_key /etc/ssl/app.example.com/privkey.pem;

    # SPA static
    location / {
        root /var/www/access-matrix;
        try_files $uri $uri/ /index.html;
    }

    # Backend API
    location /api/ {
        proxy_pass http://access_matrix_backend/api/;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}

server {
    listen 443 ssl http2;
    server_name sso.example.com;

    ssl_certificate     /etc/ssl/sso.example.com/fullchain.pem;
    ssl_certificate_key /etc/ssl/sso.example.com/privkey.pem;

    location / {
        proxy_pass http://keycloak;
        proxy_http_version 1.1;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

---

## 7. Health checks / observability

| Endpoint | Purpose |
|---|---|
| `GET /api/health` | Business-level; returns profile + timestamp |
| `GET /api/actuator/health` | Spring Boot standard, includes DB / Redis / Mail status |
| `GET /api/actuator/health/liveness` | K8s liveness probe |
| `GET /api/actuator/health/readiness` | K8s readiness probe |
| `GET /api/actuator/prometheus` | Prometheus metrics |

Example K8s probes:

```yaml
livenessProbe:
  httpGet:
    path: /api/actuator/health/liveness
    port: 9135
  initialDelaySeconds: 30
  periodSeconds: 10
readinessProbe:
  httpGet:
    path: /api/actuator/health/readiness
    port: 9135
  initialDelaySeconds: 15
  periodSeconds: 5
```

---

## 8. Security checklist (verify before go-live)

- [ ] `SPRING_PROFILES_ACTIVE=prod` (don't skip — the default is `prod`, but confirm)
- [ ] `CORE_JWT_SECRET` is >= 32 bytes random (jwt mode)
- [ ] In oidc mode, `CORE_OIDC_ISSUER_URI` uses HTTPS
- [ ] DB uses a dedicated business user with a strong password (not the `postgres` superuser)
- [ ] Keycloak is backed by PostgreSQL, not the embedded H2
- [ ] Keycloak uses a service account (client_credentials), not admin/admin
- [ ] Keycloak Realm settings → Brute Force Detection is enabled
- [ ] `CORE_CORS_ALLOWED_ORIGINS` lists origins strictly (no `*`)
- [ ] Reverse proxy adds `X-Frame-Options: SAMEORIGIN`, `Content-Security-Policy`, and HSTS
- [ ] `application.yml` / `application-local.yml` contain **no** hard-coded SMTP / DB / JWT secrets
- [ ] Redis has AUTH enabled and isn't reachable from the public internet
- [ ] PG `pg_hba.conf` restricts methods to `md5` / `scram-sha-256`; `trust` is not allowed
- [ ] All logs ship to a centralised log system (don't go disk-only — a full disk leaves you blind)
- [ ] DB / Redis backups are scheduled and restore-tested (untested backups don't exist)

---

## 9. Upgrade process

1. Run the tests: `./mvnw test && cd frontend && npm run test && npm run test:e2e`
2. Back up the DB: `pg_dump access_matrix > backup-YYYYMMDD.sql`
3. Back up the Keycloak realm: admin console → realm → Action → Partial export
4. Pull the latest code: `git fetch && git checkout v1.2.3`
5. Run the new migrations: `./mvnw -pl core-bootstrap flyway:migrate`, or restart the backend (which runs them automatically)
6. Rolling-restart the backend pods (on K8s: `kubectl rollout restart deployment/backend`)
7. Publish the new frontend `dist` to the CDN
8. Health-check + smoke test: log in, view menus, walk through the critical business paths
9. On failure → rollback: restore the DB from backup, redeploy code via `git checkout v1.2.2`

---

## 10. Troubleshooting (production-specific)

### 10.1 Users get 401 immediately after logging in

- Check Redis connectivity (forced-logout sets and refresh tokens live in Redis)
- Check JWT clock skew (a > 5-minute drift between the OIDC issuer and the backend host triggers rejection)

### 10.2 RBAC cache is "poisoned" — role changes don't reflect for users

- Quick fix: `DEL access_matrix:user:permissions:*` (via Redis CLI)
- Root cause: check whether the role-edit endpoint forgot to call `cacheService.evictRole(...)`

### 10.3 Multi-tenant data leaks across tenants

- Immediately fail over to the previous version
- Confirm `app.mybatis.tenant.enabled` is `true` in `application.yml`
- Audit every hand-written `@Select` SQL to ensure each one explicitly includes `tenant_id = #{tenantId}`
- Read the comments in the [V20 migration](../backend/core-bootstrap/src/main/resources/db/migration/V20__core_auth_user_tenant_unique.sql)

---

## 11. Containerisation (Docker)

Backend:

```dockerfile
FROM eclipse-temurin:25-jre-alpine
WORKDIR /app
COPY target/core-service.jar /app/app.jar
EXPOSE 9135
ENTRYPOINT ["java", \
  "-XX:+UseZGC", \
  "-XX:MaxRAMPercentage=75.0", \
  "-XX:+AlwaysPreTouch", \
  "-Dfile.encoding=UTF-8", \
  "--enable-native-access=ALL-UNNAMED", \
  "-XX:+EnableDynamicAgentLoading", \
  "-jar", "/app/app.jar"]
```

Frontend:

```dockerfile
FROM nginx:1.27-alpine
COPY dist/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
```

A K8s Helm chart is not included in the repo (the project itself isn't tied to K8s). Community PRs welcome if you need one.

---

## Next steps

- Business-side usage: [User Guide](user-guide.md)
- Building new features: [Development](development.md)
