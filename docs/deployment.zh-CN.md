# Deployment

[English](deployment.md) · **中文**

生产部署指南 —— 把 access-matrix 部到 staging / prod 环境。

> 本地开发环境见 [Getting Started](getting-started.zh-CN.md)。

---

## 1. 部署架构推荐

```
                       ┌──────────────────┐
                       │  CDN / 静态托管    │  index.html + assets
                       │  (Cloudflare /   │  (vite build → dist/)
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
            └────────── 同一个 PG 集群 ──────────┘
                (不同 schema; 可独立扩缩)
```

后端无状态 → 水平扩。Session / refresh-token / 强制下线 都在 Redis，pod 之间无差异。

---

## 2. 环境变量清单

### 2.1 后端

| 变量 | 必需 | 说明 | 例 |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | 必需 | `prod` / `dev` / `staging`。默认 `prod`（fail-closed） | `prod` |
| `CORE_DB_URL` | 必需 | JDBC URL | `jdbc:postgresql://pg-prod:5432/access_matrix?stringtype=unspecified` |
| `CORE_DB_USERNAME` | 必需 | DB 用户名 | `access_matrix_app` |
| `CORE_DB_PASSWORD` | 必需 | DB 密码（推荐 secret manager 注入） | |
| `CORE_REDIS_HOST` | 必需 | Redis 主机 | `redis-prod.svc.cluster.local` |
| `CORE_REDIS_PORT` | 默认 6379 | | |
| `CORE_REDIS_PASSWORD` | 推荐 | Redis AUTH 密码 | |
| `CORE_REDIS_DB` | 默认 0 | DB index | |
| `CORE_JWT_SECRET` | jwt 模式必需 | HS256 密钥，>= 32 字节 | 32+ 字符随机串 |
| `CORE_OIDC_ISSUER_URI` | oidc 模式必需 | Keycloak realm URL | `https://sso.example.com/realms/acme` |
| `CORE_KEYCLOAK_SERVER_URL` | oidc 模式必需 | Keycloak 根 URL | `https://sso.example.com` |
| `CORE_KEYCLOAK_ADMIN_REALM` | oidc 模式 | admin 凭据所在 realm | `master` |
| `CORE_KEYCLOAK_ADMIN_CLIENT_ID` | oidc 模式 | service account client | `access-matrix-admin` |
| `CORE_KEYCLOAK_ADMIN_CLIENT_SECRET` | oidc 模式必需（生产） | service account secret | |
| `CORE_KEYCLOAK_ADMIN_USERNAME` | dev 用 | 不推荐生产使用 | |
| `CORE_KEYCLOAK_ADMIN_PASSWORD` | dev 用 | 同上 | |
| `CORE_MAIL_HOST` | 启用邮件时 | SMTP 主机 | `smtp.exmail.qq.com` 或 `email-smtp.ap-northeast-1.amazonaws.com` |
| `CORE_MAIL_PORT` | 启用邮件时 | 465 (SSL) 或 587 (STARTTLS) | |
| `CORE_MAIL_USERNAME` | 启用邮件时 | SMTP 用户 | |
| `CORE_MAIL_PASSWORD` | 启用邮件时 | SMTP 密码 / app password | |
| `CORE_MAIL_ENABLED` | 默认 false | 主开关 | `true` |
| `CORE_MAIL_FROM` | 启用邮件时 | 发件人地址（必须跟 SMTP user 在同一域） | `noreply@your-domain.com` |
| `CORE_MAIL_FROM_NAME` | 推荐 | 发件人显示名 | `Access Matrix` |
| `CORE_APP_BASE_URL` | 启用邮件时 | 业务系统外部 URL，邮件链接用 | `https://app.example.com` |
| `CORE_INVITE_TOKEN_TTL` | 默认 7d | 邀请 token 有效期 | `7d` / `48h` |
| `CORE_CORS_ALLOWED_ORIGINS` | 必需 | 允许 CORS 的来源（逗号分隔） | `https://app.example.com` |

### 2.2 前端（构建时）

构建前端时通过 `.env.production` 设置：

```dotenv
# frontend/.env.production
VITE_API_BASE_URL=https://app.example.com/api

VITE_OIDC_ENABLED=true
VITE_OIDC_ISSUER=https://sso.example.com/realms/acme
VITE_OIDC_CLIENT_ID=access-matrix-frontend
VITE_OIDC_REDIRECT_URI=https://app.example.com/sso/callback
VITE_OIDC_SCOPES=openid profile email
```

构建：

```bash
cd frontend
npm ci
npm run build           # 产物在 dist/
```

把 `dist/` 上传 CDN 或扔进 nginx 静态目录。

---

## 3. 数据库准备

### 3.1 创建专用 DB user（不要用 postgres 超管）

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
ALTER SCHEMA keycloak OWNER TO keycloak_user;  -- 单独的 DB user 给 KC
```

最干净的隔离：Keycloak 用独立 DB user，只能 access keycloak schema。业务 user 不能访问 keycloak schema。

### 3.3 Flyway 自动跑

后端首次启动会自动跑所有 V*.sql 迁移。无需手动 schema 初始化。

**注意**：业务 DB user 需要 DDL 权限（CREATE TABLE / ALTER）。生产偏好把 DDL 用单独高权账号跑（CI 步骤），业务运行时 user 只有 DML 权限：

```yaml
spring:
  flyway:
    enabled: false                  # 关掉自动迁移
    # 用 CI 步骤跑 mvn flyway:migrate -D flyway.user=ddl_user -D ...
```

---

## 4. Keycloak 生产部署

### 4.1 不要用 dev mode

启动用 `start`（不是 `start-dev`）：

```bash
kc.sh build                         # 一次性优化构建
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

或者反代终止 TLS、Keycloak 内部 HTTP：

```bash
kc.sh start --optimized \
  --hostname=sso.example.com \
  --hostname-strict=true \
  --proxy-headers=xforwarded \
  --http-enabled=true \
  --http-port=8180
```

### 4.2 用 PG 而非内置 H2

```bash
KC_DB=postgres \
KC_DB_URL=jdbc:postgresql://pg-prod:5432/access_matrix?currentSchema=keycloak \
KC_DB_USERNAME=keycloak_user \
KC_DB_PASSWORD=<secret> \
kc.sh start --optimized ...
```

### 4.3 service account（生产管理凭据）

不要用 `admin-cli + admin/admin`。生产做法：

1. 在 master realm（或要管理的 realm）建一个 client `access-matrix-admin`
2. Client authentication = ON，拿到 secret
3. Service accounts roles = ON
4. 给这个 service account 绑 `realm-management/manage-users` 这一个 role（最小权限）
5. 把 secret 注入 backend：

```yaml
CORE_KEYCLOAK_ADMIN_REALM=acme        # 或 master，看你 client 建在哪
CORE_KEYCLOAK_ADMIN_CLIENT_ID=access-matrix-admin
CORE_KEYCLOAK_ADMIN_CLIENT_SECRET=<secret>
# username / password 不设 → KeycloakUserService 自动走 client_credentials grant
```

### 4.4 Realm 配置版本化

`infra/keycloak/realms/<env>-realm.json` 跟 repo 走，用 `--import-realm` 启动时灌：

```bash
kc.sh start --optimized --import-realm
```

import 文件名要确保跟 deploy 进 `$KEYCLOAK_HOME/data/import/` 的一致。

### 4.5 SMTP 配置（让 Keycloak 自己发邮件）

到 Realm settings → Email：
- Host, Port, From, Username, Password
- 启用 "Enable SSL" 或 "Enable StartTLS"
- 启用所有 actions（reset password / verify email / …）

用户的 "忘记密码" 流程就靠这个 SMTP。跟业务侧 MailService 是独立 SMTP（可以同一个，也可以分开）。

---

## 5. 后端 JVM 配置

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

Spring Boot Maven plugin 已经把头 6 行写进 `repackage` 的 manifest。`java -jar` 启动会自动用。

容器化时建议把 `MaxRAMPercentage` 设 75%，让 Kubernetes / Docker 资源限制生效。

---

## 6. Nginx 配置示例

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

## 7. 健康检查 / 可观测

| 端点 | 用途 |
|---|---|
| `GET /api/health` | 业务级，简单返回 profile + timestamp |
| `GET /api/actuator/health` | Spring Boot 标准，含 DB / Redis / Mail 状态 |
| `GET /api/actuator/health/liveness` | K8s liveness probe |
| `GET /api/actuator/health/readiness` | K8s readiness probe |
| `GET /api/actuator/prometheus` | Prometheus 指标 |

K8s probe 示例：

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

## 8. 安全清单（上线前必查）

- [ ] `SPRING_PROFILES_ACTIVE=prod`（不要漏，默认就是 prod 但要确认）
- [ ] `CORE_JWT_SECRET` >= 32 字节随机（jwt 模式）
- [ ] OIDC 模式下 `CORE_OIDC_ISSUER_URI` 用 HTTPS
- [ ] DB 用专用业务 user，密码强（不用 `postgres` 超管）
- [ ] Keycloak 用 PG 后端，不用内置 H2
- [ ] Keycloak 用 service account（client_credentials），不用 admin/admin
- [ ] Keycloak Realm settings → Brute Force Detection 启用
- [ ] CORS `CORE_CORS_ALLOWED_ORIGINS` 严格列举（不要用 `*`）
- [ ] 反代加 `X-Frame-Options: SAMEORIGIN`、`Content-Security-Policy`、HSTS
- [ ] application.yml / application-local.yml 里**没有** SMTP / DB / JWT 密码硬编码
- [ ] Redis 启 AUTH，外网不可达
- [ ] PG `pg_hba.conf` 限制 `md5` / `scram-sha-256`，不允许 `trust`
- [ ] 全套日志 → 集中日志系统（不要 disk-only，磁盘满了你瞎了）
- [ ] DB / Redis 定时备份验证（不验证 = 没备份）

---

## 9. 升级流程

1. 跑测试：`./mvnw test && cd frontend && npm run test && npm run test:e2e`
2. 备份 DB：`pg_dump access_matrix > backup-YYYYMMDD.sql`
3. 备份 Keycloak realm：admin console → realm → Action → Partial export
4. Pull 最新代码：`git fetch && git checkout v1.2.3`
5. 跑新迁移：`./mvnw -pl core-bootstrap flyway:migrate` 或重启后端（自动跑）
6. 滚动重启后端 pod（K8s `kubectl rollout restart deployment/backend`）
7. 上 CDN 新前端 dist
8. 健康检查 + 烟雾测试：登录、看菜单、操作业务关键路径
9. 异常 → rollback：DB 用 backup，代码用 `git checkout v1.2.2` 重部署

---

## 10. 故障排查（生产专用）

### 10.1 用户登录后立刻报 401

- 检查 Redis 连不连得通（强制下线集合 / refresh token 都在 Redis）
- 检查 JWT 时钟偏差（OIDC issuer 跟后端服务器时间差 > 5 分钟会拒绝）

### 10.2 RBAC 缓存"中毒"，改了角色用户还是看老权限

- 临时：`DEL access_matrix:user:permissions:*` （Redis CLI）
- 根因：检查改角色 endpoint 是不是漏调 `cacheService.evictRole(...)`

### 10.3 多租户串了数据

- 立刻把流量切回上一个版本
- 检查 `application.yml` 的 `app.mybatis.tenant.enabled` 是不是 true
- 检查所有手写 `@Select` SQL 是不是都有显式 `tenant_id = #{tenantId}`
- 看 [V20 migration](../backend/core-bootstrap/src/main/resources/db/migration/V20__core_auth_user_tenant_unique.sql) 注释

---

## 11. 容器化（Docker）

后端：

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

前端：

```dockerfile
FROM nginx:1.27-alpine
COPY dist/ /usr/share/nginx/html/
COPY nginx.conf /etc/nginx/conf.d/default.conf
```

K8s Helm chart 不在仓库（项目本身不绑定 K8s）。需要的话社区贡献 PR 欢迎。

---

## 下一步

- 业务侧使用：[User Guide](user-guide.zh-CN.md)
- 开发新功能：[Development](development.zh-CN.md)
