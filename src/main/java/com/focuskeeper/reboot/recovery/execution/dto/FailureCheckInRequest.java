package com.focuskeeper.reboot.recovery.execution.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FailureCheckInRequest(
        @NotBlank(message = "userId는 필수입니다.")
        String userId,
        @NotBlank(message = "sessionId는 필수입니다.")
        String sessionId,
        @NotBlank(message = "reason은 필수입니다.")
        String reason,
        @Size(max = 200, message = "note는 최대 200자까지 허용됩니다.")
        String note
) {
}
