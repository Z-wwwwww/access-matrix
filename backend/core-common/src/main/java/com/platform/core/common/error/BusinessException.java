package com.platform.core.common.error;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object detail;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.msg());
        this.errorCode = errorCode;
        this.detail = null;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.detail = null;
    }

    public BusinessException(ErrorCode errorCode, String message, Object detail) {
        super(message);
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public BusinessException(ErrorCode errorCode, Throwable cause) {
        super(errorCode.msg(), cause);
        this.errorCode = errorCode;
        this.detail = null;
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public Object detail() {
        return detail;
    }
}
