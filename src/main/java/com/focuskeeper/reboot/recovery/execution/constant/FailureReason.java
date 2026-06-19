package com.focuskeeper.reboot.recovery.execution.constant;

import java.util.Arrays;

/**
 * 사용자가 복귀 세션을 실패로 체크인할 때 선택하는 실패 이유 분류다.
 */
public enum FailureReason {
    TOO_BIG,
    INTERRUPTION,
    LOW_ENERGY,
    UNCLEAR_NEXT_ACTION,
    CONTEXT_SWITCHED;

    /**
     * 외부 문자열을 enum 값으로 변환한다.
     */
    public static FailureReason from(String value) {
        return Arrays.stream(values())
                .filter(reason -> reason.name().equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("지원하지 않는 failure reason입니다."));
    }
}
