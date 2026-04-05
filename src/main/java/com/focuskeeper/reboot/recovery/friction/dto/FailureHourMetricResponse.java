package com.focuskeeper.reboot.recovery.friction.dto;

import java.math.BigDecimal;

/**
 * 특정 시간대의 실패 건수와 비중을 표현하는 응답 DTO다.
 */
public record FailureHourMetricResponse(
        int localHour,
        int failureCount,
        BigDecimal failureRatio,
        boolean peakHour
) {
}
