package com.platform.system.platform.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.id.IdGenerator;
import com.platform.core.common.result.PageResult;
import com.platform.core.infrastructure.security.keycloak.KeycloakRealmService;
import com.platform.system.platform.dto.TenantDto;
import com.platform.system.platform.entity.TenantEntity;
import com.platform.system.platform.mapper.TenantMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Tenant CRUD for platform-ops callers. All operations are bound to the
 * platform-ops authority surface ({@code platform:tenant:*}) — see
 * {@code PlatformTenantController} for the controller-layer gating.
 *
 * <h3>Two-sided writes</h3>
 * Creating or soft-deleting a tenant changes state in TWO places:
 *
 * <ul>
 *   <li><b>Keycloak</b> — the realm itself. {@link KeycloakRealmService}
 *       handles realm creation / disabling.</li>
 *   <li><b>core_tenant</b> — our central registry row.</li>
 * </ul>
 *
 * <p>We always touch Keycloak FIRST. If Keycloak fails we never touch
 * the DB → no orphan row. If Keycloak succeeds and the DB step fails,
 * the realm is the leftover (the operator can either retry to recover
 * or manually delete the realm). Symmetric on soft-delete: disable
 * realm first, then UPDATE mark — if mark=0 fails we have a disabled
 * realm and a still-active row, which retry resolves correctly.
 *
 * <h3>Soft delete only</h3>
 * The management API exposes only soft delete (mark=0 + KC realm
 * disabled). Hard delete is a separate ops-only task — a "I really
 * mean it, after 30 days of retention" cleanup that requires shell
 * access. This keeps the platform UI safe from one-click data loss.
 */
@Service
public class TenantAdminService {

    private static final Logger log = LoggerFactory.getLogger(TenantAdminService.class);

    /** tenant codes reserved by the project — never available to customers. */
    private static final Set<String> RESERVED_CODES = Set.of("system", "demo");

    private final TenantMapper tenantMapper;
    /**
     * Keycloak realm operations are only available when
     * {@code app.security.mode=oidc}; in other modes {@link KeycloakRealmService}
     * isn't a bean. ObjectProvider keeps this service bootable in those
     * modes (where tenant management is meaningless anyway and the
     * controller is gated separately).
     */
    private final ObjectProvider<KeycloakRealmService> realmServiceProvider;

    public TenantAdminService(TenantMapper tenantMapper,
                              ObjectProvider<KeycloakRealmService> realmServiceProvider) {
        this.tenantMapper = tenantMapper;
        this.realmServiceProvider = realmServiceProvider;
    }

    public PageResult<TenantDto.View> list(long page, long size, String keyword) {
        Page<TenantEntity> p = new Page<>(page, size);
        QueryWrapper<TenantEntity> w = new QueryWrapper<TenantEntity>()
                .eq("mark", 1)
                .orderByDesc("create_time");
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like("tenant_code", keyword).or().like("display_name", keyword));
        }
        Page<TenantEntity> result = tenantMapper.selectPage(p, w);
        List<TenantDto.View> records = result.getRecords().stream().map(this::toView).toList();
        return PageResult.of(records, result.getTotal(), page, size);
    }

    public TenantDto.View get(String id) {
        TenantEntity row = tenantMapper.selectById(id);
        if (row == null || !Integer.valueOf(1).equals(row.getMark())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found: " + id);
        }
        return toView(row);
    }

    @Transactional
    public String create(TenantDto.CreateRequest req) {
        // ── 1. Validate ─────────────────────────────────────────────
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

        // ── 2. Create realm in Keycloak ─────────────────────────────
        // If this throws, we haven't touched the DB yet → no orphan row.
        realmService.createRealmFromTemplate(req.tenantCode(), req.displayName());

        // ── 3. Insert registry row ──────────────────────────────────
        // If THIS fails, the realm is orphaned in Keycloak. Retry of the
        // same request will fail at the realmExists check above; the
        // operator must either manually delete the orphan realm or
        // hand-edit core_tenant to add the row. Pick the lesser evil:
        // keeping the realm is the safer side because no business data
        // exists for it yet.
        TenantEntity row = new TenantEntity();
        row.setId(IdGenerator.ulid());
        row.setTenantId("system");
        row.setTenantCode(req.tenantCode());
        row.setDisplayName(req.displayName());
        row.setContactEmail(req.contactEmail());
        row.setStatus(1);
        row.setMark(1);
        row.setCreateUser("platform-admin");
        row.setUpdateUser("platform-admin");
        row.setCreateTime(LocalDateTime.now());
        row.setUpdateTime(LocalDateTime.now());
        tenantMapper.insert(row);

        log.info("[tenant] created tenant '{}' (id={}, displayName='{}')",
                req.tenantCode(), row.getId(), req.displayName());
        return row.getId();
    }

    /**
     * Soft-delete a tenant: disable the Keycloak realm (users can't sign
     * in any more) and mark the DB row deleted. Business data
     * ({@code core_auth_user}, etc. with this tenant_code) stays
     * untouched so a future "undo" or hard-purge has its substrate.
     */
    @Transactional
    public void softDelete(String id) {
        TenantEntity row = tenantMapper.selectById(id);
        if (row == null || !Integer.valueOf(1).equals(row.getMark())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "Tenant not found: " + id);
        }
        if (RESERVED_CODES.contains(row.getTenantCode())) {
            throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                    "Built-in tenant '" + row.getTenantCode() + "' cannot be deleted");
        }

        // KC first so we know the IdP can be reached BEFORE we mark the
        // row dead. If KC is down we'd rather fail-loud and let the
        // operator retry once it's back, than mark dead in DB but leave
        // the realm enabled (a state that lets users keep signing in
        // even though the registry says the tenant is gone).
        KeycloakRealmService realmService = realmServiceProvider.getIfAvailable();
        if (realmService != null) {
            try {
                realmService.disableRealm(row.getTenantCode());
            } catch (Exception e) {
                log.warn("[tenant] disableRealm for '{}' failed: {}",
                        row.getTenantCode(), e.toString());
                throw new BusinessException(ErrorCode.BUSINESS_ERROR,
                        "Could not disable realm in Keycloak: " + e.getMessage());
            }
        }

        // Then the registry row. UpdateWrapper because mark is @TableLogic
        // — updateById would silently strip mark from the SET clause.
        tenantMapper.update(null,
                new UpdateWrapper<TenantEntity>()
                        .eq("id", id)
                        .eq("mark", 1)
                        .set("mark", 0)
                        .set("update_user", "platform-admin")
                        .set("update_time", LocalDateTime.now()));

        log.info("[tenant] soft-deleted tenant '{}' (id={})", row.getTenantCode(), id);
    }

    private TenantDto.View toView(TenantEntity e) {
        return new TenantDto.View(
                e.getId(),
                e.getTenantCode(),
                e.getDisplayName(),
                e.getContactEmail(),
                e.getStatus(),
                e.getCreateTime(),
                e.getUpdateTime()
        );
    }
}
