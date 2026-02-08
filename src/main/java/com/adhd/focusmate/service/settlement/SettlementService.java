package com.adhd.focusmate.service.settlement;

import com.adhd.focusmate.common.exception.BusinessException;
import com.adhd.focusmate.common.exception.ErrorCode;
import com.adhd.focusmate.domain.model.Challenge;
import com.adhd.focusmate.domain.model.UserItem;
import com.adhd.focusmate.domain.model.Wallet;
import com.adhd.focusmate.domain.model.type.ChallengeStatus;
import com.adhd.focusmate.domain.model.type.ItemType;
import com.adhd.focusmate.dto.settlement.SettlementResult;
import com.adhd.focusmate.repository.ChallengeRepository;
import com.adhd.focusmate.repository.UserItemRepository;
import com.adhd.focusmate.repository.WalletRepository;
import com.adhd.focusmate.service.verification.ChallengeVerifier;
import com.adhd.focusmate.service.verification.VerifierFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 범용 정산 서비스<br>
 * 챌린지 타입에 무관하게 검증 결과에 따라 정산 처리
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final ChallengeRepository challengeRepository;
    private final WalletRepository walletRepository;
    private final UserItemRepository userItemRepository;
    private final VerifierFactory verifierFactory;
    private final RedisTemplate<String, String> redisTemplate; // Redis 추가

    private static final int DEFAULT_DEPOSIT = 1000; // 기본 예치금
    private static final long SUCCESS_REWARD_POINTS = 100L; // 성공 보상 포인트
    private static final long GRACE_PERIOD_HOURS = 24L; // 패자부활전 유예 기간

    /**
     * 챌린지 정산 처리
     */
    @Transactional
    public SettlementResult settleChallenge(Long challengeId) {
        Challenge challenge = findChallenge(challengeId);
        Long userId = challenge.getUser().getId();

        // 1. Verifier로 검증
        ChallengeVerifier verifier = verifierFactory.getVerifier(challenge.getChallengeType());
        boolean verificationPassed = verifier.verify(challenge);

        log.info("Settlement: Challenge [{}] verification result: {}", challengeId, verificationPassed);

        // 2. 검증 실패 시 면제권 확인
        boolean savedByItem = false;
        if (!verificationPassed) {
            savedByItem = tryUsePassTicket(userId);
            if (savedByItem) {
                verificationPassed = true; // 결과 오버라이드
                log.info("Settlement: Challenge [{}] saved by PASS_TICKET", challengeId);
            }
        }

        // 3. 최종 결과에 따른 정산
        Wallet wallet = findWalletForUpdate(userId);

        if (verificationPassed) {
            return processSuccess(challenge, wallet, savedByItem);
        } else {
            // Pivot: 실패 시 즉시 몰수가 아닌, FROZEN 상태로 전환 (Grace Period)
            return processFrozen(challenge);
        }
    }

    /**
     * Recovery Quest 완료 처리 (패자부활 성공)
     */
    @Transactional
    public void completeRecoveryQuest(Long challengeId, Long userId) {
        Challenge challenge = findChallenge(challengeId);

        if (challenge.getStatus() != ChallengeStatus.FROZEN) {
            throw new BusinessException(ErrorCode.INVALID_TASK_STATUS, "Challenge is not in FROZEN state");
        }

        // 유효기간 확인 (Redis)
        String frozenKey = "frozen:challenge:" + challengeId;
        if (Boolean.FALSE.equals(redisTemplate.hasKey(frozenKey))) {
            // 기간 만료 -> 이미 Burned 처리되었거나 만료됨 (Batch에서 처리 필요하지만 여기선 예외)
            throw new BusinessException(ErrorCode.INVALID_TASK_STATUS, "Recovery period expired");
        }

        // 성공 처리 (포인트 복구)
        // 예치금은 그대로 유지 (몰수 안 함)
        challenge.complete();

        // Redis Key 삭제
        redisTemplate.delete(frozenKey);

        log.info("Recovery Quest Completed: Challenge [{}] restored to COMPLETED.", challengeId);
    }

    /**
     * 성공 처리: 예치금 환급 + 포인트 지급
     */
    private SettlementResult processSuccess(Challenge challenge, Wallet wallet, boolean savedByItem) {
        challenge.complete();

        // 예치금 환급
        wallet.refund(DEFAULT_DEPOSIT);

        // 포인트 지급
        wallet.addPoint(SUCCESS_REWARD_POINTS);

        log.info("Settlement SUCCESS: Challenge [{}], Refund: {}, Points: +{}",
                challenge.getId(), DEFAULT_DEPOSIT, SUCCESS_REWARD_POINTS);

        return SettlementResult.builder()
                .challengeId(challenge.getId())
                .status(ChallengeStatus.COMPLETED)
                .depositRefunded(true)
                .refundAmount(DEFAULT_DEPOSIT)
                .pointsAwarded(SUCCESS_REWARD_POINTS)
                .savedByItem(savedByItem)
                .build();
    }

    /**
     * 실패 처리 -> FROZEN (Grace Period)
     * 포인트 차감 없음. Redis에 상태 저장.
     */
    private SettlementResult processFrozen(Challenge challenge) {
        challenge.freeze();

        // Redis에 Grace Period 저장 (24시간)
        String frozenKey = "frozen:challenge:" + challenge.getId();
        redisTemplate.opsForValue().set(frozenKey, "FROZEN", java.time.Duration.ofHours(GRACE_PERIOD_HOURS));

        log.info("Settlement FROZEN: Challenge [{}], Logic: Recovery Opportunity for {} hours",
                challenge.getId(), GRACE_PERIOD_HOURS);

        return SettlementResult.builder()
                .challengeId(challenge.getId())
                .status(ChallengeStatus.FROZEN)
                .depositRefunded(false) // 아직 환급 안 됨 (동결)
                .refundAmount(0)
                .pointsAwarded(0L)
                .savedByItem(false)
                .build();
    }

    /**
     * 면제권(PASS_TICKET) 사용 시도
     */
    private boolean tryUsePassTicket(Long userId) {
        Optional<UserItem> passTicketOpt = userItemRepository
                .findByUserIdAndItemTypeForUpdate(userId, ItemType.PASS_TICKET);

        if (passTicketOpt.isPresent() && passTicketOpt.get().hasItem(1)) {
            UserItem passTicket = passTicketOpt.get();
            passTicket.consume(1);
            log.info("PASS_TICKET consumed for user [{}]. Remaining: {}",
                    userId, passTicket.getQuantity());
            return true;
        }
        return false;
    }

    // ===== Helper Methods =====

    private Challenge findChallenge(Long challengeId) {
        return challengeRepository.findById(challengeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Challenge not found"));
    }

    private Wallet findWalletForUpdate(Long userId) {
        return walletRepository.findByUserIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "Wallet not found"));
    }
}
