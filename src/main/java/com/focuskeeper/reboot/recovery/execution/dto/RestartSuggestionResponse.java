package com.focuskeeper.reboot.recovery.execution.dto;

public record RestartSuggestionResponse(
        String restartType,
        int suggestedMinutes,
        String message
) {
}
