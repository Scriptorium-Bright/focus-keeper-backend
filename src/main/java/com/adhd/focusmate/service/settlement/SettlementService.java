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

    private static final int DEFAULT_DEPOSIT = 1000; // 기본 예치금
    private static final long SUCCESS_REWARD_POINTS = 100L; // 성공 보상 포인트

    /**
     * 챌린지 정산 처리
     * <br>
     * 1. Verifier로 결과 확인 <br>
     * 2. 실패 시 → 면제권 확인 → 있으면 사용하고 성공 처리<br>
     * 3. 성공 시 → 예치금 환급 + 포인트 지급<br>
     * 4. 최종 실패 시 → 예치금 몰수 (플랫폼 수익)
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
            return processFailure(challenge);
        }
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
     * 실패 처리: 예치금 몰수 -> 수익 방식에서 수수료를 가져간다던지 예치금을 가져가는 행위는 문제가 될 수 있어 몰수만 하기로 함
     */
    private SettlementResult processFailure(Challenge challenge) {
        challenge.fail();

        // 예치금은 환급하지 않음 (=몰수)
        log.info("Settlement FAIL: Challenge [{}], Deposit burned: {}",
                challenge.getId(), DEFAULT_DEPOSIT);

        return SettlementResult.builder()
                .challengeId(challenge.getId())
                .status(ChallengeStatus.FAILED)
                .depositRefunded(false)
                .refundAmount(0)
                .pointsAwarded(0L)
                .savedByItem(false)
                .build();
    }

    /**
     * 면제권(PASS_TICKET) 사용 시도
     * 
     * @return 사용 성공 여부
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
