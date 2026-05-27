package com.platform.system.platform.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.error.BusinessException;
import com.platform.core.infrastructure.security.keycloak.KeycloakRealmService;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService;
import com.platform.system.platform.dto.TenantDto;
import com.platform.system.platform.entity.TenantEntity;
import com.platform.system.platform.mapper.TenantMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
 *   3. The two-sided create writes Keycloak FIRST, then DB — verified
 *      by ordering of mock invocations.
 *   4. softDelete refuses built-in tenants AND disables the KC realm
 *      before marking the registry row dead (so we never end up with
 *      a "deleted in DB but still-enabled realm" state).
 *
 * The matcher carve-out for platform:* (commit-level) is exercised in
 * PermissionMatcherTest; the controller-layer @RequiresPermission gate
 * is integration-tested elsewhere.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TenantAdminServiceTest {

    @Mock TenantMapper tenantMapper;
    @Mock KeycloakRealmService realmService;
    private ObjectProvider<KeycloakRealmService> realmServiceProvider;
    private TenantAdminService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        // ObjectProvider with the mock as its single available bean.
        realmServiceProvider = (ObjectProvider<KeycloakRealmService>) mock(ObjectProvider.class);
        when(realmServiceProvider.getIfAvailable()).thenReturn(realmService);
        service = new TenantAdminService(tenantMapper, realmServiceProvider);
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

    // ─── create ──────────────────────────────────────────────────────

    @Test
    void create_reservedSystemCode_rejected() {
        TenantDto.CreateRequest req = new TenantDto.CreateRequest("system", "Should not work", null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reserved");

        verify(realmService, never()).realmExists(any());
        verify(realmService, never()).createRealmFromTemplate(any(), any());
        verify(tenantMapper, never()).insert(any(TenantEntity.class));
    }

    @Test
    void create_reservedDemoCode_rejected() {
        TenantDto.CreateRequest req = new TenantDto.CreateRequest("demo", "Should not work", null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("reserved");

        verify(tenantMapper, never()).insert(any(TenantEntity.class));
    }

    @Test
    void create_codeAlreadyInRegistry_failsBeforeTouchingKeycloak() {
        when(tenantMapper.findActiveByCode("acme")).thenReturn(row("ULID-EXIST", "acme"));
        TenantDto.CreateRequest req = new TenantDto.CreateRequest("acme", "Duplicate", null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists");

        // Critical: we must NOT have created a KC realm. Doing so would
        // leak an orphan realm whenever someone re-submits a name.
        verify(realmService, never()).createRealmFromTemplate(any(), any());
        verify(tenantMapper, never()).insert(any(TenantEntity.class));
    }

    @Test
    void create_realmExistsInKc_failsBeforeRegistryInsert() {
        when(tenantMapper.findActiveByCode("acme")).thenReturn(null);
        when(realmService.realmExists("acme")).thenReturn(true);
        TenantDto.CreateRequest req = new TenantDto.CreateRequest("acme", "Acme", null);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already exists in Keycloak");

        verify(realmService, never()).createRealmFromTemplate(any(), any());
        verify(tenantMapper, never()).insert(any(TenantEntity.class));
    }

    @Test
    void create_happyPath_kcFirstThenDb() {
        when(tenantMapper.findActiveByCode("acme")).thenReturn(null);
        when(realmService.realmExists("acme")).thenReturn(false);
        TenantDto.CreateRequest req = new TenantDto.CreateRequest("acme", "Acme Corp.", "admin@acme.example");

        String newId = service.create(req);

        assertThat(newId).hasSize(26);   // ULID

        // Verify ordering: KC create BEFORE DB insert. Critical for the
        // "no orphan row on KC failure" guarantee — if the order were
        // reversed and KC threw, we'd be stuck with a registry row
        // pointing at a non-existent realm.
        org.mockito.InOrder inOrder = org.mockito.Mockito.inOrder(realmService, tenantMapper);
        inOrder.verify(realmService).createRealmFromTemplate("acme", "Acme Corp.");
        inOrder.verify(tenantMapper).insert(any(TenantEntity.class));

        ArgumentCaptor<TenantEntity> cap = ArgumentCaptor.forClass(TenantEntity.class);
        verify(tenantMapper).insert(cap.capture());
        TenantEntity inserted = cap.getValue();
        assertThat(inserted.getTenantCode()).isEqualTo("acme");
        assertThat(inserted.getDisplayName()).isEqualTo("Acme Corp.");
        assertThat(inserted.getContactEmail()).isEqualTo("admin@acme.example");
        assertThat(inserted.getTenantId()).isEqualTo("system");
        assertThat(inserted.getStatus()).isEqualTo(1);
    }

    @Test
    void create_kcThrows_dbNotTouched() {
        when(tenantMapper.findActiveByCode("acme")).thenReturn(null);
        when(realmService.realmExists("acme")).thenReturn(false);
        org.mockito.Mockito.doThrow(new KeycloakUserService.KeycloakOperationException("network"))
                .when(realmService).createRealmFromTemplate("acme", "Acme");

        assertThatThrownBy(() -> service.create(new TenantDto.CreateRequest("acme", "Acme", null)))
                .isInstanceOf(KeycloakUserService.KeycloakOperationException.class);

        verify(tenantMapper, never()).insert(any(TenantEntity.class));
    }

    @Test
    void create_kcAbsent_refuses() {
        // mode=password (or KC bean otherwise unavailable) → no realm
        // creation possible. Service must fail fast, not insert a registry
        // row pointing at a realm that doesn't exist.
        when(tenantMapper.findActiveByCode("acme")).thenReturn(null);
        when(realmServiceProvider.getIfAvailable()).thenReturn(null);

        assertThatThrownBy(() -> service.create(new TenantDto.CreateRequest("acme", "Acme", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Keycloak is not enabled");

        verify(tenantMapper, never()).insert(any(TenantEntity.class));
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

        // KC FIRST, DB second — same ordering rationale as create.
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
        // If we can't disable the realm in KC, we'd rather refuse the
        // soft delete entirely than leave the realm live but the row
        // marked dead — that's the worst of both worlds (users keep
        // signing in to a tenant the registry says is gone).
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
