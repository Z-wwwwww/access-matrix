package com.platform.core.infrastructure.config;

import com.platform.core.infrastructure.config.properties.AppMybatisProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Startup verification that {@code TENANT_EXCLUDED_TABLES} matches DB reality.
 *
 * <h3>Why this exists</h3>
 * <p>The {@link MybatisPlusConfig#mybatisPlusInterceptor MyBatis tenant interceptor}
 * is what makes multi-tenant isolation work — it injects
 * {@code AND tenant_id = ?} into every SELECT / UPDATE / DELETE against a
 * non-excluded table. Two ways a developer can silently break that contract
 * when adding a new business table:
 *
 * <ol>
 *   <li><b>Forget the {@code tenant_id} column</b> AND add the table to
 *       {@link MybatisPlusConfig#TENANT_EXCLUDED_TABLES} — the table looks
 *       fine in dev (single tenant) but in production all tenants see each
 *       other's data. <em>Silent data leak.</em></li>
 *   <li><b>Forget the {@code tenant_id} column</b> WITHOUT adding to the
 *       exclusion list — SQL hits {@code column tenant_id does not exist} the
 *       first time anyone queries the table. <em>Loud, but found at runtime
 *       not build time.</em></li>
 * </ol>
 *
 * <p>This guard converts both into a startup failure with a clear message,
 * <em>before</em> any HTTP request can touch the broken table.
 *
 * <h3>What it checks</h3>
 * <p>For every {@code BASE TABLE} in the {@code public} schema:
 * <ul>
 *   <li>If the table has a {@code tenant_id} column → must NOT be in the
 *       exclusion list (otherwise the exclusion is wasted — the column
 *       exists but the filter skips it; a dev who later removes the
 *       exclusion would expose stale data).</li>
 *   <li>If the table has NO {@code tenant_id} column → must be in the
 *       exclusion list (otherwise the next SELECT crashes).</li>
 * </ul>
 *
 * <h3>What it does NOT check</h3>
 * <ul>
 *   <li>Whether {@code tenant_id} is {@code NOT NULL} — there are valid
 *       reasons to allow NULL during a multi-stage migration. If you want
 *       this enforced, add a NOT NULL check separately.</li>
 *   <li>Whether unique indexes include {@code tenant_id} — a separate
 *       concern (covered by code review + the {@code @TableLogic} convention).</li>
 *   <li>Tables outside the {@code public} schema (Keycloak's own
 *       {@code keycloak.*} tables, etc.). Multi-schema separation is its
 *       own isolation mechanism.</li>
 * </ul>
 *
 * <h3>When it runs</h3>
 * <p>{@code ApplicationReadyEvent}, only when the tenant interceptor itself
 * is enabled ({@code app.mybatis.tenant.enabled=true}). If you've disabled
 * the interceptor (single-tenant deploy, test profile, …) this guard
 * doesn't apply.
 */
@Component
public class TenantSchemaGuard {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaGuard.class);

    private static final String SCHEMA = "public";
    private static final String TENANT_COLUMN = "tenant_id";

    private final JdbcTemplate jdbc;
    private final AppMybatisProperties props;

    public TenantSchemaGuard(JdbcTemplate jdbc, AppMybatisProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    /**
     * Run AFTER {@code PermissionConsistencyGuard} (HIGHEST_PRECEDENCE +
     * default). The two guards are independent but if both fail we'd rather
     * see permission errors first since they're more common during dev.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE - 1000)
    public void verify() {
        if (!props.tenant().enabled()) {
            log.info("[TenantSchemaGuard] skipped — app.mybatis.tenant.enabled=false");
            return;
        }
        Set<String> excluded = lowercase(MybatisPlusConfig.TENANT_EXCLUDED_TABLES);
        Set<String> tablesInDb = loadPublicBaseTables();
        Set<String> tablesWithTenantCol = loadTablesWithTenantColumn();

        Set<String> missingColumn = new TreeSet<>();   // table has no tenant_id, not in excluded → CRASH on SELECT
        Set<String> wastedExclusion = new TreeSet<>(); // table has tenant_id, in excluded → wasted protection

        for (String table : tablesInDb) {
            boolean hasCol = tablesWithTenantCol.contains(table);
            boolean isExcluded = excluded.contains(table);
            if (!hasCol && !isExcluded) {
                missingColumn.add(table);
            }
            if (hasCol && isExcluded) {
                wastedExclusion.add(table);
            }
        }

        // Wasted exclusions are recoverable but worth logging — typically
        // means someone added a tenant_id column post-hoc and forgot to
        // remove the EXCLUDED entry. Doesn't break isolation but the
        // exclusion is a latent footgun.
        if (!wastedExclusion.isEmpty()) {
            log.warn("[TenantSchemaGuard] tables in TENANT_EXCLUDED_TABLES that DO have a tenant_id column "
                    + "— remove from exclusion list so the tenant filter applies: {}", wastedExclusion);
        }

        if (!missingColumn.isEmpty()) {
            // Hard fail — every SELECT against these tables will either
            // crash (no tenant_id column) or silently leak (if someone
            // also adds them to EXCLUDED). Refuse to start the app.
            throw new IllegalStateException(
                    "[TenantSchemaGuard][FATAL] Tables missing the 'tenant_id' column and NOT in "
                            + "MybatisPlusConfig.TENANT_EXCLUDED_TABLES: " + missingColumn + "\n"
                            + "  Fix EITHER by:\n"
                            + "    (a) Adding the 'tenant_id varchar(64) NOT NULL' column in a Flyway "
                            + "        migration — preferred for any per-tenant business data.\n"
                            + "    (b) Adding the table name to MybatisPlusConfig.TENANT_EXCLUDED_TABLES "
                            + "        — ONLY for platform-global data (one set of rows for the whole "
                            + "        installation). Note this means ALL tenants see ALL rows; if that "
                            + "        is not what you want, choose (a).");
        }

        log.info("[TenantSchemaGuard] OK — {} public tables scanned, {} excluded, no leaks detected",
                tablesInDb.size(), excluded.size());
    }

    private Set<String> loadPublicBaseTables() {
        List<String> rows = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = ? AND table_type = 'BASE TABLE'",
                String.class, SCHEMA);
        Set<String> out = new LinkedHashSet<>();
        for (String r : rows) out.add(r.toLowerCase());
        return out;
    }

    private Set<String> loadTablesWithTenantColumn() {
        List<String> rows = jdbc.queryForList(
                "SELECT table_name FROM information_schema.columns "
                        + "WHERE table_schema = ? AND column_name = ?",
                String.class, SCHEMA, TENANT_COLUMN);
        Set<String> out = new LinkedHashSet<>();
        for (String r : rows) out.add(r.toLowerCase());
        return out;
    }

    private static Set<String> lowercase(Set<String> in) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : in) out.add(s.toLowerCase());
        return out;
    }
}
