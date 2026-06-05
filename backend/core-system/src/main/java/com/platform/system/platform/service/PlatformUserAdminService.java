package com.platform.system.platform.service;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.result.PageResult;
import com.platform.core.common.security.BuiltInRoles;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.platform.dto.PlatformUserDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Manage platform-ops staff: the users that live in the {@code system} tenant
 * and hold PLATFORM_ADMIN. Gated by {@code platform:user:*}. This closes the gap
 * noted in {@code SystemAdminSeeder} (which only seeds the single dev {@code ops}
 * user) — adding further operators no longer needs hand-run SQL + Keycloak.
 *
 * <p>Cross-tenant by nature: callers are platform-ops (JWT {@code tid='system'}),
 * so the MyBatis tenant interceptor is bypassed; raw {@link JdbcTemplate} keeps
 * the writes explicitly scoped to {@code system}.
 *
 * <p><b>Create</b> mirrors the user half of {@code TenantAdminService}: provision
 * a Keycloak user in the {@code system} realm, persist the {@code core_auth_user}
 * row (no local password — KC owns the credential), and bind PLATFORM_ADMIN. The
 * KC user gets a one-time temporary password (forced change on first login)
 * returned once to the operator. If the DB half fails, the KC user is deleted
 * (compensation) so no orphan remains.
 */
@Service
public class PlatformUserAdminService {

    private static final Logger log = LoggerFactory.getLogger(PlatformUserAdminService.class);

    private static final String SYSTEM_TENANT = "system";
    private static final String SYSTEM_REALM  = "system";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    /** KC is only present in oidc mode; ObjectProvider keeps this bootable otherwise. */
    private final ObjectProvider<KeycloakUserService> userServiceProvider;

    public PlatformUserAdminService(JdbcTemplate jdbc,
                                    ObjectProvider<KeycloakUserService> userServiceProvider) {
        this.jdbc = jdbc;
        this.userServiceProvider = userServiceProvider;
    }

    public PageResult<PlatformUserDto.View> list(long page, long size, String keyword) {
        StringBuilder where = new StringBuilder(
                "FROM core_auth_user u WHERE u.tenant_id = ? AND u.mark = 1");
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (u.username ILIKE ? OR u.email ILIKE ? OR u.display_name ILIKE ?)");
        }
        Object[] countArgs = keyword == null || keyword.isBlank()
                ? new Object[] { SYSTEM_TENANT }
                : new Object[] { SYSTEM_TENANT, like(keyword), like(keyword), like(keyword) };
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + where, Long.class, countArgs);
        long totalVal = total == null ? 0 : total;

        long offset = (page - 1) * size;
        Object[] pageArgs = new Object[countArgs.length + 2];
        System.arraycopy(countArgs, 0, pageArgs, 0, countArgs.length);
        pageArgs[countArgs.length] = size;
        pageArgs[countArgs.length + 1] = offset;

        List<PlatformUserDto.View> rows = jdbc.query(
                "SELECT u.id, u.username, u.email, u.display_name, u.status, u.create_time, "
                        + "       EXISTS(SELECT 1 FROM core_rbac_user_role r "
                        + "              WHERE r.user_id = u.id AND r.tenant_id = '" + SYSTEM_TENANT + "' "
                        + "                AND r.role_id = '" + BuiltInRoles.PLATFORM_ADMIN_ID + "' AND r.mark = 1) AS platform_admin "
                        + where + " ORDER BY u.create_time DESC LIMIT ? OFFSET ?",
                (rs, n) -> new PlatformUserDto.View(
                        rs.getString("id"), rs.getString("username"), rs.getString("email"),
                        rs.getString("display_name"), (Integer) rs.getObject("status"),
                        rs.getBoolean("platform_admin"),
                        rs.getObject("create_time") == null ? null
                                : ((java.sql.Timestamp) rs.getObject("create_time")).toLocalDateTime()),
                pageArgs);
        return PageResult.of(rows, totalVal, page, size);
    }

    public PlatformUserDto.CreateResponse create(PlatformUserDto.CreateRequest req) {
        Long exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM core_auth_user WHERE tenant_id = ? AND username = ? AND mark = 1",
                Long.class, SYSTEM_TENANT, req.username());
        if (exists != null && exists > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "A platform user with username '" + req.username() + "' already exists");
        }
        KeycloakUserService kc = userServiceProvider.getIfAvailable();
        if (kc == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Keycloak is not enabled — platform user provisioning requires app.security.mode=oidc");
        }

        String tempPassword = generateTempPassword();
        // External mutation first; compensated below if the DB half fails.
        String kcId = kc.createUser(SYSTEM_REALM, req.username(), req.email(), req.displayName(), tempPassword);
        try {
            String userId = IdGenerator.ulid();
            String userNo = nextSystemUserNo();
            LocalDateTime now = LocalDateTime.now();
            jdbc.update(
                    "INSERT INTO core_auth_user "
                            + "  (id, tenant_id, username, email, user_no, display_name, "
                            + "   password_hash, keycloak_id, status, mark, "
                            + "   create_user, update_user, create_time, update_time) "
                            + "VALUES (?, ?, ?, ?, ?, ?, NULL, ?, 1, 1, "
                            + "        'platform-admin', 'platform-admin', ?, ?)",
                    userId, SYSTEM_TENANT, req.username(), req.email(), userNo,
                    req.displayName(), kcId, now, now);
            jdbc.update(
                    "INSERT INTO core_rbac_user_role "
                            + "  (id, tenant_id, user_id, role_id, mark, create_user, update_user) "
                            + "VALUES (?, ?, ?, ?, 1, 'platform-admin', 'platform-admin')",
                    IdGenerator.ulid(), SYSTEM_TENANT, userId, BuiltInRoles.PLATFORM_OPERATOR_ID);

            log.info("[platform-user] provisioned ops user '{}' (id={}, kcId={}) with PLATFORM_OPERATOR",
                    req.username(), userId, kcId);
            return new PlatformUserDto.CreateResponse(userId, req.username(), tempPassword);
        } catch (RuntimeException e) {
            // Compensation: remove the orphan KC user so a retry can reuse the username.
            try {
                kc.deleteUser(SYSTEM_REALM, kcId);
                log.warn("[platform-user] create failed for '{}' — compensated by deleting the orphan KC user",
                        req.username(), e);
            } catch (RuntimeException ce) {
                log.error("[platform-user] create failed for '{}' AND KC compensation failed — "
                        + "orphan Keycloak user {} needs manual cleanup", req.username(), kcId, ce);
            }
            throw e;
        }
    }

    /** Enable or disable a platform operator. KC first (stronger gate), then DB status. */
    @Transactional
    public void setEnabled(String id, boolean enabled) {
        Target t = requireManageable(id);
        KeycloakUserService kc = userServiceProvider.getIfAvailable();
        if (kc != null && t.keycloakId() != null && !t.keycloakId().isBlank()) {
            kc.setEnabled(SYSTEM_REALM, t.keycloakId(), enabled);
        }
        jdbc.update("UPDATE core_auth_user SET status = ?, update_time = ? WHERE id = ?",
                enabled ? 1 : 0, LocalDateTime.now(), t.id());
        log.info("[platform-user] {} ops user '{}' (id={})", enabled ? "enabled" : "disabled", t.username(), t.id());
    }

    /** Soft-delete a platform operator (DB first so app access stops; KC user removed best-effort). */
    @Transactional
    public void delete(String id) {
        Target t = requireManageable(id);
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("UPDATE core_rbac_user_role SET mark = 0, update_time = ? "
                + "WHERE user_id = ? AND tenant_id = ? AND mark = 1", now, t.id(), SYSTEM_TENANT);
        jdbc.update("UPDATE core_auth_user SET mark = 0, update_time = ? WHERE id = ?", now, t.id());
        KeycloakUserService kc = userServiceProvider.getIfAvailable();
        if (kc != null && t.keycloakId() != null && !t.keycloakId().isBlank()) {
            try {
                kc.deleteUser(SYSTEM_REALM, t.keycloakId());
            } catch (RuntimeException e) {
                // DB row is already gone; a lingering KC user would only re-provision
                // a roleless account on next login. Log for manual cleanup.
                log.warn("[platform-user] deleted DB user '{}' but KC delete failed (kcId={}): {}",
                        t.username(), t.keycloakId(), e.toString());
            }
        }
        log.info("[platform-user] deleted ops user '{}' (id={})", t.username(), t.id());
    }

    /** Reset a platform operator's password to a one-time temporary one (KC forces change). */
    public PlatformUserDto.ResetPwResponse resetPassword(String id) {
        Target t = requireManageable(id);
        KeycloakUserService kc = userServiceProvider.getIfAvailable();
        if (kc == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Keycloak is not enabled — password reset requires app.security.mode=oidc");
        }
        if (t.keycloakId() == null || t.keycloakId().isBlank()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "User has no Keycloak link");
        }
        String tempPassword = generateTempPassword();
        kc.setPassword(SYSTEM_REALM, t.keycloakId(), tempPassword, true);
        log.info("[platform-user] reset password for ops user '{}' (id={})", t.username(), t.id());
        return new PlatformUserDto.ResetPwResponse(t.username(), tempPassword);
    }

    /**
     * Load a manageable target: must be a system-tenant user, NOT the caller
     * themselves, and NOT a PLATFORM_ADMIN (so the super 'ops' account can never
     * be disabled/deleted/reset through this console).
     */
    private Target requireManageable(String id) {
        Map<String, Object> u;
        try {
            u = jdbc.queryForMap(
                    "SELECT id, username, keycloak_id FROM core_auth_user "
                            + "WHERE id = ? AND tenant_id = ? AND mark = 1", id, SYSTEM_TENANT);
        } catch (EmptyResultDataAccessException e) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Platform user not found: " + id);
        }
        String userId = (String) u.get("id");
        if (userId.equals(RequestContext.userId())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR, "You cannot manage your own account here");
        }
        Long isSuper = jdbc.queryForObject(
                "SELECT COUNT(*) FROM core_rbac_user_role WHERE user_id = ? AND tenant_id = ? "
                        + "AND role_id = ? AND mark = 1",
                Long.class, userId, SYSTEM_TENANT, BuiltInRoles.PLATFORM_ADMIN_ID);
        if (isSuper != null && isSuper > 0) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Cannot manage a platform admin (ops) account");
        }
        return new Target(userId, (String) u.get("username"), (String) u.get("keycloak_id"));
    }

    private record Target(String id, String username, String keycloakId) {}

    /** Next per-tenant user_no for the system tenant: U%08d after the current max. */
    private String nextSystemUserNo() {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(CAST(SUBSTRING(user_no FROM 2) AS INTEGER)), 0) "
                        + "FROM core_auth_user WHERE tenant_id = ? AND user_no ~ '^U[0-9]+$'",
                Integer.class, SYSTEM_TENANT);
        return String.format("U%08d", (max == null ? 0 : max) + 1);
    }

    /** 16-char temp password with guaranteed upper/lower/digit to satisfy common KC policies. */
    private static String generateTempPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ", lower = "abcdefghijkmnpqrstuvwxyz", digit = "23456789";
        String all = upper + lower + digit;
        StringBuilder sb = new StringBuilder();
        sb.append(upper.charAt(RANDOM.nextInt(upper.length())));
        sb.append(lower.charAt(RANDOM.nextInt(lower.length())));
        sb.append(digit.charAt(RANDOM.nextInt(digit.length())));
        for (int i = 0; i < 13; i++) sb.append(all.charAt(RANDOM.nextInt(all.length())));
        return sb.toString();
    }

    private static String like(String kw) {
        return "%" + kw + "%";
    }
}
