package com.focuskeeper.reboot.recovery.retrospective.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.execution.FailureReason;
import com.focuskeeper.reboot.recovery.execution.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import com.focuskeeper.reboot.recovery.retrospective.dto.WeeklyRetrospectiveResponse;
import com.focuskeeper.reboot.recovery.retrospective.entity.WeeklyRetrospective;
import com.focuskeeper.reboot.recovery.retrospective.repository.WeeklyRetrospectiveRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class WeeklyRetrospectiveService {

    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(9);

    private final WeeklyRetrospectiveRepository weeklyRetrospectiveRepository;
    private final RecoverySessionRepository recoverySessionRepository;
    private final FailureEventRepository failureEventRepository;
    private final RestartEventRepository restartEventRepository;
    private final RetrospectiveSummaryPolicy retrospectiveSummaryPolicy;
    private final AntiSlipActionPolicy antiSlipActionPolicy;

    public WeeklyRetrospectiveService(
            WeeklyRetrospectiveRepository weeklyRetrospectiveRepository,
            RecoverySessionRepository recoverySessionRepository,
            FailureEventRepository failureEventRepository,
            RestartEventRepository restartEventRepository,
            RetrospectiveSummaryPolicy retrospectiveSummaryPolicy,
            AntiSlipActionPolicy antiSlipActionPolicy
    ) {
        this.weeklyRetrospectiveRepository = weeklyRetrospectiveRepository;
        this.recoverySessionRepository = recoverySessionRepository;
        this.failureEventRepository = failureEventRepository;
        this.restartEventRepository = restartEventRepository;
        this.retrospectiveSummaryPolicy = retrospectiveSummaryPolicy;
        this.antiSlipActionPolicy = antiSlipActionPolicy;
    }

    @Transactional
    public WeeklyRetrospectiveResponse generate(String userId, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        OffsetDateTime periodStart = weekStart.atStartOfDay().atOffset(DEFAULT_OFFSET);
        OffsetDateTime periodEndExclusive = weekStart.plusWeeks(1).atStartOfDay().atOffset(DEFAULT_OFFSET);

            long sessionStartedCount = recoverySessionRepository.countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                    userId,
                    periodStart,
                    periodEndExclusive
            );
            long sessionCompletedCount =
                    recoverySessionRepository.countByUserIdAndStatusAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                            userId,
                            RecoverySessionStatus.COMPLETED,
                            periodStart,
                            periodEndExclusive
                    );
            long sessionInterruptedCount =
                    recoverySessionRepository.countByUserIdAndStatusAndStartedAtGreaterThanEqualAndStartedAtLessThan(
                            userId,
                            RecoverySessionStatus.INTERRUPTED,
                            periodStart,
                            periodEndExclusive
                    );
            long failureCount = failureEventRepository.countByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                    userId,
                    periodStart,
                    periodEndExclusive
            );
            long restartCount = restartEventRepository.countByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
                    userId,
                    periodStart,
                    periodEndExclusive
            );

            FailureReason dominantFailureReason = failureEventRepository.findDominantReason(
                    userId,
                    periodStart,
                    periodEndExclusive
            );
            String summary = retrospectiveSummaryPolicy.summarize(
                    sessionStartedCount,
                    sessionCompletedCount,
                    sessionInterruptedCount,
                    failureCount,
                    restartCount,
                    dominantFailureReason
            );
            var antiSlipAction = antiSlipActionPolicy.suggest(
                    sessionCompletedCount,
                    sessionInterruptedCount,
                    dominantFailureReason
            );

        OffsetDateTime generatedAt = OffsetDateTime.now();
        WeeklyRetrospective retrospective = weeklyRetrospectiveRepository.findByUserIdAndWeekStart(userId, weekStart)
                .map(existing -> {
                    existing.regenerate(
                            sessionStartedCount,
                            sessionCompletedCount,
                            sessionInterruptedCount,
                            failureCount,
                            restartCount,
                            dominantFailureReason == null ? null : dominantFailureReason.name(),
                            summary,
                            antiSlipAction,
                            generatedAt
                    );
                    return existing;
                })
                .orElseGet(() -> WeeklyRetrospective.create(
                        userId,
                        weekStart,
                        weekEnd,
                        sessionStartedCount,
                        sessionCompletedCount,
                        sessionInterruptedCount,
                        failureCount,
                        restartCount,
                        dominantFailureReason == null ? null : dominantFailureReason.name(),
                        summary,
                        antiSlipAction,
                        generatedAt
                ));

        return weeklyRetrospectiveRepository.save(retrospective).toResponse();
    }

    public WeeklyRetrospectiveResponse get(String userId, LocalDate weekStart) {
        return weeklyRetrospectiveRepository.findByUserIdAndWeekStart(userId, weekStart)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of(
                                "userId", userId,
                                "weekStart", weekStart.toString()
                        )
                ))
                .toResponse();
    }
}
