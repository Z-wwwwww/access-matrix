package com.platform.core.infrastructure.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.platform.core.common.audit.OpLog;
import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
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

    /**
     * Mask string fields whose name (case-insensitive) matches these.
     *
     * <p>The value alternation is {@code (?:\\.|[^"\\])*} — "an escape sequence,
     * or any character that is neither a quote nor a backslash" — NOT the naive
     * {@code [^"]*}. A JSON string may legally contain an <em>escaped</em> quote,
     * and {@code [^"]*} stops dead at the backslash-quote pair, so the match ends
     * in the MIDDLE of the value: everything after the first {@code "} in the
     * password survives into {@code core_oplog.request_body} in cleartext, and
     * the row's JSON is left syntactically broken as a bonus. This is not
     * theoretical — {@code app.security.password-policy.require-symbol} is on by
     * default, so {@code "} is a perfectly ordinary character for a user to pick,
     * and both {@code POST /user} (DIRECT mode, admin types the password) and
     * {@code POST /me/break-glass-password} are {@code @OpLog}-audited with the
     * password sitting in the request body.
     */
    private static final Pattern PASSWORD_FIELD_PATTERN =
            Pattern.compile("(?i)\"(password|passwordHash|newPassword|oldPassword|passwd|pwd)\"\\s*:\\s*\"(?:\\\\.|[^\"\\\\])*\"");

    private final ObjectProvider<OpLogSink> sinkProvider;
    private final JsonMapper jsonMapper;
    private final com.platform.core.infrastructure.security.ClientIpResolver clientIpResolver;

    public OpLogAspect(ObjectProvider<OpLogSink> sinkProvider, JsonMapper jsonMapper,
                       com.platform.core.infrastructure.security.ClientIpResolver clientIpResolver) {
        this.sinkProvider = sinkProvider;
        this.clientIpResolver = clientIpResolver;
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
        String ip      = clientIpResolver.resolve(req);
        String ua      = req == null ? null : req.getHeader("User-Agent");
        boolean ok     = thrown == null;
        String errMsg  = thrown == null ? null : safe(thrown.getMessage(), 500);
        // Error classification: a BusinessException is a deliberate, expected
        // rejection (carries its own 4xx/7xx ErrorCode); anything else is an
        // unexpected failure → 500. Lets monitoring count only real errors.
        //
        // DuplicateKeyException has to be named explicitly. It is thrown from
        // INSIDE the service — i.e. inside this advice — so unlike the framework
        // client errors (which never reach the controller method and so never
        // reach this aspect) it does get classified here, and as a non-
        // BusinessException it landed on 500. That is the number
        // PlatformDashboardService.reliability() actually reads
        // ("success = false AND error_code = 500"), so mapping it to a business
        // error at the HTTP layer alone left the "API errors (24h)" KPI polluted
        // by a caller retyping a name that is already taken — the very thing that
        // mapping was for. Keep this in step with
        // GlobalExceptionHandler.handleDuplicateKey.
        Integer errorCode = thrown == null ? null
                : (thrown instanceof BusinessException be ? be.errorCode().code()
                : thrown instanceof DuplicateKeyException ? ErrorCode.BUSINESS_ERROR.code()
                : ErrorCode.INTERNAL_ERROR.code());

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
                errorCode,
                costMs
        );
    }

    /** Package-private so a test can drive the real serialise + mask path. */
    String serialiseArgs(Object[] args) {
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
