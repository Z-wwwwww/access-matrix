package com.platform.core.common.context;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;

import java.util.Locale;

public final class RequestContext {

    private static final ThreadLocal<RequestContext> HOLDER = new ThreadLocal<>();

    private final String tenantId;
    private final String userId;
    private final String username;
    private final Locale locale;
    private final String traceId;

    private RequestContext(String tenantId, String userId, String username, Locale locale, String traceId) {
        this.tenantId = tenantId;
        this.userId = userId;
        this.username = username;
        this.locale = locale;
        this.traceId = traceId;
    }

    public static void set(String tenantId, String userId, String username, Locale locale, String traceId) {
        HOLDER.set(new RequestContext(tenantId, userId, username, locale, traceId));
    }

    public static RequestContext current() {
        return HOLDER.get();
    }

    public static RequestContext require() {
        RequestContext ctx = HOLDER.get();
        if (ctx == null) {
            throw new BusinessException(ErrorCode.MISSING_TENANT, "RequestContext not initialized");
        }
        return ctx;
    }

    public static String tenantId() {
        RequestContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.tenantId;
    }

    /**
     * Tenant id with a {@code "demo"} fallback. Use for mapper calls that
     * always need a non-null tenant. The fallback is for dev / QA contexts
     * where no X-Tenant-Id header was supplied; production callers always
     * have one (set by the SPA based on subdomain routing).
     */
    public static String tenantIdOrDefault() {
        String tid = tenantId();
        return (tid == null || tid.isBlank()) ? "demo" : tid;
    }

    public static String userId() {
        RequestContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.userId;
    }

    /** Current request locale. May be null when invoked outside of a web thread (jobs / startup). */
    public static Locale locale() {
        RequestContext ctx = HOLDER.get();
        return ctx == null ? null : ctx.locale;
    }

    public static void clear() {
        HOLDER.remove();
    }

    public String getTenantId() { return tenantId; }
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public Locale getLocale() { return locale; }
    public String getTraceId() { return traceId; }
}
