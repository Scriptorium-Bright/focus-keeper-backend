package com.focuskeeper.reboot.common.response;

import com.focuskeeper.reboot.common.trace.TraceContext;

public record ErrorResponse(
        boolean success,
        ErrorBody error,
        String traceId
) {
    public record ErrorBody(
            String code,
            String message,
            Object details
    ) {
    }

    public static ErrorResponse of(String code, String message, Object details) {
        return new ErrorResponse(
                false,
                new ErrorBody(code, message, details),
                TraceContext.currentTraceId()
        );
    }
}
