package com.focuskeeper.reboot.common.observability;

import com.focuskeeper.reboot.common.observability.dto.OperationsAlertResponse;

public record OperationsAlertTransitionEvent(
        OperationsAlertTransitionType eventType,
        String emittedAt,
        String previousStatus,
        String previousSeverity,
        OperationsAlertResponse alert
) {
}
