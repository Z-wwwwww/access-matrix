package com.platform.core.infrastructure.security.rbac;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.security.DataScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.mock.env.MockEnvironment;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What {@link DataScopeAspect} actually gates on a MyBatis mapper.
 *
 * <p>Two things needed pinning, and they only make sense together.
 *
 * <p><b>1. The aspect really does advise a mapper.</b> That is not obvious by
 * reading: a MyBatis mapper bean is a {@code FactoryBean}-produced JDK proxy of
 * the interface, and {@code @within} is matched against the target class — a
 * {@code java.lang.reflect.Proxy} subclass, which does not inherit annotations
 * from the interfaces it implements. If it did NOT advise, the guard every
 * service leans on ({@code TaskService.list}'s javadoc calls it "the headline")
 * would be pure decoration and a forgotten {@code apply()} would leak every row
 * in the tenant. The setup below reproduces MyBatis-Spring's exact shape so this
 * is answered by running it rather than by reasoning about Spring internals.
 *
 * <p><b>2. It must gate only the methods that take a wrapper.</b> It used to
 * demand a marked argument on EVERY method of a {@code @DataScope} mapper.
 * {@code selectById(Serializable)} has no wrapper to mark, so the demand could
 * never be satisfied — and {@code dev} is the DEFAULT profile
 * ({@code active: ${SPRING_PROFILES_ACTIVE:dev}}) and is strict, so on every
 * developer machine {@code GET/PUT/DELETE /demo/task/&#123;id&#125;} — all three
 * enter {@code TaskService.loadVisibleOr404} → {@code selectById} — threw
 * INTERNAL_ERROR before reaching the database. {@code DemoSeeder} died on its
 * first task probe for the same reason, and {@code seed()} funnels that into one
 * {@code log.warn("DemoSeeder: skipped — …")}: the demo tasks were never planted
 * and {@code syncUsersToKeycloak()}, the last step, never ran.
 *
 * <p>The gate is decided on the SIGNATURE, so {@code selectList(null)} — a real
 * "return everything" call — is still caught rather than waved through for
 * carrying no wrapper instance.
 */
class DataScopeAspectOnMapperProxyTest {

    /** Stand-in for TaskMapper: type-level @DataScope, reached through a JDK proxy. */
    @DataScope(deptColumn = "dept_id", creatorColumn = "create_user")
    public interface ScopedMapper {
        Object selectPage(Object page, Wrapper<?> wrapper);
        Object selectList(Wrapper<?> wrapper);
        Object selectById(String id);
        Object insert(Object entity);
    }

    /** Stand-in for MapperFactoryBean. */
    static class ProxyMapperFactoryBean implements FactoryBean<ScopedMapper> {
        @Override
        public ScopedMapper getObject() {
            return (ScopedMapper) Proxy.newProxyInstance(
                    ScopedMapper.class.getClassLoader(),
                    new Class<?>[]{ScopedMapper.class},
                    (proxy, method, args) -> null);
        }

        @Override
        public Class<?> getObjectType() {
            return ScopedMapper.class;
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class Config {
        @Bean
        DataScopeAspect dataScopeAspect() {
            // "test" is one of the two strict profiles, so a missed marker throws
            // rather than merely warning — the behaviour under test.
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles("test");
            return new DataScopeAspect(env);
        }

        @Bean
        ProxyMapperFactoryBean scopedMapper() {
            return new ProxyMapperFactoryBean();
        }
    }

    private AnnotationConfigApplicationContext ctx;

    @AfterEach
    void tearDown() {
        if (ctx != null) ctx.close();
        DataScopeContext.clear();
    }

    private ScopedMapper mapper() {
        ctx = new AnnotationConfigApplicationContext(Config.class);
        return ctx.getBean(ScopedMapper.class);
    }

    @Test
    void theAspectAdvisesAMapperExposedAsAJdkProxy() {
        assertThat(AopUtils.isAopProxy(mapper()))
                .as("if the mapper is not proxied by Spring AOP at all, @DataScope is decoration")
                .isTrue();
    }

    @Test
    void anUnmarkedWrapperIsRejectedUnderAStrictProfile() {
        ScopedMapper m = mapper();

        assertThatThrownBy(() -> m.selectPage(new Object(), new QueryWrapper<>()))
                .as("the guard every list query leans on")
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("DataScopeHelper.apply()");
    }

    @Test
    void aMarkedWrapperPassesThrough() {
        ScopedMapper m = mapper();
        QueryWrapper<Object> wrapper = new QueryWrapper<>();
        DataScopeContext.markApplied(wrapper);

        assertThatNoException().isThrownBy(() -> m.selectPage(new Object(), wrapper));
    }

    @Test
    void aNullWrapperIsStillRejected_theSignatureDecides() {
        ScopedMapper m = mapper();

        // selectList(null) means "every row" — the case the guard exists for. It
        // must not slip through merely because there is no instance to mark.
        assertThatThrownBy(() -> m.selectList(null))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void methodsWithNoWrapperParameterAreNotGated() {
        ScopedMapper m = mapper();

        // Nothing here can have "forgotten to scope a wrapper" — there is none.
        // Demanding a marker was an unconditional refusal, not a scope check.
        assertThatNoException().isThrownBy(() -> m.selectById("SOME-ULID"));
        assertThatNoException().isThrownBy(() -> m.insert(new Object()));
    }
}
