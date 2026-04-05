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
/**
 * 저장된 daily KPI mart를 조회 전용으로 읽어오는 서비스다.
 *
 * 계산 책임은 없고, 이미 생성된 mart row를 찾아 API 응답 DTO로 변환하는 역할만 담당한다.
 */
public class DailyKpiQueryService {

    private final DailyKpiMetricRepository dailyKpiMetricRepository;

    public DailyKpiQueryService(DailyKpiMetricRepository dailyKpiMetricRepository) {
        this.dailyKpiMetricRepository = dailyKpiMetricRepository;
    }

    /**
     * 사용자와 날짜로 일간 KPI mart 행을 조회하고, 없으면 404 성격의 비즈니스 예외를 던진다.
     */
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
