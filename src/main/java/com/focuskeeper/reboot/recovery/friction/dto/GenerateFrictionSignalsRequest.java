package com.focuskeeper.reboot.recovery.friction.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * friction signal 계산을 요청하는 입력 DTO다.
 */
public record GenerateFrictionSignalsRequest(
        @NotBlank(message = "userId는 필수입니다.")
        String userId,
        @NotBlank(message = "metricDate는 필수입니다.")
        String metricDate
) {
}
