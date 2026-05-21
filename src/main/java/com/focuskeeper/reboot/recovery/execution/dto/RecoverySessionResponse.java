package com.focuskeeper.reboot.recovery.execution.dto;

// 그냥 말그대로 Response (Entity 보호)
public record RecoverySessionResponse(
        String sessionId,
        String timeboxId,
        String status,
        String startedAt,
        String endedAt,
        String createdAt
) {
}
