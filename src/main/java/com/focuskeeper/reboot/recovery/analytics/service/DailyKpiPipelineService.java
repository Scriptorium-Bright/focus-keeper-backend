package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiMetric;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiMetricRepository;
import com.focuskeeper.reboot.recovery.execution.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DailyKpiPipelineService {

    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(9);

    private final DailyKpiMetricRepository dailyKpiMetricRepository;
    private final RecoverySessionRepository recoverySessionRepository;
    private final FailureEventRepository failureEventRepository;
    private final RestartEventRepository restartEventRepository;
    private final TimeboxRepository timeboxRepository;

    public DailyKpiPipelineService(
            DailyKpiMetricRepository dailyKpiMetricRepository,
            RecoverySessionRepository recoverySessionRepository,
            FailureEventRepository failureEventRepository,
            RestartEventRepository restartEventRepository,
            TimeboxRepository timeboxRepository
    ) {
        this.dailyKpiMetricRepository = dailyKpiMetricRepository;
        this.recoverySessionRepository = recoverySessionRepository;
        this.failureEventRepository = failureEventRepository;
        this.restartEventRepository = restartEventRepository;
        this.timeboxRepository = timeboxRepository;
    }

    public DailyKpiMetric generate(String userId, LocalDate metricDate) {
        OffsetDateTime periodStart = metricDate.atStartOfDay().atOffset(DEFAULT_OFFSET);
        OffsetDateTime periodEndExclusive = metricDate.plusDays(1).atStartOfDay().atOffset(DEFAULT_OFFSET);
        OffsetDateTime restartEndExclusive = periodEndExclusive.plusHours(48);

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
                restartEndExclusive
        );
        List<com.focuskeeper.reboot.recovery.planning.entity.Timebox> plannedWorkTimeboxes =
                timeboxRepository.findAllByUserIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                        userId,
                        periodStart,
                        periodEndExclusive
                ).stream()
                        .filter(timebox -> timebox.getType() == TimeboxType.WORK)
                        .toList();

        Map<String, List<RestartEventRepository.RestartSlice>> restartByFailureEventId = restarts.stream()
                .collect(Collectors.groupingBy(RestartEventRepository.RestartSlice::getFailureEventId));

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

        for (FailureEventRepository.FailureSlice failure : failures) {
            List<RestartEventRepository.RestartSlice> linkedRestarts = restartByFailureEventId
                    .getOrDefault(failure.getFailureEventId(), List.of())
                    .stream()
                    .sorted(Comparator.comparing(RestartEventRepository.RestartSlice::getOccurredAt))
                    .toList();

            List<RestartEventRepository.RestartSlice> within24 = linkedRestarts.stream()
                    .filter(restart -> !restart.getOccurredAt().isAfter(failure.getOccurredAt().plusHours(24)))
                    .toList();
            List<RestartEventRepository.RestartSlice> within48 = linkedRestarts.stream()
                    .filter(restart -> !restart.getOccurredAt().isAfter(failure.getOccurredAt().plusHours(48)))
                    .toList();

            restartCount24 += within24.size();
            restartCount48 += within48.size();
            recovery24 = recovery24 || !within24.isEmpty();
            recovery48 = recovery48 || !within48.isEmpty();

            if (!within48.isEmpty()) {
                long candidateTtr = Duration.between(
                        failure.getOccurredAt(),
                        within48.getFirst().getOccurredAt()
                ).toMinutes();
                ttrMinutes = ttrMinutes == null ? candidateTtr : Math.min(ttrMinutes, candidateTtr);
            }
        }

        BigDecimal cycleCompletionRate = ratio(sessionCompletedCount, sessionStartedCount);

        Set<String> startedTimeboxIds = sessions.stream()
                .map(RecoverySessionRepository.SessionSlice::getTimeboxId)
                .collect(Collectors.toSet());
        BigDecimal planExecutionRate = ratio(startedTimeboxIds.size(), plannedWorkTimeboxes.size());

        long plannedWorkMinutes = plannedWorkTimeboxes.stream()
                .mapToLong(timebox -> Duration.between(timebox.getStartAt(), timebox.getEndAt()).toMinutes())
                .sum();
        long actualWorkMinutes = sessions.stream()
                .filter(session -> session.getEndedAt() != null)
                .mapToLong(session -> Duration.between(session.getStartedAt(), session.getEndedAt()).toMinutes())
                .sum();
        long estimationErrorMinutes = Math.abs(plannedWorkMinutes - actualWorkMinutes);

        OffsetDateTime generatedAt = OffsetDateTime.now();
        boolean finalRecovery24 = recovery24;
        boolean finalRecovery48 = recovery48;
        int finalRestartCount24 = restartCount24;
        int finalRestartCount48 = restartCount48;
        Long finalTtrMinutes = ttrMinutes;
        DailyKpiMetric dailyKpiMetric = dailyKpiMetricRepository.findByUserIdAndMetricDate(userId, metricDate)
                .map(existing -> {
                    existing.regenerate(
                            activation,
                            failureCount,
                            finalRecovery24,
                            finalRecovery48,
                            finalRestartCount24,
                            finalRestartCount48,
                            finalTtrMinutes,
                            cycleCompletionRate,
                            planExecutionRate,
                            plannedWorkMinutes,
                            actualWorkMinutes,
                            estimationErrorMinutes,
                            generatedAt
                    );
                    return existing;
                })
                .orElseGet(() -> DailyKpiMetric.create(
                        userId,
                        metricDate,
                        activation,
                        failureCount,
                        finalRecovery24,
                        finalRecovery48,
                        finalRestartCount24,
                        finalRestartCount48,
                        finalTtrMinutes,
                        cycleCompletionRate,
                        planExecutionRate,
                        plannedWorkMinutes,
                        actualWorkMinutes,
                        estimationErrorMinutes,
                        generatedAt
                ));

        return dailyKpiMetricRepository.save(dailyKpiMetric);
    }

    private BigDecimal ratio(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }
}
