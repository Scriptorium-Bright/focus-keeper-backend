package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiQualityResponse;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiQualityReportRepository;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DailyKpiQualityQueryService {

    private final DailyKpiQualityReportRepository dailyKpiQualityReportRepository;

    public DailyKpiQualityQueryService(DailyKpiQualityReportRepository dailyKpiQualityReportRepository) {
        this.dailyKpiQualityReportRepository = dailyKpiQualityReportRepository;
    }

    public DailyKpiQualityResponse get(String userId, LocalDate metricDate) {
        return dailyKpiQualityReportRepository.findByUserIdAndMetricDate(userId, metricDate)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of(
                                "userId", userId,
                                "metricDate", metricDate.toString()
                        )
                ))
                .toResponse();
    }
}
