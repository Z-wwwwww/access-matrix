package com.platform.business.demo.startup;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.platform.business.demo.task.entity.TaskEntity;
import com.platform.business.demo.task.mapper.TaskMapper;
import com.platform.core.infrastructure.security.rbac.DataScopeContext;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.entity.UserRoleEntity;
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

import java.time.LocalDate;
import java.util.List;

/**
 * Seeds 5 demo users (one per data_scope mode), their role bindings, and a
 * small fleet of {@code demo_task} rows that the data-scope walkthrough
 * filters against. Runs once at startup on the {@code local} profile —
 * production / dev / test never see these rows.
 *
 * <p>Why this lives in Java rather than V10 SQL:
 * <ul>
 *   <li>Password BCrypt hashes need the same {@link PasswordEncoder} the
 *       login flow uses, otherwise pre-baked hashes risk drift.</li>
 *   <li>The seeder runs after {@code LocalAdminSeeder} (order =
 *       HIGHEST_PRECEDENCE + 10) so the {@code admin} user exists as a
 *       reference assignee.</li>
 *   <li>Everything is idempotent: re-running the app does not duplicate
 *       users or tasks.</li>
 * </ul>
 *
 * <p>The seeded layout — see {@code docs/data-scope-demo.md} for the per-user
 * visibility matrix:
 * <pre>
 *   admin           HQ    SUPER_ADMIN     → sees all 15 tasks
 *   demo_all        HQ    DEMO_ALL        → sees all 15 tasks
 *   demo_deptsub    TOKYO DEMO_DEPT_SUB   → TOKYO + KYOTO  =  8 tasks
 *   demo_dept       OSAKA DEMO_DEPT       → OSAKA          =  4 tasks
 *   demo_self       TOKYO DEMO_SELF       → 3 tasks they created
 *   demo_custom     HQ    DEMO_CUSTOM     → KYOTO          =  3 tasks
 * </pre>
 */
@Component
@Profile("local")
public class DemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final String DEMO_PASSWORD = "demo123";

    // Fixed placeholder ULIDs so seed rows can reference each other deterministically.
    private static final String USER_DEMO_ALL     = "00000000000000000000USER11";
    private static final String USER_DEMO_DEPTSUB = "00000000000000000000USER12";
    private static final String USER_DEMO_DEPT    = "00000000000000000000USER13";
    private static final String USER_DEMO_SELF    = "00000000000000000000USER14";
    private static final String USER_DEMO_CUSTOM  = "00000000000000000000USER15";
    private static final String USER_ADMIN        = null; // resolved at runtime

    private static final String ROLE_DEMO_ALL     = "00000000000000000000ROLE11";
    private static final String ROLE_DEMO_DEPTSUB = "00000000000000000000ROLE12";
    private static final String ROLE_DEMO_DEPT    = "00000000000000000000ROLE13";
    private static final String ROLE_DEMO_SELF    = "00000000000000000000ROLE14";
    private static final String ROLE_DEMO_CUSTOM  = "00000000000000000000ROLE15";

    private static final String DEPT_HQ    = "00000000000000000000DEPT01";
    private static final String DEPT_TOKYO = "00000000000000000000DEPT02";
    private static final String DEPT_OSAKA = "00000000000000000000DEPT03";
    private static final String DEPT_KYOTO = "00000000000000000000DEPT04";

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final TaskMapper taskMapper;
    private final PasswordEncoder encoder;

    public DemoSeeder(UserMapper userMapper, UserRoleMapper userRoleMapper,
                      TaskMapper taskMapper, PasswordEncoder encoder) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.taskMapper = taskMapper;
        this.encoder = encoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public void seed() {
        try {
            ensureUser(USER_DEMO_ALL,     "demo_all",     "Demo: All Scope",      DEPT_HQ);
            ensureUser(USER_DEMO_DEPTSUB, "demo_deptsub", "Demo: Dept + Sub",     DEPT_TOKYO);
            ensureUser(USER_DEMO_DEPT,    "demo_dept",    "Demo: Own Dept",       DEPT_OSAKA);
            ensureUser(USER_DEMO_SELF,    "demo_self",    "Demo: Self Only",      DEPT_TOKYO);
            ensureUser(USER_DEMO_CUSTOM,  "demo_custom",  "Demo: Custom (KYOTO)", DEPT_HQ);

            ensureUserRole(USER_DEMO_ALL,     ROLE_DEMO_ALL);
            ensureUserRole(USER_DEMO_DEPTSUB, ROLE_DEMO_DEPTSUB);
            ensureUserRole(USER_DEMO_DEPT,    ROLE_DEMO_DEPT);
            ensureUserRole(USER_DEMO_SELF,    ROLE_DEMO_SELF);
            ensureUserRole(USER_DEMO_CUSTOM,  ROLE_DEMO_CUSTOM);

            seedTasks();
            log.info("DemoSeeder: ✓ data-scope demo data ready (5 users, 15 tasks)");
        } catch (Exception e) {
            log.warn("DemoSeeder: skipped — {}", e.getMessage());
        } finally {
            // Seeder runs outside any HTTP request — make sure no thread-local
            // bookkeeping bleeds into whatever worker thread Spring reuses next.
            DataScopeContext.clear();
        }
    }

    private void ensureUser(String userId, String username, String displayName, String deptId) {
        UserEntity existing = userMapper.selectById(userId);
        if (existing != null && existing.getMark() != null && existing.getMark() == 1) {
            return;
        }
        UserEntity u = new UserEntity();
        u.setId(userId);
        u.setTenantId("default");
        u.setUsername(username);
        u.setEmail(username + "@demo.local");
        u.setUserNo(username);
        u.setDisplayName(displayName);
        u.setDeptId(deptId);
        u.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        u.setRoles("[]");       // deprecated JSONB columns — kept for NOT NULL strategy
        u.setAuthorities("[]");
        u.setStatus(1);
        u.setMark(1);
        u.setCreateUser("system");
        u.setUpdateUser("system");
        userMapper.insert(u);
        log.info("DemoSeeder: inserted user {} (id={})", username, userId);
    }

    private void ensureUserRole(String userId, String roleId) {
        Long existing = userRoleMapper.selectCount(
                new QueryWrapper<UserRoleEntity>()
                        .eq("user_id", userId)
                        .eq("role_id", roleId)
                        .eq("mark", 1));
        if (existing != null && existing > 0) return;
        UserRoleEntity link = new UserRoleEntity();
        link.setUserId(userId);
        link.setRoleId(roleId);
        userRoleMapper.insert(link);
    }

    private void seedTasks() {
        // If anything already exists, treat seeding as done — keeps idempotency cheap.
        QueryWrapper<TaskEntity> existsW = new QueryWrapper<TaskEntity>().eq("mark", 1);
        DataScopeContext.markApplied(existsW);
        Long existing = taskMapper.selectCount(existsW);
        if (existing != null && existing > 0) return;

        // 15 tasks distributed so each demo user observes a distinctive subset.
        // (dept, creator)
        insertTask("HQ kickoff briefing",        DEPT_HQ,    USER_ADMIN,        1, 2);
        insertTask("HQ Q3 budget review",        DEPT_HQ,    USER_ADMIN,        2, 3);
        insertTask("HQ vendor renewal",          DEPT_HQ,    USER_DEMO_ALL,     1, 1);

        insertTask("TOKYO weekly standup notes", DEPT_TOKYO, USER_DEMO_DEPTSUB, 3, 1);
        insertTask("TOKYO onboarding plan",      DEPT_TOKYO, USER_DEMO_DEPTSUB, 1, 2);
        insertTask("TOKYO client follow-up",     DEPT_TOKYO, USER_DEMO_SELF,    2, 3);
        insertTask("TOKYO retro action items",   DEPT_TOKYO, USER_DEMO_SELF,    1, 2);
        insertTask("TOKYO ops audit",            DEPT_TOKYO, USER_ADMIN,        1, 1);

        insertTask("OSAKA dispatch schedule",    DEPT_OSAKA, USER_DEMO_DEPT,    2, 2);
        insertTask("OSAKA vendor visit",         DEPT_OSAKA, USER_DEMO_DEPT,    1, 1);
        insertTask("OSAKA monthly report",       DEPT_OSAKA, USER_DEMO_DEPT,    3, 2);
        insertTask("OSAKA staff training",       DEPT_OSAKA, USER_ADMIN,        1, 2);

        insertTask("KYOTO opening checklist",    DEPT_KYOTO, USER_DEMO_DEPTSUB, 1, 3);
        insertTask("KYOTO local event setup",    DEPT_KYOTO, USER_DEMO_SELF,    2, 2);
        insertTask("KYOTO supplier intake",      DEPT_KYOTO, USER_DEMO_CUSTOM,  1, 1);
    }

    private void insertTask(String title, String deptId, String creatorOrNull,
                            int status, int priority) {
        TaskEntity t = new TaskEntity();
        // Deterministic id from title so re-runs do not duplicate; ULID-shaped padding.
        String shortKey = title.toUpperCase().replaceAll("[^A-Z0-9]", "").substring(0,
                Math.min(8, title.replaceAll("[^A-Za-z0-9]", "").length()));
        t.setId(("DEMOTASK00000000000000000" + shortKey).substring(0, 26));
        t.setDeptId(deptId);
        t.setTitle(title);
        t.setContent("Demo task — illustrates data-scope visibility for `" + title + "`");
        t.setStatus(status);
        t.setPriority(priority);
        t.setDueDate(LocalDate.now().plusDays(7));
        // BaseEntity audit fields: tenantId / createUser / updateUser are normally
        // filled by AuditMetaObjectHandler; for seeded rows where no request
        // context is active we set them explicitly so the rows do not look like
        // they were created by "anonymous".
        t.setTenantId("default");
        t.setMark(1);
        if (creatorOrNull != null) {
            t.setCreateUser(creatorOrNull);
            t.setUpdateUser(creatorOrNull);
            t.setAssigneeUserId(creatorOrNull);
        } else {
            // null creator → admin user. We resolve the admin id once.
            String adminId = resolveAdminUserId();
            t.setCreateUser(adminId);
            t.setUpdateUser(adminId);
            t.setAssigneeUserId(adminId);
        }

        // The seeder runs outside any HTTP request — there's no caller-side
        // DataScopeHelper.apply, so the @DataScope guard on TaskMapper would
        // throw in dev/local. Tag this thread's request marker as if apply()
        // had been called: insertions don't get filtered anyway.
        DataScopeContext.markApplied(t);
        try {
            taskMapper.insert(t);
        } finally {
            DataScopeContext.clear();
        }
    }

    private String resolveAdminUserId() {
        List<UserEntity> admins = userMapper.selectList(
                new QueryWrapper<UserEntity>().eq("username", "admin").eq("mark", 1));
        return admins.isEmpty() ? "system" : admins.get(0).getId();
    }
}
