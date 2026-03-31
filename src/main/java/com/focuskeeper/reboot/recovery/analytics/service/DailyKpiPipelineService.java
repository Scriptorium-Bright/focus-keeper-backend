package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.common.persistence.DatabaseDialectResolver;
import com.focuskeeper.reboot.common.observability.OperationsMetricRecorder;
import com.focuskeeper.reboot.common.observability.OperationsPipelineKeys;
import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiMetric;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiMetricRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiMetricUpsertJdbcRepository;
import com.focuskeeper.reboot.recovery.execution.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository.FailureSlice;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository.SessionSlice;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository.RestartSlice;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import io.micrometer.core.instrument.Timer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DailyKpiPipelineService {

    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(9);

    private final DatabaseDialectResolver databaseDialectResolver;
    private final DailyKpiMetricRepository dailyKpiMetricRepository;
    private final DailyKpiMetricUpsertJdbcRepository dailyKpiMetricUpsertJdbcRepository;
    private final RecoverySessionRepository recoverySessionRepository;
    private final FailureEventRepository failureEventRepository;
    private final RestartEventRepository restartEventRepository;
    private final TimeboxRepository timeboxRepository;
    private final DailyKpiQualityService dailyKpiQualityService;
    private final DailyKpiWatermarkService dailyKpiWatermarkService;
    private final OperationsMetricRecorder operationsMetricRecorder;

    public DailyKpiPipelineService(
            DatabaseDialectResolver databaseDialectResolver,
            DailyKpiMetricRepository dailyKpiMetricRepository,
            DailyKpiMetricUpsertJdbcRepository dailyKpiMetricUpsertJdbcRepository,
            RecoverySessionRepository recoverySessionRepository,
            FailureEventRepository failureEventRepository,
            RestartEventRepository restartEventRepository,
            TimeboxRepository timeboxRepository,
            DailyKpiQualityService dailyKpiQualityService,
            DailyKpiWatermarkService dailyKpiWatermarkService,
            OperationsMetricRecorder operationsMetricRecorder
    ) {
        this.databaseDialectResolver = databaseDialectResolver;
        this.dailyKpiMetricRepository = dailyKpiMetricRepository;
        this.dailyKpiMetricUpsertJdbcRepository = dailyKpiMetricUpsertJdbcRepository;
        this.recoverySessionRepository = recoverySessionRepository;
        this.failureEventRepository = failureEventRepository;
        this.restartEventRepository = restartEventRepository;
        this.timeboxRepository = timeboxRepository;
        this.dailyKpiQualityService = dailyKpiQualityService;
        this.dailyKpiWatermarkService = dailyKpiWatermarkService;
        this.operationsMetricRecorder = operationsMetricRecorder;
    }

    /**
     * 원천 실행 이벤트를 읽어 사용자의 일간 KPI를 계산하고, mart 저장 후 품질 리포트와 워터마크까지 갱신한다.
     */
    public void generate(String userId, LocalDate metricDate) {
        Timer.Sample sample = operationsMetricRecorder.startSample();
        try {
            // KPI 기준일은 KST 하루 범위로 자르고, 재시작은 Recovery48 계산을 위해 48시간 뒤까지 함께 읽는다.
            OffsetDateTime periodStart = metricDate.atStartOfDay().atOffset(DEFAULT_OFFSET);
            OffsetDateTime periodEndExclusive = metricDate.plusDays(1).atStartOfDay().atOffset(DEFAULT_OFFSET);
            OffsetDateTime restartEndExclusive = periodEndExclusive.plusHours(48);

            List<SessionSlice> sessions = recoverySessionRepository.findSlicesByUserIdAndStartedAtBetween(
                    userId,
                    periodStart,
                    periodEndExclusive
            );
            List<FailureSlice> failures = failureEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                    userId,
                    periodStart,
                    periodEndExclusive
            );
            List<RestartSlice> restarts = restartEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                    userId,
                    periodStart,
                    restartEndExclusive
            );
            List<Timebox> dailyTimeboxes = timeboxRepository.findAllByUserIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                    userId,
                    periodStart,
                    periodEndExclusive
            );
            // 계획 대비 실행률은 휴식 블록을 제외한 실제 work timebox만 기준으로 본다.
            List<Timebox> plannedWorkTimeboxes = dailyTimeboxes.stream()
                    .filter(timebox -> timebox.getType() == TimeboxType.WORK)
                    .toList();
            List<RestartSlice> dailyRestarts = restarts.stream()
                    .filter(restart -> normalizeMetricDate(restart.getOccurredAt()).equals(metricDate))
                    .toList();
            Map<String, Timebox> dailyTimeboxesById = dailyTimeboxes.stream()
                    .collect(Collectors.toMap(Timebox::getId, Function.identity()));

            OffsetDateTime generatedAt = OffsetDateTime.now();
            generateMetric(
                    userId,
                    metricDate,
                    sessions,
                    failures,
                    indexRestartsByFailureEventId(restarts),
                    plannedWorkTimeboxes,
                    generatedAt
            );
            // generate가 이미 읽은 raw slice를 quality에도 재사용해 같은 날짜 범위를 다시 조회하지 않게 한다.
            dailyKpiQualityService.generateFromLoadedRaw(
                    userId,
                    metricDate,
                    generatedAt,
                    sessions,
                    failures,
                    dailyRestarts,
                    dailyTimeboxesById
            );
            // mart 저장 직후 같은 generatedAt으로 품질 리포트와 watermark를 갱신해 한 번의 생성 배치로 묶는다.
            dailyKpiWatermarkService.advance(userId, metricDate, generatedAt);
            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "generate",
                    "success"
            );
        } catch (RuntimeException exception) {
            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "generate",
                    "failure"
            );
            throw exception;
        }
    }

    void generateMetric(
            String userId,
            LocalDate metricDate,
            List<SessionSlice> sessions,
            List<FailureSlice> failures,
            Map<String, List<RestartSlice>> restartByFailureEventId,
            List<Timebox> plannedWorkTimeboxes,
            OffsetDateTime generatedAt
    ) {
        int sessionStartedCount = sessions.size();
        int sessionCompletedCount = (int) sessions.stream()
                .filter(session -> session.getStatus() == RecoverySessionStatus.COMPLETED)
                .count();
        boolean activation = sessionStartedCount > 0;

        int failureCount = failures.size();
        int restartCount24 = 0;
        int restartCount48 = 0;
        boolean recovery24 = false;
        boolean recovery48 = false;
        Long ttrMinutes = null;

        for (FailureSlice failure : failures) {
            // 하나의 failure에 restart가 여러 번 붙을 수 있으므로 시간순으로 정렬한 뒤 윈도우별로 잘라 본다.
            List<RestartSlice> linkedRestarts = restartByFailureEventId
                    .getOrDefault(failure.getFailureEventId(), List.of())
                    .stream()
                    .sorted(Comparator.comparing(RestartSlice::getOccurredAt))
                    .toList();

            List<RestartSlice> within24 = linkedRestarts.stream()
                    .filter(restart -> !restart.getOccurredAt().isAfter(failure.getOccurredAt().plusHours(24)))
                    .toList();
            List<RestartSlice> within48 = linkedRestarts.stream()
                    .filter(restart -> !restart.getOccurredAt().isAfter(failure.getOccurredAt().plusHours(48)))
                    .toList();

            restartCount24 += within24.size();
            restartCount48 += within48.size();
            recovery24 = recovery24 || !within24.isEmpty();
            recovery48 = recovery48 || !within48.isEmpty();

            if (!within48.isEmpty()) {
                // TTR은 각 failure마다 "가장 처음 돌아온 restart"를 보고, 일 단위 KPI에는 그중 최소값을 남긴다.
                long candidateTtr = Duration.between(
                        failure.getOccurredAt(),
                        within48.getFirst().getOccurredAt()
                ).toMinutes();
                ttrMinutes = ttrMinutes == null ? candidateTtr : Math.min(ttrMinutes, candidateTtr);
            }
        }

        BigDecimal cycleCompletionRate = ratio(sessionCompletedCount, sessionStartedCount);

        Set<String> startedTimeboxIds = sessions.stream()
                .map(SessionSlice::getTimeboxId)
                .collect(Collectors.toSet());
        // 한 timebox에서 세션이 여러 번 생겨도 "계획한 블록을 실제로 시작했는가"만 보도록 distinct timebox 기준으로 계산한다.
        BigDecimal planExecutionRate = ratio(startedTimeboxIds.size(), plannedWorkTimeboxes.size());

        long plannedWorkMinutes = plannedWorkTimeboxes.stream()
                .mapToLong(timebox -> Duration.between(timebox.getStartAt(), timebox.getEndAt()).toMinutes())
                .sum();
        long actualWorkMinutes = sessions.stream()
                .filter(session -> session.getEndedAt() != null)
                .mapToLong(session -> Duration.between(session.getStartedAt(), session.getEndedAt()).toMinutes())
                .sum();
        // 계획 시간과 실제 세션 종료 시간 합계를 비교해 "얼마나 빗나갔는지"만 절대값으로 남긴다.
        long estimationErrorMinutes = Math.abs(plannedWorkMinutes - actualWorkMinutes);

        persistMetric(
                userId,
                metricDate,
                activation,
                failureCount,
                recovery24,
                recovery48,
                restartCount24,
                restartCount48,
                ttrMinutes,
                cycleCompletionRate,
                planExecutionRate,
                plannedWorkMinutes,
                actualWorkMinutes,
                estimationErrorMinutes,
                generatedAt
        );
    }

    private Map<String, List<RestartSlice>> indexRestartsByFailureEventId(List<RestartSlice> restarts) {
        return restarts.stream()
                .collect(Collectors.groupingBy(RestartSlice::getFailureEventId));
    }

    private LocalDate normalizeMetricDate(OffsetDateTime timestamp) {
        return timestamp.withOffsetSameInstant(DEFAULT_OFFSET).toLocalDate();
    }

    private void persistMetric(
            String userId,
            LocalDate metricDate,
            boolean activation,
            int failureCount,
            boolean recovery24,
            boolean recovery48,
            int restartCount24,
            int restartCount48,
            Long ttrMinutes,
            BigDecimal cycleCompletionRate,
            BigDecimal planExecutionRate,
            long plannedWorkMinutes,
            long actualWorkMinutes,
            long estimationErrorMinutes,
            OffsetDateTime generatedAt
    ) {
        if (databaseDialectResolver.isPostgreSql()) {
            dailyKpiMetricUpsertJdbcRepository.upsert(
                    userId,
                    metricDate,
                    activation,
                    failureCount,
                    recovery24,
                    recovery48,
                    restartCount24,
                    restartCount48,
                    ttrMinutes,
                    cycleCompletionRate,
                    planExecutionRate,
                    plannedWorkMinutes,
                    actualWorkMinutes,
                    estimationErrorMinutes,
                    generatedAt
            );
            return;
        }

        dailyKpiMetricRepository.findByUserIdAndMetricDate(userId, metricDate)
                .ifPresentOrElse(
                        existing -> {
                            existing.regenerate(
                                    activation,
                                    failureCount,
                                    recovery24,
                                    recovery48,
                                    restartCount24,
                                    restartCount48,
                                    ttrMinutes,
                                    cycleCompletionRate,
                                    planExecutionRate,
                                    plannedWorkMinutes,
                                    actualWorkMinutes,
                                    estimationErrorMinutes,
                                    generatedAt
                            );
                            dailyKpiMetricRepository.save(existing);
                        },
                        () -> dailyKpiMetricRepository.save(
                                DailyKpiMetric.create(
                                        userId,
                                        metricDate,
                                        activation,
                                        failureCount,
                                        recovery24,
                                        recovery48,
                                        restartCount24,
                                        restartCount48,
                                        ttrMinutes,
                                        cycleCompletionRate,
                                        planExecutionRate,
                                        plannedWorkMinutes,
                                        actualWorkMinutes,
                                        estimationErrorMinutes,
                                        generatedAt
                                )
                        )
                );
    }

    /**
     * 분모가 0인 경우를 포함해 KPI 비율 값을 소수점 넷째 자리까지 계산한다.
     */
    private BigDecimal ratio(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }
}
