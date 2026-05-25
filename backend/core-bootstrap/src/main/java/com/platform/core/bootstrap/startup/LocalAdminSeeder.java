package com.platform.core.bootstrap.startup;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.entity.RoleEntity;
import com.platform.system.rbac.entity.UserRoleEntity;
import com.platform.system.rbac.mapper.RoleMapper;
import com.platform.system.rbac.mapper.UserRoleMapper;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.security.BuiltInRoles;
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

@Component
@Profile("local")
public class LocalAdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(LocalAdminSeeder.class);

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordEncoder encoder;

    public LocalAdminSeeder(UserMapper userMapper, RoleMapper roleMapper,
                            UserRoleMapper userRoleMapper, PasswordEncoder encoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.encoder = encoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void seed() {
        UserEntity admin = ensureAdminUser();
        ensureSuperAdminLink(admin);
        ensureHqDeptLink(admin);
    }

    private void ensureHqDeptLink(UserEntity admin) {
        // Stage 3 seed: link admin to HQ dept so DEPT/DEPT_AND_SUB scopes can demo against it.
        if ("00000000000000000000DEPT01".equals(admin.getDeptId())) return;
        admin.setDeptId("00000000000000000000DEPT01");
        // Same warning as ensureAdminUser: leave update_time alone, OptimisticLocker manages it.
        userMapper.updateById(admin);
        log.info("LocalAdminSeeder: bound admin user to HQ department");
    }

    private UserEntity ensureAdminUser() {
        UserEntity existing = userMapper.findByIdentifier("default", "admin");
        String adminHash = encoder.encode("admin");
        LocalDateTime now = LocalDateTime.now();

        if (existing == null) {
            UserEntity u = new UserEntity();
            u.setId(IdGenerator.ulid());
            u.setTenantId("default");
            u.setUsername("admin");
            u.setEmail("admin@platform.local");
            u.setUserNo("U00000001");
            u.setDisplayName("Local Admin");
            u.setPasswordHash(adminHash);
            u.setStatus(1);
            u.setMark(1);
            u.setCreateUser("system");
            u.setUpdateUser("system");
            u.setCreateTime(now);
            u.setUpdateTime(now);
            userMapper.insert(u);
            log.info("LocalAdminSeeder: inserted admin user (id={})", u.getId());
            return u;
        }

        boolean dirty = false;
        if (!encoder.matches("admin", existing.getPasswordHash())) {
            existing.setPasswordHash(adminHash);
            dirty = true;
            log.info("LocalAdminSeeder: reset admin password to default");
        }
        if (existing.getStatus() == null || existing.getStatus() != 1) {
            existing.setStatus(1);
            dirty = true;
        }
        if (existing.getUserNo() == null || existing.getUserNo().isBlank()) {
            existing.setUserNo("U00000001");
            dirty = true;
        }
        if (existing.getEmail() == null || existing.getEmail().isBlank()) {
            existing.setEmail("admin@platform.local");
            dirty = true;
        }
        if (dirty) {
            // Do NOT touch update_time — it is the @Version column. Setting it manually
            // breaks OptimisticLocker's WHERE clause and silently no-ops the update.
            // MyBatis-Plus's OptimisticLockerInnerInterceptor handles the version bump itself.
            userMapper.updateById(existing);
            log.info("LocalAdminSeeder: refreshed admin user fields");
        }
        return existing;
    }

    private void ensureSuperAdminLink(UserEntity admin) {
        // Look up by seeded ULID (see BuiltInRoles), not by code/name — the admin
        // may have renamed the role's display name, which is now fully editable.
        RoleEntity superAdmin = roleMapper.selectById(BuiltInRoles.SUPER_ADMIN_ID);
        if (superAdmin == null || !Integer.valueOf(1).equals(superAdmin.getMark())) {
            log.warn("LocalAdminSeeder: SUPER_ADMIN role not found — Flyway V5 may not have run yet");
            return;
        }
        Long existing = userRoleMapper.selectCount(
                new QueryWrapper<UserRoleEntity>()
                        .eq("user_id", admin.getId())
                        .eq("role_id", superAdmin.getId())
                        .eq("mark", 1));
        if (existing != null && existing > 0) {
            return;
        }
        UserRoleEntity link = new UserRoleEntity();
        link.setUserId(admin.getId());
        link.setRoleId(superAdmin.getId());
        userRoleMapper.insert(link);
        log.info("LocalAdminSeeder: linked admin user to SUPER_ADMIN role");
    }
}
