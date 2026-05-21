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
     * Tables that hold platform-global state (not per-tenant), so the tenant filter must skip them.
     * NOTE: `core_auth_user` / `core_auth_login_log` ARE tenant-scoped — they stay subject to the filter.
     */
    private static final Set<String> TENANT_EXCLUDED_TABLES = Set.of(
            "flyway_schema_history",
            "core_meta",
            "core_numbering_management",
            "core_numbering_key"
    );

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
                    return new StringValue(tid == null ? "default" : tid);
                }

                @Override
                public boolean ignoreTable(String tableName) {
                    if (tableName == null) return true;
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
