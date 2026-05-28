package com.platform.system.platform.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.error.BusinessException;
import com.platform.core.infrastructure.config.properties.AppMailProperties;
import com.platform.core.infrastructure.mail.MailService;
import com.platform.core.infrastructure.numbering.NumberingService;
import com.platform.core.infrastructure.security.keycloak.KeycloakRealmService;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.auth.service.InviteTokenService;
import com.platform.system.platform.dto.TenantDto;
import com.platform.system.platform.entity.TenantEntity;
import com.platform.system.platform.mapper.TenantMapper;
import com.platform.system.rbac.service.RbacSeederService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the TenantAdminService branching that matters most:
 *
 *   1. Reserved codes ('system', 'demo') cannot be created.
 *   2. Existing code conflicts fail BEFORE we touch Keycloak (so we
 *      never leak orphan realms on duplicate-name attempts).
 *   3. The two-sided create writes Keycloak FIRST, then DB, and the
 *      onboarding flow (numbering → rbac seed → user create → KC user
 *      → invite mint → mail) runs in that order so a failure at any
 *      step doesn't leave a half-provisioned tenant.
 *   4. softDelete refuses built-in tenants AND disables the KC realm
 *      before marking the registry row dead.
 *   5. Suspend / resume call the right KC methods and toggle status.
 *   6. update propagates displayName to KC.
 *   7. Admin username resolution: explicit → use as-is; null → derive
 *      from email local-part.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantAdminServiceTest {

    @Mock TenantMapper tenantMapper;
    @Mock KeycloakRealmService realmService;
    @Mock KeycloakUserService kcUserService;
    @Mock NumberingService numberingService;
    @Mock RbacSeederService rbacSeederService;
    @Mock InviteTokenService inviteTokenService;
    @Mock MailService mailService;
    @Mock AppMailProperties mailProps;
    @Mock JdbcTemplate jdbc;

    private ObjectProvider<KeycloakRealmService> realmServiceProvider;
    private ObjectProvider<KeycloakUserService> userServiceProvider;
    private ObjectProvider<MailService> mailProvider;
    private TenantAdminService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        realmServiceProvider = (ObjectProvider<KeycloakRealmService>) mock(ObjectProvider.class);
        userServiceProvider = (ObjectProvider<KeycloakUserService>) mock(ObjectProvider.class);
        mailProvider = (ObjectProvider<MailService>) mock(ObjectProvider.class);
        when(realmServiceProvider.getIfAvailable()).thenReturn(realmService);
        when(userServiceProvider.getIfAvailable()).thenReturn(kcUserService);
        when(mailProvider.getIfAvailable()).thenReturn(mailService);
        when(mailProps.baseUrl()).thenReturn("https://app.test");
        when(mailProps.fromName()).thenReturn("Access Matrix");
        when(mailProps.from()).thenReturn("noreply@test");

        // Default happy-path stubs the create flow needs to reach the end.
        when(kcUserService.createUser(anyString(), anyString(), anyString(), anyString(), eq(null)))
                .thenReturn("kc-uuid-new");
        when(numberingService.next(eq("USER"), anyString())).thenReturn("U00000001");
        when(rbacSeederService.seedDefaultsForTenant(anyString())).thenReturn("ROLE-NEW-SUPER");
        when(inviteTokenService.mint(anyString(), anyString(), anyString())).thenReturn("invite-token-xyz");

        service = new TenantAdminService(tenantMapper, realmServiceProvider, userServiceProvider,
                numberingService, rbacSeederService, inviteTokenService,
                mailProvider, mailProps, jdbc);
    }

    private TenantEntity row(String id, String code) {
        TenantEntity e = new TenantEntity();
        e.setId(id);
        e.setTenantId("system");
        e.setTenantCode(code);
        e.setDisplayName(code + " display");
        e.setStatus(1);
        e.setMark(1);
        return e;
    }

    // ─── create: validation gates ──────────────────────────────────────

    @Test
    void create_reservedSystemCode_rejected() {
        TenantDto.CreateRequest req = new TenantDto.CreateRequest(
                "system", "Should not work", null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reserved");

        verify(realmService, never()).realmExists(any());
        verify(realmService, never()).createRealmFromTemplate(any(), any());
        verify(tenantMapper, never()).insert(any(TenantEntity.class));
    }

    @Test
    void create_reservedDemoCode_rejected() {
        TenantDto.CreateRequest req = new TenantDto.CreateRequest(
                "demo", "Should not work", null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reserved");

        verify(tenantMapper, never()).insert(any(TenantEntity.class));
    }

    @Test
    void create_codeAlreadyInRegistry_failsBeforeTouchingKeycloak() {
        when(tenantMapper.findActiveByCode("acme")).thenReturn(row("ULID-EXIST", "acme"));
        TenantDto.CreateRequest req = new TenantDto.CreateRequest(
                "acme", "Duplicate", null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");

        verify(realmService, never()).createRealmFromTemplate(any(), any());
        verify(tenantMapper, never()).insert(any(TenantEntity.class));
    }

    @Test
    void create_realmExistsInKc_failsBeforeRegistryInsert() {
        when(tenantMapper.findActiveByCode("acme")).thenReturn(null);
        when(realmService.realmExists("acme")).thenReturn(true);
        TenantDto.CreateRequest req = new TenantDto.CreateRequest(
                "acme", "Acme", null, null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists in Keycloak");

        verify(realmService, never()).createRealmFromTemplate(any(), any());
        verify(tenantMapper, never()).insert(any(TenantEntity.class));
    }

    @Test
    void create_kcAbsent_refuses() {
        when(tenantMapper.findActiveByCode("acme")).thenReturn(null);
        when(realmServiceProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.create(new TenantDto.CreateRequest(
                "acme", "Acme", null, null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Keycloak is not enabled");

        verify(tenantMapper, never()).insert(any(TenantEntity.class));
    }

    // ─── create: full onboarding happy path ────────────────────────────

    @Test
    void create_happyPath_orderingThroughOnboarding() {
        when(tenantMapper.findActiveByCode("acme")).thenReturn(null);
        when(realmService.realmExists("acme")).thenReturn(false);
        TenantDto.CreateRequest req = new TenantDto.CreateRequest(
                "acme", "Acme Corp.", "admin@acme.example", null);

        String newId = service.create(req);
        assertThat(newId).hasSize(26);   // ULID

        // Critical ordering: every step from the top of create() must run
        // before the next so a failure mid-flow doesn't leave the tenant
        // half-provisioned. KC realm goes first (avoids orphan rows); the
        // rest are tied together by @Transactional so a failure rolls back
        // the DB writes (the realm orphan recovery is documented separately).
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(
                realmService, tenantMapper, numberingService, rbacSeederService,
                kcUserService, inviteTokenService, mailService);
        inOrder.verify(realmService).createRealmFromTemplate("acme", "Acme Corp.");
        inOrder.verify(tenantMapper).insert(any(TenantEntity.class));
        inOrder.verify(numberingService).seedDefaultsForTenant("acme");
        inOrder.verify(rbacSeederService).seedDefaultsForTenant("acme");
        inOrder.verify(kcUserService).createUser(eq("acme"), eq("admin"),
                eq("admin@acme.example"), anyString(), eq(null));
        inOrder.verify(inviteTokenService).mint(eq("acme"), anyString(), eq("kc-uuid-new"));
        inOrder.verify(mailService).sendHtmlAsync(eq("admin@acme.example"),
                any(), eq("user-invite.subject"), any(Object[].class),
                eq("user-invite"), any());
    }

    @Test
    void create_explicitAdminUsername_usedVerbatim() {
        when(tenantMapper.findActiveByCode("acme")).thenReturn(null);
        when(realmService.realmExists("acme")).thenReturn(false);
        TenantDto.CreateRequest req = new TenantDto.CreateRequest(
                "acme", "Acme", "ops@acme.example", "tenant-owner");

        service.create(req);

        verify(kcUserService).createUser(eq("acme"), eq("tenant-owner"),
                eq("ops@acme.example"), anyString(), eq(null));
    }

    @Test
    void create_nullContactEmail_stillProvisionsUserButSkipsMail() {
        // Operator might be onboarding a tenant whose admin email isn't
        // known yet. Don't block creation — the registry + user + invite
        // token exist; operator can resend invite via a follow-up endpoint.
        when(tenantMapper.findActiveByCode("acme")).thenReturn(null);
        when(realmService.realmExists("acme")).thenReturn(false);
        TenantDto.CreateRequest req = new TenantDto.CreateRequest(
                "acme", "Acme", null, "admin");

        service.create(req);

        verify(rbacSeederService).seedDefaultsForTenant("acme");
        // Mail step short-circuits when email is null — never call mailService.
        verify(mailService, never()).sendHtmlAsync(any(), any(), any(),
                any(Object[].class), any(), any());
    }

    // ─── deriveUsernameFromEmail unit tests ───────────────────────────

    @Test
    void deriveUsername_basicLocalPart() {
        assertThat(TenantAdminService.deriveUsernameFromEmail("admin@acme.example")).isEqualTo("admin");
        assertThat(TenantAdminService.deriveUsernameFromEmail("Ops.Team@acme.example")).isEqualTo("opsteam");
    }

    @Test
    void deriveUsername_plusAddressing_stripsPlus() {
        // info+platform@acme.com is the Gmail-style alias — '+' isn't valid
        // in our username, drop everything outside [a-z0-9_-].
        assertThat(TenantAdminService.deriveUsernameFromEmail("info+team@acme.com")).isEqualTo("infoteam");
    }

    @Test
    void deriveUsername_emptyOrUnparseable_fallsBackToAdmin() {
        assertThat(TenantAdminService.deriveUsernameFromEmail(null)).isEqualTo("admin");
        assertThat(TenantAdminService.deriveUsernameFromEmail("")).isEqualTo("admin");
        assertThat(TenantAdminService.deriveUsernameFromEmail("...@acme.com")).isEqualTo("admin");
        assertThat(TenantAdminService.deriveUsernameFromEmail("@acme.com")).isEqualTo("admin");
    }

    @Test
    void deriveUsername_leadingSeparator_stripped() {
        // Username must start with alphanumeric; strip leading dash/underscore.
        assertThat(TenantAdminService.deriveUsernameFromEmail("_internal@acme.com")).isEqualTo("internal");
    }

    // ─── softDelete ──────────────────────────────────────────────────

    @Test
    void softDelete_builtInSystem_refused() {
        when(tenantMapper.selectById("id-sys")).thenReturn(row("id-sys", "system"));

        assertThatThrownBy(() -> service.softDelete("id-sys"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Built-in tenant");

        verify(realmService, never()).disableRealm(any());
        verify(tenantMapper, never()).update(any(), any());
    }

    @Test
    void softDelete_builtInDemo_refused() {
        when(tenantMapper.selectById("id-demo")).thenReturn(row("id-demo", "demo"));

        assertThatThrownBy(() -> service.softDelete("id-demo"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Built-in tenant");

        verify(realmService, never()).disableRealm(any());
    }

    @Test
    void softDelete_happyPath_disablesRealmThenMarksRowDeleted() {
        when(tenantMapper.selectById("id-acme")).thenReturn(row("id-acme", "acme"));

        service.softDelete("id-acme");

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(realmService, tenantMapper);
        inOrder.verify(realmService).disableRealm("acme");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<TenantEntity>> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        inOrder.verify(tenantMapper).update(org.mockito.ArgumentMatchers.isNull(), cap.capture());
        String sql = cap.getValue().getSqlSet();
        assertThat(sql).contains("mark=");
        assertThat(cap.getValue().getParamNameValuePairs().values()).contains(0);
    }

    @Test
    void softDelete_kcDown_failsAndDoesNotMarkRow() {
        when(tenantMapper.selectById("id-acme")).thenReturn(row("id-acme", "acme"));
        org.mockito.Mockito.doThrow(new KeycloakUserService.KeycloakOperationException("KC down"))
                .when(realmService).disableRealm("acme");

        assertThatThrownBy(() -> service.softDelete("id-acme"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Could not disable realm");

        verify(tenantMapper, never()).update(any(), any());
    }

    @Test
    void softDelete_alreadyDeleted_notFound() {
        TenantEntity deleted = row("id-x", "x");
        deleted.setMark(0);
        when(tenantMapper.selectById("id-x")).thenReturn(deleted);

        assertThatThrownBy(() -> service.softDelete("id-x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");

        verify(realmService, never()).disableRealm(any());
    }

    // ─── suspend / resume ────────────────────────────────────────────

    @Test
    void suspend_happyPath_disablesRealmAndFlipsStatus() {
        when(tenantMapper.selectById("id-acme")).thenReturn(row("id-acme", "acme"));

        service.suspend("id-acme");

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(realmService, tenantMapper);
        inOrder.verify(realmService).disableRealm("acme");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<TenantEntity>> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        inOrder.verify(tenantMapper).update(org.mockito.ArgumentMatchers.isNull(), cap.capture());
        // status=0, mark stays 1 (distinct from softDelete which sets mark=0).
        assertThat(cap.getValue().getSqlSet()).contains("status=");
        assertThat(cap.getValue().getSqlSet()).doesNotContain("mark=");
    }

    @Test
    void suspend_alreadySuspended_idempotentNoOp() {
        TenantEntity suspended = row("id-acme", "acme");
        suspended.setStatus(0);
        when(tenantMapper.selectById("id-acme")).thenReturn(suspended);

        service.suspend("id-acme");

        verify(realmService, never()).disableRealm(any());
        verify(tenantMapper, never()).update(any(), any());
    }

    @Test
    void suspend_builtIn_refused() {
        when(tenantMapper.selectById("id-demo")).thenReturn(row("id-demo", "demo"));

        assertThatThrownBy(() -> service.suspend("id-demo"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Built-in tenant");
    }

    @Test
    void resume_happyPath_enablesRealmAndFlipsStatus() {
        TenantEntity suspended = row("id-acme", "acme");
        suspended.setStatus(0);
        when(tenantMapper.selectById("id-acme")).thenReturn(suspended);

        service.resume("id-acme");

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(realmService, tenantMapper);
        inOrder.verify(realmService).enableRealm("acme");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<TenantEntity>> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        inOrder.verify(tenantMapper).update(org.mockito.ArgumentMatchers.isNull(), cap.capture());
        assertThat(cap.getValue().getSqlSet()).contains("status=");
        assertThat(cap.getValue().getParamNameValuePairs().values()).contains(1);
    }

    @Test
    void resume_alreadyActive_idempotentNoOp() {
        when(tenantMapper.selectById("id-acme")).thenReturn(row("id-acme", "acme"));   // status=1

        service.resume("id-acme");

        verify(realmService, never()).enableRealm(any());
        verify(tenantMapper, never()).update(any(), any());
    }

    // ─── update ──────────────────────────────────────────────────────

    @Test
    void update_propagatesDisplayNameToKc_thenUpdatesRow() {
        when(tenantMapper.selectById("id-acme")).thenReturn(row("id-acme", "acme"));
        TenantDto.UpdateRequest req = new TenantDto.UpdateRequest("Acme Inc.", "new@acme.example");

        service.update("id-acme", req);

        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(realmService, tenantMapper);
        inOrder.verify(realmService).updateDisplayName("acme", "Acme Inc.");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<TenantEntity>> cap = ArgumentCaptor.forClass(UpdateWrapper.class);
        inOrder.verify(tenantMapper).update(org.mockito.ArgumentMatchers.isNull(), cap.capture());
        String sql = cap.getValue().getSqlSet();
        assertThat(sql).contains("display_name=");
        assertThat(sql).contains("contact_email=");
        assertThat(cap.getValue().getParamNameValuePairs().values()).contains("Acme Inc.", "new@acme.example");
    }

    @Test
    void update_kcFails_rowNotUpdated() {
        when(tenantMapper.selectById("id-acme")).thenReturn(row("id-acme", "acme"));
        org.mockito.Mockito.doThrow(new KeycloakUserService.KeycloakOperationException("KC down"))
                .when(realmService).updateDisplayName(anyString(), anyString());

        assertThatThrownBy(() -> service.update("id-acme",
                new TenantDto.UpdateRequest("New Name", "x@y.example")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Could not update realm");

        verify(tenantMapper, never()).update(any(), any());
    }

    // ─── list ────────────────────────────────────────────────────────

    @Test
    void list_returnsViewsMappedFromEntities() {
        TenantEntity e1 = row("id-1", "acme");
        TenantEntity e2 = row("id-2", "beta");
        Page<TenantEntity> page = new Page<>(1, 20);
        page.setRecords(List.of(e1, e2));
        page.setTotal(2L);
        when(tenantMapper.selectPage(any(Page.class), any(Wrapper.class))).thenReturn(page);

        var result = service.list(1, 20, null);

        assertThat(result.total()).isEqualTo(2);
        assertThat(result.records()).hasSize(2);
        assertThat(result.records().get(0).tenantCode()).isEqualTo("acme");
        assertThat(result.records().get(1).tenantCode()).isEqualTo("beta");
    }
}
