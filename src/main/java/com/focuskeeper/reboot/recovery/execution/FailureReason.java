package com.focuskeeper.reboot.recovery.execution;

import java.util.Arrays;

public enum FailureReason {
    TOO_BIG,
    INTERRUPTION,
    LOW_ENERGY,
    UNCLEAR_NEXT_ACTION,
    CONTEXT_SWITCHED;

    public static FailureReason from(String value) {
        return Arrays.stream(values())
                .filter(reason -> reason.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 failure reason입니다."));
    }
}
