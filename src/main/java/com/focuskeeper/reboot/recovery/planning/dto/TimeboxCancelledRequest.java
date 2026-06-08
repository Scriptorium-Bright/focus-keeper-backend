package com.focuskeeper.reboot.recovery.planning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record TimeboxCancelledRequest(
        @NotEmpty(message = "ids는 1개 이상이어야합니다.")
        List<String> ids,
        @NotBlank(message = "userId는 필수입니다.")
        String userId
) {
}
