package com.platform.core.infrastructure.security.rbac;

import com.platform.core.common.context.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Returns the {@link DataScopeDecision} for the current request.
 *
 * <p>Reads the user id from {@link RequestContext} (populated by
 * {@code CoreRequestContextFilter}). In OIDC mode the value has already
 * been JIT-translated from the Keycloak UUID into the business ULID via
 * {@code OidcJitUserService}, so the data-scope lookup hits the right
 * RBAC rows. Reading {@code JwtAuthenticationToken.getToken().getSubject()}
 * directly here was the source of an outage where SUPER_ADMIN users
 * saw zero data — the Keycloak UUID doesn't match any
 * {@code core_auth_user.id}, so the scope resolver returned an empty
 * dept-id set and every list query came back blank.
 *
 * <p>The actual decision-load is delegated to a {@link UserDataScopeLookup}
 * bean (implemented in core-system, Caffeine-cached).
 */
@Component
public class DataScopeResolver {

    private static final Logger log = LoggerFactory.getLogger(DataScopeResolver.class);

    private final ObjectProvider<UserDataScopeLookup> lookupProvider;

    public DataScopeResolver(ObjectProvider<UserDataScopeLookup> lookupProvider) {
        this.lookupProvider = lookupProvider;
    }

    public DataScopeDecision currentDecision() {
        String userId = RequestContext.userId();
        if (userId == null || userId.isBlank()) return DataScopeDecision.empty(null);
        UserDataScopeLookup lookup = lookupProvider.getIfAvailable();
        if (lookup == null) {
            log.warn("No UserDataScopeLookup bean registered — returning empty decision for {}", userId);
            return DataScopeDecision.empty(userId);
        }
        return lookup.loadDecision(userId);
    }
}
