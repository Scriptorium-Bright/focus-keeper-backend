package com.focuskeeper.reboot.recovery.planning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateExecutionUnitRequest(
        @NotBlank(message = "userId는 필수입니다.")
        String userId,
        @NotBlank(message = "title은 필수입니다.")
        @Size(max = 200, message = "title은 최대 200자까지 허용됩니다.")
        String title
) {
}
