package com.focuskeeper.reboot.recovery.analytics.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 일간 KPI를 날짜 구간 단위로 다시 계산할 때 사용하는 백필 요청 DTO다.
 */
public record BackfillDailyKpiRequest(
        @NotBlank(message = "userId는 비어 있을 수 없습니다.")
        String userId,
        @NotBlank(message = "startDate는 비어 있을 수 없습니다.")
        String startDate,
        @NotBlank(message = "endDate는 비어 있을 수 없습니다.")
        String endDate
) {
}
