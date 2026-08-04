package com.platform.core.infrastructure.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mybatis")
public record AppMybatisProperties(Tenant tenant) {

    public AppMybatisProperties {
        // FAIL-CLOSED. This default used to be `new Tenant(false)`, i.e. absent
        // config ⇒ NO row-level tenant filtering ⇒ every tenant reads every other
        // tenant's rows, silently. Three places already promise the opposite:
        //   - application.yml above the key: "FAIL-CLOSED：默认 ON，新/漏配 profile
        //     也隔离，绝不默认跨租户可见"
        //   - backend/.env.example: "mybatis.tenant.enabled 恒 true(租户隔离,勿动)"
        //   - every other security fallback in this package (AppSecurityProperties'
        //     rateLimit / lockout / refreshCookie.secure all default to the safe value)
        // and there is no backstop: TenantSchemaGuard skips itself when the flag is
        // false (INFO log only), so the misconfiguration would produce no signal.
        // A genuinely tenant-less profile must now turn it off EXPLICITLY, which is
        // exactly what the yml comment already prescribes.
        if (tenant == null) tenant = new Tenant(true);
    }

    public record Tenant(boolean enabled) {}
}
