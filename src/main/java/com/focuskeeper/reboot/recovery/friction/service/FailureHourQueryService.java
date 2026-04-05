package com.focuskeeper.reboot.recovery.friction.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.friction.dto.FailureHourDistributionResponse;
import com.focuskeeper.reboot.recovery.friction.dto.FailureHourMetricResponse;
import com.focuskeeper.reboot.recovery.friction.entity.FailureHourMetric;
import com.focuskeeper.reboot.recovery.friction.entity.FailureHourReport;
import com.focuskeeper.reboot.recovery.friction.repository.FailureHourMetricRepository;
import com.focuskeeper.reboot.recovery.friction.repository.FailureHourReportRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
/**
 * 저장된 failure-hour report와 hourly metric row를 읽어 조회 응답으로 조합하는 서비스다.
 */
public class FailureHourQueryService {

    private final FailureHourReportRepository failureHourReportRepository;
    private final FailureHourMetricRepository failureHourMetricRepository;

    public FailureHourQueryService(
            FailureHourReportRepository failureHourReportRepository,
            FailureHourMetricRepository failureHourMetricRepository
    ) {
        this.failureHourReportRepository = failureHourReportRepository;
        this.failureHourMetricRepository = failureHourMetricRepository;
    }

    /**
     * 저장된 시간대별 실패 분포 리포트와 hourly metric row를 함께 조회한다.
     */
    public FailureHourDistributionResponse get(String userId, LocalDate metricDate) {
        FailureHourReport report = failureHourReportRepository.findByUserIdAndMetricDate(userId, metricDate)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of(
                                "userId", userId,
                                "metricDate", metricDate.toString()
                        )
                ));

        List<FailureHourMetricResponse> hourlyMetrics = failureHourMetricRepository
                .findAllByUserIdAndMetricDateOrderByLocalHourAsc(userId, metricDate).stream()
                .map(FailureHourMetric::toResponse)
                .toList();

        return report.toResponse(hourlyMetrics);
    }
}
