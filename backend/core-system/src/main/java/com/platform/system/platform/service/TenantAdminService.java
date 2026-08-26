package com.platform.system.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.time.AppTime;
import com.platform.core.common.id.IdGenerator;
import com.platform.system.dict.builtin.TenantStatus;
import com.platform.core.common.result.PageResult;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.mail.MailService;
import com.platform.core.infrastructure.numbering.NumberingService;
import com.platform.core.infrastructure.security.keycloak.KeycloakRealmService;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.service.InviteTokenService;
import com.platform.system.auth.service.SessionTerminationService;
import com.platform.system.platform.dto.TenantDto;
import com.platform.system.rbac.service.BuiltInRoleLookup;
import com.platform.system.rbac.service.PermissionCacheService;
import com.platform.system.platform.entity.TenantEntity;
import com.platform.system.platform.mapper.TenantMapper;
import com.platform.system.rbac.service.RbacSeederService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Tenant CRUD for platform-ops callers. All operations are bound to the
 * platform-ops authority surface ({@code platform:tenant:*}) — see
 * {@code PlatformTenantController} for the controller-layer gating.
 *
 * <h3>Two-sided writes</h3>
 * Creating, suspending, or soft-deleting a tenant changes state in TWO places:
 *
 * <ul>
 *   <li><b>Keycloak</b> — the realm itself. {@link KeycloakRealmService}
 *       handles realm creation / enable / disable.</li>
 *   <li><b>core_tenant</b> — our central registry row.</li>
 * </ul>
 *
 * <p>We always touch Keycloak FIRST for destructive operations. If
 * Keycloak fails we never touch the DB → no orphan row. If Keycloak
 * succeeds and the DB step fails, the realm is the leftover (the
 * operator can either retry to recover or manually delete the realm).
 *
 * <h3>Onboarding flow on create</h3>
 * Inside the same transaction, {@link #create} also:
 * <ol>
 *   <li>Seeds the new tenant's numbering counters (so user_no allocation works).</li>
 *   <li>Seeds the new tenant's RBAC scaffolding (SUPER_ADMIN role +
 *       {@code tenant:*} permission + cloned menus) via
 *       {@link RbacSeederService}.</li>
 *   <li>Creates the first admin user (no password) and binds them to
 *       SUPER_ADMIN.</li>
 *   <li>Creates the matching Keycloak user (no credentials) in the new
 *       realm so the OIDC JIT bind path can link them on first login.</li>
 *   <li>Mints an invite token and emails it to {@code contactEmail} so
 *       the recipient sets their own password via the invite landing page.</li>
 * </ol>
 *
 * <p>The mail step is wrapped in a try/catch — a flaky SMTP must not
 * roll back the tenant. If the email never arrives, the operator can
 * trigger a resend later (TODO: separate "resend invite" endpoint).
 *
 * <h3>Soft delete vs suspend</h3>
 * <ul>
 *   <li><b>Suspend</b> (status=0, mark=1) — temporary pause. Realm is
 *       disabled in KC so logins fail, but the tenant stays visible in
 *       the platform list and is easily resumable.</li>
 *   <li><b>Soft delete</b> (mark=0) — tenant removed from the list. Same
 *       KC effect, but recovery requires DB edit or a future "restore"
 *       feature. Business data stays untouched.</li>
 *   <li>Hard delete is intentionally NOT exposed — see follow-up task.</li>
 * </ul>
 */
@Service
public class TenantAdminService {

    private static final Logger log = LoggerFactory.getLogger(TenantAdminService.class);

    /**
     * Tenant codes reserved/protected by the project — can't be created by
     * customers, and can't be suspended/deleted. Only {@code system} is truly
     * built-in: it owns the platform-ops users and the central registry, so
     * losing it would break the whole platform. {@code demo} is an ordinary
     * (seeded) tenant — editable, suspendable, deletable like any customer.
     */
    private static final Set<String> RESERVED_CODES = Set.of("system");

    /** Lowercase alphanumeric + dash/underscore, 1..64 chars. Username constraint. */
    private static final Pattern USERNAME_OK = Pattern.compile("^[a-z0-9][a-z0-9_-]{0,63}$");

    /** Used by NumberingService.next when allocating the new admin's user_no. */
    private static final String USER_NO_KBN = "USER";

    /**
     * Every per-tenant table {@link #hardDelete} purges, in FK-safe order.
     *
     * <p>Extracted as a named inventory because it is the one place that has to
     * stay in step with the schema: a table that carries {@code tenant_id} but is
     * missing here keeps its rows after the tenant is gone.
     * {@link TenantPurgeCoverageGuard} cross-checks this list against
     * {@code information_schema} at boot so a newly-added per-tenant table can't
     * be forgotten silently.
     *
     * <p>Order matters — junction tables before the parents they reference (the
     * FKs are {@code ON DELETE RESTRICT}), users after {@code user_role}, and the
     * {@code core_tenant} registry row is NOT here: it is deleted last, by id,
     * after the Keycloak realm.
     */
    static final List<String> TENANT_PURGE_TABLES = List.of(
            "core_rbac_role_dept",
            "core_rbac_role_menu",
            "core_rbac_role_permission",
            "core_rbac_user_role",
            "core_rbac_role",
            "core_rbac_permission",
            "core_rbac_dept",
            "core_auth_user",
            "core_auth_login_log",
            "core_oplog",
            "core_password_reset_token",
            "core_user_invite",
            "core_numbering_key",
            "core_numbering_management",
            "core_domain_event",
            "core_notification",
            "core_job_log",
            "core_job",
            "demo_task");

    private final TenantMapper tenantMapper;
    /**
     * Keycloak realm operations are only available when
     * {@code app.security.mode=oidc}; in other modes {@link KeycloakRealmService}
     * isn't a bean. ObjectProvider keeps this service bootable in those
     * modes (where tenant management is meaningless anyway and the
     * controller is gated separately).
     */
    private final ObjectProvider<KeycloakRealmService> realmServiceProvider;
    private final ObjectProvider<KeycloakUserService> userServiceProvider;
    private final NumberingService numberingService;
    private final RbacSeederService rbacSeederService;
    private final InviteTokenService inviteTokenService;
    private final ObjectProvider<MailService> mailProvider;
    private final AppMailProperties mailProps;
    private final JdbcTemplate jdbc;
    /**
     * Self-reference (through the Spring proxy) used to invoke the
     * {@code @Transactional} {@link #persistNewTenant} from the NON-transactional
     * {@link #create}. A plain {@code this.persistNewTenant(...)} self-call would
     * bypass the proxy and the {@code @Transactional} advice — so the whole DB
     * unit (registry row + numbering/RBAC seed + user + invite) would NOT share
     * one transaction, and the numbering seed wouldn't be visible to the
     * immediately-following allocation. Routing through the proxy keeps it a
     * single declarative transaction, while create() itself stays outside any
     * transaction so its Keycloak calls aren't trapped in one.
     */
    private final ObjectProvider<TenantAdminService> self;
    /** Tenant-wide force-logout on suspend (terminates every active session of the tenant). */
    private final SessionTerminationService sessionTermination;
    /**
     * Only used to drop the per-tenant SUPER_ADMIN-role-id cache entry when this
     * service changes which role that is — see the invalidate() calls in
     * {@link #create} and {@link #hardDelete}.
     */
    private final BuiltInRoleLookup roleLookup;
    /** Dept caches are keyed by TENANT CODE, so a hard-delete has to clear them — see hardDelete. */
    private final PermissionCacheService permissionCacheService;

    public TenantAdminService(TenantMapper tenantMapper,
                              ObjectProvider<KeycloakRealmService> realmServiceProvider,
                              ObjectProvider<KeycloakUserService> userServiceProvider,
                              NumberingService numberingService,
                              RbacSeederService rbacSeederService,
                              InviteTokenService inviteTokenService,
                              ObjectProvider<MailService> mailProvider,
                              AppMailProperties mailProps,
                              JdbcTemplate jdbc,
                              ObjectProvider<TenantAdminService> self,
                              SessionTerminationService sessionTermination,
                              BuiltInRoleLookup roleLookup,
                              PermissionCacheService permissionCacheService) {
        this.tenantMapper = tenantMapper;
        this.realmServiceProvider = realmServiceProvider;
        this.userServiceProvider = userServiceProvider;
        this.numberingService = numberingService;
        this.rbacSeederService = rbacSeederService;
        this.inviteTokenService = inviteTokenService;
        this.mailProvider = mailProvider;
        this.mailProps = mailProps;
        this.jdbc = jdbc;
        this.self = self;
        this.sessionTermination = sessionTermination;
        this.roleLookup = roleLookup;
        this.permissionCacheService = permissionCacheService;
    }

    public PageResult<TenantDto.View> list(long page, long size, String keyword, Integer status) {
        Page<TenantEntity> p = new Page<>(page, size);
        QueryWrapper<TenantEntity> w = new QueryWrapper<TenantEntity>()
                .eq("mark", 1)
                .orderByDesc("create_time");
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("tenant_code", keyword).or().like("display_name", keyword));
        }
        // Optional status segment (1=active / 0=suspended; TenantStatus). null = all.
        if (status != null) {
            w.eq("status", status);
        }
        Page<TenantEntity> result = tenantMapper.selectPage(p, w);
        // One batch grouped count (not N+1) for this page's tenants; business rows
        // carry tenant_id = tenant_code, so we group core_auth_user by tenant_id.
        Map<String, Long> counts = userCounts(
                result.getRecords().stream().map(TenantEntity::getTenantCode).toList());
        List<TenantDto.View> records = result.getRecords().stream()
                .map(e -> toView(e, counts.getOrDefault(e.getTenantCode(), 0L)))
                .toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    /**
     * Non-deleted user count per tenant for the given tenant codes, in a single
     * grouped query. Raw {@link JdbcTemplate} so the MyBatis tenant interceptor
     * stays out of the way (these are deliberately cross-tenant reads). Tenants
     * with no users are simply absent from the map (caller defaults to 0).
     */
    private Map<String, Long> userCounts(List<String> tenantCodes) {
        if (tenantCodes.isEmpty()) return Map.of();
        String placeholders = String.join(",", java.util.Collections.nCopies(tenantCodes.size(), "?"));
        Map<String, Long> counts = new HashMap<>();
        jdbc.query(
                "SELECT tenant_id, COUNT(*) AS c FROM core_auth_user "
                        + "WHERE mark = 1 AND tenant_id IN (" + placeholders + ") GROUP BY tenant_id",
                rs -> { counts.put(rs.getString("tenant_id"), rs.getLong("c")); },
                tenantCodes.toArray());
        return counts;
    }

    /**
     * Aggregate counts for the platform tenant dashboard. Read-only — no state
     * change, so no domain event. Counts every registry row (mark=1), built-in
     * tenants included, so the KPI totals line up with {@link #list}. The monthly
     * series is a dense 12-month window (current month back 11) with gaps filled
     * to 0, built here so the frontend can plot it without back-filling.
     *
     * <p>Raw JdbcTemplate (not the mapper) so the MyBatis tenant interceptor stays
     * out of the way — same reasoning as {@link TenantMapper#findActiveByCode}.
     */
    public TenantDto.Stats stats() {
        long total        = count("SELECT COUNT(*) FROM core_tenant WHERE mark = 1");
        long active       = count("SELECT COUNT(*) FROM core_tenant WHERE mark = 1 AND status = 1");
        long suspended    = count("SELECT COUNT(*) FROM core_tenant WHERE mark = 1 AND status = 0");
        // Month boundaries are business-time (JST) calendar decisions: bucket the
        // timestamptz instants in AppTime.zone() and convert the window edge back
        // to an instant, so the result is independent of the DB session TimeZone.
        String tz = AppTime.zone().getId();
        long newThisMonth = count("SELECT COUNT(*) FROM core_tenant WHERE mark = 1 "
                + "AND create_time >= date_trunc('month', now() AT TIME ZONE '" + tz + "') AT TIME ZONE '" + tz + "'");

        // Sparse {month -> count} straight from PG, then densify into a fixed
        // 12-month window so months with zero signups still get a point.
        Map<String, Long> byMonth = new HashMap<>();
        jdbc.queryForList(
                "SELECT to_char(create_time AT TIME ZONE '" + tz + "', 'YYYY-MM') AS m, COUNT(*) AS c "
                        + "FROM core_tenant "
                        + "WHERE mark = 1 "
                        + "  AND create_time >= (date_trunc('month', now() AT TIME ZONE '" + tz + "') "
                        + "                      - INTERVAL '11 months') AT TIME ZONE '" + tz + "' "
                        + "GROUP BY 1")
                .forEach(r -> byMonth.put((String) r.get("m"), ((Number) r.get("c")).longValue()));

        List<TenantDto.MonthlyCount> monthly = new ArrayList<>(12);
        YearMonth cursor = YearMonth.now(AppTime.zone()).minusMonths(11);
        for (int i = 0; i < 12; i++) {
            String label = cursor.toString();   // 'YYYY-MM', matches the to_char above
            monthly.add(new TenantDto.MonthlyCount(label, byMonth.getOrDefault(label, 0L)));
            cursor = cursor.plusMonths(1);
        }
        return new TenantDto.Stats(total, active, suspended, newThisMonth, monthly);
    }

    private long count(String sql) {
        Long n = jdbc.queryForObject(sql, Long.class);
        return n == null ? 0L : n;
    }

    public TenantDto.View get(String id) {
        TenantEntity row = tenantMapper.selectById(id);
        if (row == null || !Integer.valueOf(1).equals(row.getMark())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found: " + id);
        }
        return toView(row, userCounts(List.of(row.getTenantCode())).getOrDefault(row.getTenantCode(), 0L));
    }

    /**
     * Provision a new tenant across Keycloak + the DB.
     *
     * <p><b>Consistency model (saga with compensation).</b> Creating a tenant
     * mutates two systems that have no shared transaction: Keycloak (the realm
     * + admin user) and our DB (registry row + RBAC + business user). We can't
     * wrap a KC REST call in a DB transaction, so instead:
     * <ol>
     *   <li>Validate (no side effects).</li>
     *   <li>Create the realm in KC, then the admin user in KC — the only two
     *       external mutations, done <em>before</em> the DB work.</li>
     *   <li>Run <em>all</em> DB writes in a single {@link #txTemplate}
     *       transaction so they commit or roll back as one unit.</li>
     *   <li>If the DB transaction (or the KC user creation) fails, compensate
     *       by deleting the realm — which cascades away the KC admin user too.
     *       This guarantees we never strand an orphan realm that would block a
     *       retry on the {@code realmExists} guard.</li>
     * </ol>
     * Net effect: either the tenant exists fully in both systems, or in
     * neither. The only residue a failure can leave is if compensation itself
     * fails (KC unreachable mid-rollback) — that case is logged at ERROR with
     * both stack traces for manual cleanup.
     */
    public String create(TenantDto.CreateRequest req) {
        // ── 1. Validate (no side effects yet) ───────────────────────
        if (RESERVED_CODES.contains(req.tenantCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Tenant code '" + req.tenantCode() + "' is reserved");
        }
        if (tenantMapper.findActiveByCode(req.tenantCode()) != null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Tenant code '" + req.tenantCode() + "' already exists");
        }
        KeycloakRealmService realmService = realmServiceProvider.getIfAvailable();
        if (realmService == null) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Keycloak is not enabled — tenant provisioning requires app.security.mode=oidc");
        }
        if (realmService.realmExists(req.tenantCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Realm '" + req.tenantCode() + "' already exists in Keycloak — "
                            + "either pick a different code or import it via the DB after manual cleanup");
        }

        String adminUsername = resolveAdminUsername(req);
        String adminEmail = req.contactEmail();
        String adminDisplayName = req.displayName() + " Admin";

        // ── 2. External mutation: create the realm in Keycloak ───────
        // Done outside any DB transaction. Compensated by deleteRealm in
        // the catch below if anything downstream fails.
        realmService.createRealmFromTemplate(req.tenantCode(), req.displayName());

        try {
            // ── 3. External mutation: create the KC admin user ───────
            // No credentials — the invite landing page sets the password
            // later. Created here (before the DB tx) so its kcId can be
            // persisted atomically with the business rows.
            String kcId = null;
            KeycloakUserService userService = userServiceProvider.getIfAvailable();
            if (userService != null) {
                kcId = userService.createUser(req.tenantCode(), adminUsername, adminEmail,
                        adminDisplayName, /* tempPassword = */ null);
            }
            final String kcIdFinal = kcId;

            // ── 4. All DB writes, atomically ─────────────────────────
            // Pure DB, no external calls inside (the invite email is
            // fire-and-forget and swallows its own errors). Invoked through
            // the proxy (self) so @Transactional applies — one transaction for
            // the whole unit, so the numbering/RBAC seed is visible to the
            // allocation that immediately follows it.
            String newTenantId = self.getObject().persistNewTenant(
                    req, adminUsername, adminEmail, adminDisplayName, kcIdFinal);
            // Drop any cached answer for this code. BuiltInRoleLookup caches the
            // "not found" result too (its loader wraps in Optional precisely so
            // Caffeine will), so a code that was probed before its SUPER_ADMIN role
            // existed — most plausibly a re-create of a code that was just
            // hard-deleted — would keep answering null/stale for up to 10 min.
            roleLookup.invalidate(req.tenantCode());
            return newTenantId;
        } catch (RuntimeException e) {
            // ── Compensation ─────────────────────────────────────────
            // KC user creation or the DB transaction failed after the
            // realm was created. Delete the realm so no orphan remains to
            // block a retry. Deleting the realm cascades away the KC admin
            // user, so one delete covers both external mutations.
            try {
                realmService.deleteRealm(req.tenantCode());
                log.warn("[tenant] create failed for '{}' — compensated by deleting the orphan realm",
                        req.tenantCode(), e);
            } catch (RuntimeException ce) {
                // Compensation failed → a real orphan realm remains. Log
                // LOUDLY (both causes) so an operator can clean up by hand.
                log.error("[tenant] create failed for '{}' AND compensation (realm delete) failed — "
                                + "manual cleanup of the Keycloak realm is required. Original cause below.",
                        req.tenantCode(), e);
                log.error("[tenant] compensation failure detail", ce);
            }
            throw e;
        }
    }

    /**
     * Pure-DB half of {@link #create}, run inside {@link #txTemplate}: inserts
     * the registry row, seeds numbering + RBAC, provisions the business admin
     * user + SUPER_ADMIN binding, and mints/sends the invite. Contains NO
     * external (Keycloak) calls — the only outbound I/O is the fire-and-forget
     * invite email, whose failure is swallowed and never rolls back the tenant.
     *
     * @param kcId the Keycloak user id created in {@link #create} (nullable
     *             when Keycloak user provisioning is unavailable)
     * @return the new tenant registry row id (ULID)
     */
    @Transactional
    public String persistNewTenant(TenantDto.CreateRequest req, String adminUsername,
                                   String adminEmail, String adminDisplayName, String kcId) {
        // ── Registry row ────────────────────────────────────────────
        TenantEntity row = new TenantEntity();
        row.setId(IdGenerator.ulid());
        row.setTenantId("system");
        row.setTenantCode(req.tenantCode());
        row.setDisplayName(req.displayName());
        row.setContactEmail(req.contactEmail());
        row.setStatus(TenantStatus.ACTIVE.code());
        row.setMark(1);
        row.setCreateUser("platform-admin");
        row.setUpdateUser("platform-admin");
        row.setCreateTime(OffsetDateTime.now());
        row.setUpdateTime(OffsetDateTime.now());
        tenantMapper.insert(row);

        // ── Per-tenant numbering definitions ────────────────────────
        // Without this, the new tenant's first numberingService.next("USER", ...)
        // would error with "Numbering definition not found".
        numberingService.seedDefaultsForTenant(req.tenantCode());

        // ── RBAC scaffolding (role + perm + menus) ──────────────────
        // Returns the new SUPER_ADMIN role id so we can bind the admin user.
        String superAdminRoleId = rbacSeederService.seedDefaultsForTenant(req.tenantCode());

        // ── Business user row. Use JdbcTemplate so we set tenant_id ──
        // explicitly to the NEW tenant — going through UserMapper would
        // pick up RequestContext.tenantId() = 'system' via AuditMetaObjectHandler.
        String userId = IdGenerator.ulid();
        String userNo = numberingService.next(USER_NO_KBN, req.tenantCode());
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update(
                "INSERT INTO core_auth_user "
                        + "  (id, tenant_id, username, email, user_no, display_name, "
                        + "   password_hash, keycloak_id, status, mark, "
                        + "   create_user, update_user, create_time, update_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, NULL, ?, 1, 1, "
                        + "        'platform-admin', 'platform-admin', ?, ?)",
                userId, req.tenantCode(), adminUsername, adminEmail, userNo,
                adminDisplayName, kcId, now, now);

        // user_role binding to SUPER_ADMIN.
        jdbc.update(
                "INSERT INTO core_rbac_user_role "
                        + "  (id, tenant_id, user_id, role_id, mark, create_user, update_user) "
                        + "VALUES (?, ?, ?, ?, 1, 'platform-admin', 'platform-admin')",
                IdGenerator.ulid(), req.tenantCode(), userId, superAdminRoleId);

        // ── Mint invite + send email ────────────────────────────────
        // Mint is durable (token row). sendInviteMail is fire-and-forget;
        // a flaky SMTP must not roll back the tenant, so it swallows errors.
        String token = inviteTokenService.mint(req.tenantCode(), userId, kcId);
        sendInviteMail(adminUsername, adminEmail, adminDisplayName,
                req.tenantCode(), token);

        log.info("[tenant] created tenant '{}' (id={}, displayName='{}') with admin '{}' invited",
                req.tenantCode(), row.getId(), req.displayName(), adminUsername);
        return row.getId();
    }

    /**
     * Resend the tenant admin's onboarding invite — for when the first email
     * never arrived, or the address was wrong.
     *
     * <p>Targets the single admin invited at tenant creation, located via the
     * outstanding (unconsumed) invite for the tenant. If {@code correctedEmail}
     * is non-blank it first fixes the admin's email everywhere
     * ({@code core_auth_user} + the Keycloak user + the tenant's contact email).
     * Then it invalidates any still-open invites (so only the new link works),
     * mints a fresh token, and re-sends.
     *
     * <p>Raw JDBC for the cross-tenant reads/writes so the MyBatis tenant
     * interceptor doesn't scope them to the caller's ('system') tenant — same
     * reason {@link #persistNewTenant} uses JDBC for the new tenant's rows.
     *
     * @param tenantId      the registry row id (ULID)
     * @param correctedEmail nullable; when set, corrects the admin's email
     */
    @Transactional
    public void resendAdminInvite(String tenantId, String correctedEmail) {
        TenantEntity row = tenantMapper.selectById(tenantId);
        if (row == null || !Integer.valueOf(1).equals(row.getMark())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found: " + tenantId);
        }
        if (RESERVED_CODES.contains(row.getTenantCode())) {
            // The UI disables this button for the built-in tenant; the endpoint is the
            // real boundary. 'system' holds the PLATFORM_ADMIN / PLATFORM_OPERATOR
            // built-in roles and the ops users' own pending invites, so without this
            // the query below would happily pick an ops user and re-mail them with the
            // TENANT-admin wording. Resending an ops invite belongs to
            // PlatformUserAdminService.resendInvite.
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Built-in tenant '" + row.getTenantCode() + "' has no tenant admin invite — "
                            + "use the platform-user console to resend an operator invite");
        }
        String tenantCode = row.getTenantCode();

        // Target the invite of the tenant ADMIN specifically — the holder of the
        // tenant's built-in SUPER_ADMIN role.
        //
        // This used to be "the pending invite with the latest expires_at", which is
        // wrong the moment the tenant has ANY newer pending invite: ordinary business
        // users invited through UserAdminService (INVITE mode) land in the very same
        // core_user_invite table under the very same tenant_id, and both flows share
        // app.invite.token-ttl — so "latest expires_at" just means "most recently
        // invited", which is almost never the admin (they were invited at tenant
        // creation). Verified against the real DB: with an admin invite and one
        // later business-user invite present, the old query picked the business user.
        // The operator then got a success toast while: that employee's pending link
        // was voided and re-mailed to them with the tenant-admin wording, and — when
        // a correctedEmail was supplied — their address was rewritten in the DB and in
        // Keycloak AND copied into core_tenant.contact_email, i.e. the tenant's
        // platform contact silently became a random employee's mailbox. The actual
        // admin's invite was never touched, so the one thing the operator asked for
        // did not happen.
        //
        // assignRoles refuses to grant SUPER_ADMIN to a second user, so the holder is
        // unique; is_built_in = 1 identifies it without depending on the role name.
        List<Map<String, Object>> pending = jdbc.queryForList(
                "SELECT i.user_id, i.keycloak_id FROM core_user_invite i "
                        + "  JOIN core_rbac_user_role ur "
                        + "    ON ur.user_id = i.user_id AND ur.tenant_id = i.tenant_id AND ur.mark = 1 "
                        + "  JOIN core_rbac_role r "
                        + "    ON r.id = ur.role_id AND r.tenant_id = ur.tenant_id "
                        + "   AND r.mark = 1 AND r.is_built_in = 1 "
                        + " WHERE i.tenant_id = ? AND i.used_at IS NULL AND i.mark = 1 "
                        + " ORDER BY i.expires_at DESC LIMIT 1",
                tenantCode);
        if (pending.isEmpty()) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "No pending admin invite for tenant '" + tenantCode + "' — the admin may have "
                            + "already activated their account. (Business users' invites are resent "
                            + "from the tenant's own user console.)");
        }
        String userId = (String) pending.get(0).get("user_id");
        String keycloakId = (String) pending.get(0).get("keycloak_id");

        Map<String, Object> u = jdbc.queryForMap(
                "SELECT username, display_name, email FROM core_auth_user WHERE id = ? AND mark = 1",
                userId);
        String username = (String) u.get("username");
        String displayName = (String) u.get("display_name");
        String currentEmail = (String) u.get("email");

        boolean correcting = correctedEmail != null && !correctedEmail.isBlank();
        String targetEmail = correcting ? correctedEmail.trim() : currentEmail;
        if (targetEmail == null || targetEmail.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "No email to send to — provide a corrected email in the request body.");
        }

        // Correct the email everywhere if a new one was given.
        if (correcting && !targetEmail.equals(currentEmail)) {
            // Precise duplicate pre-check, like every other email-edit path
            // (UserAdminService.assertEmailAvailable, PlatformUserAdminService's
            // create/update counts). Without it the clash surfaces only when
            // uk_core_auth_user_tenant_email fires, and DuplicateKeyException maps
            // to the generic 700 "this name or code is already taken" — which never
            // tells the operator that the EMAIL is what collided.
            Long dup = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM core_auth_user "
                            + "WHERE tenant_id = ? AND email = ? AND mark = 1 AND id <> ?",
                    Long.class, tenantCode, targetEmail, userId);
            if (dup != null && dup > 0) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR, "error.user.emailExists");
            }
            OffsetDateTime now = OffsetDateTime.now();
            jdbc.update("UPDATE core_auth_user SET email = ?, update_time = ? WHERE id = ?",
                    targetEmail, now, userId);
            jdbc.update("UPDATE core_tenant SET contact_email = ?, update_time = ? WHERE id = ?",
                    targetEmail, now, tenantId);
            KeycloakUserService userService = userServiceProvider.getIfAvailable();
            if (userService != null && keycloakId != null && !keycloakId.isBlank()) {
                userService.updateEmail(tenantCode, keycloakId, targetEmail);
            }
        }

        // Invalidate any still-open invites so only the freshly-minted link works.
        jdbc.update("UPDATE core_user_invite SET used_at = ? "
                        + "WHERE tenant_id = ? AND user_id = ? AND used_at IS NULL AND mark = 1",
                OffsetDateTime.now(), tenantCode, userId);

        String token = inviteTokenService.mint(tenantCode, userId, keycloakId);
        sendInviteMail(username, targetEmail, displayName, tenantCode, token);

        log.info("[tenant] resent admin invite for tenant '{}' to '{}' (corrected={})",
                tenantCode, targetEmail, correcting);
    }

    /**
     * Resolve the admin username from the create request. If the operator
     * provided one, validate and use it. Otherwise derive from
     * {@code contactEmail}'s local-part: lowercase, keep alphanumeric +
     * dash/underscore, drop everything else (so {@code info+team@acme.com}
     * → {@code infoteam}); fall back to {@code "admin"} on empty result.
     */
    static String deriveUsernameFromEmail(String email) {
        if (email == null) return "admin";
        int at = email.indexOf('@');
        // `at >= 0` not `> 0` so the pathological "@acme.com" (empty local-part)
        // takes the local="" path and falls back to "admin", instead of slurping
        // the domain and yielding something like "acmecom".
        String local = at >= 0 ? email.substring(0, at) : email;
        // Trim to charset; collapse anything non-matching to nothing.
        StringBuilder sb = new StringBuilder(local.length());
        for (char c : local.toLowerCase(Locale.ROOT).toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
                sb.append(c);
            }
        }
        String derived = sb.toString();
        if (derived.isEmpty()) return "admin";
        // Username MUST start with alphanumeric per USERNAME_OK regex; strip
        // leading separators by walking forward to the first ok char.
        int start = 0;
        while (start < derived.length() && (derived.charAt(start) == '-' || derived.charAt(start) == '_')) {
            start++;
        }
        if (start == derived.length()) return "admin";
        derived = derived.substring(start);
        if (derived.length() > 64) derived = derived.substring(0, 64);
        return derived;
    }

    private String resolveAdminUsername(TenantDto.CreateRequest req) {
        if (req.adminUsername() != null && !req.adminUsername().isBlank()) {
            if (!USERNAME_OK.matcher(req.adminUsername()).matches()) {
                throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                        "adminUsername must match [a-z0-9][a-z0-9_-]{0,63}");
            }
            return req.adminUsername();
        }
        return deriveUsernameFromEmail(req.contactEmail());
    }

    private void sendInviteMail(String username, String email, String displayName,
                                String tenantCode, String token) {
        MailService mail = mailProvider.getIfAvailable();
        if (mail == null || email == null || email.isBlank()) {
            log.warn("[tenant] skipped invite email for tenant '{}' admin '{}' — mail service or email unavailable",
                    tenantCode, username);
            return;
        }
        try {
            Map<String, Object> model = new HashMap<>();
            model.put("appName", mailProps.fromName());
            model.put("username", username);
            model.put("displayName", displayName);
            model.put("tenantId", tenantCode);
            model.put("supportEmail", mailProps.from());
            model.put("inviteUrl", mailProps.baseUrl() + "/invite/" + token);
            // Validity quoted in the email — derived from the configured invite
            // TTL (app.invite.token-ttl), not a hardcoded literal, so it stays
            // truthful if the TTL changes. The template appends the unit ("days").
            model.put("expiresIn", String.valueOf(inviteTokenService.ttlDays()));

            Object[] subjectArgs = new Object[] { "[" + mailProps.fromName() + "]" };
            // Recipient locale: the platform admin's current locale is the best
            // guess. The new tenant admin has no profile to ask yet; once they
            // accept the invite and set a locale, subsequent emails will follow it.
            Locale locale = RequestContext.locale();
            if (locale == null) locale = Locale.JAPAN;

            mail.sendHtmlAsync(email, locale,
                    "user-invite.subject", subjectArgs,
                    "user-invite", model);
        } catch (Exception e) {
            log.warn("[tenant] invite email dispatch failed for tenant '{}' admin '{}': {}",
                    tenantCode, username, e.toString());
            // Don't propagate — registry row is already committed, retry via
            // a separate resend flow (TODO).
        }
    }

    /**
     * Update mutable registry fields. {@code tenant_code} stays immutable —
     * renaming it would require coordinated changes across the Keycloak realm,
     * every business row's tenant_id, and any external client linking. Use
     * hard-delete + recreate if a rename is truly needed.
     *
     * <p>Also patches Keycloak's realm displayName attribute so the admin
     * console matches what the platform console shows.
     */
    @Transactional
    public void update(String id, TenantDto.UpdateRequest req) {
        TenantEntity row = tenantMapper.selectById(id);
        if (row == null || !Integer.valueOf(1).equals(row.getMark())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found: " + id);
        }

        KeycloakRealmService realmService = realmServiceProvider.getIfAvailable();
        if (realmService != null) {
            try {
                realmService.updateDisplayName(row.getTenantCode(), req.displayName());
            } catch (Exception e) {
                log.warn("[tenant] KC updateDisplayName for '{}' failed: {}",
                        row.getTenantCode(), e.toString());
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Could not update realm displayName in Keycloak: " + e.getMessage());
            }
        }

        tenantMapper.update(null,
                new UpdateWrapper<TenantEntity>()
                        .eq("id", id)
                        .eq("mark", 1)
                        .set("display_name", req.displayName())
                        .set("contact_email", req.contactEmail())
                        .set("update_user", "platform-admin")
                        .set("update_time", OffsetDateTime.now()));

        log.info("[tenant] updated tenant '{}' (id={}) — displayName='{}', contactEmail='{}'",
                row.getTenantCode(), id, req.displayName(), req.contactEmail());
    }

    /**
     * Suspend a tenant: status=0 (visible in list, marked paused) and the
     * Keycloak realm disabled so logins fail. Reversible from the UI via
     * {@link #resume}; symmetric with soft-delete on the KC side but keeps
     * the registry row mark=1.
     */
    @Transactional
    public void suspend(String id) {
        TenantEntity row = requireActiveTenantNotBuiltIn(id, "suspend");
        if (Integer.valueOf(0).equals(row.getStatus())) {
            return; // already suspended — idempotent
        }
        KeycloakRealmService realmService = realmServiceProvider.getIfAvailable();
        if (realmService != null) {
            try {
                realmService.disableRealm(row.getTenantCode());
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Could not disable realm in Keycloak: " + e.getMessage());
            }
        }
        tenantMapper.update(null,
                new UpdateWrapper<TenantEntity>()
                        .eq("id", id)
                        .eq("mark", 1)
                        .set("status", TenantStatus.SUSPENDED.code())
                        .set("update_user", "platform-admin")
                        .set("update_time", OffsetDateTime.now()));
        // Terminate every active session of the tenant NOW — disabling the realm
        // only blocks NEW logins, but already-issued (self-contained) access tokens
        // keep working until they expire. The tenant-wide kick makes the
        // ForceLogoutFilter reject them on the very next request.
        sessionTermination.terminateTenant(row.getTenantCode());
        log.info("[tenant] suspended tenant '{}' (id={})", row.getTenantCode(), id);
    }

    /**
     * Resume a previously suspended tenant: status=1 and the Keycloak
     * realm re-enabled so logins work again.
     */
    @Transactional
    public void resume(String id) {
        TenantEntity row = requireActiveTenantNotBuiltIn(id, "resume");
        if (Integer.valueOf(1).equals(row.getStatus())) {
            return; // already active — idempotent
        }
        KeycloakRealmService realmService = realmServiceProvider.getIfAvailable();
        if (realmService != null) {
            try {
                realmService.enableRealm(row.getTenantCode());
            } catch (Exception e) {
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Could not enable realm in Keycloak: " + e.getMessage());
            }
        }
        tenantMapper.update(null,
                new UpdateWrapper<TenantEntity>()
                        .eq("id", id)
                        .eq("mark", 1)
                        .set("status", TenantStatus.ACTIVE.code())
                        .set("update_user", "platform-admin")
                        .set("update_time", OffsetDateTime.now()));
        // Lift the tenant-wide kick so the tenant's users can log in again.
        sessionTermination.reactivateTenant(row.getTenantCode());
        log.info("[tenant] resumed tenant '{}' (id={})", row.getTenantCode(), id);
    }

    private TenantEntity requireActiveTenantNotBuiltIn(String id, String op) {
        TenantEntity row = tenantMapper.selectById(id);
        if (row == null || !Integer.valueOf(1).equals(row.getMark())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found: " + id);
        }
        if (RESERVED_CODES.contains(row.getTenantCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Built-in tenant '" + row.getTenantCode() + "' cannot be " + op + "ed");
        }
        return row;
    }

    /**
     * Permanently delete a tenant — the "empty recycle bin" operation.
     *
     * <p>Flow ("recycle bin" UX): operator must first {@link #suspend}
     * the tenant, then call this from the suspended-tenant view. The
     * status=0 prerequisite is a deliberate friction point — single-
     * click "active → gone" was rejected as too dangerous.
     *
     * <p>Order (DB first, KC last):
     * <ol>
     *   <li>DELETE per-tenant business rows in FK-safe order. Junction
     *       tables ({@code core_rbac_role_*, core_rbac_user_role}) before
     *       their parents ({@code core_rbac_role / permission / menu /
     *       dept}); {@code core_auth_user} after {@code user_role}; the
     *       rest in any order; {@code core_tenant} registry last.</li>
     *   <li>Delete the Keycloak realm. Done after DB so a DB failure
     *       doesn't leave us with a gone realm but live data rows.</li>
     * </ol>
     *
     * <p>Confirmation: {@code confirmCode} must match the row's
     * {@code tenantCode} exactly. Defence-in-depth — the frontend gates
     * on the same typed match, but the backend re-validates so an
     * operator armed with curl can't slip a path-id past.
     *
     * <p>Irreversible. No undo.
     */
    @Transactional
    public void hardDelete(String id, String confirmCode) {
        TenantEntity row = tenantMapper.selectById(id);
        if (row == null || !Integer.valueOf(1).equals(row.getMark())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found: " + id);
        }
        if (RESERVED_CODES.contains(row.getTenantCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Built-in tenant '" + row.getTenantCode() + "' cannot be deleted");
        }
        if (!Integer.valueOf(0).equals(row.getStatus())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Tenant '" + row.getTenantCode() + "' must be suspended before delete "
                            + "(active tenants can't be hard-deleted in one step)");
        }
        if (confirmCode == null || !confirmCode.equals(row.getTenantCode())) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED,
                    "confirmCode must match the tenant code exactly");
        }

        String tenantCode = row.getTenantCode();
        log.warn("[tenant] HARD DELETE starting for '{}' (id={}) — irreversible", tenantCode, id);

        // ── 1. Per-tenant business rows ─────────────────────────────
        // Inventory + ordering live in TENANT_PURGE_TABLES; see its javadoc.
        // Menus are a single GLOBAL set since V41 (no tenant_id column), so
        // core_rbac_menu is deliberately absent — the role_menu bindings purged
        // via the inventory are the only tenant-owned menu link. Likewise
        // core_support_session: its tenant_id is always 'system' (a record of what
        // platform-OPS did, not tenant data), the target tenant lives in
        // tenant_code, and the dashboard already LEFT JOINs core_tenant so a
        // deleted tenant just shows a null display name.
        for (String table : TENANT_PURGE_TABLES) {
            deleteByTenant(table, tenantCode);
        }

        // ── 2. Keycloak realm ───────────────────────────────────────
        // Done before the registry row so a KC failure leaves the
        // registry row intact for retry. We've already deleted the
        // business data — operator can retry the whole hardDelete and
        // step 1 will be no-ops, KC delete will succeed.
        KeycloakRealmService realmService = realmServiceProvider.getIfAvailable();
        if (realmService != null) {
            try {
                realmService.deleteRealm(tenantCode);
            } catch (Exception e) {
                log.warn("[tenant] deleteRealm for '{}' failed: {}", tenantCode, e.toString());
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Could not delete realm in Keycloak: " + e.getMessage());
            }
        }

        // ── 3. Registry row (hard DELETE, not mark=0) ──────────────
        // Bypass MP soft-delete: use raw SQL so the row physically leaves.
        // tenantMapper.deleteById would honour @TableLogic and just mark=0.
        int rows = jdbc.update("DELETE FROM core_tenant WHERE id = ?", id);
        if (rows != 1) {
            // Unexpected — the selectById above succeeded but DELETE
            // affected the wrong number of rows. Log loudly so it surfaces
            // post-mortem, but don't blow up the operation since the rest
            // already succeeded.
            log.warn("[tenant] registry DELETE for id={} affected {} rows (expected 1)", id, rows);
        }

        // The tenant's SUPER_ADMIN role row is gone, but BuiltInRoleLookup still has
        // tenantCode -> old role id cached for up to 10 min. Hard-delete then RE-CREATE
        // with the SAME code is a supported workflow (uk_core_tenant_code is partial on
        // mark=1, and the recycle-bin flow is documented), and the re-seeded role gets a
        // FRESH ULID — verified against the DB that the id genuinely changes. A stale
        // entry then makes isTenantSuperAdmin() miss: the new tenant's super admin is no
        // longer "protected", so any user:update holder can edit / disable / delete /
        // re-role them, assignRoles stops refusing a second SUPER_ADMIN holder, and the
        // break-glass exemption clears their password_hash on first SSO bind.
        roleLookup.invalidate(tenantCode);
        // Same reasoning, second cache: deptTree is @Cacheable(key = "#tenantId")
        // — keyed by the tenant CODE, which the re-create reuses — and it holds
        // for 30 minutes (app.cache.specs). Its rows are gone from
        // core_rbac_dept by now, but the cached tree survives, so the NEW
        // tenant's admin is served the DELETED tenant's departments in
        // /dept/tree and in every picker built on it (DeptPicker,
        // DeptTreeDialog, the user editor). Worse than cosmetic: picking one
        // writes a dept_id that matches no row, and DataScopeQueryService then
        // resolves a null path and falls back to that dangling id — the user
        // silently sees nothing. evictAllDepts is the same hammer
        // DeptAdminService swings on any dept write; a tenant hard-delete is at
        // least as structural, and rare enough that the re-resolve cost is
        // irrelevant.
        permissionCacheService.evictAllDepts();

        log.warn("[tenant] HARD DELETE complete for '{}' (id={})", tenantCode, id);
    }

    /**
     * DELETE FROM the given table where tenant_id matches, via JdbcTemplate
     * (bypasses MP tenant interceptor — we're operating from system tenant
     * and target a different tenant, so interceptor scoping would no-op).
     * Logs the row count so the operator can see at a glance how much was
     * removed from each table.
     */
    private void deleteByTenant(String table, String tenantCode) {
        int rows = jdbc.update("DELETE FROM " + table + " WHERE tenant_id = ?", tenantCode);
        if (rows > 0) {
            log.info("[tenant] DELETE {} rows from {} for tenant='{}'", rows, table, tenantCode);
        }
    }

    private TenantDto.View toView(TenantEntity e, long userCount) {
        return new TenantDto.View(
                e.getId(),
                e.getTenantCode(),
                e.getDisplayName(),
                e.getContactEmail(),
                e.getStatus(),
                userCount,
                e.getCreateTime(),
                e.getUpdateTime()
        );
    }
}
