package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.common.persistence.DatabaseDialectResolver;
import com.focuskeeper.reboot.common.observability.OperationsAlertService;
import com.focuskeeper.reboot.common.observability.OperationsMetricRecorder;
import com.focuskeeper.reboot.common.observability.OperationsPipelineKeys;
import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiLastProcessedDateResponse;
import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiLastProcessedDate;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiLastProcessedDateRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiLastProcessedDateUpsertJdbcRepository;
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
/**
 * 일간 KPI 파이프라인의 마지막 처리 날짜를 관리하는 서비스다.
 *
 * 이 서비스는 단순 조회뿐 아니라, monotonic update 규칙을 적용해
 * 과거 날짜 backfill 때문에 처리 기준점이 뒤로 가지 않도록 보장한다.
 */
public class DailyKpiLastProcessedDateService {

    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(9);

    private final DatabaseDialectResolver databaseDialectResolver;
    private final DailyKpiLastProcessedDateRepository dailyKpiLastProcessedDateRepository;
    private final DailyKpiLastProcessedDateUpsertJdbcRepository dailyKpiLastProcessedDateUpsertJdbcRepository;
    private final OperationsMetricRecorder operationsMetricRecorder;
    private final OperationsAlertService operationsAlertService;

    public DailyKpiLastProcessedDateService(
            DatabaseDialectResolver databaseDialectResolver,
            DailyKpiLastProcessedDateRepository dailyKpiLastProcessedDateRepository,
            DailyKpiLastProcessedDateUpsertJdbcRepository dailyKpiLastProcessedDateUpsertJdbcRepository,
            OperationsMetricRecorder operationsMetricRecorder,
            OperationsAlertService operationsAlertService
    ) {
        this.databaseDialectResolver = databaseDialectResolver;
        this.dailyKpiLastProcessedDateRepository = dailyKpiLastProcessedDateRepository;
        this.dailyKpiLastProcessedDateUpsertJdbcRepository = dailyKpiLastProcessedDateUpsertJdbcRepository;
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
            persistLastProcessedDate(userId, metricDate, updatedAt);
            publishLastProcessedDate(userId, metricDate);
            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "last_processed_date_advance",
                    "success"
            );
            operationsAlertService.resolveBatchFailure(
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "last_processed_date_advance",
                    userId,
                    "Daily KPI last processed date advanced successfully.",
                    Map.of("metricDate", metricDate.toString())
            );
        } catch (RuntimeException exception) {
            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "last_processed_date_advance",
                    "failure"
            );
            operationsAlertService.reportBatchFailure(
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "last_processed_date_advance",
                    userId,
                    "Failed to advance daily KPI last processed date.",
                    Map.of(
                            "metricDate", metricDate.toString(),
                            "error", exception.getClass().getSimpleName()
                    )
            );
            throw exception;
        }
    }

    /**
     * 저장소 구현체 차이를 숨기고 lastProcessedDate를 저장한다.
     *
     * PostgreSQL이면 monotonic upsert를 우선 사용하고,
     * 그 외 런타임에서는 JPA 조회 후 advance/save 흐름으로 동일한 의미를 맞춘다.
     */
    private void persistLastProcessedDate(String userId, LocalDate metricDate, OffsetDateTime updatedAt) {
        /*
         * Upsert 도입 전 JPA-only 저장 흐름:
         *
         * DailyKpiLastProcessedDate lastProcessedDateRecord = dailyKpiLastProcessedDateRepository.findByPipelineKeyAndUserId(
         *                 OperationsPipelineKeys.DAILY_KPI_PIPELINE,
         *                 userId
         *         )
         *         .map(existing -> {
         *             existing.advance(metricDate, updatedAt);
         *             return existing;
         *         })
         *         .orElseGet(() -> DailyKpiLastProcessedDate.create(
         *                 OperationsPipelineKeys.DAILY_KPI_PIPELINE,
         *                 userId,
         *                 metricDate,
         *                 updatedAt
         *         ));
         *
         * dailyKpiLastProcessedDateRepository.save(lastProcessedDateRecord);
         */
        if (databaseDialectResolver.isPostgreSql()) {
            dailyKpiLastProcessedDateUpsertJdbcRepository.upsert(
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    userId,
                    metricDate,
                    updatedAt
            );
            return;
        }

        DailyKpiLastProcessedDate lastProcessedDateRecord = dailyKpiLastProcessedDateRepository.findByPipelineKeyAndUserId(
                        OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                        userId
                )
                .map(existing -> {
                    existing.advance(metricDate, updatedAt);
                    return existing;
                })
                .orElseGet(() -> DailyKpiLastProcessedDate.create(
                        OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                        userId,
                        metricDate,
                        updatedAt
                ));

        dailyKpiLastProcessedDateRepository.save(lastProcessedDateRecord);
    }

    /**
     * 사용자 기준 일간 KPI 파이프라인의 마지막 처리 날짜를 조회한다.
     *
     * 조회 시점에도 processing lag 메트릭과 alert를 함께 갱신해
     * 운영 화면에서 보는 값과 메트릭 저장소의 값이 크게 벌어지지 않게 한다.
     */
    public DailyKpiLastProcessedDateResponse get(String userId) {
        DailyKpiLastProcessedDateResponse response = dailyKpiLastProcessedDateRepository.findByPipelineKeyAndUserId(
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
        publishLastProcessedDate(userId, LocalDate.parse(response.lastProcessedDate()));
        return response;
    }

    /**
     * 마지막 처리 날짜를 운영 메트릭과 lag alert로 투영한다.
     *
     * 엔티티 row 자체는 DB에 남기고, 운영 계층에서는 "오늘 기준 며칠 밀렸는가"만 숫자형 신호로 본다.
     */
    private void publishLastProcessedDate(String userId, LocalDate lastProcessedDate) {
        long lagDays = Math.max(ChronoUnit.DAYS.between(lastProcessedDate, LocalDate.now(DEFAULT_OFFSET)), 0);
        operationsMetricRecorder.recordProcessingLagSeconds(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                userId,
                lagDays * 86400
        );
        operationsAlertService.evaluateProcessingLag(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                userId,
                lastProcessedDate
        );
    }
}
