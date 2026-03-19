package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiQualityReport;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiQualityReportRepository;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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

    public DailyKpiQualityService(
            DailyKpiQualityReportRepository dailyKpiQualityReportRepository,
            RecoverySessionRepository recoverySessionRepository,
            FailureEventRepository failureEventRepository,
            RestartEventRepository restartEventRepository,
            TimeboxRepository timeboxRepository
    ) {
        this.dailyKpiQualityReportRepository = dailyKpiQualityReportRepository;
        this.recoverySessionRepository = recoverySessionRepository;
        this.failureEventRepository = failureEventRepository;
        this.restartEventRepository = restartEventRepository;
        this.timeboxRepository = timeboxRepository;
    }

    public DailyKpiQualityReport generate(String userId, LocalDate metricDate, OffsetDateTime generatedAt) {
        OffsetDateTime periodStart = metricDate.atStartOfDay().atOffset(DEFAULT_OFFSET);
        OffsetDateTime periodEndExclusive = metricDate.plusDays(1).atStartOfDay().atOffset(DEFAULT_OFFSET);

        List<RecoverySessionRepository.SessionSlice> sessions = recoverySessionRepository.findSlicesByUserIdAndStartedAtBetween(
                userId,
                periodStart,
                periodEndExclusive
        );
        List<FailureEventRepository.FailureSlice> failures = failureEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                userId,
                periodStart,
                periodEndExclusive
        );
        List<RestartEventRepository.RestartSlice> restarts = restartEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                userId,
                periodStart,
                periodEndExclusive
        );

        Map<String, Timebox> timeboxesById = loadTimeboxes(sessions);
        Map<String, FailureEventRepository.FailureReference> failureById = loadFailures(restarts, userId);

        int duplicateRestartLinkCount = (int) restarts.stream()
                .collect(Collectors.groupingBy(RestartEventRepository.RestartSlice::getFailureEventId, Collectors.counting()))
                .values()
                .stream()
                .filter(count -> count > 1)
                .count();

        int orphanRestartCount = (int) restarts.stream()
                .filter(restart -> !failureById.containsKey(restart.getFailureEventId()))
                .count();

        int restartBeforeFailureCount = (int) restarts.stream()
                .filter(restart -> {
                    FailureEventRepository.FailureReference failure = failureById.get(restart.getFailureEventId());
                    return failure != null && restart.getOccurredAt().isBefore(failure.getOccurredAt());
                })
                .count();

        int lateRestartLinkCount = (int) restarts.stream()
                .filter(restart -> {
                    FailureEventRepository.FailureReference failure = failureById.get(restart.getFailureEventId());
                    return failure != null && restart.getOccurredAt().isAfter(failure.getOccurredAt().plusHours(48));
                })
                .count();

        int breakSessionReferenceCount = (int) sessions.stream()
                .filter(session -> {
                    Timebox timebox = timeboxesById.get(session.getTimeboxId());
                    return timebox != null && timebox.getType() == TimeboxType.BREAK;
                })
                .count();

        int missingTimeboxReferenceCount = (int) sessions.stream()
                .filter(session -> !timeboxesById.containsKey(session.getTimeboxId()))
                .count();

        int timezoneMismatchCount = countTimezoneMismatch(sessions, failures, restarts, timeboxesById.values());
        int totalIssueCount = duplicateRestartLinkCount
                + orphanRestartCount
                + restartBeforeFailureCount
                + lateRestartLinkCount
                + breakSessionReferenceCount
                + missingTimeboxReferenceCount
                + timezoneMismatchCount;
        boolean healthy = totalIssueCount == 0;

        return dailyKpiQualityReportRepository.findByUserIdAndMetricDate(userId, metricDate)
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
    }

    private Map<String, Timebox> loadTimeboxes(List<RecoverySessionRepository.SessionSlice> sessions) {
        Set<String> timeboxIds = sessions.stream()
                .map(RecoverySessionRepository.SessionSlice::getTimeboxId)
                .collect(Collectors.toSet());

        if (timeboxIds.isEmpty()) {
            return Map.of();
        }

        return StreamSupport.stream(timeboxRepository.findAllById(timeboxIds).spliterator(), false)
                .collect(Collectors.toMap(Timebox::getId, Function.identity()));
    }

    private Map<String, FailureEventRepository.FailureReference> loadFailures(
            List<RestartEventRepository.RestartSlice> restarts,
            String userId
    ) {
        Set<String> failureEventIds = restarts.stream()
                .map(RestartEventRepository.RestartSlice::getFailureEventId)
                .collect(Collectors.toSet());

        if (failureEventIds.isEmpty()) {
            return Map.of();
        }

        return failureEventRepository.findReferencesByUserIdAndIdIn(userId, failureEventIds).stream()
                .collect(Collectors.toMap(
                        FailureEventRepository.FailureReference::getFailureEventId,
                        Function.identity()
                ));
    }

    private int countTimezoneMismatch(
            List<RecoverySessionRepository.SessionSlice> sessions,
            List<FailureEventRepository.FailureSlice> failures,
            List<RestartEventRepository.RestartSlice> restarts,
            Iterable<Timebox> timeboxes
    ) {
        int sessionMismatchCount = (int) sessions.stream()
                .filter(session -> !DEFAULT_OFFSET.equals(session.getStartedAt().getOffset()))
                .count();
        int failureMismatchCount = (int) failures.stream()
                .filter(failure -> !DEFAULT_OFFSET.equals(failure.getOccurredAt().getOffset()))
                .count();
        int restartMismatchCount = (int) restarts.stream()
                .filter(restart -> !DEFAULT_OFFSET.equals(restart.getOccurredAt().getOffset()))
                .count();

        int timeboxMismatchCount = 0;
        for (Timebox timebox : timeboxes) {
            if (!DEFAULT_OFFSET.equals(timebox.getStartAt().getOffset())) {
                timeboxMismatchCount++;
            }
        }

        return sessionMismatchCount + failureMismatchCount + restartMismatchCount + timeboxMismatchCount;
    }
}
