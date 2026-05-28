package com.platform.core.infrastructure.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import com.platform.core.common.context.RequestContext;
import com.platform.core.infrastructure.config.properties.AppMybatisProperties;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

@Configuration
@MapperScan("com.platform.**.mapper")
public class MybatisPlusConfig {

    /**
     * Tables the MyBatis tenant-line interceptor must skip when injecting
     * {@code WHERE tenant_id = ?}.
     *
     * <p>Two categories live here:
     * <ul>
     *   <li><b>Platform-global tables</b> — one row-set for the whole
     *       installation, not per-tenant. {@code flyway_schema_history},
     *       {@code core_meta}, {@code core_numbering_management}. These
     *       have NO {@code tenant_id} column on the DB side, and they would
     *       crash on every SELECT if the interceptor tried to filter them.</li>
     *   <li><b>JdbcTemplate-only per-tenant tables</b> — they DO have
     *       {@code tenant_id} on the DB side, but their access path doesn't
     *       go through MyBatis at all, so the interceptor would never see
     *       their queries anyway. Listed here as <em>belt-and-suspenders</em>:
     *       if a future dev wires up a MyBatis mapper against the table,
     *       this entry prevents auto-injection (since the JdbcTemplate code
     *       already scopes by tenant manually and a double-filter would
     *       silently no-op). {@code core_numbering_key} is the example —
     *       see {@link com.platform.core.infrastructure.numbering.NumberingService}
     *       where every SQL explicitly binds {@code tenant_id}. {@link TenantSchemaGuard}
     *       will WARN about this entry; that warning is the audit trail
     *       reminding future devs "this isn't a bug, the table is manually
     *       scoped — don't drop it from EXCLUDED without removing the
     *       JdbcTemplate access path too".</li>
     * </ul>
     *
     * <p>NOTE: {@code core_auth_user} / {@code core_auth_login_log} are
     * tenant-scoped and accessed via MyBatis — they stay subject to the filter.
     *
     * <p>Package-private so {@link TenantSchemaGuard} can cross-check the
     * exclusion list against the actual DB schema at startup. A table missing
     * its {@code tenant_id} column AND missing from this list will fail-fast
     * at boot (the dangerous silent-leak case).
     */
    static final Set<String> TENANT_EXCLUDED_TABLES = Set.of(
            "flyway_schema_history",       // global — no tenant_id column
            "core_meta",                   // global — no tenant_id column
            "core_numbering_management",   // global — no tenant_id column
            "core_numbering_key"           // per-tenant, JdbcTemplate-only — manually scoped
    );

    /**
     * Tenant id that signals "platform-ops caller — bypass scoping".
     * Users in the 'system' realm carry {@code tid='system'} on their JWT;
     * the filter recognises that and refuses to inject the
     * {@code WHERE tenant_id = 'system'} predicate, which would otherwise
     * scope queries to a tenant that has no business data. Bypassing
     * scoping lets PLATFORM_ADMIN holders read / write across all
     * business tenants — the magic that makes a cross-tenant management
     * UI possible without per-tenant @InterceptorIgnore annotations
     * sprinkled across the codebase.
     */
    private static final String PLATFORM_TENANT_ID = "system";

    private final AppMybatisProperties props;

    public MybatisPlusConfig(AppMybatisProperties props) {
        this.props = props;
    }

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        if (props.tenant().enabled()) {
            interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {
                @Override
                public Expression getTenantId() {
                    String tid = RequestContext.tenantId();
                    // "demo" fallback — see RequestContext.tenantIdOrDefault.
                    // The system-tenant bypass is handled by ignoreTable below
                    // (returns true for ALL tables when caller is platform-ops),
                    // so this branch only fires for business tenants.
                    return new StringValue(tid == null ? "demo" : tid);
                }

                @Override
                public boolean ignoreTable(String tableName) {
                    if (tableName == null) return true;
                    // Platform-ops caller — bypass scoping for EVERY table so
                    // queries read across all business tenants. The role check
                    // (`platform:*` permission) happens at the controller layer;
                    // by the time SQL is being emitted, the security decision
                    // is already made.
                    if (PLATFORM_TENANT_ID.equals(RequestContext.tenantId())) {
                        return true;
                    }
                    String lower = tableName.toLowerCase();
                    if (lower.startsWith("flyway_")) return true;
                    return TENANT_EXCLUDED_TABLES.contains(lower);
                }
            }));
        }

        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.POSTGRE_SQL);
        pagination.setMaxLimit(500L);
        pagination.setOverflow(false);
        interceptor.addInnerInterceptor(pagination);

        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());
        return interceptor;
    }
}
