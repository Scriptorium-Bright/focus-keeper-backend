package com.focuskeeper.reboot.common.observability;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.observability.dto.RecoveryLoopOverviewResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiResponse;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiMetricRepository;
import com.focuskeeper.reboot.recovery.friction.dto.FailureHourDistributionResponse;
import com.focuskeeper.reboot.recovery.friction.dto.FrictionSegmentReportResponse;
import com.focuskeeper.reboot.recovery.friction.dto.FrictionSignalReportResponse;
import com.focuskeeper.reboot.recovery.friction.service.FailureHourQueryService;
import com.focuskeeper.reboot.recovery.friction.service.FrictionSegmentQueryService;
import com.focuskeeper.reboot.recovery.friction.service.FrictionSignalQueryService;
import java.time.LocalDate;
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

    private final DailyKpiMetricRepository dailyKpiMetricRepository;
    private final FailureHourQueryService failureHourQueryService;
    private final FrictionSignalQueryService frictionSignalQueryService;
    private final FrictionSegmentQueryService frictionSegmentQueryService;

    public OperationsOverviewService(
            DailyKpiMetricRepository dailyKpiMetricRepository,
            FailureHourQueryService failureHourQueryService,
            FrictionSignalQueryService frictionSignalQueryService,
            FrictionSegmentQueryService frictionSegmentQueryService
    ) {
        this.dailyKpiMetricRepository = dailyKpiMetricRepository;
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
