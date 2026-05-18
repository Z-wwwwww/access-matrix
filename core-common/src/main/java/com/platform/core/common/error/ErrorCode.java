package com.platform.core.common.error;

public enum ErrorCode {

    OK(0, "success"),
    BAD_REQUEST(400, "Bad request"),
    UNAUTHORIZED(401, "Unauthorized"),
    FORBIDDEN(403, "Forbidden"),
    NOT_FOUND(404, "Not found"),
    TOO_MANY_REQUESTS(429, "Too many requests"),
    INTERNAL_ERROR(500, "Internal server error"),

    BUSINESS_ERROR(700, "Business error"),
    VALIDATION_FAILED(701, "Validation failed"),
    OPTIMISTIC_LOCK_CONFLICT(702, "Optimistic lock conflict"),
    MISSING_TENANT(710, "Missing tenant context"),
    EXTERNAL_SERVICE_ERROR(720, "External service error"),
    ACCOUNT_LOCKED(730, "Account locked"),

    BAD_CREDENTIALS(401, "Bad credentials"),
    ACCOUNT_DISABLED(403, "Account disabled"),
    INVALID_TOKEN(401, "Invalid token"),
    EXPIRED_TOKEN(401, "Expired token");

    private final int code;
    private final String msg;

    ErrorCode(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int code() {
        return code;
    }

    public String msg() {
        return msg;
    }
}
