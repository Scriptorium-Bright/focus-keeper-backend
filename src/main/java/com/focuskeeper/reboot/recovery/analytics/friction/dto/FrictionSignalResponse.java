package com.focuskeeper.reboot.recovery.analytics.friction.dto;

public record FrictionSignalResponse(
        String signalType,
        boolean active,
        int evidenceCount,
        String generatedAt
) {
}
