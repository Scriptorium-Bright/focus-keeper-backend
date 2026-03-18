package com.focuskeeper.reboot.recovery.execution;

import java.time.OffsetDateTime;

public record FailureEventResponse(
        String id,
        String sessionId,
        String timeboxId,
        FailureReason reason,
        String note,
        OffsetDateTime occurredAt
) {
}
