package com.focuskeeper.reboot.recovery.analytics.friction.dto;

import java.util.List;

public record FailureHourDistributionResponse(
        String reportId,
        String userId,
        String metricDate,
        int totalFailureCount,
        Integer peakFailureHour,
        String peakFailureWindow,
        String generatedAt,
        List<FailureHourMetricResponse> hourlyMetrics
) {
}
