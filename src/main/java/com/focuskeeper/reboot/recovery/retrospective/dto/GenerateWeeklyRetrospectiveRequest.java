package com.focuskeeper.reboot.recovery.retrospective.dto;

import jakarta.validation.constraints.NotBlank;

public record GenerateWeeklyRetrospectiveRequest(
        @NotBlank String userId,
        @NotBlank String weekStart
) {
}
