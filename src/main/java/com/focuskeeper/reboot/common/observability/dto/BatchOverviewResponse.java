package com.focuskeeper.reboot.common.observability.dto;

import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiQualityResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiWatermarkResponse;
import com.focuskeeper.reboot.recovery.retrospective.dto.WeeklyRetrospectiveResponse;
import java.util.List;

public record BatchOverviewResponse(
        String userId,
        String metricDate,
        DailyKpiQualityResponse qualityReport,
        DailyKpiWatermarkResponse watermark,
        WeeklyRetrospectiveResponse weeklyRetrospective,
        List<String> metricNames
) {
}
