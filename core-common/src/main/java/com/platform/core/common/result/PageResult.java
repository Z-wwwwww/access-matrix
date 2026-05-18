package com.platform.core.common.result;

import java.util.List;

public record PageResult<T>(List<T> records, long total, long page, long limit) {

    public static <T> PageResult<T> of(List<T> records, long total, long page, long limit) {
        return new PageResult<>(records, total, page, limit);
    }

    public static <T> PageResult<T> empty(long page, long limit) {
        return new PageResult<>(List.of(), 0L, page, limit);
    }
}
