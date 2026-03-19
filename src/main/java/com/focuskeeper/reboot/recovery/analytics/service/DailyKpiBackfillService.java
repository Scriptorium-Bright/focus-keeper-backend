package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.analytics.dto.BackfillDailyKpiResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DailyKpiBackfillService {

    private final DailyKpiPipelineService dailyKpiPipelineService;
    private final DailyKpiWatermarkService dailyKpiWatermarkService;

    public DailyKpiBackfillService(
            DailyKpiPipelineService dailyKpiPipelineService,
            DailyKpiWatermarkService dailyKpiWatermarkService
    ) {
        this.dailyKpiPipelineService = dailyKpiPipelineService;
        this.dailyKpiWatermarkService = dailyKpiWatermarkService;
    }

    public BackfillDailyKpiResponse backfill(String userId, LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("dateRange", "endDate는 startDate보다 빠를 수 없습니다.")
            );
        }

        List<String> processedMetricDates = new ArrayList<>();
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            dailyKpiPipelineService.generate(userId, current);
            processedMetricDates.add(current.toString());
            current = current.plusDays(1);
        }

        return new BackfillDailyKpiResponse(
                userId,
                startDate.toString(),
                endDate.toString(),
                processedMetricDates.size(),
                processedMetricDates,
                dailyKpiWatermarkService.get(userId)
        );
    }
}
