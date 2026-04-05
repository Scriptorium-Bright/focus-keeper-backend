package com.focuskeeper.reboot.recovery.analytics.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 특정 사용자와 날짜에 대해 daily KPI 생성 배치를 요청하는 입력 DTO다.
 */
public record GenerateDailyKpiRequest(
        @NotBlank(message = "userId는 비어 있을 수 없습니다.")
        String userId,
        @NotBlank(message = "metricDate는 비어 있을 수 없습니다.")
        String metricDate
) {
}
