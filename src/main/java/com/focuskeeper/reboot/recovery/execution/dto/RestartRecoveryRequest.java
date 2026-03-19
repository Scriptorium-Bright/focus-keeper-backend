package com.focuskeeper.reboot.recovery.execution.dto;

import jakarta.validation.constraints.NotBlank;

public record RestartRecoveryRequest(
        @NotBlank(message = "userId는 필수입니다.")
        String userId,
        @NotBlank(message = "failureEventId는 필수입니다.")
        String failureEventId
) {
}
