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
/**
 * 일간 KPI 품질 리포트를 조회 전용으로 읽어오는 서비스다.
 *
 * KPI 계산이 끝난 뒤 생성된 DQ 결과를 API나 운영 화면에서 재사용할 수 있게 해준다.
 */
public class DailyKpiQualityQueryService {

    private final DailyKpiQualityReportRepository dailyKpiQualityReportRepository;

    public DailyKpiQualityQueryService(DailyKpiQualityReportRepository dailyKpiQualityReportRepository) {
        this.dailyKpiQualityReportRepository = dailyKpiQualityReportRepository;
    }

    /**
     * 사용자와 날짜 기준으로 KPI 품질 리포트를 조회하고, 없으면 조회 실패 예외를 던진다.
     */
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
