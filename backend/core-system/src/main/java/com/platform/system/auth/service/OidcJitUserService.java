package com.platform.system.auth.service;

import com.platform.core.common.id.IdGenerator;
import com.platform.core.infrastructure.security.OidcUserResolver;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
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

    private final UserMapper userMapper;

    /**
     * Defaults match {@link com.platform.core.infrastructure.config.properties.AppSecurityProperties.Jwt}
     * so they're aligned with the Spring Security side.
     */
    @Value("${app.security.jwt.tenant-claim:tid}")
    private String tenantClaim;

    @Value("${app.security.jwt.username-claim:preferred_username}")
    private String usernameClaim;

    public OidcJitUserService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    @Transactional
    public String resolveBusinessUserId(Jwt jwt) {
        String kcId = jwt.getSubject();
        String tid  = jwt.getClaimAsString(tenantClaim);
        if (kcId == null || kcId.isBlank() || tid == null || tid.isBlank()) {
            log.warn("OIDC JIT: token missing sub or {} claim — refusing to provision", tenantClaim);
            return null;
        }

        // 1. Fast path: already bound.
        UserEntity bound = userMapper.findByKeycloakIdAndTenant(kcId, tid);
        if (bound != null) return bound.getId();

        // 2. Legacy user with same username — bind.
        String username = jwt.getClaimAsString(usernameClaim);
        if (username != null && !username.isBlank()) {
            UserEntity legacy = userMapper.findByIdentifier(tid, username);
            if (legacy != null) {
                // mark is @TableLogic so we cannot setMark + updateById here;
                // we're not changing mark, just keycloak_id + update_user, so
                // updateById is safe. The link is exclusive (unique index on
                // (tenant_id, keycloak_id) WHERE mark=1) — duplicate-on-update
                // would mean some other row in this tenant already grabbed
                // this kcId, which shouldn't be possible given Step 1 missed.
                legacy.setKeycloakId(kcId);
                userMapper.updateById(legacy);
                log.info("OIDC JIT: bound existing legacy user {} (tenant {}) to keycloak id {}",
                        legacy.getId(), tid, kcId);
                return legacy.getId();
            }
        }

        // 3. Brand new — insert.
        UserEntity fresh = new UserEntity();
        fresh.setId(IdGenerator.ulid());
        fresh.setTenantId(tid);
        fresh.setKeycloakId(kcId);
        fresh.setUsername(username == null || username.isBlank() ? kcId : username);
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
        fresh.setDisplayName(display == null || display.isBlank() ? fresh.getUsername() : display);
        fresh.setStatus(1);
        // Password column stays NULL — these users authenticate via the IdP.
        // User number (userNo) intentionally left NULL: numbering is a business
        // event that should be triggered explicitly by an admin (UserAdminService.create),
        // not silently on every first-login. The user appears in User.vue
        // without a number until an admin runs "assign user number".
        userMapper.insert(fresh);
        log.info("OIDC JIT: provisioned new user {} (tenant {}, username {}) for keycloak id {}",
                fresh.getId(), tid, fresh.getUsername(), kcId);
        return fresh.getId();
    }

}
