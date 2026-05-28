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
     *
     * Q. 급 궁금해진게, pipelineKey는 특정 pipeline에 대한 key인가? 특정 pipeline이라는것도 이상하긴 한데 . . 갑자기 헷갈림
     * A. 맞다. pipelineKey는 "어떤 처리 흐름의 진행 상태인가"를 구분하는 운영용 식별자다.
     *    여기서는 DAILY_KPI_PIPELINE 한 종류만 저장하지만, 같은 사용자라도 failure-hour, friction-signal처럼
     *    서로 다른 파이프라인의 lastProcessedDate를 따로 관리하려면 pipelineKey + userId가 자연키가 된다.
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
     *
     * Q. 이 부분 좀 설명 다시 좀.. lag도 갑자기 헷갈리고 alert도 헷갈리네
     * A. get은 단순 조회처럼 보이지만, 조회한 lastProcessedDate를 운영 신호로도 다시 발행한다.
     *    lag는 "오늘 기준으로 파이프라인 처리가 며칠 밀렸는가"이고, alert는 그 lag가 허용 수준을 넘었을 때
     *    운영자가 알아볼 수 있게 상태를 열거나 닫는 판단이다. 즉 DB 상태값을 운영 모니터링 값으로 투영하는 단계다.
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
        // Q. ChronoUnit 이건 뭐임?
        // A. java.time에서 날짜/시간 단위 차이를 계산하는 enum이다.
        //    여기서는 ChronoUnit.DAYS.between(A, B)로 lastProcessedDate부터 오늘까지 며칠 차이인지 계산한다.
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
