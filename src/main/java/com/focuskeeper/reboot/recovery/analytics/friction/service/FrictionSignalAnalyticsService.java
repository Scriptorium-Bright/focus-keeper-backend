package com.focuskeeper.reboot.recovery.analytics.friction.service;

import com.focuskeeper.reboot.recovery.analytics.friction.FrictionSignalType;
import com.focuskeeper.reboot.recovery.analytics.friction.entity.RecoveryFrictionSignal;
import com.focuskeeper.reboot.recovery.analytics.friction.repository.RecoveryFrictionSignalRepository;
import com.focuskeeper.reboot.recovery.execution.FailureReason;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class FrictionSignalAnalyticsService {

    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(9);

    private final FailureEventRepository failureEventRepository;
    private final RestartEventRepository restartEventRepository;
    private final RecoveryFrictionSignalRepository recoveryFrictionSignalRepository;

    public FrictionSignalAnalyticsService(
            FailureEventRepository failureEventRepository,
            RestartEventRepository restartEventRepository,
            RecoveryFrictionSignalRepository recoveryFrictionSignalRepository
    ) {
        this.failureEventRepository = failureEventRepository;
        this.restartEventRepository = restartEventRepository;
        this.recoveryFrictionSignalRepository = recoveryFrictionSignalRepository;
    }

    /**
     * 지정한 날짜의 실패/재시작 이벤트를 읽어 반복 실패와 지연 재시작 신호를 계산해 저장한다.
     */
    public List<RecoveryFrictionSignal> generate(String userId, LocalDate metricDate) {
        OffsetDateTime periodStart = metricDate.atStartOfDay().atOffset(DEFAULT_OFFSET);
        OffsetDateTime periodEndExclusive = metricDate.plusDays(1).atStartOfDay().atOffset(DEFAULT_OFFSET);
        OffsetDateTime restartEndExclusive = periodEndExclusive.plusHours(48);

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

        Map<String, List<RestartEventRepository.RestartSlice>> restartByFailureEventId = restarts.stream()
                .collect(java.util.stream.Collectors.groupingBy(RestartEventRepository.RestartSlice::getFailureEventId));

        int tooBigRepeatCount = (int) failures.stream()
                .filter(failure -> failure.getReason() == FailureReason.TOO_BIG)
                .count();

        int lateRestartCount = (int) failures.stream()
                .filter(failure -> restartByFailureEventId.getOrDefault(failure.getFailureEventId(), List.of()).stream()
                        .anyMatch(restart -> restart.getOccurredAt().isAfter(failure.getOccurredAt().plusHours(24))
                                && !restart.getOccurredAt().isAfter(failure.getOccurredAt().plusHours(48))))
                .count();

        OffsetDateTime generatedAt = OffsetDateTime.now();

        RecoveryFrictionSignal tooBigRepeatSignal = upsertSignal(
                userId,
                metricDate,
                FrictionSignalType.TOO_BIG_REPEAT,
                tooBigRepeatCount >= 2,
                tooBigRepeatCount,
                generatedAt
        );
        RecoveryFrictionSignal lateRestartSignal = upsertSignal(
                userId,
                metricDate,
                FrictionSignalType.LATE_RESTART,
                lateRestartCount > 0,
                lateRestartCount,
                generatedAt
        );

        return List.of(tooBigRepeatSignal, lateRestartSignal);
    }

    private RecoveryFrictionSignal upsertSignal(
            String userId,
            LocalDate metricDate,
            FrictionSignalType signalType,
            boolean active,
            int evidenceCount,
            OffsetDateTime generatedAt
    ) {
        RecoveryFrictionSignal signal = recoveryFrictionSignalRepository
                .findByUserIdAndMetricDateAndSignalType(userId, metricDate, signalType)
                .map(existing -> {
                    existing.regenerate(active, evidenceCount, generatedAt);
                    return existing;
                })
                .orElseGet(() -> RecoveryFrictionSignal.create(
                        userId,
                        metricDate,
                        signalType,
                        active,
                        evidenceCount,
                        generatedAt
                ));

        return recoveryFrictionSignalRepository.save(signal);
    }
}
