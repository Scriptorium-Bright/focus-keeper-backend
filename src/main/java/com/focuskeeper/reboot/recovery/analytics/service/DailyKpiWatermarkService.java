package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.common.persistence.DatabaseDialectResolver;
import com.focuskeeper.reboot.common.observability.OperationsAlertService;
import com.focuskeeper.reboot.common.observability.OperationsMetricRecorder;
import com.focuskeeper.reboot.common.observability.OperationsPipelineKeys;
import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiWatermarkResponse;
import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiWatermark;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiWatermarkRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiWatermarkUpsertJdbcRepository;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DailyKpiWatermarkService {

    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(9);

    private final DatabaseDialectResolver databaseDialectResolver;
    private final DailyKpiWatermarkRepository dailyKpiWatermarkRepository;
    private final DailyKpiWatermarkUpsertJdbcRepository dailyKpiWatermarkUpsertJdbcRepository;
    private final OperationsMetricRecorder operationsMetricRecorder;
    private final OperationsAlertService operationsAlertService;

    public DailyKpiWatermarkService(
            DatabaseDialectResolver databaseDialectResolver,
            DailyKpiWatermarkRepository dailyKpiWatermarkRepository,
            DailyKpiWatermarkUpsertJdbcRepository dailyKpiWatermarkUpsertJdbcRepository,
            OperationsMetricRecorder operationsMetricRecorder,
            OperationsAlertService operationsAlertService
    ) {
        this.databaseDialectResolver = databaseDialectResolver;
        this.dailyKpiWatermarkRepository = dailyKpiWatermarkRepository;
        this.dailyKpiWatermarkUpsertJdbcRepository = dailyKpiWatermarkUpsertJdbcRepository;
        this.operationsMetricRecorder = operationsMetricRecorder;
        this.operationsAlertService = operationsAlertService;
    }

    /**
     * 일간 KPI 파이프라인이 마지막으로 처리한 날짜를 저장하거나 앞으로 전진시킨다.
     */
    @Transactional
    public void advance(String userId, LocalDate metricDate, OffsetDateTime updatedAt) {
        Timer.Sample sample = operationsMetricRecorder.startSample();
        try {
            persistWatermark(userId, metricDate, updatedAt);
            publishWatermark(userId, metricDate);
            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "watermark_advance",
                    "success"
            );
            operationsAlertService.resolveBatchFailure(
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "watermark_advance",
                    userId,
                    "Daily KPI watermark advanced successfully.",
                    Map.of("metricDate", metricDate.toString())
            );
        } catch (RuntimeException exception) {
            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "watermark_advance",
                    "failure"
            );
            operationsAlertService.reportBatchFailure(
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "watermark_advance",
                    userId,
                    "Failed to advance daily KPI watermark.",
                    Map.of(
                            "metricDate", metricDate.toString(),
                            "error", exception.getClass().getSimpleName()
                    )
            );
            throw exception;
        }
    }

    private void persistWatermark(String userId, LocalDate metricDate, OffsetDateTime updatedAt) {
        if (databaseDialectResolver.isPostgreSql()) {
            dailyKpiWatermarkUpsertJdbcRepository.upsert(
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    userId,
                    metricDate,
                    updatedAt
            );
            return;
        }

        DailyKpiWatermark watermark = dailyKpiWatermarkRepository.findByPipelineKeyAndUserId(
                        OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                        userId
                )
                .map(existing -> {
                    existing.advance(metricDate, updatedAt);
                    return existing;
                })
                .orElseGet(() -> DailyKpiWatermark.create(
                        OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                        userId,
                        metricDate,
                        updatedAt
                ));

        dailyKpiWatermarkRepository.save(watermark);
    }

    /**
     * 사용자 기준 일간 KPI 파이프라인 워터마크를 조회한다.
     */
    public DailyKpiWatermarkResponse get(String userId) {
        DailyKpiWatermarkResponse response = dailyKpiWatermarkRepository.findByPipelineKeyAndUserId(
                        OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                        userId
                )
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of(
                                "pipelineKey", OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                                "userId", userId
                        )
                ))
                .toResponse();
        publishWatermark(userId, LocalDate.parse(response.lastProcessedDate()));
        return response;
    }

    private void publishWatermark(String userId, LocalDate lastProcessedDate) {
        long lagDays = Math.max(ChronoUnit.DAYS.between(lastProcessedDate, LocalDate.now(DEFAULT_OFFSET)), 0);
        operationsMetricRecorder.recordWatermarkLagSeconds(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                userId,
                lagDays * 86400
        );
        operationsAlertService.evaluateWatermarkLag(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                userId,
                lastProcessedDate
        );
    }
}
