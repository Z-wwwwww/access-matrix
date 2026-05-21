package com.platform.core.infrastructure.security.rbac;

import java.util.IdentityHashMap;

/**
 * Per-request marker that records which query wrappers have been run through
 * {@link DataScopeHelper#apply}.
 *
 * <p>The {@link DataScopeAspect} cross-checks against this set when a Mapper
 * method marked with {@link com.platform.core.common.security.DataScope}
 * is invoked: if none of the call arguments were marked, the service forgot
 * to apply the scope filter and the request is rejected (strict profiles)
 * or warned (prod).
 *
 * <p>Identity comparison ({@link IdentityHashMap}) — query wrappers may
 * override {@code equals}, but we want "the very same instance the service
 * applied to", not "an equal-looking sibling".
 *
 * <p>Cleared at request boundary by {@code CoreRequestContextFilter} so
 * thread-pool re-use never leaks a stale marker.
 */
public final class DataScopeContext {

    private static final ThreadLocal<IdentityHashMap<Object, Boolean>> MARKED =
            ThreadLocal.withInitial(IdentityHashMap::new);

    private DataScopeContext() {}

    public static void markApplied(Object wrapper) {
        if (wrapper == null) return;
        MARKED.get().put(wrapper, Boolean.TRUE);
    }

    public static boolean wasApplied(Object wrapper) {
        if (wrapper == null) return false;
        return MARKED.get().containsKey(wrapper);
    }

    public static void clear() {
        MARKED.remove();
    }
}
