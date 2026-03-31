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
    }

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
            RestartQualityStats restartStats = analyzeRestarts(restarts, failureOccurredAtById);
            SessionQualityStats sessionStats = analyzeSessions(sessions, timeboxesById);
            int failureTimezoneMismatchCount = countFailureTimezoneMismatch(failures);
            int timeboxTimezoneMismatchCount = countTimeboxTimezoneMismatch(timeboxesById.values());

            int duplicateRestartLinkCount = restartStats.duplicateRestartLinkCount();
            int orphanRestartCount = restartStats.orphanRestartCount();
            int restartBeforeFailureCount = restartStats.restartBeforeFailureCount();
            int lateRestartLinkCount = restartStats.lateRestartLinkCount();
            int breakSessionReferenceCount = sessionStats.breakSessionReferenceCount();
            int missingTimeboxReferenceCount = sessionStats.missingTimeboxReferenceCount();
            int timezoneMismatchCount = restartStats.timezoneMismatchCount()
                    + sessionStats.timezoneMismatchCount()
                    + failureTimezoneMismatchCount
                    + timeboxTimezoneMismatchCount;
            int totalIssueCount = duplicateRestartLinkCount
                    + orphanRestartCount
                    + restartBeforeFailureCount
                    + lateRestartLinkCount
                    + breakSessionReferenceCount
                    + missingTimeboxReferenceCount
                    + timezoneMismatchCount;
            boolean healthy = totalIssueCount == 0;

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

    private RestartQualityStats analyzeRestarts(
            List<RestartSlice> restarts,
            Map<String, OffsetDateTime> failureOccurredAtById
    ) {
        Set<String> seenFailureEventIds = new HashSet<>();
        Set<String> duplicateFailureEventIds = new HashSet<>();
        int orphanRestartCount = 0;
        int restartBeforeFailureCount = 0;
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

    private int countFailureTimezoneMismatch(List<FailureSlice> failures) {
        int failureMismatchCount = 0;
        for (FailureSlice failure : failures) {
            if (!DEFAULT_OFFSET.equals(failure.getOccurredAt().getOffset())) {
                failureMismatchCount++;
            }
        }
        return failureMismatchCount;
    }

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
