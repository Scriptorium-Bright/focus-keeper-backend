package com.focuskeeper.reboot.recovery.execution.dto;

import com.focuskeeper.reboot.recovery.execution.FailureReason;
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
