package com.focuskeeper.reboot.recovery.analytics.dto;

import java.util.List;

/**
 * 백필 작업이 어떤 날짜 범위를 다시 계산했고 마지막 처리 날짜가 어디까지 전진했는지 반환하는 DTO다.
 */
public record BackfillDailyKpiResponse(
        String userId,
        String startDate,
        String endDate,
        int processedDays,
        List<String> processedMetricDates,
        DailyKpiLastProcessedDateResponse lastProcessedDate
) {
}
