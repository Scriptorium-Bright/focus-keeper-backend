package com.focuskeeper.reboot.recovery.analytics.friction.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateFailureHourDistributionRequest(
        @NotBlank(message = "userId는 필수입니다.")
        String userId,
        @NotBlank(message = "metricDate는 필수입니다.")
        String metricDate
) {
}
