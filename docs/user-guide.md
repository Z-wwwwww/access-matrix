# User Guide

**English** · [中文](user-guide.zh-CN.md)

For **business administrators** and **end users**: signing in, user management, roles, permissions, data scopes, multi-tenancy, and password self-service.

> For installation steps see [Getting Started](getting-started.md). This guide assumes the system is already up and running.

---

## 1. Signing in

### 1.1 Two sign-in modes

| Mode | Entry point | Credentials | Best for |
|---|---|---|---|
| **Password** | `/login` with a username + password | BCrypt hash in the business table `core_auth_user` | Single-machine dev, environments with no IdP |
| **SSO (OIDC)** | `/login`, click "Sign in with SSO" → redirect to Keycloak | Keycloak / Azure AD / any OIDC IdP | Production, corporate networks, anywhere an IdP already exists |

The backend's `app.security.mode` decides which mode is active. In SSO mode both buttons are shown; in password mode you only see the username/password fields.

### 1.2 First sign-in

| Mode | Username | Password |
|---|---|---|
| Password (local profile) | `admin` | `admin` |
| SSO (local profile) | `admin` | `admin` (created automatically at startup by `LocalKeycloakAdminSeeder`) |

After signing in you land on the admin console. On a first SSO sign-in, `OidcJitUserService` automatically binds the Keycloak user to the business system's admin row (it writes `keycloak_id`).

### 1.3 Switching tenants

At the bottom of the login page there is a **"Show advanced"** link; expanding it reveals a tenant-ID field. The default is `default`.

> Switching tenants means switching Keycloak realms. Each tenant has its own user store, roles, and permissions. See [Multi-tenancy](#5-multi-tenancy) for the full picture.

---

## 2. User management (invite / direct modes)

Navigate to **System → Users** and click "Create" to open the drawer.

### 2.1 Choose a provision mode

The drawer has two radio buttons at the top:

| Mode | Flow | Best for |
|---|---|---|
| **Invitation email (INVITE)** | Admin enters only the email → backend creates the Keycloak user (no password) → invitation email is sent → user clicks the link and sets their own password | Real employees / customers. The user owns the password; the admin never sees it. |
| **Direct create (DIRECT)** | Admin enters an initial password → backend creates the Keycloak user (password marked temporary) → an "account opened" notification email is sent | Bots / shared accounts / a fallback when SMTP is broken |

### 2.2 INVITE flow in detail

```
Admin action                  System behavior                User action
─────────                     ─────────                      ─────────
Fill the form (username/email/dept)
Click save  ──────────►   1. Call the Keycloak Admin API
                              create the user (no credential)
                           2. INSERT core_auth_user
                              keycloak_id = <UUID>
                           3. INSERT core_user_invite
                              token_hash = SHA256(token)
                              expires_at = now + 7d
                           4. Send email to user.email
                              link http://app/invite/<token>

                                                            receive email → click link

                           5. GET /auth/invite/<token>
                              ├─ probe valid ✓
                              └─ render password form
                                                            enter password → submit
                           6. POST /auth/invite/<token>
                              ├─ consume token (used_at = now)
                              └─ Keycloak setPassword(temp=false)
                                                            redirect to /login
                                                            normal SSO sign-in
```

**Invitation links are valid for 7 days** (`app.invite.token-ttl`) and are one-shot. Expired and already-used tokens return the same generic "invalid invitation" message (to prevent token enumeration).

### 2.3 DIRECT flow in detail

```
Admin fills the form (incl. password)
Click save  ──────────►   1. Keycloak Admin API
                              create the user, password marked TEMPORARY
                           2. INSERT core_auth_user
                              keycloak_id = <UUID>
                           3. Send email to user.email
                              "Your account is X, initial password Y, please change it on first sign-in."

                                                            receive email
                                                            sign in with X/Y
                           4. Keycloak forces a password change
                                                            set a new password → enter the system
```

### 2.4 What if email is broken?

When `app.mail.enabled=false` or the SMTP config is wrong, MailService **does not throw**; it just logs `would have sent X to Y`. This is by design — the user row is already created, the email simply did not go out.

Recovery:
- After fixing the SMTP config, the admin can **resend the invitation** (no UI yet; for now, manually delete the `core_user_invite` row via SQL and re-call the admin API).
- Or switch to DIRECT mode and recreate (use SQL to flip the user's status, then create again).

> **TODO**: a "resend invitation" button is on the roadmap — see [development roadmap](development.md#roadmap).

### 2.5 Editing a user

In the user-detail view, you can change:

| Field | What happens |
|---|---|
| email / displayName / deptId / status | Direct UPDATE on the business table |
| **password** | **Not editable here** — the user changes their own password via the Keycloak Account Console; admins can reset via the Keycloak Admin API |
| **role assignment** | `user_role` on the business side, takes effect immediately (`evictUser` triggers Redis cache invalidation) |

### 2.6 Deleting a user

| Mode | Behavior |
|---|---|
| Password mode | Soft-delete on business tables (`mark=0`) + soft-delete `user_role` + immediate force-logout |
| OIDC mode | Soft-delete on business tables + **also** delete in Keycloak (`KeycloakUserService.deleteUser`) + force-logout |

**Protections**:
- The built-in `admin` user cannot be deleted.
- The last SUPER_ADMIN cannot be deleted / disabled / stripped of the SUPER_ADMIN role.

---

## 3. Roles and permissions

### 3.1 Built-in roles

| Role | ID | Notes |
|---|---|---|
| **SUPER_ADMIN** | `00000000000000000000ROLE01` | Highest privilege in the system, includes the `*:*` wildcard, protected by the "last one" rule. |

All custom roles are derived from this.

### 3.2 Permission code rules

The permission-code format is `<resource>:<action>`. Examples:

| Code | Meaning |
|---|---|
| `user:read` | User list / detail |
| `user:create` | Create a user |
| `user:delete` | Delete a user |
| `role:*` | All actions on the role resource (wildcard) |
| `*:*` | All actions on all resources (super-admin only) |

The backend protects endpoints with the `@RequiresPermission("user:read")` annotation. `PermissionMatcher` runs a three-layer match:

```
Granted permission   Required permission   Result
─────                ─────                 ────
*:*                  any                   ✅
user:*               user:read             ✅
user:read            user:read             ✅
user:read            user:delete           ❌
user:*               role:read             ❌
```

### 3.3 Granting permissions to a role

Go to **System → Roles** → click a role → "Permissions" tab → pick permissions from the tree. Saving takes effect immediately (Redis cache evict).

### 3.4 Granting roles to a user

Go to **System → Users** → click a user → "Roles" tab → tick the roles → save.

A user can have multiple roles; **permissions are the union** (OR).

### 3.5 Button-level permissions on the frontend

The frontend uses the `v-permission` directive to hide buttons the user has no permission for:

```html
<button v-permission="'user:create'">Create</button>
<button v-permission="['user:update','user:delete']">Edit/Delete</button>  <!-- both required -->
<button v-any-permission="['user:update','user:delete']">Action</button>   <!-- either one suffices -->
```

The directive consults `authStore.authorities`, which uses the same permission codes the backend's `@RequiresPermission` does.

---

## 4. Data scopes (five kinds)

The same endpoint can return different rows for different users. The role's `data_scope` field decides which:

| Scope | Meaning | Typical role |
|---|---|---|
| **1 ALL** | See all data in the tenant | Super-admin / GM |
| **2 DEPT_AND_SUB** | Own department + all sub-departments | Department head |
| **3 DEPT** | Own department only | Section chief |
| **4 SELF** | Only rows created by the user | Individual contributor |
| **5 CUSTOM** | Explicit departments listed in `role_dept` | Cross-department liaison |

### 4.1 How business code consumes it

Annotate a Mapper / Service with `@DataScope`:

```java
@DataScope(deptColumn = "dept_id", userColumn = "create_user")
public List<Task> findAll() {
    return taskMapper.selectList(null);
}
```

The `DataScopeAspect` aspect automatically appends `AND (dept_id IN (...) OR create_user = ...)` to the SQL.

### 4.2 Multi-role union rule

When a user has multiple roles, **the data scope is the union** (the wider one wins). Example: a user with both DEPT and SELF roles sees their own department's rows plus every row they themselves created (even outside that department).

### 4.3 Demo

The `local` profile seeds five demo users and 15 task rows to illustrate all five scopes. See [data-scope demo](data-scope-demo.md) for the full walkthrough.

---

## 5. Multi-tenancy

### 5.1 Concepts

| Concept | Implementation |
|---|---|
| **Tenant identifier** | String `tenant_id` (a ULID or a human-readable label) |
| **Business-side isolation** | Every table has a `tenant_id` column; the MyBatis-Plus interceptor automatically appends `tenant_id = current` to the SQL `WHERE` clause |
| **IdP-side isolation** | Each tenant = one Keycloak realm |
| **Carried in the JWT** | The `tid` claim (OIDC mode) or the `X-Tenant-Id` header (pre-auth / fallback) |

### 5.2 Switching tenants

**SSO mode**: just sign in to a different realm (each realm has its own user store).

**Password mode**: on the login page, "Show advanced" → enter the tenant ID. The frontend stores it in `localStorage.tenantId` and sends the `X-Tenant-Id` header with every request.

### 5.3 Adding a new tenant (operator-side)

1. **Keycloak**: admin console → Create realm → name = the new tenant ID.
   - In the new realm, create a client `access-matrix-backend` (copy the settings from the `default` realm).
   - Configure a hardcoded `tid` claim mapper with value = the new tenant ID.
2. **Business side**: nothing to do. Every migration / table is already tenant-id-aware. The first user to sign in is JIT-provisioned into `core_auth_user`.
3. **Roles / permissions**: a brand-new tenant has no roles to start — sign in as SUPER_ADMIN (which is cross-tenant) to create them, or write a seeder.

### 5.4 Usernames and role names may collide across tenants

V20 changed the uniqueness constraint to a composite `(tenant_id, username)`. Both tenants can have a user called `admin` without conflict.

---

## 6. Password self-service / forgot password

Once Keycloak is in front, passwords live entirely in the IdP — the business side no longer holds them.

### 6.1 Changing your password

After signing in, the user menu in the top right → **"Change password"** → a dialog with an "Open Account Console" button → opens the Keycloak Account Console in a new tab (`http://localhost:8180/realms/<tenant>/account/`) → change it in Keycloak.

### 6.2 Forgot password

On the `/login` page, the **"Forgot password?"** link at the bottom right → redirects to Keycloak's `reset-credentials` flow → enter the email → Keycloak sends a reset link → click the link and set a new password → returns to the business system.

> This requires the Keycloak realm's SMTP to be configured (Realm settings → Email); otherwise the email cannot be sent.

### 6.3 Enabling MFA / second factor

Entirely managed in Keycloak. Admin console → realm → Authentication → Required Actions → enable `Configure OTP` or `WebAuthn Register`.

On their next sign-in the user is forced to enroll MFA. The business system needs no changes.

---

## 7. Audit log

Critical business actions (deleting a user, changing a role's permissions, force-logout, …) are written to `core_oplog` automatically via the `@OpLog` annotation.

View them at **System → Operation log** (requires the `oplog:read` permission).

Fields:
- `operator` — the ULID of the actor
- `action` — operation type (`delete-user`, `assign-roles`, etc.)
- `target` — the target object's ID
- `detail` — extra JSON payload
- `tenant_id` / `trace_id` / `create_time`

---

## 8. Force-logout

An admin clicks "Force logout" on a user → `ForceLogoutService.kickOut(userId)` → writes a Redis set → every subsequent API call from that user returns 401.

When to use it:
- A departing employee whose password is unchanged but who must lose access right now.
- A security incident — freeze a suspicious account.
- After changing a user's permissions you want them to apply immediately (`evictUser` uses a similar mechanism).

---

## 9. Common admin tasks

### 9.1 Onboarding a new employee

1. System → Users → Create.
2. Pick **INVITE** mode.
3. Fill in the email + department + roles.
4. Save → the system sends the invitation email automatically.
5. The employee clicks the link, sets a password, and is signed in.

### 9.2 Promoting a normal user to admin

1. System → Users → click the user → Roles tab.
2. Tick `SUPER_ADMIN` (or your custom "Admin" role).
3. Save → the user's Redis cache is invalidated automatically; the change applies on their next request.
4. (Optional) Trigger a force-logout so they sign in again and refresh every client.

### 9.3 Off-boarding an employee

1. System → Users → find the user.
2. Click "Disable" → status becomes 0 → immediate force-logout.
3. Or "Delete" outright → soft-delete + delete from Keycloak + force-logout.
4. Business data the user created is untouched (no cascading delete).

### 9.4 A colleague says they cannot see a page

1. System → Roles → find their role.
2. Permissions tab → check whether the relevant permission is ticked.
3. If not, tick it, save, and ask them to refresh.
4. If it is a data-scope issue (they see the page but the list is empty), check the role's `data_scope` field.

---

## 10. Next steps

- Building new features: [Development](development.md)
- Going to production: [Deployment](deployment.md)
