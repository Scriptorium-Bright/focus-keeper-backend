package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiResponse;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiMetricRepository;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DailyKpiQueryService {

    private final DailyKpiMetricRepository dailyKpiMetricRepository;

    public DailyKpiQueryService(DailyKpiMetricRepository dailyKpiMetricRepository) {
        this.dailyKpiMetricRepository = dailyKpiMetricRepository;
    }

    public DailyKpiResponse get(String userId, LocalDate metricDate) {
        return dailyKpiMetricRepository.findByUserIdAndMetricDate(userId, metricDate)
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
