package com.platform.core.bootstrap.startup;

import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.core.common.id.IdGenerator;
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
    private final PasswordEncoder encoder;

    public LocalAdminSeeder(UserMapper userMapper, PasswordEncoder encoder) {
        this.userMapper = userMapper;
        this.encoder = encoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void seed() {
        UserEntity existing = userMapper.findByIdentifier("admin");
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
            u.setRoles("[\"ADMIN\"]");
            u.setAuthorities("[\"*:*\"]");
            u.setStatus(1);
            u.setMark(1);
            u.setCreateUser("system");
            u.setUpdateUser("system");
            u.setCreateTime(now);
            u.setUpdateTime(now);
            userMapper.insert(u);
            log.info("LocalAdminSeeder: inserted admin user (id={})", u.getId());
            return;
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
            existing.setUpdateTime(now);
            existing.setUpdateUser("system");
            userMapper.updateById(existing);
            log.info("LocalAdminSeeder: refreshed admin user fields");
        }
    }
}
