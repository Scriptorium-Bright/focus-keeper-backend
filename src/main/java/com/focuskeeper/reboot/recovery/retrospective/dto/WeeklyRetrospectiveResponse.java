package com.focuskeeper.reboot.recovery.retrospective.dto;

public record WeeklyRetrospectiveResponse(
        String retrospectiveId,
        String weekStart,
        String weekEnd,
        long sessionStartedCount,
        long sessionCompletedCount,
        long sessionInterruptedCount,
        long failureCount,
        long restartCount,
        String dominantFailureReason,
        String summary,
        String generatedAt
) {
}
