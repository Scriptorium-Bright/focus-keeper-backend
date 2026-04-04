package com.focuskeeper.reboot.recovery.analytics.dto;

import java.util.List;

public record BackfillDailyKpiResponse(
        String userId,
        String startDate,
        String endDate,
        int processedDays,
        List<String> processedMetricDates,
        DailyKpiLastProcessedDateResponse lastProcessedDate
) {
}
