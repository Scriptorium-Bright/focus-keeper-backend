package com.focuskeeper.reboot.recovery.analytics.friction.dto;

import java.util.List;

public record FrictionSignalReportResponse(
        String userId,
        String metricDate,
        List<FrictionSignalResponse> signals
) {
}
