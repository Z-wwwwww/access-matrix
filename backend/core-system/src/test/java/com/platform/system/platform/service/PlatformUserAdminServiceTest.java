package com.platform.system.platform.service;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.security.BuiltInRoles;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.mail.MailService;
import com.platform.core.infrastructure.security.ForceLogoutService;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.service.InviteTokenService;
import com.platform.system.auth.service.SessionTerminationService;
import com.platform.system.platform.dto.PlatformUserDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * First unit coverage for the platform-ops staff console. Pins the parts whose
 * failure modes are silent:
 *
 *   1. Duplicate username / email are rejected BEFORE Keycloak is touched, so a
 *      clash never leaves an orphan KC user.
 *   2. The two DB writes of create() go through the {@code @Transactional}
 *      {@link PlatformUserAdminService#persistNewOpsUser} via the self-proxy —
 *      they used to be two independent autocommits, so a failing user_role
 *      INSERT left a committed, roleless core_auth_user row (with keycloak_id
 *      pointing at the KC user the catch block then deleted), and its username
 *      blocked every retry on the usernameDup pre-check.
 *   3. A DB failure still compensates by deleting the KC user.
 *   4. New operators are bound to PLATFORM_OPERATOR (not PLATFORM_ADMIN).
 *   5. requireManageable refuses self-management and refuses touching a
 *      PLATFORM_ADMIN (the super 'ops' account).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlatformUserAdminServiceTest {

    @Mock JdbcTemplate jdbc;
    @Mock KeycloakUserService kcUserService;
    @Mock ForceLogoutService forceLogoutService;
    @Mock MailService mailService;
    @Mock AppMailProperties mailProps;
    @Mock InviteTokenService inviteTokenService;
    @Mock SessionTerminationService sessionTermination;
    @Mock com.platform.core.infrastructure.numbering.NumberingService numberingService;

    private ObjectProvider<KeycloakUserService> userServiceProvider;
    private ObjectProvider<MailService> mailProvider;
    private ObjectProvider<PlatformUserAdminService> selfProvider;
    private PlatformUserAdminService service;

    private static final String KC_ID = "kc-uuid-new";

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        userServiceProvider = (ObjectProvider<KeycloakUserService>) mock(ObjectProvider.class);
        mailProvider = (ObjectProvider<MailService>) mock(ObjectProvider.class);
        selfProvider = (ObjectProvider<PlatformUserAdminService>) mock(ObjectProvider.class);
        when(userServiceProvider.getIfAvailable()).thenReturn(kcUserService);
        when(mailProvider.getIfAvailable()).thenReturn(mailService);
        when(mailProps.baseUrl()).thenReturn("https://app.test");
        when(mailProps.fromName()).thenReturn("Access Matrix");
        when(mailProps.from()).thenReturn("noreply@test");

        when(kcUserService.createUser(anyString(), anyString(), anyString(), anyString(), eq(null)))
                .thenReturn(KC_ID);
        when(inviteTokenService.mint(anyString(), anyString(), anyString())).thenReturn("invite-token-xyz");
        // No duplicates, and nextSystemUserNo()'s MAX() probe returns 0.
        when(jdbc.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(0L);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);

        service = new PlatformUserAdminService(jdbc, userServiceProvider, forceLogoutService,
                mailProvider, mailProps, inviteTokenService, sessionTermination,
                numberingService, selfProvider);
        // In-process self-proxy: persistNewOpsUser runs directly on the same
        // instance (no real transaction in a unit test — interactions still verify).
        when(selfProvider.getObject()).thenReturn(service);
    }

    private static PlatformUserDto.CreateRequest req() {
        return new PlatformUserDto.CreateRequest("newops", "newops@test", "New Ops");
    }

    // ── 0. pagination boundaries (the only hand-rolled paginator) ────────────

    /**
     * Captures the (size, offset) actually handed to the raw SQL. The controller
     * declares only {@code @RequestParam(defaultValue = ...)}, so page/size arrive
     * unvalidated; Postgres rejects a negative LIMIT/OFFSET outright (verified
     * against the real DB), which surfaced as a 500 rather than an empty page.
     */
    private long[] captureLimitOffset(long page, long size) {
        when(jdbc.queryForObject(contains("SELECT COUNT(*)"), eq(Long.class), any(Object[].class)))
                .thenReturn(0L);
        org.mockito.ArgumentCaptor<Object[]> args = org.mockito.ArgumentCaptor.forClass(Object[].class);
        when(jdbc.query(contains("LIMIT ? OFFSET ?"), any(org.springframework.jdbc.core.RowMapper.class),
                args.capture()))
                .thenReturn(java.util.List.of());

        service.list(page, size, null);

        Object[] a = args.getValue();
        return new long[] { (Long) a[a.length - 2], (Long) a[a.length - 1] };
    }

    @Test
    void list_page0_doesNotProduceANegativeOffset() {
        long[] lo = captureLimitOffset(0, 20);
        assertThat(lo[1]).as("offset must never go negative — Postgres rejects it").isEqualTo(0L);
        assertThat(lo[0]).isEqualTo(20L);
    }

    @Test
    void list_negativeSize_isClampedToAtLeastOne() {
        long[] lo = captureLimitOffset(1, -5);
        assertThat(lo[0]).as("LIMIT must never go negative").isEqualTo(1L);
        assertThat(lo[1]).isEqualTo(0L);
    }

    @Test
    void list_hugeSize_isCappedLikeTheMyBatisLists() {
        // MybatisPlusConfig caps every other list at 500 via setMaxLimit(500).
        long[] lo = captureLimitOffset(1, 1_000_000);
        assertThat(lo[0]).isEqualTo(500L);
    }

    @Test
    void list_normalPaging_stillComputesTheUsualOffset() {
        long[] lo = captureLimitOffset(3, 20);
        assertThat(lo[0]).isEqualTo(20L);
        assertThat(lo[1]).isEqualTo(40L);
    }

    // ── 1. duplicates rejected before Keycloak ──────────────────────────────

    @Test
    void create_rejectsDuplicateUsernameBeforeTouchingKeycloak() {
        when(jdbc.queryForObject(contains("username = ?"), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.opsuser.usernameExists");

        verify(kcUserService, never()).createUser(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void create_rejectsDuplicateEmailBeforeTouchingKeycloak() {
        when(jdbc.queryForObject(contains("email = ?"), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("error.opsuser.emailExists");

        verify(kcUserService, never()).createUser(anyString(), anyString(), anyString(), anyString(), any());
    }

    // ── 2/4. happy path: one transactional unit, PLATFORM_OPERATOR binding ──

    @Test
    void create_routesBothInsertsThroughTheTransactionalSelfProxy() {
        PlatformUserDto.CreateResponse resp = service.create(req());

        assertThat(resp.username()).isEqualTo("newops");
        // The DB half must be reached through the proxy, not a plain self-call.
        verify(selfProvider).getObject();
        verify(jdbc).update(contains("INSERT INTO core_auth_user"), any(Object[].class));
    }

    @Test
    void create_bindsPlatformOperatorNotPlatformAdmin() {
        service.create(req());

        // The user_role INSERT takes (id, tenantId, userId, roleId) — roleId is 4th.
        ArgumentCaptor<Object> id = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> tenant = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> userId = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> roleId = ArgumentCaptor.forClass(Object.class);
        verify(jdbc).update(contains("INSERT INTO core_rbac_user_role"),
                id.capture(), tenant.capture(), userId.capture(), roleId.capture());
        assertThat(tenant.getValue()).isEqualTo("system");
        assertThat(roleId.getValue()).isEqualTo(BuiltInRoles.PLATFORM_OPERATOR_ID);
        assertThat(roleId.getValue()).isNotEqualTo(BuiltInRoles.PLATFORM_ADMIN_ID);
    }

    @Test
    void persistNewOpsUser_isTransactional() throws Exception {
        // Structural pin: the two INSERTs are only atomic because the advice is
        // present on this method. Mockito can't exercise Spring's proxy, so assert
        // the annotation directly — losing it silently reintroduces the bug.
        Method m = PlatformUserAdminService.class.getMethod(
                "persistNewOpsUser", PlatformUserDto.CreateRequest.class, String.class);
        assertThat(m.getAnnotation(Transactional.class)).isNotNull();
    }

    // ── 3. compensation ─────────────────────────────────────────────────────

    @Test
    void create_deletesKeycloakUserWhenTheDbUnitFails() {
        doThrow(new IllegalStateException("boom"))
                .when(jdbc).update(contains("INSERT INTO core_rbac_user_role"), any(Object[].class));

        assertThatThrownBy(() -> service.create(req()))
                .isInstanceOf(IllegalStateException.class);

        verify(kcUserService).deleteUser("system", KC_ID);
    }

    // ── 5. requireManageable guards ─────────────────────────────────────────

    @Test
    void setEnabled_refusesManagingAPlatformAdmin() {
        when(jdbc.queryForMap(anyString(), any(Object[].class)))
                .thenReturn(java.util.Map.of(
                        "id", "OTHER-OPS-ID",
                        "username", "ops",
                        "email", "ops@test",
                        "display_name", "Ops",
                        "keycloak_id", "kc-ops"));
        // isSuper probe → 1 row (holds PLATFORM_ADMIN)
        when(jdbc.queryForObject(contains("core_rbac_user_role"), eq(Long.class), any(Object[].class)))
                .thenReturn(1L);

        assertThatThrownBy(() -> service.setEnabled("OTHER-OPS-ID", false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("platform admin");

        verify(sessionTermination, never()).applyEnabled(anyString(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    // ── user_no allocation ──────────────────────────────────────────────────
    // The console used to compute the number itself —
    // MAX(CAST(SUBSTRING(user_no FROM 2) AS INTEGER)) + 1 — which advances
    // nothing, so core_numbering_management's counter never learned about the
    // numbers handed out here. The two allocators then drift until they produce
    // the SAME value; verified on a live DB, where the counter's next value for
    // `system` (U00000002) was already held by an operator this console created.
    // (tenant_id, user_no) is uniquely indexed, so the collision is a hard insert
    // failure for whoever allocates next from the counter — most plausibly
    // OidcJitUserService, which guards the numbering call but not the insert.

    @Test
    void create_allocatesUserNoThroughNumberingService() {
        when(numberingService.next("USER", "system")).thenReturn("U00000042");
        when(kcUserService.createUser(eq("system"), any(), any(), any(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("kc-new");

        service.create(req());

        verify(numberingService).next("USER", "system");
        // The value the allocator returned is what actually gets inserted.
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, org.mockito.Mockito.atLeastOnce())
                .update(org.mockito.ArgumentMatchers.contains("INSERT INTO core_auth_user"), args.capture());
        assertThat(args.getAllValues().stream().flatMap(java.util.Arrays::stream))
                .contains("U00000042");
    }

    @Test
    void create_neverDerivesTheNumberFromTheBusinessColumn() {
        when(numberingService.next("USER", "system")).thenReturn("U00000042");
        when(kcUserService.createUser(eq("system"), any(), any(), any(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn("kc-new");

        service.create(req());

        // No MAX(...)/SUBSTRING(user_no ...) probe may remain — that query IS the bug.
        verify(jdbc, never()).queryForObject(
                org.mockito.ArgumentMatchers.contains("SUBSTRING(user_no"),
                org.mockito.ArgumentMatchers.<Class<Integer>>any(),
                org.mockito.ArgumentMatchers.<Object[]>any());
    }

}
