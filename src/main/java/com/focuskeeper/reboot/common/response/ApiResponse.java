package com.focuskeeper.reboot.common.response;

import com.focuskeeper.reboot.common.trace.TraceContext;

public record ApiResponse<T>(
        boolean success,
        T data,
        String message,
        String traceId
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, "OK", TraceContext.currentTraceId());
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, TraceContext.currentTraceId());
    }
}
