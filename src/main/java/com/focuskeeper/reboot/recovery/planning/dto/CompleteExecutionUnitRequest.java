package com.focuskeeper.reboot.recovery.planning.dto;

import jakarta.validation.constraints.NotBlank;

public record CompleteExecutionUnitRequest(
        @NotBlank(message = "userId는 필수입니다.")
        String userId
) {
}
