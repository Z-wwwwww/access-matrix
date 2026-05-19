package com.platform.core.infrastructure.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.platform.core.common.audit.OpLog;
import com.platform.core.common.context.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.util.regex.Pattern;

/**
 * Captures method invocations annotated with {@link OpLog} and persists an
 * audit record. Runs <em>after</em> {@code PermissionAspect} (which has
 * order=10) — see {@link #ORDER}: a 403 denied by permissions therefore
 * does <em>not</em> generate a "success" audit row.
 */
@Aspect
@Component
@Order(OpLogAspect.ORDER)
public class OpLogAspect {

    public static final int ORDER = 50;

    private static final Logger log = LoggerFactory.getLogger(OpLogAspect.class);
    private static final int MAX_BODY_BYTES = 4096;

    /** Mask string fields whose name (case-insensitive) matches these. */
    private static final Pattern PASSWORD_FIELD_PATTERN =
            Pattern.compile("(?i)\"(password|passwordHash|newPassword|oldPassword|passwd|pwd)\"\\s*:\\s*\"[^\"]*\"");

    private final ObjectProvider<OpLogSink> sinkProvider;
    private final JsonMapper jsonMapper;

    public OpLogAspect(ObjectProvider<OpLogSink> sinkProvider, JsonMapper jsonMapper) {
        this.sinkProvider = sinkProvider;
        // Re-derive a mapper that won't blow up on circular refs in arbitrary controller arg objects.
        this.jsonMapper = jsonMapper.rebuild()
                .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
                .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
                .build();
    }

    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint pjp, OpLog annotation) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable thrown = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable t) {
            thrown = t;
            throw t;
        } finally {
            try {
                int costMs = (int) (System.currentTimeMillis() - start);
                OpLogRecord record = buildRecord(pjp, annotation, costMs, thrown);
                OpLogSink sink = sinkProvider.getIfAvailable();
                if (sink != null) {
                    sink.record(record);
                } else {
                    log.debug("No OpLogSink registered; dropping audit row for action={}", annotation.action());
                }
            } catch (Exception capture) {
                log.warn("OpLogAspect: failed to record audit row: {}", capture.getMessage());
            }
        }
    }

    private OpLogRecord buildRecord(ProceedingJoinPoint pjp, OpLog ann, int costMs, Throwable thrown) {
        HttpServletRequest req = currentRequest();
        String userId   = RequestContext.userId();
        // Username is in the JWT (preferred_username); RequestContext only populates it in jwt mode.
        // In permit-all mode it stays null — acceptable for an audit row, the userId is what matters.
        RequestContext ctx = RequestContext.current();
        String username = ctx == null ? null : ctx.getUsername();
        String tenantId = RequestContext.tenantId();

        String body    = serialiseArgs(pjp.getArgs());
        String uri     = req == null ? null : req.getRequestURI();
        String method  = req == null ? null : req.getMethod();
        String ip      = clientIp(req);
        String ua      = req == null ? null : req.getHeader("User-Agent");
        boolean ok     = thrown == null;
        String errMsg  = thrown == null ? null : safe(thrown.getMessage(), 500);

        return new OpLogRecord(
                tenantId == null ? "default" : tenantId,
                userId,
                username,
                ann.module(),
                ann.action(),
                ann.targetType().isBlank() ? null : ann.targetType(),
                null,  // targetId — caller could embed in args; we leave null for now
                uri,
                method,
                ip,
                safe(ua, 500),
                body,
                ok,
                errMsg,
                costMs
        );
    }

    private String serialiseArgs(Object[] args) {
        if (args == null || args.length == 0) return null;
        Object payload = args.length == 1 ? args[0] : args;
        String json;
        try {
            json = jsonMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "[serialisation-failed: " + e.getClass().getSimpleName() + "]";
        }
        if (json == null) return null;
        json = PASSWORD_FIELD_PATTERN.matcher(json).replaceAll("\"$1\":\"***\"");
        if (json.length() > MAX_BODY_BYTES) {
            json = json.substring(0, MAX_BODY_BYTES) + "...[truncated]";
        }
        return json;
    }

    private static String clientIp(HttpServletRequest req) {
        if (req == null) return null;
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String xrip = req.getHeader("X-Real-IP");
        if (xrip != null && !xrip.isBlank()) return xrip.trim();
        return req.getRemoteAddr();
    }

    private static String safe(String s, int max) {
        if (s == null) return null;
        return s.length() > max ? s.substring(0, max) : s;
    }

    private static HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) return sra.getRequest();
        return null;
    }
}
