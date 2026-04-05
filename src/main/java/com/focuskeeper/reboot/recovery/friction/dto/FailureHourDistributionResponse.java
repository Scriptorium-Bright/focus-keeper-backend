package com.focuskeeper.reboot.recovery.friction.dto;

import java.util.List;

/**
 * 하루 실패 분포 요약과 시간대별 상세 metric을 함께 담는 응답 DTO다.
 */
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
