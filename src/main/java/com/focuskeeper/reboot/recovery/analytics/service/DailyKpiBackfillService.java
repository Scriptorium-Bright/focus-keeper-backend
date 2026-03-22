package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.common.observability.OperationsMetricRecorder;
import com.focuskeeper.reboot.common.observability.OperationsPipelineKeys;
import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.analytics.dto.BackfillDailyKpiResponse;
import io.micrometer.core.instrument.Timer;
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
    private final OperationsMetricRecorder operationsMetricRecorder;

    public DailyKpiBackfillService(
            DailyKpiPipelineService dailyKpiPipelineService,
            DailyKpiWatermarkService dailyKpiWatermarkService,
            OperationsMetricRecorder operationsMetricRecorder
    ) {
        this.dailyKpiPipelineService = dailyKpiPipelineService;
        this.dailyKpiWatermarkService = dailyKpiWatermarkService;
        this.operationsMetricRecorder = operationsMetricRecorder;
    }

    /**
     * 지정한 날짜 구간을 하루씩 다시 계산해 KPI mart를 재생성하고, 처리 결과와 최신 워터마크를 반환한다.
     */
    public BackfillDailyKpiResponse backfill(String userId, LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("dateRange", "endDate는 startDate보다 빠를 수 없습니다.")
            );
        }

        Timer.Sample sample = operationsMetricRecorder.startSample();
        try {
            List<String> processedMetricDates = new ArrayList<>();
            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                dailyKpiPipelineService.generate(userId, current);
                processedMetricDates.add(current.toString());
                current = current.plusDays(1);
            }

            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.BACKFILL_REPROCESS,
                    "backfill",
                    "success"
            );
            operationsMetricRecorder.recordBackfillProcessedDays(
                    OperationsPipelineKeys.BACKFILL_REPROCESS,
                    processedMetricDates.size()
            );

            return new BackfillDailyKpiResponse(
                    userId,
                    startDate.toString(),
                    endDate.toString(),
                    processedMetricDates.size(),
                    processedMetricDates,
                    dailyKpiWatermarkService.get(userId)
            );
        } catch (RuntimeException exception) {
            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.BACKFILL_REPROCESS,
                    "backfill",
                    "failure"
            );
            throw exception;
        }
    }
}
