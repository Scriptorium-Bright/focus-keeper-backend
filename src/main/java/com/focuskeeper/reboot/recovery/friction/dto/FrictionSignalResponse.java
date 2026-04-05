package com.focuskeeper.reboot.recovery.friction.dto;

/**
 * 개별 friction signal 한 건을 표현하는 응답 DTO다.
 */
public record FrictionSignalResponse(
        String signalType,
        boolean active,
        int evidenceCount,
        String generatedAt
) {
}
