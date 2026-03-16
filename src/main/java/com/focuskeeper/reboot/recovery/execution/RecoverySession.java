package com.focuskeeper.reboot.recovery.execution;

import java.time.OffsetDateTime;

public record RecoverySession(
        String id,
        String userId,
        String timeboxId,
        RecoverySessionStatus status,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        OffsetDateTime createdAt
) {
    RecoverySession complete(OffsetDateTime endedAt) {
        return new RecoverySession(id, userId, timeboxId, RecoverySessionStatus.COMPLETED, startedAt, endedAt, createdAt);
    }

    RecoverySession interrupt(OffsetDateTime endedAt) {
        return new RecoverySession(id, userId, timeboxId, RecoverySessionStatus.INTERRUPTED, startedAt, endedAt, createdAt);
    }
}
