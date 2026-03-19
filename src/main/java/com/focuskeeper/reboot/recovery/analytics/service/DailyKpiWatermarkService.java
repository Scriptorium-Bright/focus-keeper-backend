package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiWatermarkResponse;
import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiWatermark;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiWatermarkRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DailyKpiWatermarkService {

    static final String DAILY_KPI_PIPELINE_KEY = "daily_kpi_pipeline";

    private final DailyKpiWatermarkRepository dailyKpiWatermarkRepository;

    public DailyKpiWatermarkService(DailyKpiWatermarkRepository dailyKpiWatermarkRepository) {
        this.dailyKpiWatermarkRepository = dailyKpiWatermarkRepository;
    }

    @Transactional
    public void advance(String userId, LocalDate metricDate, OffsetDateTime updatedAt) {
        DailyKpiWatermark watermark = dailyKpiWatermarkRepository.findByPipelineKeyAndUserId(
                        DAILY_KPI_PIPELINE_KEY,
                        userId
                )
                .map(existing -> {
                    existing.advance(metricDate, updatedAt);
                    return existing;
                })
                .orElseGet(() -> DailyKpiWatermark.create(
                        DAILY_KPI_PIPELINE_KEY,
                        userId,
                        metricDate,
                        updatedAt
                ));

        dailyKpiWatermarkRepository.save(watermark);
    }

    public DailyKpiWatermarkResponse get(String userId) {
        return dailyKpiWatermarkRepository.findByPipelineKeyAndUserId(DAILY_KPI_PIPELINE_KEY, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of(
                                "pipelineKey", DAILY_KPI_PIPELINE_KEY,
                                "userId", userId
                        )
                ))
                .toResponse();
    }
}
