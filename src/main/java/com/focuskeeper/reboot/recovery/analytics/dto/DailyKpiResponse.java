package com.focuskeeper.reboot.recovery.analytics.dto;

import java.math.BigDecimal;

/**
 * 하루 단위 KPI mart를 외부 API에 그대로 노출하기 위한 조회 응답 DTO다.
 */
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
