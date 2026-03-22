package com.focuskeeper.reboot.common.observability.dto;

import java.util.Map;

public record OperationsAlertResponse(
        String alertKey,
        String pipelineKey,
        String stage,
        String userId,
        String severity,
        boolean active,
        String summary,
        Map<String, String> details,
        String lastChangedAt
) {
}
