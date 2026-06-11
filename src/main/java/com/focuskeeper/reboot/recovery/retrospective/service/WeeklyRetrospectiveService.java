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

/**
 * Daily 이벤트(Failure, Restart, Session) 통계를 주 단위(월~일)로 묶어 조회하고,
 * 각 Policy를 호출해 주간 총평(Summary) 및 다음 주 행동 처방(Anti-slip Action)을 도출하는 핵심 서비스다.
 * <p>
 * OOM 방지를 위해 객체 전체를 로드하지 않고 Count 집계 쿼리와 구조화된 도메인 규칙(Policy) 객체에 의존하며,
 * 동일 주차에 대해 여러 번 호출되더라도 결과가 멱등하게 갱신(regenerate)되도록 구현되어 있다.
 */
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

    /**
     * 특정 주(월~일)의 통계를 집계하여 주간 회고 데이터를 생성 또는 덮어쓴다(Upsert).
     *
     * DB에서 원천 이벤트를 읽어와 통계를 내고, Policy를 통해 코칭 텍스트를 생성한 뒤 영속화한다.
     * 여러 번 호출되어도 해당 주차의 데이터를 덮어쓰므로 멱등성(Idempotency)이 보장된다.
     */
    @Transactional
    // critical
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

    /**
     * 특정 주차에 대해 이미 생성되어 있는 주간 회고 데이터를 조회한다.
     *
     * 아직 해당 주차의 파이프라인(generate)이 실행되지 않아 데이터가 없는 경우 BusinessException을 던진다.
     */
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
