package com.focuskeeper.reboot.recovery.execution;

import java.time.OffsetDateTime;

public record FailureEventDto(
        String id,
        String userId,
        String sessionId,
        String timeboxId,
        FailureReason reason,
        String note,
        OffsetDateTime occurredAt
) {
}
