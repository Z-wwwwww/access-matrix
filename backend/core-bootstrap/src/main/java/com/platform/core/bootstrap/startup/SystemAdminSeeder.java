package com.platform.core.bootstrap.startup;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.security.BuiltInRoles;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.entity.RoleEntity;
import com.platform.system.rbac.entity.UserRoleEntity;
import com.platform.system.rbac.mapper.RoleMapper;
import com.platform.system.rbac.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Ensures the 'system' tenant has a usable 'ops' user with the
 * PLATFORM_ADMIN role bound — analogue of {@link LocalAdminSeeder} for
 * the platform-ops tenant.
 *
 * <p>This is dev-only ({@code @Profile("local")}). In prod the platform
 * operator provisions their own staff into the system tenant out-of-band
 * (a SaaS company doesn't want a deployment artefact deciding who their
 * platform admins are).
 *
 * <p>Why a separate seeder from LocalAdminSeeder:
 * <ul>
 *   <li>Different tenant ({@code system} vs {@code demo}) — the queries
 *       and the entity's tenant_id differ on every line.</li>
 *   <li>Different role binding (PLATFORM_ADMIN vs SUPER_ADMIN).</li>
 *   <li>Decouples failure modes — if the system seeding fails (e.g. V26
 *       didn't apply yet), the demo seeding for normal dev still runs.</li>
 * </ul>
 *
 * <p>Order = {@code HIGHEST_PRECEDENCE + 10}, AFTER {@link LocalAdminSeeder}
 * (HIGHEST_PRECEDENCE) and {@link LocalKeycloakAdminSeeder} (HIGHEST_PRECEDENCE + 5).
 * Avoids racing the demo-tenant seeders that may share Mapper / cache state.
 *
 * <p>Sets a synthetic RequestContext while inserting so the MP tenant
 * interceptor (which normally inject WHERE tenant_id=?) takes the
 * platform-ops bypass path — without this the userRoleMapper.insert
 * would try to scope by the seeder's empty context and land the row
 * with the wrong tenant_id.
 */
@Component
@Profile("local")
public class SystemAdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(SystemAdminSeeder.class);

    /** The platform-ops tenant — matches the realm name and the JWT tid claim. */
    private static final String SYSTEM_TENANT = "system";
    /** Local-dev default credential. Rotate in any non-local environment. */
    private static final String OPS_USERNAME = "ops";
    private static final String OPS_EMAIL    = "ops@platform.local";
    private static final String OPS_DISPLAY  = "Platform Ops";
    private static final String OPS_PASSWORD = "ops";

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder encoder;

    public SystemAdminSeeder(UserMapper userMapper, RoleMapper roleMapper,
                             UserRoleMapper userRoleMapper, PasswordEncoder encoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.encoder = encoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public void seed() {
        // Take the platform-ops fast path through the MP tenant interceptor
        // — see MybatisPlusConfig.PLATFORM_TENANT_ID. Without this, INSERTs
        // through userRoleMapper would get scoped by the default "demo"
        // fallback and the row would land under the wrong tenant.
        RequestContext.set(SYSTEM_TENANT, "system", "system", Locale.JAPAN, "system-admin-seeder");
        try {
            UserEntity ops = ensureOpsUser();
            ensurePlatformAdminLink(ops);
        } finally {
            RequestContext.clear();
        }
    }

    private UserEntity ensureOpsUser() {
        UserEntity existing = userMapper.findByIdentifier(SYSTEM_TENANT, OPS_USERNAME);
        String opsHash = encoder.encode(OPS_PASSWORD);
        LocalDateTime now = LocalDateTime.now();

        if (existing == null) {
            UserEntity u = new UserEntity();
            u.setId(IdGenerator.ulid());
            u.setTenantId(SYSTEM_TENANT);
            u.setUsername(OPS_USERNAME);
            u.setEmail(OPS_EMAIL);
            // userNo namespace is per-tenant; the system tenant gets its own
            // sequence and the first ops user is #1.
            u.setUserNo("U00000001");
            u.setDisplayName(OPS_DISPLAY);
            u.setPasswordHash(opsHash);
            u.setStatus(1);
            u.setMark(1);
            u.setCreateUser("system");
            u.setUpdateUser("system");
            u.setCreateTime(now);
            u.setUpdateTime(now);
            userMapper.insert(u);
            log.info("SystemAdminSeeder: inserted ops user (id={})", u.getId());
            return u;
        }

        // Idempotent refresh — keep the password aligned with the local-dev
        // default so a dev who somehow rotated it still gets ops/ops back
        // after restart (same contract as LocalAdminSeeder).
        boolean dirty = false;
        if (!encoder.matches(OPS_PASSWORD, existing.getPasswordHash())) {
            existing.setPasswordHash(opsHash);
            dirty = true;
            log.info("SystemAdminSeeder: reset ops password to default");
        }
        if (existing.getStatus() == null || existing.getStatus() != 1) {
            existing.setStatus(1);
            dirty = true;
        }
        if (dirty) {
            userMapper.updateById(existing);
        }
        return existing;
    }

    private void ensurePlatformAdminLink(UserEntity ops) {
        RoleEntity platformAdmin = roleMapper.selectById(BuiltInRoles.PLATFORM_ADMIN_ID);
        if (platformAdmin == null || !Integer.valueOf(1).equals(platformAdmin.getMark())) {
            // V26 didn't run yet — most likely the migrations are out of
            // order or this is a partial environment. Don't crash; just
            // warn so the dev notices and fixes.
            log.warn("SystemAdminSeeder: PLATFORM_ADMIN role not found — V26 may not have run yet");
            return;
        }
        Long existing = userRoleMapper.selectCount(
                new QueryWrapper<UserRoleEntity>()
                        .eq("tenant_id", ops.getTenantId())
                        .eq("user_id", ops.getId())
                        .eq("role_id", platformAdmin.getId())
                        .eq("mark", 1));
        if (existing != null && existing > 0) {
            return;
        }
        UserRoleEntity link = new UserRoleEntity();
        link.setTenantId(ops.getTenantId());
        link.setUserId(ops.getId());
        link.setRoleId(platformAdmin.getId());
        userRoleMapper.insert(link);
        log.info("SystemAdminSeeder: linked ops user to PLATFORM_ADMIN role");
    }
}
