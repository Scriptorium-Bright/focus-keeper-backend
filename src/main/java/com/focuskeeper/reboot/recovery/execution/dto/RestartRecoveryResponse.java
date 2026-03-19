package com.focuskeeper.reboot.recovery.execution.dto;

public record RestartRecoveryResponse(
        RestartEventResponse restartEvent,
        RecoverySessionResponse recoverySession,
        RestartSuggestionResponse restartSuggestion
) {
}
