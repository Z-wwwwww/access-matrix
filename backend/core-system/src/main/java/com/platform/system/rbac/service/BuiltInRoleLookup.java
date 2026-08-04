package com.platform.system.rbac.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.platform.system.rbac.entity.RoleEntity;
import com.platform.system.rbac.mapper.RoleMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * Per-tenant lookup for built-in role IDs.
 *
 * <h3>Why this exists</h3>
 * {@link com.platform.core.common.security.BuiltInRoles#SUPER_ADMIN_ID} is
 * the demo tenant's hardcoded ULID. It's the right answer when the system
 * had exactly one business tenant (demo). After per-tenant SUPER_ADMIN
 * roles got seeded by {@link RbacSeederService} on tenant creation, every
 * other tenant has a fresh random ULID — and any code doing
 * {@code roleIds.contains(BuiltInRoles.SUPER_ADMIN_ID)} silently evaluates
 * false for those tenants, disabling 4 important guards:
 *
 * <ul>
 *   <li>{@code AuthService} break-glass alert eligibility</li>
 *   <li>{@code OidcJitUserService} password_hash preservation on JIT bind</li>
 *   <li>{@code BreakGlassController} super-admin gate</li>
 *   <li>{@code UserAdminService} "last active SUPER_ADMIN" deletion guard</li>
 * </ul>
 *
 * <p>This lookup resolves the role ID per tenant, by querying for
 * {@code (tenant_id, name='Super Administrator', is_built_in=1, mark=1)}.
 * The role name is the canonical key — it's locked by {@code is_built_in=1}
 * (UI rename refused by {@code RoleAdminService.assertNotBuiltIn}), so it's
 * a stable identifier across all tenants.
 *
 * <h3>Cache posture</h3>
 * Caffeine TTL 10 min, max 500 entries (well above any plausible tenant
 * count). Built-in roles can't be renamed via UI, so the name &rarr; id mapping
 * is durable for a living tenant.
 *
 * <p>The mapping DOES change on tenant create and hard delete, and
 * {@code TenantAdminService} calls {@link #invalidate(String)} on both. This
 * used to be documented as self-correcting ("create is a natural cache miss;
 * after hard delete no callers remain for that tenant") — both halves are wrong
 * when a code is hard-deleted and RE-CREATED, which is a supported workflow
 * ({@code uk_core_tenant_code} is partial on {@code mark = 1}). The re-seeded
 * role gets a fresh ULID — verified against the DB that the id changes — and the
 * loader caches the {@code Optional.empty()} "not found" answer too. A stale or
 * empty entry makes every caller of {@link #superAdminRoleId} silently wrong for
 * up to 10 minutes: {@code UserAdminService.assertNotProtectedAdmin} stops
 * recognising the new tenant's super admin (so any {@code user:update} holder can
 * edit / disable / delete / re-role them), {@code assignRoles} stops refusing a
 * second SUPER_ADMIN holder, and the break-glass exemption clears their
 * {@code password_hash} on first SSO bind.
 *
 * <h3>What this does NOT replace</h3>
 * {@link com.platform.core.common.security.BuiltInRoles#SUPER_ADMIN_ID}
 * stays. It still encodes "the demo tenant's super admin role id" and is
 * the right answer for demo-scoped bootstrap code ({@code LocalAdminSeeder}).
 * The constant is still useful as a known fixed value for tests and seeds.
 * What we're fixing here is the mistake of using it as if it applied to
 * <em>every</em> tenant.
 */
@Service
public class BuiltInRoleLookup {

    /** Name of the auto-seeded super-admin role across all tenants. Locked by is_built_in=1. */
    public static final String SUPER_ADMIN_NAME = "Super Administrator";

    private final RoleMapper roleMapper;
    private final LoadingCache<String, Optional<String>> superAdminCache;

    public BuiltInRoleLookup(RoleMapper roleMapper) {
        this.roleMapper = roleMapper;
        this.superAdminCache = Caffeine.newBuilder()
                .maximumSize(500)
                .expireAfterWrite(Duration.ofMinutes(10))
                .build(this::loadSuperAdminRoleId);
    }

    /**
     * The SUPER_ADMIN role id for {@code tenantId}, or {@code null} if the
     * tenant has no built-in super-admin row (e.g. unseeded or hard-deleted).
     */
    public String superAdminRoleId(String tenantId) {
        if (tenantId == null || tenantId.isBlank()) return null;
        return superAdminCache.get(tenantId).orElse(null);
    }

    /**
     * Drop the cached super-admin id for one tenant. Call after a role
     * rename / RBAC reseed / hard delete if the change might land while
     * the JVM is up. Most paths don't need to call this — TTL handles it.
     */
    public void invalidate(String tenantId) {
        if (tenantId == null) return;
        superAdminCache.invalidate(tenantId);
    }

    /** Drop all entries. Test fixtures use this between cases. */
    public void invalidateAll() {
        superAdminCache.invalidateAll();
    }

    private Optional<String> loadSuperAdminRoleId(String tenantId) {
        RoleEntity r = roleMapper.selectOne(new QueryWrapper<RoleEntity>()
                .eq("tenant_id", tenantId)
                .eq("name", SUPER_ADMIN_NAME)
                .eq("is_built_in", 1)
                .eq("mark", 1));
        // Optional.ofNullable on the wrapper so Caffeine actually caches
        // the "not found" answer too (LoadingCache rejects null values).
        return Optional.ofNullable(r == null ? null : r.getId());
    }
}
