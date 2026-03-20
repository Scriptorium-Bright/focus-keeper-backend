package com.focuskeeper.reboot.recovery.analytics.friction.dto;

import java.math.BigDecimal;

public record FailureHourMetricResponse(
        int localHour,
        int failureCount,
        BigDecimal failureRatio,
        boolean peakHour
) {
}
