package com.focuskeeper.reboot.recovery.analytics.dto;

public record DailyKpiLastProcessedDateResponse(
        String pipelineKey,
        String userId,
        String lastProcessedDate,
        String updatedAt
) {
}
