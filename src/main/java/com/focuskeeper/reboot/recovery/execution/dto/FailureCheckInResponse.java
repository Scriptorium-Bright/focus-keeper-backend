package com.focuskeeper.reboot.recovery.execution.dto;

// 세션 실패 기록 & 재시작 제안을 위한 response
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
