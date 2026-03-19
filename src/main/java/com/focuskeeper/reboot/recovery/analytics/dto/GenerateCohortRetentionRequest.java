package com.focuskeeper.reboot.recovery.analytics.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateCohortRetentionRequest(
        @NotBlank(message = "cohortDate는 비어 있을 수 없습니다.")
        String cohortDate
) {
}
