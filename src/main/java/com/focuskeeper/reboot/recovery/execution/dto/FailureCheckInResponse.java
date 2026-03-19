package com.focuskeeper.reboot.recovery.execution.dto;

public record FailureCheckInResponse(
        String failureEventId,
        String sessionId,
        String timeboxId,
        String reason,
        String note,
        String occurredAt,
        String sessionStatus,
        RestartSuggestionResponse restartSuggestion
) {
}
