package com.focuskeeper.reboot.recovery.execution;

import java.time.OffsetDateTime;

public record RecoverySessionDto(
        String id,
        String userId,
        String timeboxId,
        RecoverySessionStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        OffsetDateTime createdAt
) {
}
