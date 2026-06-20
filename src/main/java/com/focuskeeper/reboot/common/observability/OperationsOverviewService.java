package com.focuskeeper.reboot.common.observability;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.observability.dto.BatchOverviewResponse;
import com.focuskeeper.reboot.common.observability.dto.OperationsAlertResponse;
import com.focuskeeper.reboot.common.observability.dto.RecoveryLoopOverviewResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiQualityResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiLastProcessedDateResponse;
import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiMetric;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiMetricRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiQualityReportRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiLastProcessedDateRepository;
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

/**
 * Observability 계층의 "대시보드 조립기" 역할을 한다.
 *
 * 메트릭 자체를 계산하거나 alert를 판정하지는 않고, KPI/DQ/lastProcessedDate/retrospective/
 * friction 보고서와 활성 alert를 모아 운영 화면에 필요한 overview 응답으로 합친다.
 */
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
            "reboot_batch_processing_lag_seconds",
            "reboot_backfill_processed_days",
            "reboot_expiration_runs_total",
            "reboot_expiration_skipped_runs_total",
            "reboot_expiration_duration",
            "reboot_expiration_processed_items",
            "reboot_expiration_last_success_timestamp_seconds",
            "reboot_expiration_last_duration_seconds",
            "reboot_expiration_running"
    );

    private final DailyKpiMetricRepository dailyKpiMetricRepository;
    private final DailyKpiQualityReportRepository dailyKpiQualityReportRepository;
    private final DailyKpiLastProcessedDateRepository dailyKpiLastProcessedDateRepository;
    private final WeeklyRetrospectiveRepository weeklyRetrospectiveRepository;
    private final FailureHourQueryService failureHourQueryService;
    private final FrictionSignalQueryService frictionSignalQueryService;
    private final FrictionSegmentQueryService frictionSegmentQueryService;
    private final OperationsAlertService operationsAlertService;

    public OperationsOverviewService(
            DailyKpiMetricRepository dailyKpiMetricRepository,
            DailyKpiQualityReportRepository dailyKpiQualityReportRepository,
            DailyKpiLastProcessedDateRepository dailyKpiLastProcessedDateRepository,
            WeeklyRetrospectiveRepository weeklyRetrospectiveRepository,
            FailureHourQueryService failureHourQueryService,
            FrictionSignalQueryService frictionSignalQueryService,
            FrictionSegmentQueryService frictionSegmentQueryService,
            OperationsAlertService operationsAlertService
    ) {
        this.dailyKpiMetricRepository = dailyKpiMetricRepository;
        this.dailyKpiQualityReportRepository = dailyKpiQualityReportRepository;
        this.dailyKpiLastProcessedDateRepository = dailyKpiLastProcessedDateRepository;
        this.weeklyRetrospectiveRepository = weeklyRetrospectiveRepository;
        this.failureHourQueryService = failureHourQueryService;
        this.frictionSignalQueryService = frictionSignalQueryService;
        this.frictionSegmentQueryService = frictionSegmentQueryService;
        this.operationsAlertService = operationsAlertService;
    }

    /**
     * recovery loop 운영 화면에 필요한 스냅샷을 조립한다.
     *
     * 특정 사용자/날짜 기준 daily KPI와 보조 분석 결과, 활성 alert를 한 번에 묶어
     * "현재 복귀 루프가 건강한지"를 빠르게 읽을 수 있는 응답을 만든다.
     */
    public RecoveryLoopOverviewResponse getRecoveryLoopOverview(String userId, LocalDate metricDate) {
        DailyKpiResponse dailyKpi = dailyKpiMetricRepository.findByUserIdAndMetricDate(userId, metricDate)
                .map(DailyKpiMetric::toResponse)
                .orElse(null);
        List<OperationsAlertResponse> activeAlerts = operationsAlertService.getAlerts(true, userId);

        return new RecoveryLoopOverviewResponse(
                userId,
                metricDate.toString(),
                dailyKpi,
                optionalFailureHour(userId, metricDate),
                optionalSignals(userId, metricDate),
                optionalSegments(userId, metricDate),
                RECOVERY_LOOP_METRICS,
                activeAlerts
        );
    }

    /**
     * batch/DQ/lastProcessedDate/retrospective 운영 화면에 필요한 스냅샷을 조립한다.
     *
     * 파이프라인 품질, freshness, 주간 회고 입력 준비 상태와 현재 활성 경보를 한 응답으로 합쳐
     * 운영자가 "재처리가 필요한가, 품질 이슈가 있는가"를 판단할 수 있게 한다.
     */
    public BatchOverviewResponse getBatchOverview(String userId, LocalDate metricDate) {
        DailyKpiQualityResponse qualityReport = dailyKpiQualityReportRepository.findByUserIdAndMetricDate(userId, metricDate)
                .map(report -> report.toResponse())
                .orElse(null);
        DailyKpiLastProcessedDateResponse lastProcessedDate = dailyKpiLastProcessedDateRepository
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
                lastProcessedDate,
                weeklyRetrospective,
                BATCH_METRICS,
                operationsAlertService.getAlerts(true, userId)
        );
    }

    /**
     * failure hour 보고서가 아직 없더라도 overview 전체를 실패시키지 않기 위한 null 허용 조회다.
     *
     * 운영 대시보드는 일부 보조 보고서가 비어 있어도 핵심 KPI와 alert를 먼저 보여주는 편이 낫기 때문에
     * 조회 실패 시 예외 대신 null을 돌려준다.
     */
    private FailureHourDistributionResponse optionalFailureHour(String userId, LocalDate metricDate) {
        try {
            return failureHourQueryService.get(userId, metricDate);
        } catch (BusinessException exception) {
            return null;
        }
    }

    /**
     * friction signal 보고서도 동일한 이유로 선택 조회한다.
     *
     * 보조 분석 결과 미생성 상태를 "운영 스냅샷의 일부 빈칸"으로 다루기 위해 null 허용 전략을 쓴다.
     */
    private FrictionSignalReportResponse optionalSignals(String userId, LocalDate metricDate) {
        try {
            return frictionSignalQueryService.get(userId, metricDate);
        } catch (BusinessException exception) {
            return null;
        }
    }

    /**
     * friction segment 보고서가 없을 때 overview API 전체가 500으로 실패하지 않도록 방어한다.
     *
     * 운영 대시보드는 완전한 분석 결과보다 현재 상태를 우선 보여줘야 하므로,
     * 생성 전 상태나 데이터 부재를 허용하는 방향으로 조립한다.
     */
    private FrictionSegmentReportResponse optionalSegments(String userId, LocalDate metricDate) {
        try {
            return frictionSegmentQueryService.get(userId, metricDate);
        } catch (BusinessException exception) {
            return null;
        }
    }
}
