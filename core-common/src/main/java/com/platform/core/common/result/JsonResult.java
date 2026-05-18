package com.platform.core.common.result;

import com.platform.core.common.error.ErrorCode;

public record JsonResult<T>(int code, String msg, T data) {

    public static final int CODE_OK = 0;
    public static final String MSG_OK = "success";

    public static <T> JsonResult<T> ok() {
        return new JsonResult<>(CODE_OK, MSG_OK, null);
    }

    public static <T> JsonResult<T> ok(T data) {
        return new JsonResult<>(CODE_OK, MSG_OK, data);
    }

    public static <T> JsonResult<T> error(ErrorCode ec) {
        return new JsonResult<>(ec.code(), ec.msg(), null);
    }

    public static <T> JsonResult<T> error(ErrorCode ec, T detail) {
        return new JsonResult<>(ec.code(), ec.msg(), detail);
    }

    public static <T> JsonResult<T> error(int code, String msg) {
        return new JsonResult<>(code, msg, null);
    }
}
