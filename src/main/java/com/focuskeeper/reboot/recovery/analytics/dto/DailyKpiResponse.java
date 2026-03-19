package com.focuskeeper.reboot.recovery.analytics.dto;

import java.math.BigDecimal;

public record DailyKpiResponse(
        String dailyKpiId,
        String userId,
        String metricDate,
        boolean activation,
        int failureCount,
        boolean recovery24,
        boolean recovery48,
        int restartCount24,
        int restartCount48,
        Long ttrMinutes,
        BigDecimal cycleCompletionRate,
        BigDecimal planExecutionRate,
        long plannedWorkMinutes,
        long actualWorkMinutes,
        long estimationErrorMinutes,
        String generatedAt
) {
}
