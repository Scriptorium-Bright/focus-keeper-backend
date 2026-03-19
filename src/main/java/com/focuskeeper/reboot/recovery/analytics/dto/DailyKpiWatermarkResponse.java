package com.focuskeeper.reboot.recovery.analytics.dto;

public record DailyKpiWatermarkResponse(
        String pipelineKey,
        String userId,
        String lastProcessedDate,
        String updatedAt
) {
}
