package com.focuskeeper.reboot.recovery.execution.dto;

import com.focuskeeper.reboot.recovery.execution.RestartType;
import java.time.OffsetDateTime;

public record RestartEventResponse(
        String id,
        String failureEventId,
        RestartType restartType,
        int suggestedMinutes,
        OffsetDateTime occurredAt
) {
}
