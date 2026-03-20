package com.focuskeeper.reboot.recovery.analytics.friction.dto;

public record FrictionSegmentResponse(
        String segmentType,
        String title,
        String summary,
        String evidence
) {
}
