package com.focuskeeper.reboot.recovery.planning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record DailyBig3BoardRequest(
        @NotBlank(message = "userId는 필수입니다.")
        String userId,
        @NotEmpty(message = "big3ItemIds는 최소 한 개 이상이어야 합니다.")
        List<String> big3ItemIds
) {
}
