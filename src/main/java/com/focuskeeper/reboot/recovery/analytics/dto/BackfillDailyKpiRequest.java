package com.focuskeeper.reboot.recovery.analytics.dto;

import jakarta.validation.constraints.NotBlank;

public record BackfillDailyKpiRequest(
        @NotBlank(message = "userId는 비어 있을 수 없습니다.")
        String userId,
        @NotBlank(message = "startDate는 비어 있을 수 없습니다.")
        String startDate,
        @NotBlank(message = "endDate는 비어 있을 수 없습니다.")
        String endDate
) {
}
