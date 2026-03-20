package com.focuskeeper.reboot.recovery.analytics.friction.dto;

import java.util.List;

public record FrictionSegmentReportResponse(
        String userId,
        String metricDate,
        List<FrictionSegmentResponse> segments
) {
}
