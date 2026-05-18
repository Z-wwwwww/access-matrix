# access-matrix — Platform Foundation

A multi-module Spring Boot 4 / JDK 25 **common platform** that ships the
authentication scaffolding, cross-cutting concerns, and infrastructure
glue for downstream business modules. Naming is business-neutral
(`core-*`, `com.platform.core.*`, `CORE_*`) so the foundation can host
multiple products. The first consumer is InnTouch PMS, but no PMS
business logic lives here.

This repository delivers an **auth skeleton only** — login, refresh,
logout, profile lookup, admin unlock/reset, audit log, rate limiting,
account lockout, password policy. No reservations, listings, pricing,
or other domain code.

## Tech stack

| Layer        | Choice                                                              |
| ------------ | ------------------------------------------------------------------- |
| Language     | Java 25 LTS (virtual threads, scoped values)                        |
| GC           | Generational ZGC                                                    |
| Framework    | Spring Boot 4.0.6 (Spring 7, Spring Security 7)                     |
| Web          | Tomcat 11 (Jakarta EE 11), virtual threads enabled                  |
| Persistence  | MyBatis-Plus 3.5.16 (`mybatis-plus-spring-boot4-starter`)           |
| Database     | PostgreSQL 16+ (`new_inntouch_core`), JSONB for roles/authorities   |
| Migrations   | Flyway 11 (`flyway-core` + `flyway-database-postgresql`)            |
| L1 cache     | Caffeine 3 via Spring Cache                                         |
| L2 / session | Redis 7 (Lettuce), Spring Data Redis 4                              |
| Auth         | Spring Security 7 OAuth2 Resource Server, HS256 JWT, rotating refresh |
| Rate limit   | Bucket4j 8.10 in-memory per-IP, applied to `/auth/*`                |
| Password     | BCrypt cost 12, HIBP k-anonymity check                              |
| IDs          | Monotonic ULID (CHAR(26))                                           |
| Logging      | Log4j2 async (Disruptor 4, 256k ring)                               |
| API docs     | springdoc-openapi 3                                                 |
| JSON         | Jackson 3 (`tools.jackson.*`) + Blackbird; Spring still defaults to Jackson 2 |

## Module layout

```
core-parent  (pom)
├── core-common         JsonResult / PageResult / ErrorCode / BusinessException
│                       RequestContext (ThreadLocal, virtual-thread safe) / IdGenerator
├── core-domain         placeholder for business aggregates
├── core-infrastructure security / web / persistence / cache / numbering / config
└── core-bootstrap      Spring Boot entrypoint, controllers, services, mappers,
                        application*.yml, log4j2-spring.xml, Flyway V1–V4
```

Dependency direction is strict: `bootstrap → infrastructure → domain → common`.

## Profiles

| Profile | `security.mode` | DB / Redis / JWT source                           |
| ------- | --------------- | ------------------------------------------------- |
| local   | permit-all      | hardcoded localhost (PG `abcd@1234`, Redis db=1)  |
| dev     | jwt             | env: `CORE_DB_URL/USERNAME/PASSWORD`, `CORE_REDIS_*`, `CORE_JWT_SECRET`, `CORE_CORS_ALLOWED_ORIGINS` |
| test    | jwt             | env (same shape as dev)                           |
| prod    | jwt             | env, `CORE_JWT_SECRET` >= 32 bytes, Redis SSL on  |

Non-local profiles fail-fast at startup if `CORE_JWT_SECRET` is missing
or under 32 bytes — no silent fallback to dev placeholder.

## Quick start

```powershell
# 1. Create the database (one-time)
psql -h 127.0.0.1 -U postgres `
     -c "CREATE DATABASE new_inntouch_core WITH ENCODING 'UTF8' TEMPLATE template0;"

# 2. Build
mvn -DskipTests package

# 3. Run (uses local profile)
java -jar core-bootstrap\target\core-service.jar --spring.profiles.active=local
```

Expected startup output ends with:

```
============================================================
  CORE-SERVICE is READY
------------------------------------------------------------
  profile        : local
  port           : 9135
  context-path   : /api
  security.mode  : permit-all
============================================================
```

Flyway will apply V1–V4 and seed the `USER` numbering definition. The
`local` profile additionally seeds an `admin/admin` user on every boot.

## Smoke test

```bash
curl http://127.0.0.1:9135/api/health

curl -X POST http://127.0.0.1:9135/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

# Take access_token from the previous response:
curl http://127.0.0.1:9135/api/user/me -H "Authorization: Bearer <token>"
```

Successful login returns:

```json
{"code":0,"msg":"success","data":{
  "access_token":"eyJ...",
  "refresh_token":"...",
  "expires_in":900,
  "token_type":"Bearer"
}}
```

The refresh token is **also** delivered as an `HttpOnly` cookie
(`core_refresh`, `Path=/api/auth`, `SameSite=Strict`, `Secure` on
non-local profiles).

## Endpoint inventory

| Method | Path                            | Auth        | Notes                                      |
| ------ | ------------------------------- | ----------- | ------------------------------------------ |
| GET    | `/api/health`                   | open        | app + profile + timestamp                  |
| POST   | `/api/auth/login`               | open        | username / email / user_no all accepted    |
| POST   | `/api/auth/refresh`             | refresh tok | single-use rotation                        |
| POST   | `/api/auth/logout`              | refresh tok | revoke + clear cookie                      |
| GET    | `/api/user/me`                  | Bearer JWT  | full profile                               |
| POST   | `/api/admin/auth/unlock`        | `auth:unlock` or `*:*`         |             |
| POST   | `/api/admin/auth/reset-password`| `auth:reset-password` or `*:*` | enforces password policy + HIBP |
| GET    | `/api/swagger-ui.html`          | open        | OpenAPI 3 UI                               |
| GET    | `/api/actuator/health`          | open        | probes enabled                             |
| GET    | `/api/v3/api-docs`              | open        | OpenAPI 3 JSON                             |

Error envelope is consistent across all endpoints:

```json
{"code": <int>, "msg": "<string>", "data": <T | null>}
```

| Code | Meaning                  |
| ---- | ------------------------ |
| 0    | success                  |
| 400  | bad request              |
| 401  | unauthenticated / invalid token |
| 403  | forbidden / account disabled |
| 404  | not found                |
| 429  | rate-limited (`/auth/*`) |
| 500  | server error             |
| 700  | generic business error   |
| 701  | validation failed        |
| 702  | optimistic lock conflict |
| 710  | missing tenant context   |
| 720  | external service error   |
| 730  | account locked           |

## Schema

Flyway migrations (under `core-bootstrap/src/main/resources/db/migration/`):

| Version | Tables created                                                |
| ------- | ------------------------------------------------------------- |
| V1      | `core_meta`                                                   |
| V2      | `pms_auth_user` (username / email / user_no login, JSONB roles & authorities) |
| V3      | `pms_auth_login_log` (async audit)                            |
| V4      | `sys_numbering_management`, `sys_numbering_key`, `USER` seed  |

`AuthSchemaBootstrap` runs the same DDL idempotently as a safety net
for dirty `flyway_schema_history` situations.

## JVM flags

The Spring Boot Maven plugin wires these into `mvn spring-boot:run`,
and the runbook above expects the same for direct `java -jar`:

```
-XX:+UseZGC
-XX:MaxRAMPercentage=75.0
-Xms512m
-XX:+AlwaysPreTouch
-Dfile.encoding=UTF-8
--enable-native-access=ALL-UNNAMED
-XX:+EnableDynamicAgentLoading
```

## Not in scope (intentionally)

- Business domain (reservations, listings, pricing, …)
- MFA, password expiry/history, password reset self-service
- Email/SMS providers, file upload, message queues
- Sentinel / Hystrix, Prometheus dashboards (registry is wired; dashboards live elsewhere)
- CI/CD pipelines

These belong to consumer modules, not the foundation.

## Locked design decisions

| ID         | Decision                                                                 |
| ---------- | ------------------------------------------------------------------------ |
| AUTH-P-01  | BCrypt cost 12                                                           |
| AUTH-P-02  | Password minimum length 8 (PCI 8.3)                                      |
| AUTH-P-03  | Require all 4 character classes                                          |
| AUTH-P-04  | HIBP enabled, fail-open if HIBP unreachable                              |
| AUTH-I-01  | Login identifier accepts `username` / `email` / `user_no` interchangeably |
| AUTH-B-06  | Admin unlock endpoint + audit log                                        |
| AUTH-P-07  | No self-service password reset (admin endpoint only)                     |
| naming     | `core-*` / `com.platform.core.*` / `CORE_*` — business-neutral foundation |
| ID strategy| Monotonic ULID over UUID for B+Tree locality                             |
| Storage    | PostgreSQL JSONB over MySQL JSON, JDBC `stringtype=unspecified` for transparent JSONB binding |
