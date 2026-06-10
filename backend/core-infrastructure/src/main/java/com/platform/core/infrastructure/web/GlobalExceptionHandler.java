package com.platform.core.infrastructure.web;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.infrastructure.config.properties.AppDebugProperties;
import com.platform.core.infrastructure.security.keycloak.KeycloakUserService.KeycloakOperationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final AppDebugProperties debug;

    public GlobalExceptionHandler(AppDebugProperties debug) {
        this.debug = debug;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<JsonResult<Object>> handleBusiness(BusinessException ex) {
        log.warn("Business exception: code={} msg={}", ex.errorCode().code(), ex.getMessage());
        JsonResult<Object> body = new JsonResult<>(ex.errorCode().code(), ex.getMessage(), ex.detail());
        return ResponseEntity.ok(body);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<JsonResult<Map<String, String>>> handleBindError(Exception ex) {
        Map<String, String> errors = new HashMap<>();
        if (ex instanceof MethodArgumentNotValidException manve) {
            for (FieldError fe : manve.getBindingResult().getFieldErrors()) {
                errors.put(fe.getField(), fe.getDefaultMessage());
            }
        } else if (ex instanceof BindException be) {
            for (FieldError fe : be.getBindingResult().getFieldErrors()) {
                errors.put(fe.getField(), fe.getDefaultMessage());
            }
        }
        return ResponseEntity.ok(JsonResult.error(ErrorCode.VALIDATION_FAILED, errors));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<JsonResult<Map<String, String>>> handleConstraint(ConstraintViolationException ex) {
        Map<String, String> errors = new HashMap<>();
        for (ConstraintViolation<?> cv : ex.getConstraintViolations()) {
            errors.put(cv.getPropertyPath().toString(), cv.getMessage());
        }
        return ResponseEntity.ok(JsonResult.error(ErrorCode.VALIDATION_FAILED, errors));
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<JsonResult<Object>> handleOptimistic(OptimisticLockingFailureException ex) {
        log.warn("Optimistic lock conflict: {}", ex.getMessage());
        return ResponseEntity.ok(JsonResult.error(ErrorCode.OPTIMISTIC_LOCK_CONFLICT));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<JsonResult<Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(JsonResult.error(ErrorCode.FORBIDDEN));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<JsonResult<Object>> handleAuth(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(JsonResult.error(ErrorCode.UNAUTHORIZED));
    }

    /**
     * A client (typically an SSE / {@code EventSource} tab from
     * {@code SseEmitterRegistry}) disconnected before the response finished
     * writing. Tomcat reports the broken write through the servlet async
     * machinery, which surfaces here as an {@link AsyncRequestNotUsableException}
     * (it {@code extends IOException}, so without this it would fall into
     * {@link #handleGeneric} and be logged as a bogus server error — and then
     * fail again trying to serialize a JsonResult onto a {@code text/event-stream}
     * response). The connection is already gone: there is nothing to send back,
     * so return {@code void} and log at debug.
     */
    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public void handleClientDisconnect(AsyncRequestNotUsableException ex) {
        log.debug("Client disconnected before the response finished: {}", ex.getMessage());
    }

    /**
     * A Keycloak-side rejection (validation / policy / conflict) is a caller
     * error, not a server bug — surface it as a business error (with the KC
     * detail logged at WARN for ops) instead of letting it fall through to
     * {@link #handleGeneric} and become a raw 500 + scary "Unhandled exception".
     * e.g. creating a user whose username is shorter than KC's user-profile
     * length policy. The message is an i18n KEY localized by the frontend.
     */
    @ExceptionHandler(KeycloakOperationException.class)
    public ResponseEntity<JsonResult<Object>> handleKeycloak(KeycloakOperationException ex) {
        log.warn("Keycloak operation rejected: {}", ex.getMessage());
        return ResponseEntity.ok(new JsonResult<>(
                ErrorCode.BUSINESS_ERROR.code(), "error.keycloak.operationFailed", null));
    }

    /**
     * An unknown path (e.g. a typo'd API URL) falls through to the resource
     * handler chain and surfaces as a {@link NoResourceFoundException}. Without
     * this it lands in {@link #handleGeneric}, gets logged as a 500 "Unhandled
     * exception" and pollutes the error-rate metrics — it is a plain 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<JsonResult<Object>> handleNoResourceFound(NoResourceFoundException ex) {
        log.debug("No resource for path: {}", ex.getResourcePath());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(JsonResult.error(ErrorCode.NOT_FOUND));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<JsonResult<Object>> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        if (debug.exposeErrorDetails()) {
            Map<String, String> detail = new HashMap<>();
            detail.put("exception", ex.getClass().getName());
            detail.put("message", ex.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(JsonResult.error(ErrorCode.INTERNAL_ERROR.code(),
                            ErrorCode.INTERNAL_ERROR.msg() + ": " + ex.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(JsonResult.error(ErrorCode.INTERNAL_ERROR));
    }
}
