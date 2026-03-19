package com.focuskeeper.reboot.recovery.analytics.dto;

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
