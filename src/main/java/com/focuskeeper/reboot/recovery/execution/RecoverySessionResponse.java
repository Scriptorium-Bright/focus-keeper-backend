package com.focuskeeper.reboot.recovery.execution;

public record RecoverySessionResponse(
        String sessionId,
        String timeboxId,
        String status,
        String startedAt,
        String endedAt,
        String createdAt
) {
}
