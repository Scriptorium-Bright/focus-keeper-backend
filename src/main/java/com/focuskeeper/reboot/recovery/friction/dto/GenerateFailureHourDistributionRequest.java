package com.focuskeeper.reboot.recovery.friction.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 실패 시간대 분포 리포트 생성을 요청하는 입력 DTO다.
 */
public record GenerateFailureHourDistributionRequest(
        @NotBlank(message = "userId는 필수입니다.")
        String userId,
        @NotBlank(message = "metricDate는 필수입니다.")
        String metricDate
) {
}
