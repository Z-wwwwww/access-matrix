-- V20: scope core_auth_user uniqueness to (tenant_id, *).
-- Before: username / email / user_no were globally unique → two tenants could
--   not have a user named "admin". Blocks any real multi-tenant deployment.
-- After: each tenant has its own (username) / (email) / (user_no) namespace.
-- The frontend will start sending X-Tenant-Id; the JWT carries `tid` for
-- post-auth requests; pre-auth /auth/login takes tenant from the X-Tenant-Id
-- header (read by RequestContextFilter).

DROP INDEX IF EXISTS uk_core_auth_user_username;
DROP INDEX IF EXISTS uk_core_auth_user_email;
DROP INDEX IF EXISTS uk_core_auth_user_user_no;

CREATE UNIQUE INDEX IF NOT EXISTS uk_core_auth_user_tenant_username
    ON core_auth_user (tenant_id, username) WHERE mark = 1;
CREATE UNIQUE INDEX IF NOT EXISTS uk_core_auth_user_tenant_email
    ON core_auth_user (tenant_id, email) WHERE mark = 1 AND email IS NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uk_core_auth_user_tenant_user_no
    ON core_auth_user (tenant_id, user_no) WHERE mark = 1 AND user_no IS NOT NULL;
