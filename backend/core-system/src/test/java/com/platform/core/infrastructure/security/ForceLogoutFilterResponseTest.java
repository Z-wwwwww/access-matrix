package com.platform.core.infrastructure.security;

import com.platform.core.common.context.RequestContext;
import com.platform.core.common.error.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A rejection written by a filter still has to be a {@code JsonResult}.
 *
 * <p>Filters short-circuit before any controller, so they bypass
 * {@code GlobalExceptionHandler} and hand-roll their response. There are exactly
 * two of them. {@code AuthRateLimitFilter} writes
 * {@code JsonResult.error(ErrorCode.TOO_MANY_REQUESTS)} through the injected
 * Jackson 3 {@code JsonMapper}; {@link ForceLogoutFilter} used to hand-build
 * {@code {"code":"UNAUTHORIZED","message":"Session terminated by administrator"}}
 * with a {@code new} Jackson 2 {@code ObjectMapper} — a String where the envelope
 * says {@code int}, and {@code message} where every client reads {@code msg}.
 *
 * <p>The cost was the administrator's reason for the kick: {@code request.js}
 * surfaces {@code error.response.data?.msg}, which resolved to {@code undefined},
 * so the user got the generic "リクエストに失敗しました". (The redirect to /login still
 * happened — the 401 triggers a refresh, and {@code AuthService.refresh} re-checks
 * the kick and refuses — so this was the message, not the enforcement.)
 */
class ForceLogoutFilterResponseTest {

    private static final String USER_ID = "01JCUSER0000000000000000";

    @AfterEach
    void clearContext() {
        RequestContext.clear();
    }

    @Test
    @DisplayName("a kicked-out request is refused with the JsonResult envelope and an i18n key")
    void kickedRequest_writesJsonResultEnvelope() throws Exception {
        long kickAt = Instant.now().getEpochSecond();

        ForceLogoutService forceLogout = mock(ForceLogoutService.class);
        when(forceLogout.kickOutAt(USER_ID)).thenReturn(kickAt);
        when(forceLogout.tenantKickOutAt(anyString())).thenReturn(0L);

        MockHttpServletResponse resp = runFilter(forceLogout, kickAt - 60);

        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentType()).contains("application/json");

        JsonNode body = JsonMapper.builder().build().readTree(resp.getContentAsString());

        // int, not "UNAUTHORIZED" — the envelope's code is numeric everywhere else.
        assertThat(body.get("code").isNumber())
                .as("JsonResult.code is an int; a String here breaks every client that "
                        + "compares it (request.js does `code === 0` / `code === 401`)")
                .isTrue();
        assertThat(body.get("code").asInt()).isEqualTo(ErrorCode.UNAUTHORIZED.code());

        // `msg`, not `message` — request.js reads error.response.data?.msg.
        assertThat(body.has("msg"))
                .as("the field is `msg` in JsonResult; `message` is invisible to every caller")
                .isTrue();
        assertThat(body.get("msg").asString()).isEqualTo("error.auth.sessionTerminated");
        assertThat(body.has("message")).isFalse();
    }

    @Test
    @DisplayName("a token issued after the kick passes through untouched")
    void tokenIssuedAfterKick_passesThrough() throws Exception {
        long kickAt = Instant.now().getEpochSecond() - 300;

        ForceLogoutService forceLogout = mock(ForceLogoutService.class);
        when(forceLogout.kickOutAt(USER_ID)).thenReturn(kickAt);
        when(forceLogout.tenantKickOutAt(anyString())).thenReturn(0L);

        MockHttpServletResponse resp = runFilter(forceLogout, kickAt + 60);

        assertThat(resp.getStatus()).isEqualTo(200);
        assertThat(resp.getContentAsString()).isEmpty();
    }

    private MockHttpServletResponse runFilter(ForceLogoutService forceLogout, long issuedAtEpochSec)
            throws Exception {
        Instant iat = Instant.ofEpochSecond(issuedAtEpochSec);
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(USER_ID)
                .issuedAt(iat)
                .claim("tid", "demo")
                .build();

        JwtDecoder decoder = mock(JwtDecoder.class);
        when(decoder.decode(anyString())).thenReturn(jwt);

        ForceLogoutFilter filter =
                new ForceLogoutFilter(forceLogout, decoder, JsonMapper.builder().build());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/user/me");
        req.addHeader("Authorization", "Bearer token");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        // The real chain has CoreRequestContextFilter ahead of this one; it is what
        // maps the JWT subject to the business ULID the kick is keyed by.
        RequestContext.set("demo", USER_ID, "tester", Locale.JAPAN, "trace-force-logout");
        filter.doFilter(req, resp, new MockFilterChain());
        return resp;
    }

    /** Guards the sibling filter's shape too, so the pair can't drift apart again. */
    @Test
    @DisplayName("the rate-limit filter's rejection uses the same envelope")
    void rateLimitFilter_usesSameEnvelope() {
        JsonMapper mapper = JsonMapper.builder().build();
        String json = mapper.writeValueAsString(
                com.platform.core.common.result.JsonResult.error(ErrorCode.TOO_MANY_REQUESTS));
        JsonNode body = mapper.readTree(json);
        assertThat(body.has("code")).isTrue();
        assertThat(body.has("msg")).isTrue();
        assertThat(body.has("data")).isTrue();
        assertThat(body.get("code").isNumber()).isTrue();
        assertThat(body.get("code").asInt()).isEqualTo(ErrorCode.TOO_MANY_REQUESTS.code());
    }
}
