package com.focuskeeper.reboot.common.observability;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.observability.dto.BatchOverviewResponse;
import com.focuskeeper.reboot.common.observability.dto.RecoveryLoopOverviewResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiQualityResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiWatermarkResponse;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiMetricRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiQualityReportRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiWatermarkRepository;
import com.focuskeeper.reboot.recovery.friction.dto.FailureHourDistributionResponse;
import com.focuskeeper.reboot.recovery.friction.dto.FrictionSegmentReportResponse;
import com.focuskeeper.reboot.recovery.friction.dto.FrictionSignalReportResponse;
import com.focuskeeper.reboot.recovery.friction.service.FailureHourQueryService;
import com.focuskeeper.reboot.recovery.friction.service.FrictionSegmentQueryService;
import com.focuskeeper.reboot.recovery.friction.service.FrictionSignalQueryService;
import com.focuskeeper.reboot.recovery.retrospective.dto.WeeklyRetrospectiveResponse;
import com.focuskeeper.reboot.recovery.retrospective.repository.WeeklyRetrospectiveRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class OperationsOverviewService {

    private static final List<String> RECOVERY_LOOP_METRICS = List.of(
            "http.server.requests",
            "reboot_recovery_loop_actions_total",
            "reboot_recovery_loop_action_duration",
            "daily_kpi.recovery24",
            "daily_kpi.ttr_minutes",
            "daily_kpi.cycle_completion_rate"
    );

    private static final List<String> BATCH_METRICS = List.of(
            "reboot_batch_duration",
            "reboot_batch_failed_runs_total",
            "reboot_dq_issue_count",
            "reboot_batch_watermark_lag_seconds",
            "reboot_backfill_processed_days"
    );

    private final DailyKpiMetricRepository dailyKpiMetricRepository;
    private final DailyKpiQualityReportRepository dailyKpiQualityReportRepository;
    private final DailyKpiWatermarkRepository dailyKpiWatermarkRepository;
    private final WeeklyRetrospectiveRepository weeklyRetrospectiveRepository;
    private final FailureHourQueryService failureHourQueryService;
    private final FrictionSignalQueryService frictionSignalQueryService;
    private final FrictionSegmentQueryService frictionSegmentQueryService;

    public OperationsOverviewService(
            DailyKpiMetricRepository dailyKpiMetricRepository,
            DailyKpiQualityReportRepository dailyKpiQualityReportRepository,
            DailyKpiWatermarkRepository dailyKpiWatermarkRepository,
            WeeklyRetrospectiveRepository weeklyRetrospectiveRepository,
            FailureHourQueryService failureHourQueryService,
            FrictionSignalQueryService frictionSignalQueryService,
            FrictionSegmentQueryService frictionSegmentQueryService
    ) {
        this.dailyKpiMetricRepository = dailyKpiMetricRepository;
        this.dailyKpiQualityReportRepository = dailyKpiQualityReportRepository;
        this.dailyKpiWatermarkRepository = dailyKpiWatermarkRepository;
        this.weeklyRetrospectiveRepository = weeklyRetrospectiveRepository;
        this.failureHourQueryService = failureHourQueryService;
        this.frictionSignalQueryService = frictionSignalQueryService;
        this.frictionSegmentQueryService = frictionSegmentQueryService;
    }

    public RecoveryLoopOverviewResponse getRecoveryLoopOverview(String userId, LocalDate metricDate) {
        DailyKpiResponse dailyKpi = dailyKpiMetricRepository.findByUserIdAndMetricDate(userId, metricDate)
                .map(metric -> metric.toResponse())
                .orElse(null);

        return new RecoveryLoopOverviewResponse(
                userId,
                metricDate.toString(),
                dailyKpi,
                optionalFailureHour(userId, metricDate),
                optionalSignals(userId, metricDate),
                optionalSegments(userId, metricDate),
                RECOVERY_LOOP_METRICS
        );
    }

    public BatchOverviewResponse getBatchOverview(String userId, LocalDate metricDate) {
        DailyKpiQualityResponse qualityReport = dailyKpiQualityReportRepository.findByUserIdAndMetricDate(userId, metricDate)
                .map(report -> report.toResponse())
                .orElse(null);
        DailyKpiWatermarkResponse watermark = dailyKpiWatermarkRepository
                .findByPipelineKeyAndUserId(OperationsPipelineKeys.DAILY_KPI_PIPELINE, userId)
                .map(entity -> entity.toResponse())
                .orElse(null);
        LocalDate weekStart = metricDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        WeeklyRetrospectiveResponse weeklyRetrospective = weeklyRetrospectiveRepository.findByUserIdAndWeekStart(userId, weekStart)
                .map(entity -> entity.toResponse())
                .orElse(null);

        return new BatchOverviewResponse(
                userId,
                metricDate.toString(),
                qualityReport,
                watermark,
                weeklyRetrospective,
                BATCH_METRICS
        );
    }

    private FailureHourDistributionResponse optionalFailureHour(String userId, LocalDate metricDate) {
        try {
            return failureHourQueryService.get(userId, metricDate);
        } catch (BusinessException exception) {
            return null;
        }
    }

    private FrictionSignalReportResponse optionalSignals(String userId, LocalDate metricDate) {
        try {
            return frictionSignalQueryService.get(userId, metricDate);
        } catch (BusinessException exception) {
            return null;
        }
    }

    private FrictionSegmentReportResponse optionalSegments(String userId, LocalDate metricDate) {
        try {
            return frictionSegmentQueryService.get(userId, metricDate);
        } catch (BusinessException exception) {
            return null;
        }
    }
}
