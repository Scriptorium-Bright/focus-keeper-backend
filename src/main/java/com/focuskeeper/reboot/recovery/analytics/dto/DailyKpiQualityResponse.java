package com.focuskeeper.reboot.recovery.analytics.dto;

/**
 * KPI 계산 결과에 대해 검출된 데이터 품질 이슈를 전달하는 응답 DTO다.
 */
public record DailyKpiQualityResponse(
        String dailyKpiQualityReportId,
        String userId,
        String metricDate,
        boolean healthy,
        int duplicateRestartLinkCount,
        int orphanRestartCount,
        int restartBeforeFailureCount,
        int lateRestartLinkCount,
        int breakSessionReferenceCount,
        int missingTimeboxReferenceCount,
        int timezoneMismatchCount,
        int totalIssueCount,
        String generatedAt
) {
}
