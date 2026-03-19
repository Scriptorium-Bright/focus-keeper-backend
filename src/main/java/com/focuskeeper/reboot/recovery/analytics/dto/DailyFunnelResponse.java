package com.focuskeeper.reboot.recovery.analytics.dto;

import java.math.BigDecimal;

public record DailyFunnelResponse(
        String dailyFunnelId,
        String metricDate,
        long brainDumpUsers,
        long big3Users,
        long timeboxUsers,
        long sessionStartedUsers,
        long failureUsers,
        long restartUsers,
        BigDecimal big3SelectionRate,
        BigDecimal timeboxPlanningRate,
        BigDecimal sessionStartRate,
        BigDecimal failureRate,
        BigDecimal restartRate,
        String generatedAt
) {
}
