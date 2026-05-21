package com.focuskeeper.reboot.recovery.execution.dto;

import com.focuskeeper.reboot.recovery.execution.RestartType;
import java.time.OffsetDateTime;

// 그냥 말그대로 Response (Entity 보호)
public record RestartEventResponse(
        String id,
        String failureEventId,
        RestartType restartType,
        int suggestedMinutes,
        OffsetDateTime occurredAt
) {
}
