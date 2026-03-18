package com.focuskeeper.reboot.recovery.execution.dto;

import jakarta.validation.constraints.NotBlank;

public record StartRecoverySessionRequest(
        @NotBlank(message = "userId는 필수입니다.")
        String userId,
        @NotBlank(message = "timeboxId는 필수입니다.")
        String timeboxId
) {
}
