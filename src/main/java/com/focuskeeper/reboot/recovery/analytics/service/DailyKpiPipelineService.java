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
/**
 * recovery domain의 raw event를 읽어 일간 KPI mart 한 행을 계산하는 핵심 파이프라인 서비스다.
 *
 * 세션, 실패, 재시작, 타임박스 데이터를 KPI 관점으로 다시 묶고,
 * 계산 결과를 저장한 뒤 품질 리포트와 lastProcessedDate까지 같은 흐름 안에서 갱신한다.
 */
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
    private final DailyKpiLastProcessedDateService dailyKpiLastProcessedDateService;
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
            DailyKpiLastProcessedDateService dailyKpiLastProcessedDateService,
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
        this.dailyKpiLastProcessedDateService = dailyKpiLastProcessedDateService;
        this.operationsMetricRecorder = operationsMetricRecorder;
    }

    /**
     * 원천 실행 이벤트를 읽어 사용자의 일간 KPI를 계산하고, mart 저장 후 품질 리포트와 lastProcessedDate까지 갱신한다.
     * T. Extract 단계
     * A. 맞다. 여기서는 metricDate 기준으로 원천 이벤트와 계획 데이터를 먼저 읽어오는 Extract 역할을 한다.
     *    다만 메소드 전체는 Extract만 하는 게 아니라, 이후 Transform/Load까지 이어지는 파이프라인 오케스트레이션이다.
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
            // T. 세션 / 실패 / 재시작 log들을 metricDate에서 정한 날부터 시작해서 하루, 재시작은 48시간 이후까지 가져와 측정한다 (metricDate를 뭐로 이해해야할지)
            // A. 맞다. metricDate는 "KPI를 계산할 기준 날짜"다. 세션/실패는 그날 KST 00:00~24:00 범위만 보고,
            //    재시작은 failure 이후 24/48시간 회복 여부를 계산해야 하므로 기준일 다음날 이후까지 더 넓게 읽는다.
            List<Timebox> dailyTimeboxes = timeboxRepository.findAllByUserIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                    userId,
                    periodStart,
                    periodEndExclusive
            ); // Q. 얘는 어떤 TimeBoxes들을 들고오는건지?
            // A. metricDate 하루 안에 시작하는 해당 사용자의 계획 timebox들이다.
            //    이 중 WORK timebox만 골라 계획 대비 실행률과 계획 작업 시간을 계산하는 기준으로 쓴다.

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
            // [1] KPI 수치 집계 및 마트 구축 (generateMetric)
            // 조회해 온 Session, Failure 등의 이벤트를 바탕으로 KPI 수치(성공률, 24시간 내 재시작 횟수 등)를 계산하고
            // 이를 DB에 Upsert (DailyKpiMetricUpsertJdbcRepository) 하여 일간 마트를 갱신합니다.
            generateMetric(
                    userId,
                    metricDate,
                    sessions,
                    failures,
                    indexRestartsByFailureEventId(restarts),
                    plannedWorkTimeboxes,
                    generatedAt
            );

            // [2] 데이터 논리적 무결성 및 품질(DQ) 검사 (generateFromLoadedRaw)
            // KPI 집계에 사용된 원천 데이터(raw slice)를 DB에서 또 조회(N+1)하지 않기 위해 메모리 객체를 그대로 넘겨 재사용합니다.
            // 이벤트 간의 시간적 모순이나 유실 등 논리적 정합성을 검증하여 품질 마트를 함께 갱신합니다.
            dailyKpiQualityService.generateFromLoadedRaw(
                    userId,
                    metricDate,
                    generatedAt,
                    sessions,
                    failures,
                    dailyRestarts,
                    dailyTimeboxesById
            );

            // [3] 워터마크(Watermark) 전진 (advance)
            // 마트 저장 및 품질 검사가 모두 성공적으로 끝났을 때만 마지막 처리 기준점을 전진시킵니다.
            // 예외가 발생해 트랜잭션이 롤백되면 이 코드가 확정되지 않으므로, 다음 재시도(Retry) 시 데이터가 누락되지 않고 안전하게 재실행됩니다.
            // T. 현재시간 기준으로 lpd를 밀어줌 (왜 ? 나중에 backfill같은거 할 때, 날짜가 안밀려있으면 동기화하거나 하는데 문제 생긴다? 이걸 뭐라 풀어써야할지..)
            // A. lpd는 현재시간이 아니라 "성공적으로 처리한 metricDate"까지 전진한다.
            //    updatedAt은 그 처리가 언제 반영됐는지 남기는 갱신 시각이고, lastProcessedDate는 재시도/백필/운영 lag 판단의 기준점이다.
            dailyKpiLastProcessedDateService.advance(userId, metricDate, generatedAt);
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

    /**
     * 이미 읽어온 raw slice를 바탕으로 KPI 숫자 자체만 계산해 mart 저장까지 수행한다.
     *
     * 외부에서 동일 raw 데이터를 재사용할 수 있게 조회와 계산을 분리해둔 내부 메소드다.
     * T. Transform 단계
     * A. 맞다. 이미 읽어온 raw slice를 KPI 지표 값으로 바꾸는 Transform 단계다.
     *    마지막에 persistMetric을 호출하므로 좁게는 Transform, 넓게는 작은 Transform+Load 단위로 볼 수 있다.
     */
    void generateMetric(
            String userId,
            LocalDate metricDate,
            List<SessionSlice> sessions,
            List<FailureSlice> failures,
            Map<String, List<RestartSlice>> restartByFailureEventId,
            List<Timebox> plannedWorkTimeboxes,
            OffsetDateTime generatedAt
    ) {
        int sessionStartedCount = sessions.size(); // 세션 시작 count
        int sessionCompletedCount = (int) sessions.stream()
                .filter(session -> session.getStatus() == RecoverySessionStatus.COMPLETED)
                .count(); // 세션 완수 count
        boolean activation = sessionStartedCount > 0;
        // Q. 그냥 이 사람이 timebox를 활성화했는지 물어보는건가?
        // A. 거의 맞다. 여기서 activation은 해당 날짜에 사용자가 최소 1개의 recovery session을 시작했는지,
        //    즉 계획만 세운 상태를 넘어 실제 실행 흐름에 진입했는지를 나타내는 KPI 플래그다.

        int failureCount = failures.size(); // 실패 count
        int restartCount24 = 0;
        int restartCount48 = 0;
        boolean recovery24 = false;
        boolean recovery48 = false;
        // 24/48시간 이내에 재시작했는가?
        Long ttrMinutes = null;
        // Q. ttr이 뭘 의미하는지 처음 읽었을 때 이해할 수 있는가 ..?
        // A. 처음 보면 약어라 모호하다. 여기서는 Time To Restart/Recovery에 가까운 값으로,
        //    failure 발생 후 첫 restart까지 걸린 시간을 분 단위로 저장한다는 설명이 같이 있어야 읽힌다.

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

            // Q. 윈도우별로 잘라본다는게 24/48시간으로 나눠본다는건가?
            // A. 맞다. 각 failure를 기준으로 이후 24시간, 48시간 안에 연결된 restart가 있었는지 나눠 본다는 뜻이다.
            //    그래서 recovery24/recovery48과 restartCount24/restartCount48을 따로 계산한다.

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

        // T. Session 시작대비 얼마나 완수했는지에 대한 비율 (1에 가까울 수록 완수율이 높다 ..)
        // A. 맞다. 시작한 session 중 COMPLETED 상태까지 간 비율이라, 사용자가 시작한 실행 사이클을 얼마나 끝냈는지 보는 지표다.
        BigDecimal cycleCompletionRate = ratio(sessionCompletedCount, sessionStartedCount);


        // T. 시작'된' timebox들에 대해 중복없이 가져온다.
        // A. 맞다. 같은 timebox에서 session이 여러 번 열릴 수 있으므로, planExecutionRate는 중복 session 수가 아니라 distinct timebox 수로 본다.
        Set<String> startedTimeboxIds = sessions.stream()
                .map(SessionSlice::getTimeboxId)
                .collect(Collectors.toSet());
        // 한 timebox에서 세션이 여러 번 생겨도 "계획한 블록을 실제로 시작했는가"만 보도록 distinct timebox 기준으로 계산한다.
        BigDecimal planExecutionRate = ratio(startedTimeboxIds.size(), plannedWorkTimeboxes.size());

        // 계획된 시간은 몇시간인지? Q. (minute 기준?)
        // A. 맞다. 이 값은 시간(hour)이 아니라 minute 기준이다. WORK timebox들의 startAt~endAt duration을 분으로 합산한다.
        long plannedWorkMinutes = plannedWorkTimeboxes.stream()
                .mapToLong(timebox -> Duration.between(timebox.getStartAt(), timebox.getEndAt()).toMinutes())
                .sum();
        // 실제 몇 시간정도 수행했는지
        long actualWorkMinutes = sessions.stream()
                .filter(session -> session.getEndedAt() != null)
                .mapToLong(session -> Duration.between(session.getStartedAt(), session.getEndedAt()).toMinutes())
                .sum();
        // 계획 시간과 실제 세션 종료 시간 합계를 비교해 "얼마나 빗나갔는지"만 절대값으로 남긴다.
        long estimationErrorMinutes = Math.abs(plannedWorkMinutes - actualWorkMinutes);

        // T. 얘를 또 보내는데, 그냥 책임을 나눈거나 다름없다고 보면 될듯? 얘는 정제하는 역할이고, persistMetric은 적재하는 책임이고
        // A. 맞다. generateMetric은 계산/정제 책임이고, persistMetric은 DB별 저장 방식(JDBC upsert/JPA save)을 숨기는 적재 책임이다.
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

    /**
     * 재시작 이벤트를 failureEventId 기준으로 그룹화해 failure별 recovery window 계산을 빠르게 한다.
     */
    private Map<String, List<RestartSlice>> indexRestartsByFailureEventId(List<RestartSlice> restarts) {
        return restarts.stream()
                .collect(Collectors.groupingBy(RestartSlice::getFailureEventId));
    }

    /**
     * 원천 이벤트의 OffsetDateTime을 KPI 기준일(LocalDate)로 정규화한다.
     *
     * 시스템 전반이 KST 하루 단위 KPI를 전제로 하기 때문에, raw event의 offset이 달라도 같은 기준으로 잘라낸다.
     */
    private LocalDate normalizeMetricDate(OffsetDateTime timestamp) {
        return timestamp.withOffsetSameInstant(DEFAULT_OFFSET).toLocalDate();
    }

    /**
     * 계산된 KPI 값을 저장소 특성에 맞는 방식으로 반영한다.
     *
     * PostgreSQL 런타임이면 native upsert를 사용해 자연키 충돌을 DB가 처리하게 하고,
     * 그 외 환경에서는 JPA 조회 후 regenerate/save 경로로 동일한 의미를 맞춘다.
     */
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
        /*
         * Upsert 도입 전 JPA-only 저장 흐름:
         *
         * dailyKpiMetricRepository.findByUserIdAndMetricDate(userId, metricDate)
         *         .ifPresentOrElse(
         *                 existing -> {
         *                     existing.regenerate(
         *                             activation,
         *                             failureCount,
         *                             recovery24,
         *                             recovery48,
         *                             restartCount24,
         *                             restartCount48,
         *                             ttrMinutes,
         *                             cycleCompletionRate,
         *                             planExecutionRate,
         *                             plannedWorkMinutes,
         *                             actualWorkMinutes,
         *                             estimationErrorMinutes,
         *                             generatedAt
         *                     );
         *                     dailyKpiMetricRepository.save(existing);
         *                 },
         *                 () -> dailyKpiMetricRepository.save(
         *                         DailyKpiMetric.create(
         *                                 userId,
         *                                 metricDate,
         *                                 activation,
         *                                 failureCount,
         *                                 recovery24,
         *                                 recovery48,
         *                                 restartCount24,
         *                                 restartCount48,
         *                                 ttrMinutes,
         *                                 cycleCompletionRate,
         *                                 planExecutionRate,
         *                                 plannedWorkMinutes,
         *                                 actualWorkMinutes,
         *                                 estimationErrorMinutes,
         *                                 generatedAt
         *                         )
         *                 )
         *         );
         */
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
