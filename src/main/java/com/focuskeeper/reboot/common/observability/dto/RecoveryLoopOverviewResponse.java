package com.focuskeeper.reboot.common.observability.dto;

import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiResponse;
import com.focuskeeper.reboot.recovery.friction.dto.FailureHourDistributionResponse;
import com.focuskeeper.reboot.recovery.friction.dto.FrictionSegmentReportResponse;
import com.focuskeeper.reboot.recovery.friction.dto.FrictionSignalReportResponse;
import java.util.List;

public record RecoveryLoopOverviewResponse(
        String userId,
        String metricDate,
        DailyKpiResponse dailyKpi,
        FailureHourDistributionResponse failureHour,
        FrictionSignalReportResponse frictionSignals,
        FrictionSegmentReportResponse frictionSegments,
        List<String> metricNames,
        List<OperationsAlertResponse> activeAlerts
) {
}
