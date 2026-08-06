package com.platform.core.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "app.security")
public record AppSecurityProperties(
        String mode,
        Jwt jwt,
        RateLimit rateLimit,
        Lockout lockout,
        PasswordPolicy passwordPolicy,
        RefreshCookie refreshCookie) {

    public AppSecurityProperties {
        // FAIL-CLOSED. SecurityConfig reads this: "permit-all" wires
        // `anyRequest().permitAll()`, i.e. no authentication on any endpoint. This
        // fallback used to be "permit-all", which inverted the promise
        // application.yml makes for precisely this case — 「base 默认 oidc（fail-closed：
        // 即使某 profile 漏配 mode，也默认带鉴权而非放行）」. Reachable via a declared-but-
        // empty APP_SECURITY_MODE, the env var application-prod.yml tells operators to
        // use for the legacy fallback. Landing on oidc means a misconfigured deploy
        // fails to start (no issuer) instead of coming up wide open; permit-all stays
        // available, but only when a profile asks for it explicitly.
        if (mode == null || mode.isBlank()) mode = "oidc";
        if (jwt == null) jwt = new Jwt(null, "tid", "sub", "preferred_username", "scope");
        if (rateLimit == null) rateLimit = new RateLimit(true, 30, Duration.ofMinutes(1));
        if (lockout == null) lockout = new Lockout(true, 5, Duration.ofMinutes(15), Duration.ofMinutes(15));
        // Last arg is failOpenOnHibpError and it must be FALSE: when the breach
        // check can't be reached we refuse the password change rather than let a
        // possibly-breached one through. application.yml documents exactly that
        // ("false = fail-closed(本默认)"); this fallback used to say true, so a
        // deployment that omitted app.security.password-policy silently ran the
        // opposite posture — the same trap as AppMybatisProperties' old
        // `new Tenant(false)`.
        if (passwordPolicy == null) passwordPolicy = new PasswordPolicy(
                8, 128, true, true, true, true,
                true, "https://api.pwnedpasswords.com", Duration.ofSeconds(3), false);
        if (refreshCookie == null) refreshCookie = new RefreshCookie("core_refresh", "/api/auth", true, "Strict");
    }

    public record Jwt(
            String secret,
            String tenantClaim,
            String userIdClaim,
            String usernameClaim,
            String authoritiesClaim) {}

    public record RateLimit(boolean enabled, int requestsPerMinute, Duration refillPeriod) {}

    public record Lockout(boolean enabled, int maxFailures, Duration window, Duration lockDuration) {}

    public record PasswordPolicy(
            int minLength, int maxLength,
            boolean requireDigit, boolean requireUpper, boolean requireLower, boolean requireSymbol,
            boolean hibpEnabled, String hibpBaseUrl, Duration hibpTimeout, boolean failOpenOnHibpError) {}

    public record RefreshCookie(String name, String path, boolean secure, String sameSite) {}
}
