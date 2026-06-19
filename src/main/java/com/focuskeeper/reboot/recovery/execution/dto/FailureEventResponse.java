package com.focuskeeper.reboot.recovery.execution.dto;

import com.focuskeeper.reboot.recovery.execution.constant.FailureReason;
import java.time.OffsetDateTime;

// 그냥 말그대로 Response (Entity 보호)
public record FailureEventResponse(
        String id,
        String sessionId,
        String timeboxId,
        FailureReason reason,
        String note,
        OffsetDateTime occurredAt
) {
}
