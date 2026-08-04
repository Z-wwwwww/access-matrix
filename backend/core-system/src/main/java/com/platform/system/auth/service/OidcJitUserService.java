package com.platform.system.auth.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.platform.core.common.dict.CommonStatus;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.infrastructure.numbering.NumberingService;
import com.platform.system.rbac.service.BuiltInRoleLookup;
import com.platform.core.infrastructure.security.OidcUserResolver;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.PasswordResetTokenMapper;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.mapper.RoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Just-In-Time provisioning of a business {@code core_auth_user} row from a
 * verified OIDC JWT. Three branches:
 *
 * <ol>
 *   <li><b>Bound user</b> — token's {@code sub} already maps to a row via
 *       the {@code (tenant_id, keycloak_id)} index. Fast path, read-only.</li>
 *   <li><b>Legacy-password user, first SSO login</b> — no row matches
 *       {@code keycloak_id}, but a row exists with the same
 *       {@code (tenant_id, username)}. We bind it by writing
 *       {@code keycloak_id} so future requests take the fast path.</li>
 *   <li><b>Brand-new user</b> — neither matches. Insert a new
 *       {@code core_auth_user} row with a fresh ULID and seed the basic
 *       profile fields from the token claims. The user starts with no
 *       roles / no department; an admin must assign them via User.vue.</li>
 * </ol>
 *
 * <p>Only active when {@code app.security.mode=oidc}. In other modes the
 * bean is not registered and {@code CoreRequestContextFilter} falls back
 * to using the JWT subject as-is.
 *
 * <p>Username / email claim names are pulled from {@code app.security.jwt.*}
 * so they stay in sync with Spring Security's resource-server configuration.
 */
@Service
@ConditionalOnProperty(name = "app.security.mode", havingValue = "oidc")
public class OidcJitUserService implements OidcUserResolver {

    private static final Logger log = LoggerFactory.getLogger(OidcJitUserService.class);

    /** Per-tenant user numbering category (matches UserAdminService.USER_NO_KBN). */
    private static final String USER_NO_KBN = "USER";

    /** core_auth_user.username is VARCHAR(64); Keycloak allows up to 255. */
    private static final int USERNAME_MAX = 64;
    /** core_auth_user.display_name is VARCHAR(128). */
    private static final int DISPLAY_NAME_MAX = 128;

    private final UserMapper userMapper;
    /** Only for the "already migrated off SSO" guard on the legacy-bind branch. */
    private final PasswordResetTokenMapper resetTokenMapper;
    private final RoleMapper roleMapper;
    private final BuiltInRoleLookup roleLookup;
    private final NumberingService numberingService;

    /**
     * Defaults match {@link com.platform.core.infrastructure.config.properties.AppSecurityProperties.Jwt}
     * so they're aligned with the Spring Security side.
     */
    @Value("${app.security.jwt.tenant-claim:tid}")
    private String tenantClaim;

    @Value("${app.security.jwt.username-claim:preferred_username}")
    private String usernameClaim;

    /**
     * Expected OIDC issuer URL (legacy single-realm pinning). JIT only fires
     * for tokens whose {@code iss} matches this prefix; otherwise the token
     * is treated as in-house HS256 (signed by {@code AdminAuthController.login})
     * and we return its subject as-is. This is what makes the OIDC + in-house
     * dual-mode "break-glass" flow safe — without it, an HS256 token's ULID
     * subject would get written into a Keycloak user's {@code keycloak_id}
     * column and the next real OIDC login for that user would create a duplicate.
     */
    @Value("${app.security.oidc.issuer-uri:}")
    private String expectedIssuer;

    /**
     * Multi-realm trust prefix (recommended for SaaS multi-tenant). When set,
     * any token whose {@code iss} starts with {@code <base>/realms/} is
     * treated as OIDC; the actual realm name is read from the token's
     * {@code tid} claim. Takes precedence over {@link #expectedIssuer} so
     * a base-uri-configured deploy doesn't accidentally fall back to
     * single-realm semantics if both happen to be set.
     */
    @Value("${app.security.oidc.issuer-base-uri:}")
    private String expectedIssuerBase;

    public OidcJitUserService(UserMapper userMapper, RoleMapper roleMapper,
                              BuiltInRoleLookup roleLookup, NumberingService numberingService,
                              PasswordResetTokenMapper resetTokenMapper) {
        this.userMapper = userMapper;
        this.resetTokenMapper = resetTokenMapper;
        this.roleMapper = roleMapper;
        this.roleLookup = roleLookup;
        this.numberingService = numberingService;
    }

    @Override
    @Transactional
    public String resolveBusinessUserId(Jwt jwt) {
        // Skip JIT for non-OIDC tokens (HS256 break-glass tokens from
        // AdminAuthController). Their subject is already the business ULID,
        // so we return it directly — CoreRequestContextFilter's caller will
        // use it as-is. Only enforced when an expected issuer is configured
        // (the production path with @Value injection); when both are unset
        // (unit tests without Spring context) fall through and treat every
        // token as an OIDC candidate.
        //
        // Use getClaimAsString rather than getIssuer() — the latter calls
        // getClaimAsURL which throws IllegalArgumentException on non-URL
        // values. AdminAuthController.login signs HS256 tokens whose iss
        // is a plain string like "access-matrix-local", not a URL.
        String issuer = jwt.getClaimAsString("iss");
        if (expectedIssuerBase != null && !expectedIssuerBase.isBlank()) {
            String base = stripTrailingSlash(expectedIssuerBase) + "/realms/";
            if (issuer == null || !issuer.startsWith(base)) {
                return jwt.getSubject();
            }
        } else if (expectedIssuer != null && !expectedIssuer.isBlank()) {
            if (issuer == null || !issuer.startsWith(expectedIssuer)) {
                return jwt.getSubject();
            }
        }

        String kcId = jwt.getSubject();
        String tid  = jwt.getClaimAsString(tenantClaim);
        if (kcId == null || kcId.isBlank() || tid == null || tid.isBlank()) {
            log.warn("OIDC JIT: token missing sub or {} claim — refusing to provision", tenantClaim);
            return null;
        }

        // 1. Fast path: already bound.
        UserEntity bound = userMapper.findByKeycloakIdAndTenant(kcId, tid);
        if (bound != null) {
            if (isDisabled(bound)) return refuseDisabled(tid, kcId);
            return bound.getId();
        }

        // 1b. Deleted user — refuse. An admin deleted this user (the business row
        // is now mark=0), but their access token stays valid until it expires.
        // Without this guard the JIT path below would find no mark=1 row and
        // silently re-provision a brand-new roleless "ghost" account on every
        // request (no user_no, displayName from the token). Return null so the
        // request resolves to no business user → no access → the SPA logs out.
        if (userMapper.countDeletedByKeycloakIdAndTenant(kcId, tid) > 0) {
            log.warn("OIDC JIT: refusing to re-provision deleted user (tenant {}, keycloak id {}) — token still valid but account was deleted",
                    tid, kcId);
            return null;
        }

        // 2. Legacy user with same username — bind.
        //
        // Reaching this branch means the user successfully completed SSO
        // login (KC verified credentials, signed an RS256 token, we got it
        // here). That's our trigger to clean up the legacy password_hash
        // for non-super-admin users — at this point keeping the stale
        // bcrypt around would be the source of all the divergence headaches
        // we documented in docs/migration-password-to-sso.md. After this
        // UPDATE the row looks byte-identical to a fresh OIDC JIT user
        // (`password_hash=NULL`, `keycloak_id=<kcId>`), achieving the
        // "as if always OIDC" final state the migration runbook promises.
        //
        // Super-admin exemption: any user that holds the SUPER_ADMIN role
        // keeps their password_hash so they can still break-glass when KC
        // is unreachable. The exemption is exactly the same one the
        // post-migration cleanup SQL in the runbook uses, just applied
        // continuously rather than as a one-shot.
        String username = jwt.getClaimAsString(usernameClaim);
        if (username != null && !username.isBlank()) {
            UserEntity legacy = userMapper.findByIdentifier(tid, username);
            if (legacy != null) {
                // Same status gate as the fast path — see refuseDisabled(). Checked
                // BEFORE the bind so a disabled legacy user doesn't get their
                // password_hash cleared on the way to being refused: that would strip
                // their break-glass credential as a side effect of a rejected login.
                if (isDisabled(legacy)) return refuseDisabled(tid, kcId);
                // Refuse to drag an account that deliberately left SSO back onto it.
                // PasswordResetController clears keycloak_id and disables the KC user,
                // but that disable is best-effort — a surviving or re-enabled KC
                // account can still authenticate, and binding here would null the
                // password_hash the user just set, on a flow documented as
                // irreversible, with the single-use reset token already spent.
                if (resetTokenMapper.countConsumedByUser(tid, legacy.getId()) > 0) {
                    log.warn("OIDC JIT: refusing to re-bind {} (tenant {}) to keycloak id {} — this account "
                                    + "completed the SSO->password reverse migration; the Keycloak user should "
                                    + "have been disabled and needs cleaning up",
                            legacy.getId(), tid, kcId);
                    return null;
                }
                // mark is @TableLogic — UpdateWrapper is the safe shape
                // for SET clauses where some columns are nullable (the
                // entity setter + updateById path would also work for
                // keycloak_id alone, but for nullable password_hash MP's
                // NOT_NULL field strategy would strip the SET column).
                boolean isSuperAdmin = isSuperAdmin(legacy.getId(), tid);
                UpdateWrapper<UserEntity> upd = new UpdateWrapper<UserEntity>()
                        .eq("id", legacy.getId())
                        .eq("tenant_id", tid)
                        .set("keycloak_id", kcId)
                        .set("update_user", "oidc-jit-bind");
                if (!isSuperAdmin) {
                    upd.set("password_hash", null);
                }
                userMapper.update(null, upd);
                if (isSuperAdmin) {
                    log.info("OIDC JIT: bound super-admin {} (tenant {}) to keycloak id {} — password_hash preserved for break-glass",
                            legacy.getId(), tid, kcId);
                } else {
                    log.info("OIDC JIT: bound legacy user {} (tenant {}) to keycloak id {} — password_hash cleared",
                            legacy.getId(), tid, kcId);
                }
                return legacy.getId();
            }
        }

        // 3. Brand new — insert.
        UserEntity fresh = new UserEntity();
        fresh.setId(IdGenerator.ulid());
        fresh.setTenantId(tid);
        fresh.setKeycloakId(kcId);

        // Length guard on the token-derived identity. These strings come from the
        // IdP, NOT from one of our validated DTOs, so the @Size caps that protect
        // UserAdminService.create don't apply here. Keycloak's default user profile
        // allows a username up to 255 while core_auth_user.username is VARCHAR(64):
        // an ops person creating a long-username user directly in the KC admin
        // console produced a user who authenticates fine at KC and then has EVERY
        // API call blow up at this insert ("value too long for type character
        // varying(64)"), surfacing as a 500 "Unhandled exception" from
        // CoreRequestContextFilter with nothing pointing at the username length.
        // Refuse to provision instead, the same graceful shape this class already
        // uses for a deleted user: log the reason and return null → the request
        // resolves to no business user → the SPA logs out.
        String candidate = username == null || username.isBlank() ? kcId : username;
        if (candidate.length() > USERNAME_MAX) {
            log.warn("OIDC JIT: refusing to provision (tenant {}, keycloak id {}) — username is {} chars, "
                            + "core_auth_user.username holds {}. Shorten the username in Keycloak.",
                    tid, kcId, candidate.length(), USERNAME_MAX);
            return null;
        }
        fresh.setUsername(candidate);
        fresh.setEmail(jwt.getClaimAsString("email"));
        // OIDC standard claims: prefer "name"; fall back to "given_name + family_name".
        String display = jwt.getClaimAsString("name");
        if (display == null || display.isBlank()) {
            String given  = jwt.getClaimAsString("given_name");
            String family = jwt.getClaimAsString("family_name");
            if (given != null || family != null) {
                display = ((given == null ? "" : given) + " " + (family == null ? "" : family)).trim();
            }
        }
        // display_name is cosmetic (VARCHAR(128)) and the given+family concat above
        // makes it the easiest of the three to overflow — truncate rather than refuse
        // a login over a display string. (email needs no guard: our column is 255,
        // the same width as Keycloak's own, so a KC-issued email claim always fits.)
        String finalDisplay = display == null || display.isBlank() ? fresh.getUsername() : display;
        if (finalDisplay.length() > DISPLAY_NAME_MAX) {
            finalDisplay = finalDisplay.substring(0, DISPLAY_NAME_MAX);
        }
        fresh.setDisplayName(finalDisplay);
        fresh.setStatus(1);
        // Allocate the per-tenant user number so a JIT-provisioned user is a
        // COMPLETE record (same as UserAdminService.create) — there is no
        // "assign number later" path, so leaving it NULL would strand the user
        // numberless forever. Best-effort: if the tenant's numbering definition
        // is somehow missing, log and proceed (a missing number must never block
        // a legitimate SSO login).
        try {
            fresh.setUserNo(numberingService.next(USER_NO_KBN, tid));
        } catch (RuntimeException e) {
            log.warn("OIDC JIT: could not allocate user_no for new user (tenant {}, keycloak id {}): {}",
                    tid, kcId, e.toString());
        }
        // Password column stays NULL — these users authenticate via the IdP.
        userMapper.insert(fresh);
        log.info("OIDC JIT: provisioned new user {} (tenant {}, username {}) for keycloak id {}",
                fresh.getId(), tid, fresh.getUsername(), kcId);
        return fresh.getId();
    }

    private static boolean isDisabled(UserEntity u) {
        // CommonStatus: 1=enabled, 0=disabled. A null status is treated as enabled
        // (legacy rows predating the column's NOT NULL default) — same reading as
        // AuthService.login, which only refuses when status is explicitly not 1... it
        // refuses null too, so keep this the narrower "explicitly 0" check to avoid
        // locking out any row an older migration left null.
        return u.getStatus() != null && u.getStatus() != CommonStatus.ENABLED.code();
    }

    /**
     * A DISABLED user must not resolve to a business user, even though Keycloak just
     * authenticated them.
     *
     * <p>Disabling is a two-sided write: the DB flag plus a Keycloak
     * {@code setEnabled(false)}. Only the Keycloak half actually stops a fresh SSO
     * login — and {@code SessionTerminationService} applies it BEST-EFFORT, logging
     * and swallowing a failure so "a KC hiccup must not block the local kick". So if
     * Keycloak is briefly unreachable while an operator disables someone, the DB
     * commits {@code status=0}, the Redis kick invalidates their CURRENT tokens, and
     * then they simply sign in again: KC still has them enabled, the fresh token's
     * iat is after the kick so {@code ForceLogoutFilter} passes it, and this resolver
     * used to hand back the business id with no status check at all. Console says
     * disabled, user keeps working — the exact failure this project already recorded
     * once (the {@code sozo-admin2} incident) and fixed only on the Keycloak side.
     *
     * <p>Checking here makes the DB flag authoritative on the OIDC path too, matching
     * what {@code AuthService.login} has always enforced for the break-glass path.
     * Same graceful shape as the deleted-user branch: log and return null → the
     * request resolves to no business user → the SPA logs out.
     */
    private String refuseDisabled(String tid, String kcId) {
        log.warn("OIDC JIT: refusing disabled user (tenant {}, keycloak id {}) — core_auth_user.status is not "
                        + "enabled; Keycloak still authenticated them, so its setEnabled(false) may not have landed",
                tid, kcId);
        return null;
    }

    private static String stripTrailingSlash(String s) {
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    /**
     * Does this user hold the SUPER_ADMIN role in the given tenant?
     * Resolves the tenant's SUPER_ADMIN role id via {@link BuiltInRoleLookup}
     * (cached, tenant-scoped) and checks the user's role bindings. The
     * role-bindings table is small per user so the bindings query is cheap.
     */
    private boolean isSuperAdmin(String userId, String tenantId) {
        try {
            String superId = roleLookup.superAdminRoleId(tenantId);
            if (superId == null) return false;
            return roleMapper.findRoleIdsByUserId(userId, tenantId).contains(superId);
        } catch (Exception e) {
            // If the role lookup itself fails, err on the side of caution
            // and treat the user as super-admin — keeping their break-glass
            // hash is much less damaging than accidentally clearing the
            // hash of an actual admin during a transient DB hiccup.
            log.warn("OIDC JIT: super-admin check failed for {} in {} ({}), preserving password_hash defensively",
                    userId, tenantId, e.toString());
            return true;
        }
    }

}
