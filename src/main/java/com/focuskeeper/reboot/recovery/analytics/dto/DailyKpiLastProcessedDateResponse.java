package com.focuskeeper.reboot.recovery.analytics.dto;

/**
 * 특정 파이프라인이 한 사용자에 대해 마지막으로 어디까지 처리했는지 반환하는 DTO다.
 */
public record DailyKpiLastProcessedDateResponse(
        String pipelineKey,
        String userId,
        String lastProcessedDate,
        String updatedAt
) {
}
