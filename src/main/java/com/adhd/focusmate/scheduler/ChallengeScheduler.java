package com.adhd.focusmate.scheduler;

import com.adhd.focusmate.domain.model.Challenge;
import com.adhd.focusmate.domain.model.type.ChallengeStatus;
import com.adhd.focusmate.repository.ChallengeRepository;
import com.adhd.focusmate.service.settlement.SettlementResult;
import com.adhd.focusmate.service.settlement.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * 챌린지 자동 정산 스케줄러
 * 매일 자정(KST)에 실행되어 마감된 챌린지들을 정산
 * 
 * 중요: 이 메서드는 @Transactional이 아님
 * 각 챌린지 정산은 SettlementService 내부에서 개별 트랜잭션으로 처리
 * → 한 챌린지 실패가 다른 챌린지에 영향 주지 않음
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChallengeScheduler {

    private final ChallengeRepository challengeRepository;
    private final SettlementService settlementService;

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /**
     * 매일 자정(00:00 KST)에 실행
     * cron = "초 분 시 일 월 요일"
     */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void settleDailyExpiredChallenges() {
        LocalDateTime now = LocalDateTime.now(KST);
        log.info("=== Daily Settlement Scheduler Started at {} ===", now);

        // 정산 대상: 마감 지났고 아직 PENDING/IN_PROGRESS 상태인 챌린지
        List<ChallengeStatus> targetStatuses = List.of(
                ChallengeStatus.PENDING,
                ChallengeStatus.IN_PROGRESS);

        List<Challenge> expiredChallenges = challengeRepository
                .findAllPendingSettlement(now, targetStatuses);

        log.info("Found {} challenges pending settlement", expiredChallenges.size());

        int successCount = 0;
        int failCount = 0;

        for (Challenge challenge : expiredChallenges) {
            try {
                SettlementResult result = settlementService.settleChallenge(challenge.getId());

                if (result.getStatus() == ChallengeStatus.COMPLETED) {
                    successCount++;
                } else {
                    failCount++;
                }

                log.info("Settled challenge [{}]: {} (savedByItem: {})",
                        challenge.getId(), result.getStatus(), result.isSavedByItem());

            } catch (Exception e) {
                // 한 챌린지 실패가 전체 루프를 중단시키지 않음
                log.error("Failed to settle challenge [{}]: {}",
                        challenge.getId(), e.getMessage(), e);
                failCount++;
            }
        }

        log.info("=== Daily Settlement Completed: {} success, {} fail ===",
                successCount, failCount);
    }

    /**
     * 수동 정산 트리거 (운영/테스트용)
     * ApplicationRunner나 API에서 호출 가능
     */
    public void manualSettlement() {
        log.info("Manual settlement triggered");
        settleDailyExpiredChallenges();
    }
}
