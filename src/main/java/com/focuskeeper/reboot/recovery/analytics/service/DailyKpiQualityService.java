package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.common.observability.OperationsAlertService;
import com.focuskeeper.reboot.common.observability.OperationsMetricRecorder;
import com.focuskeeper.reboot.common.observability.OperationsPipelineKeys;
import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiQualityReport;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiQualityReportRepository;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository.FailureReference;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository.FailureSlice;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository.SessionSlice;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository.RestartSlice;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
/**
 * KPI 계산에 사용된 raw event들의 참조 일관성과 시간 규칙을 점검해 품질 리포트를 생성하는 서비스다.
 *
 * KPI 값이 계산됐더라도 restart 연결, timebox 참조, timezone 정합성이 깨져 있으면
 * 운영 관점에서는 신뢰할 수 없는 지표가 되므로 별도의 DQ 결과를 유지한다.
 */
public class DailyKpiQualityService {

    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(9);

    private final DailyKpiQualityReportRepository dailyKpiQualityReportRepository;
    private final RecoverySessionRepository recoverySessionRepository;
    private final FailureEventRepository failureEventRepository;
    private final RestartEventRepository restartEventRepository;
    private final TimeboxRepository timeboxRepository;
    private final OperationsMetricRecorder operationsMetricRecorder;
    private final OperationsAlertService operationsAlertService;

    public DailyKpiQualityService(
            DailyKpiQualityReportRepository dailyKpiQualityReportRepository,
            RecoverySessionRepository recoverySessionRepository,
            FailureEventRepository failureEventRepository,
            RestartEventRepository restartEventRepository,
            TimeboxRepository timeboxRepository,
            OperationsMetricRecorder operationsMetricRecorder,
            OperationsAlertService operationsAlertService
    ) {
        this.dailyKpiQualityReportRepository = dailyKpiQualityReportRepository;
        this.recoverySessionRepository = recoverySessionRepository;
        this.failureEventRepository = failureEventRepository;
        this.restartEventRepository = restartEventRepository;
        this.timeboxRepository = timeboxRepository;
        this.operationsMetricRecorder = operationsMetricRecorder;
        this.operationsAlertService = operationsAlertService;
    }

    /**
     * KPI 계산에 사용된 세션, 실패, 재시작, 타임박스를 점검해 일간 데이터 품질 리포트를 생성하거나 갱신한다.
     */
    public void generate(String userId, LocalDate metricDate, OffsetDateTime generatedAt) {
        Timer.Sample sample = operationsMetricRecorder.startSample();
        try {
            OffsetDateTime periodStart = metricDate.atStartOfDay().atOffset(DEFAULT_OFFSET);
            OffsetDateTime periodEndExclusive = metricDate.plusDays(1).atStartOfDay().atOffset(DEFAULT_OFFSET);

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
                    periodEndExclusive
            );

            // pipeline이 없어도, raw data에 대한 quality 검증이 가능하다.
            generateFromLoadedRaw(
                    userId,
                    metricDate,
                    generatedAt,
                    sessions,
                    failures,
                    restarts,
                    loadTimeboxes(sessions)
            );
        } catch (RuntimeException exception) {
            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.DAILY_KPI_QUALITY,
                    "generate",
                    "failure"
            );
            operationsAlertService.reportBatchFailure(
                    OperationsPipelineKeys.DAILY_KPI_QUALITY,
                    "generate",
                    userId,
                    "Failed to generate daily KPI quality report.",
                    Map.of(
                            "metricDate", metricDate.toString(),
                            "error", exception.getClass().getSimpleName()
                    )
            );
            throw exception;
        }
    }

    /**
     * 이미 메모리에 적재된 raw slice를 재사용해 품질 리포트를 생성한다.
     *
     * KPI 계산 직후 같은 데이터를 이어서 검사할 때 추가 쿼리를 줄이기 위해 제공되는 내부 진입점이다.
     */
    void generateFromLoadedRaw(
            String userId,
            LocalDate metricDate,
            OffsetDateTime generatedAt,
            List<SessionSlice> sessions,
            List<FailureSlice> failures,
            List<RestartSlice> restarts,
            Map<String, Timebox> timeboxesById
    ) {
        generateFromSlices(
                    userId,
                    metricDate,
                    generatedAt,
                    sessions,
                    failures,
                    restarts,
                    timeboxesById,
                    loadFailureOccurredAtById(failures, restarts, userId)
            );
        // 하루치만을 위한 fromLoadedRaw , 장기를 위한 generateFromSlices
    }

    /**
     * 세션/실패/재시작/타임박스 slice를 직접 받아 품질 지표를 계산하고 리포트를 저장한다.
     *
     * 호출자는 데이터를 어떤 방식으로 읽어왔는지와 상관없이, 이 메소드에 규격화된 slice만 넘기면 된다.
     * upsert 전환대상
     */
    void generateFromSlices(
            String userId,
            LocalDate metricDate,
            OffsetDateTime generatedAt,
            List<SessionSlice> sessions,
            List<FailureSlice> failures,
            List<RestartSlice> restarts,
            Map<String, Timebox> timeboxesById,
            Map<String, OffsetDateTime> failureOccurredAtById
    ) {
        Timer.Sample sample = operationsMetricRecorder.startSample();
        try {
            RestartQualityStats restartStats = analyzeRestarts(restarts, failureOccurredAtById); // 재시작 이벤트 시간/참조 모순 검사 결과
            SessionQualityStats sessionStats = analyzeSessions(sessions, timeboxesById); // 세션 이벤트 논리적 모순 검사 결과
            int failureTimezoneMismatchCount = countFailureTimezoneMismatch(failures); // KST(한국기준) 타임존을 벗어난 실패 이벤트 수
            int timeboxTimezoneMismatchCount = countTimeboxTimezoneMismatch(timeboxesById.values()); // KST 타임존을 벗어난 타임박스 수

            int duplicateRestartLinkCount = restartStats.duplicateRestartLinkCount(); // 하나의 실패에 중복 연결된 비정상 재시작 수
            int orphanRestartCount = restartStats.orphanRestartCount(); // 부모(실패 이벤트)가 DB에 없는 고아 재시작 수
            int restartBeforeFailureCount = restartStats.restartBeforeFailureCount(); // 실패하기도 전에 발생한 재시작 수
            int lateRestartLinkCount = restartStats.lateRestartLinkCount(); // 48시간 유효기간이 한참 지나서 들어온 지각 재시작 수
            int breakSessionReferenceCount = sessionStats.breakSessionReferenceCount(); // 휴식(Break) 타임박스에 잘못 연결된 비정상 세션 수
            int missingTimeboxReferenceCount = sessionStats.missingTimeboxReferenceCount(); // 존재하지 않는 타임박스를 참조하는 유실 세션 수
            int timezoneMismatchCount = restartStats.timezoneMismatchCount()
                    + sessionStats.timezoneMismatchCount()
                    + failureTimezoneMismatchCount
                    + timeboxTimezoneMismatchCount; // 전체 이벤트 중 타임존이 오염된 총합
            int totalIssueCount = duplicateRestartLinkCount
                    + orphanRestartCount
                    + restartBeforeFailureCount
                    + lateRestartLinkCount
                    + breakSessionReferenceCount
                    + missingTimeboxReferenceCount
                    + timezoneMismatchCount; // 해당 날짜에 발견된 모든 데이터 품질(DQ) 결함의 총합
            boolean healthy = totalIssueCount == 0; // 결함이 단 하나도 없어야 건강함(true)으로 판정

            dailyKpiQualityReportRepository.findByUserIdAndMetricDate(userId, metricDate)
                    .map(existing -> {
                        existing.regenerate(
                                healthy,
                                duplicateRestartLinkCount,
                                orphanRestartCount,
                                restartBeforeFailureCount,
                                lateRestartLinkCount,
                                breakSessionReferenceCount,
                                missingTimeboxReferenceCount,
                                timezoneMismatchCount,
                                totalIssueCount,
                                generatedAt
                        );
                        return dailyKpiQualityReportRepository.save(existing);
                    })
                    .orElseGet(() -> dailyKpiQualityReportRepository.save(DailyKpiQualityReport.create(
                            userId,
                            metricDate,
                            healthy,
                            duplicateRestartLinkCount,
                            orphanRestartCount,
                            restartBeforeFailureCount,
                            lateRestartLinkCount,
                            breakSessionReferenceCount,
                            missingTimeboxReferenceCount,
                            timezoneMismatchCount,
                            totalIssueCount,
                            generatedAt
                    )));

            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.DAILY_KPI_QUALITY,
                    "generate",
                    "success"
            );
            operationsMetricRecorder.recordDqIssueCount(
                    OperationsPipelineKeys.DAILY_KPI_QUALITY,
                    userId,
                    totalIssueCount
            );
            operationsAlertService.resolveBatchFailure(
                    OperationsPipelineKeys.DAILY_KPI_QUALITY,
                    "generate",
                    userId,
                    "Daily KPI quality report generated successfully.",
                    Map.of("metricDate", metricDate.toString())
            );
            operationsAlertService.evaluateQuality(
                    OperationsPipelineKeys.DAILY_KPI_QUALITY,
                    userId,
                    metricDate,
                    totalIssueCount
            );
        } catch (RuntimeException exception) {
            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.DAILY_KPI_QUALITY,
                    "generate",
                    "failure"
            );
            operationsAlertService.reportBatchFailure(
                    OperationsPipelineKeys.DAILY_KPI_QUALITY,
                    "generate",
                    userId,
                    "Failed to generate daily KPI quality report.",
                    Map.of(
                            "metricDate", metricDate.toString(),
                            "error", exception.getClass().getSimpleName()
                    )
            );
            throw exception;
        }
    }

    /**
     * 세션이 참조한 타임박스들을 한 번에 조회해 timeboxId 기준 맵으로 만든다.
     */
    private Map<String, Timebox> loadTimeboxes(List<SessionSlice> sessions) {
        Set<String> timeboxIds = sessions.stream()
                .map(SessionSlice::getTimeboxId)
                .collect(Collectors.toSet());

        if (timeboxIds.isEmpty()) {
            return Map.of();
        }

        return StreamSupport.stream(timeboxRepository.findAllById(timeboxIds).spliterator(), false)
                .collect(Collectors.toMap(Timebox::getId, Function.identity()));
    }

    /**
     * 재시작 이벤트가 참조한 실패 이벤트를 조회해 failureEventId 기준 맵으로 만든다.
     */
    private Map<String, OffsetDateTime> loadFailureOccurredAtById(
            List<FailureSlice> failures,
            List<RestartSlice> restarts,
            String userId
    ) {
        Map<String, OffsetDateTime> failureOccurredAtById = failures.stream()
                .collect(Collectors.toMap(
                        FailureSlice::getFailureEventId,
                        FailureSlice::getOccurredAt
                ));

        Set<String> missingFailureEventIds = restarts.stream()
                .map(RestartSlice::getFailureEventId)
                .filter(failureEventId -> !failureOccurredAtById.containsKey(failureEventId))
                .collect(Collectors.toSet());

        if (missingFailureEventIds.isEmpty()) {
            return failureOccurredAtById;
        }

        failureOccurredAtById.putAll(
                failureEventRepository.findReferencesByUserIdAndIdIn(userId, missingFailureEventIds).stream()
                        .collect(Collectors.toMap(
                                FailureReference::getFailureEventId,
                                FailureReference::getOccurredAt
                        ))
        );
        return failureOccurredAtById;
    }

    /**
     * 재시작 이벤트 관점의 품질 이상 여부를 계산한다.
     *
     * 중복 연결, orphan restart, failure보다 이른 restart, 48시간을 넘긴 늦은 restart, timezone mismatch를 한 번에 센다.
     */
    private RestartQualityStats analyzeRestarts(
            List<RestartSlice> restarts,
            Map<String, OffsetDateTime> failureOccurredAtById
    ) {
        Set<String> seenFailureEventIds = new HashSet<>();
        Set<String> duplicateFailureEventIds = new HashSet<>();
        int orphanRestartCount = 0;
        int restartBeforeFailureCount = 0; // failure 전의 restart
        int lateRestartLinkCount = 0;
        int timezoneMismatchCount = 0;

        for (RestartSlice restart : restarts) {
            if (!seenFailureEventIds.add(restart.getFailureEventId())) {
                duplicateFailureEventIds.add(restart.getFailureEventId());
            }

            OffsetDateTime failureOccurredAt = failureOccurredAtById.get(restart.getFailureEventId());
            if (failureOccurredAt == null) {
                orphanRestartCount++;
            } else {
                if (restart.getOccurredAt().isBefore(failureOccurredAt)) {
                    restartBeforeFailureCount++;
                }
                if (restart.getOccurredAt().isAfter(failureOccurredAt.plusHours(48))) {
                    lateRestartLinkCount++;
                }
            }

            if (!DEFAULT_OFFSET.equals(restart.getOccurredAt().getOffset())) {
                timezoneMismatchCount++;
            }
        }

        return new RestartQualityStats(
                duplicateFailureEventIds.size(),
                orphanRestartCount,
                restartBeforeFailureCount,
                lateRestartLinkCount,
                timezoneMismatchCount
        );
    }

    /**
     * 세션이 올바른 timebox를 참조하는지와 break session 오염 여부를 계산한다.
     */
    private SessionQualityStats analyzeSessions(
            List<SessionSlice> sessions,
            Map<String, Timebox> timeboxesById
    ) {
        int breakSessionReferenceCount = 0;
        int missingTimeboxReferenceCount = 0;
        int timezoneMismatchCount = 0;

        for (SessionSlice session : sessions) {
            Timebox timebox = timeboxesById.get(session.getTimeboxId());
            if (timebox == null) {
                missingTimeboxReferenceCount++;
            } else if (timebox.getType() == TimeboxType.BREAK) {
                breakSessionReferenceCount++;
            }

            if (!DEFAULT_OFFSET.equals(session.getStartedAt().getOffset())) {
                timezoneMismatchCount++;
            }
        }

        return new SessionQualityStats(
                breakSessionReferenceCount,
                missingTimeboxReferenceCount,
                timezoneMismatchCount
        );
    }

    /**
     * failure 이벤트 중 KST 기준과 다른 offset을 가진 건수를 센다.
     */
    private int countFailureTimezoneMismatch(List<FailureSlice> failures) {
        int failureMismatchCount = 0;
        for (FailureSlice failure : failures) {
            if (!DEFAULT_OFFSET.equals(failure.getOccurredAt().getOffset())) {
                failureMismatchCount++;
            }
        }
        return failureMismatchCount;
    }

    /**
     * timebox 데이터 중 KST 기준과 다른 offset을 가진 건수를 센다.
     */
    private int countTimeboxTimezoneMismatch(Iterable<Timebox> timeboxes) {
        int timeboxMismatchCount = 0;
        for (Timebox timebox : timeboxes) {
            if (!DEFAULT_OFFSET.equals(timebox.getStartAt().getOffset())) {
                timeboxMismatchCount++;
            }
        }
        return timeboxMismatchCount;
    }

    private record RestartQualityStats(
            int duplicateRestartLinkCount,
            int orphanRestartCount,
            int restartBeforeFailureCount,
            int lateRestartLinkCount,
            int timezoneMismatchCount
    ) {
    }

    private record SessionQualityStats(
            int breakSessionReferenceCount,
            int missingTimeboxReferenceCount,
            int timezoneMismatchCount
    ) {
    }
}
