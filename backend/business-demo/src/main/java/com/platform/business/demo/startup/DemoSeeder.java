package com.platform.business.demo.startup;

import com.platform.core.common.time.AppTime;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.dict.CommonStatus;
import com.platform.business.demo.task.entity.TaskEntity;
import com.platform.business.demo.task.mapper.TaskMapper;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.core.infrastructure.security.rbac.DataScopeContext;
import com.platform.system.auth.entity.UserEntity;
import com.platform.system.auth.mapper.UserMapper;
import com.platform.system.rbac.entity.UserRoleEntity;
import com.platform.system.rbac.mapper.UserRoleMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Seeds 5 demo users (one per data_scope mode), their role bindings, and a
 * small fleet of {@code demo_task} rows that the data-scope walkthrough
 * filters against. Runs once at startup on the {@code dev} profile (see
 * {@code @Profile} below) — production never sees these rows.
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
 * visibility matrix. ログイン ID は ASCII のままだが、表示名は実在しそうな
 * 日本人姓名に揃えてある（業務担当者の見やすさ向上のため）:
 * <pre>
 *   admin              HQ     SUPER_ADMIN     → sees all 15 tasks
 *   tanaka_taro        HQ     取締役          → sees all 15 tasks
 *   yamada_hanako      TOKYO  東京支社長      → TOKYO + KYOTO  =  8 tasks
 *   sato_ken           OSAKA  大阪支社課長    → OSAKA          =  4 tasks
 *   suzuki_misaki      TOKYO  一般社員        → 3 tasks they created
 *   takahashi_shinichi HQ     京都連絡担当    → KYOTO          =  3 tasks
 * </pre>
 */
@Component
@Profile("dev")
public class DemoSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoSeeder.class);
    private static final String DEMO_PASSWORD = "demo123";
    /** The demo (business) tenant all rows below belong to — matches the realm name. */
    private static final String DEMO_TENANT = "demo";

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

    /** Single source of truth for the 5 data-scope demo users — drives the
     *  local {@code core_auth_user} seed, the role binding, AND the Keycloak
     *  sync below, so the three never drift. */
    private record DemoUser(String id, String username, String displayName,
                            String email, String userNo, String deptId, String roleId) {}

    private static final List<DemoUser> DEMO_USERS = List.of(
            new DemoUser(USER_DEMO_ALL,     "tanaka_taro",        "田中 太郎", "tanaka.taro@demo.local",        "U00000011", DEPT_HQ,    ROLE_DEMO_ALL),
            new DemoUser(USER_DEMO_DEPTSUB, "yamada_hanako",      "山田 花子", "yamada.hanako@demo.local",      "U00000012", DEPT_TOKYO, ROLE_DEMO_DEPTSUB),
            new DemoUser(USER_DEMO_DEPT,    "sato_ken",           "佐藤 健",   "sato.ken@demo.local",           "U00000013", DEPT_OSAKA, ROLE_DEMO_DEPT),
            new DemoUser(USER_DEMO_SELF,    "suzuki_misaki",      "鈴木 美咲", "suzuki.misaki@demo.local",      "U00000014", DEPT_TOKYO, ROLE_DEMO_SELF),
            new DemoUser(USER_DEMO_CUSTOM,  "takahashi_shinichi", "高橋 慎一", "takahashi.shinichi@demo.local", "U00000015", DEPT_HQ,    ROLE_DEMO_CUSTOM));

    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final TaskMapper taskMapper;
    private final PasswordEncoder encoder;
    /**
     * Optional: only present when {@code app.security.mode=oidc} (the
     * {@link KeycloakUserService} bean is {@code @ConditionalOnProperty} on
     * that mode). In password / permit-all mode the provider yields null and
     * we skip the KC sync — the local BCrypt password seeded below is enough
     * to log in there. Injected via {@link ObjectProvider} so DemoSeeder still
     * wires cleanly when the bean is absent.
     */
    private final ObjectProvider<KeycloakUserService> keycloakUserServiceProvider;

    public DemoSeeder(UserMapper userMapper, UserRoleMapper userRoleMapper,
                      TaskMapper taskMapper, PasswordEncoder encoder,
                      ObjectProvider<KeycloakUserService> keycloakUserServiceProvider) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.taskMapper = taskMapper;
        this.encoder = encoder;
        this.keycloakUserServiceProvider = keycloakUserServiceProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE + 10)
    public void seed() {
        // Pin the demo tenant on this thread so the MP tenant interceptor scopes
        // these SELECT/INSERTs to 'demo' explicitly instead of leaning on its
        // hardcoded "demo" fallback (see MybatisPlusConfig / SystemAdminSeeder).
        RequestContext.set(DEMO_TENANT, "system", "system", Locale.JAPAN, "demo-seeder");
        try {
            for (DemoUser du : DEMO_USERS) {
                ensureUser(du.id(), du.username(), du.displayName(), du.email(), du.userNo(), du.deptId());
                ensureUserRole(du.id(), du.roleId());
            }

            seedTasks();

            // Mirror the 5 users into the Keycloak 'demo' realm so they can
            // actually SSO in (OIDC mode). Without this they exist only as
            // local core_auth_user rows and — since they are not super-admins —
            // can neither SSO (no KC account) nor break-glass, leaving the
            // data-scope walkthrough unusable in the default dev (oidc) mode.
            // First SSO login binds the KC account to the seeded row by
            // (tenant, username) — see OidcJitUserService.
            syncUsersToKeycloak();

            log.info("DemoSeeder: ✓ data-scope demo data ready (5 users, 15 tasks)");
        } catch (Exception e) {
            log.warn("DemoSeeder: skipped — {}", e.getMessage());
        } finally {
            // Seeder runs outside any HTTP request — make sure no thread-local
            // bookkeeping bleeds into whatever worker thread Spring reuses next.
            DataScopeContext.clear();
            RequestContext.clear();
        }
    }

    private void ensureUser(String userId, String username, String displayName,
                            String email, String userNo, String deptId) {
        // Probe with findMarkById, NOT selectById: `mark` is @TableLogic, so
        // MyBatis-Plus appends `AND mark = 1` and a row someone soft-deleted from
        // the user console reads as null — indistinguishable from "never seeded".
        // These rows carry FIXED ids, so acting on that null means re-inserting an
        // id the table still holds and the PRIMARY KEY rejects it; seed() catches
        // that as a plain WARN and abandons everything after it. Revive instead.
        Integer mark = userMapper.findMarkById(userId);
        if (mark != null) {
            if (mark != 1 && userMapper.reviveById(userId, OffsetDateTime.now()) > 0) {
                log.info("DemoSeeder: revived soft-deleted user {} (id={})", username, userId);
            }
            return;
        }
        UserEntity u = new UserEntity();
        u.setId(userId);
        u.setTenantId("demo");
        u.setUsername(username);
        u.setEmail(email);
        u.setUserNo(userNo);
        u.setDisplayName(displayName);
        u.setDeptId(deptId);
        u.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        u.setStatus(CommonStatus.ENABLED.code());
        u.setMark(1);
        u.setCreateUser("system");
        u.setUpdateUser("system");
        userMapper.insert(u);
        log.info("DemoSeeder: inserted user {} (id={})", username, userId);
    }

    private void ensureUserRole(String userId, String roleId) {
        Long existing = userRoleMapper.selectCount(
                new QueryWrapper<UserRoleEntity>()
                        .eq("tenant_id", "demo")
                        .eq("user_id", userId)
                        .eq("role_id", roleId)
                        .eq("mark", 1));
        if (existing != null && existing > 0) return;
        UserRoleEntity link = new UserRoleEntity();
        link.setTenantId("demo");
        link.setUserId(userId);
        link.setRoleId(roleId);
        userRoleMapper.insert(link);
    }

    /**
     * Ensure each demo user also exists in the Keycloak {@code demo} realm
     * with the shared {@link #DEMO_PASSWORD} (permanent), so they can SSO.
     * No-op when the {@link KeycloakUserService} bean is absent (non-oidc
     * mode) — the local BCrypt hash already covers password-mode login.
     *
     * <p>Best-effort per user: a single KC hiccup (or a user already present
     * from a previous boot) must not abort the rest of the demo seed. Mirrors
     * {@code LocalKeycloakAdminSeeder}'s create-then-set-permanent idiom so the
     * dev default doesn't trip a first-login password-change required action.
     */
    private void syncUsersToKeycloak() {
        KeycloakUserService kc = keycloakUserServiceProvider.getIfAvailable();
        if (kc == null) {
            log.info("DemoSeeder: Keycloak sync skipped (not oidc mode) — demo users are local-only");
            return;
        }
        for (DemoUser du : DEMO_USERS) {
            try {
                if (kc.userExists(DEMO_TENANT, du.username())) continue;
                String kcId = kc.createUser(DEMO_TENANT, du.username(), du.email(),
                        du.displayName(), DEMO_PASSWORD);
                kc.setPassword(DEMO_TENANT, kcId, DEMO_PASSWORD, /* temporary= */ false);
                log.info("DemoSeeder: provisioned KC user '{}' (kcId={}) in realm '{}'",
                        du.username(), kcId, DEMO_TENANT);
            } catch (Exception e) {
                log.warn("DemoSeeder: KC sync for '{}' failed (continuing): {}",
                        du.username(), e.toString());
            }
        }
    }

    private void seedTasks() {
        // 旧実装は「テーブルに 1 件でもあれば skip」する粗い idempotency だったが、
        // それだと業務担当者が手で 1 件作っただけで残りの seed が永久に入らなくなる。
        // 今は insertTask が ID 単位で skip するので、ここでの早期 return は廃止。

        // 15 tasks distributed so each demo user observes a distinctive subset.
        // (shortCode, title, dept, creator, status, priority)
        // shortCode はタスク ID の決定論的生成に使う。≤8 文字 / [A-Z0-9] 限定。
        insertTask("HQKICKOF", "本社 キックオフ会議",       DEPT_HQ,    USER_ADMIN,        1, 2);
        insertTask("HQBUDGET", "本社 Q3予算レビュー",       DEPT_HQ,    USER_ADMIN,        2, 3);
        insertTask("HQVENDOR", "本社 ベンダー契約更新",     DEPT_HQ,    USER_DEMO_ALL,     1, 1);

        insertTask("TYOSTAND", "東京 週次定例議事録",       DEPT_TOKYO, USER_DEMO_DEPTSUB, 3, 1);
        insertTask("TYOONBRD", "東京 オンボーディング計画", DEPT_TOKYO, USER_DEMO_DEPTSUB, 1, 2);
        insertTask("TYOFOLLO", "東京 顧客フォローアップ",   DEPT_TOKYO, USER_DEMO_SELF,    2, 3);
        insertTask("TYORETRO", "東京 振り返りアクション",   DEPT_TOKYO, USER_DEMO_SELF,    1, 2);
        insertTask("TYOAUDIT", "東京 運用監査",             DEPT_TOKYO, USER_ADMIN,        1, 1);

        insertTask("OSADISPA", "大阪 配送スケジュール",     DEPT_OSAKA, USER_DEMO_DEPT,    2, 2);
        insertTask("OSAVENDO", "大阪 ベンダー訪問",         DEPT_OSAKA, USER_DEMO_DEPT,    1, 1);
        insertTask("OSAREPOR", "大阪 月次レポート",         DEPT_OSAKA, USER_DEMO_DEPT,    3, 2);
        insertTask("OSATRAIN", "大阪 スタッフ研修",         DEPT_OSAKA, USER_ADMIN,        1, 2);

        insertTask("KYOCHECK", "京都 開店チェックリスト",   DEPT_KYOTO, USER_DEMO_DEPTSUB, 1, 3);
        insertTask("KYOEVENT", "京都 ローカルイベント準備", DEPT_KYOTO, USER_DEMO_SELF,    2, 2);
        insertTask("KYOSUPPL", "京都 仕入先受け入れ",       DEPT_KYOTO, USER_DEMO_CUSTOM,  1, 1);
    }

    /**
     * shortCode を ID の末尾に埋め込んで決定論的な 26 文字 ID を作る。
     * <p>旧版は title を A-Z0-9 抽出 → 取り過ぎ → {@code substring(0, 26)} で
     * shortCode 末尾が 1 文字しか残らず、TOKYO/HQ など同じ頭文字の task が
     * 全部 PK 衝突して 4 件しか insert できないバグがあった。
     * shortCode を ≤8 文字に限定し、{@code "DEMOTASK0000000000" (18) + padded8(shortCode)}
     * で 26 文字を厳密に組み立てるよう修正した。
     */
    private void insertTask(String shortCode, String title, String deptId, String creatorOrNull,
                            int status, int priority) {
        if (shortCode == null || shortCode.length() > 8 || !shortCode.matches("[A-Z0-9]+")) {
            throw new IllegalArgumentException(
                    "shortCode must be 1..8 chars of [A-Z0-9]: " + shortCode);
        }
        String paddedShort = (shortCode + "00000000").substring(0, 8);
        String taskId = "DEMOTASK0000000000" + paddedShort;   // 18 + 8 = 26 chars

        // 個別 task 単位の idempotency。既に入っていれば skip。
        // selectById ではなく findMarkById で見る:mark は @TableLogic なので
        // MyBatis-Plus が `AND mark = 1` を足し、デモ画面から削除された(mark=0)行は
        // null に見える —— 「まだ播種していない」と区別が付かない。この行の id は
        // 固定なので、その null を信じて INSERT すると主キーが衝突し、seed() は
        // それを WARN 一行に潰したうえで以降(残りのタスクと syncUsersToKeycloak)を
        // まるごと諦める。削除済みなら復活させる。
        Integer mark = taskMapper.findMarkById(taskId);
        if (mark != null) {
            if (mark != 1 && taskMapper.reviveById(taskId, OffsetDateTime.now()) > 0) {
                log.info("DemoSeeder: revived soft-deleted task {} (id={})", title, taskId);
            }
            return;
        }

        TaskEntity t = new TaskEntity();
        t.setId(taskId);
        t.setDeptId(deptId);
        t.setTitle(title);
        t.setContent("デモタスク — 「" + title + "」のデータスコープ可視性を確認");
        t.setStatus(status);
        t.setPriority(priority);
        t.setDueDate(LocalDate.now(AppTime.zone()).plusDays(7));
        // BaseEntity audit fields: tenantId / createUser / updateUser are normally
        // filled by AuditMetaObjectHandler; for seeded rows where no request
        // context is active we set them explicitly so the rows do not look like
        // they were created by "anonymous".
        t.setTenantId("demo");
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

        // No DataScopeContext dance here any more. DataScopeAspect used to demand a
        // marked argument on EVERY method of a @DataScope mapper, so this insert had
        // to mark the ENTITY — an object that is not a wrapper and was never going to
        // be scoped. The aspect now only gates methods that actually take a query
        // wrapper, which is the only thing a caller can forget to scope.
        taskMapper.insert(t);
    }

    private String resolveAdminUserId() {
        List<UserEntity> admins = userMapper.selectList(
                new QueryWrapper<UserEntity>().eq("username", "demo-admin").eq("mark", 1));
        return admins.isEmpty() ? "system" : admins.get(0).getId();
    }
}
