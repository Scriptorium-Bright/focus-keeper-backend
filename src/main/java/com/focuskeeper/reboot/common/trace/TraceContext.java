package com.focuskeeper.reboot.common.trace;

import org.slf4j.MDC;

public final class TraceContext {

    public static final String TRACE_ID_KEY = "traceId";
    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    private static final String UNKNOWN_TRACE_ID = "N/A";

    private TraceContext() {
    }

    public static String currentTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        return traceId == null || traceId.isBlank() ? UNKNOWN_TRACE_ID : traceId;
    }
}
