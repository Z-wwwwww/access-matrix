package com.platform.core.infrastructure.web;

import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.infrastructure.config.properties.AppDebugProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Malformed input from a client is a 4xx, not a 500.
 *
 * <p>{@code GlobalExceptionHandler} is a plain {@code @RestControllerAdvice} — it
 * does NOT extend {@code ResponseEntityExceptionHandler} — and its
 * {@code @ExceptionHandler(Exception.class)} catch-all is consulted BEFORE
 * {@code DefaultHandlerExceptionResolver}. So every Spring MVC framework
 * exception it doesn't name explicitly became a 500 with an "Unhandled
 * exception" ERROR line, and a tick on the dashboard's "API errors (24h)" KPI —
 * a metric this project defines as counting only UNEXPECTED server errors.
 *
 * <p>That mechanism is not hypothetical here: {@code NoResourceFoundException}
 * (a typo'd URL) had to be given its own handler for exactly this reason, and
 * its javadoc says so. These are the rest of the same family, all reachable by
 * an ordinary client:
 *
 * <ul>
 *   <li>{@code ?page=abc} on any list endpoint (they declare {@code long page})
 *       → MethodArgumentTypeMismatchException</li>
 *   <li>{@code {"status":"abc"}} where the DTO has {@code Integer status}, or a
 *       body truncated by a flaky connection → HttpMessageNotReadableException</li>
 *   <li>a required {@code @RequestParam} omitted → MissingServletRequestParameter</li>
 *   <li>wrong verb / wrong Content-Type → 405 / 415, never 500</li>
 * </ul>
 */
class GlobalExceptionHandlerClientErrorsTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(new AppDebugProperties(false));

    @Test
    void unreadableBodyIsABadRequest() {
        ResponseEntity<JsonResult<Object>> res = handler.handleUnreadable(
                new HttpMessageNotReadableException("JSON parse error: unexpected token", (org.springframework.http.HttpInputMessage) null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().code()).isEqualTo(ErrorCode.VALIDATION_FAILED.code());
    }

    @Test
    void aParamThatCannotBeConvertedIsABadRequest() {
        ResponseEntity<JsonResult<Object>> res = handler.handleTypeMismatch(
                new MethodArgumentTypeMismatchException("abc", Long.class, "page", null, null));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().code()).isEqualTo(ErrorCode.VALIDATION_FAILED.code());
        assertThat(res.getBody().msg())
                .as("the offending parameter name helps the caller fix it")
                .contains("page");
    }

    @Test
    void aMissingRequiredParamIsABadRequest() {
        ResponseEntity<JsonResult<Object>> res = handler.handleMissingParam(
                new MissingServletRequestParameterException("ticket", "String"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().code()).isEqualTo(ErrorCode.VALIDATION_FAILED.code());
        assertThat(res.getBody().msg()).contains("ticket");
    }

    @Test
    void wrongVerbIs405AndWrongContentTypeIs415() {
        assertThat(handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException("PUT")).getStatusCode())
                .isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);

        assertThat(handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException("text/plain")).getStatusCode())
                .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    /**
     * Taking these over from DefaultHandlerExceptionResolver means inheriting its
     * header duties — Allow is REQUIRED on a 405, and Accept is what tells the
     * caller what a 415 would have taken. Dropping them would make the response
     * strictly worse than Spring's default.
     */
    @Test
    void the405CarriesAllowAndThe415CarriesAccept() {
        var notAllowed = handler.handleMethodNotSupported(
                new HttpRequestMethodNotSupportedException(
                        "PUT", java.util.List.of("GET", "POST")));
        assertThat(notAllowed.getHeaders().getAllow())
                .containsExactlyInAnyOrder(org.springframework.http.HttpMethod.GET,
                        org.springframework.http.HttpMethod.POST);

        var unsupported = handler.handleMediaTypeNotSupported(
                new HttpMediaTypeNotSupportedException(
                        org.springframework.http.MediaType.TEXT_PLAIN,
                        java.util.List.of(org.springframework.http.MediaType.APPLICATION_JSON)));
        assertThat(unsupported.getHeaders().getFirst(org.springframework.http.HttpHeaders.ACCEPT))
                .contains("application/json");
    }

    @Test
    void noneOfTheseLeakTheExceptionText() {
        // exposeErrorDetails=false — the same posture handleGeneric takes in prod.
        String body = String.valueOf(handler.handleUnreadable(
                new HttpMessageNotReadableException("com.fasterxml.jackson.SecretInternals blew up", (org.springframework.http.HttpInputMessage) null)).getBody().msg());

        assertThat(body).doesNotContain("SecretInternals");
    }
}
