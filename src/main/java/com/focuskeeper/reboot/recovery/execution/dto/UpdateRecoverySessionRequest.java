package com.focuskeeper.reboot.recovery.execution.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRecoverySessionRequest(
        @NotBlank(message = "userId는 필수입니다.")
        String userId,
        @NotBlank(message = "sessionId는 필수입니다.")
        String sessionId
) {
}
