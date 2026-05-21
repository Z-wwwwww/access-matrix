package com.platform.core.infrastructure.web;

import com.platform.core.common.error.BusinessException;
import com.platform.core.common.error.ErrorCode;
import com.platform.core.common.result.JsonResult;
import com.platform.core.infrastructure.config.properties.AppDebugProperties;
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
