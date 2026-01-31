package com.adhd.focusmate.service.challenge;

import com.adhd.focusmate.common.exception.BusinessException;
import com.adhd.focusmate.common.exception.ErrorCode;
import com.adhd.focusmate.domain.model.Challenge;
import com.adhd.focusmate.domain.model.User;
import com.adhd.focusmate.domain.model.type.ChallengeStatus;
import com.adhd.focusmate.domain.model.type.ChallengeType;
import com.adhd.focusmate.domain.model.type.CreditLogReason;
import com.adhd.focusmate.dto.challenge.ChallengeCreateRequest;
import com.adhd.focusmate.dto.challenge.ChallengeResponse;
import com.adhd.focusmate.dto.wallet.CreditChargeRequest;
import com.adhd.focusmate.dto.wallet.CreditDeductRequest;
import com.adhd.focusmate.repository.ChallengeRepository;
import com.adhd.focusmate.repository.UserRepository;
import com.adhd.focusmate.service.verification.ChallengeVerifier;
import com.adhd.focusmate.service.verification.VerifierFactory;
import com.adhd.focusmate.service.wallet.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChallengeService {

        private final ChallengeRepository challengeRepository;
        private final WalletService walletService;
        private final UserRepository userRepository;
        private final VerifierFactory verifierFactory;

        private static final int REWARD_AMOUNT = 100;
        private static final int PENALTY_AMOUNT = 500;

        @Transactional
        public ChallengeResponse createChallenge(ChallengeCreateRequest request) {
                User user = userRepository.findById(request.userId())
                                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "User not found"));

                ChallengeType type = request.challengeType() != null
                                ? request.challengeType()
                                : ChallengeType.MANUAL;

                Challenge challenge = Challenge.builder()
                                .user(user)
                                .title(request.title())
                                .description(request.description())
                                .challengeType(type)
                                .targetValue(request.targetValue())
                                .estimatedTime(request.estimatedMinutes())
                                .deadline(request.deadline())
                                .energyLevel(request.energyLevel())
                                .status(ChallengeStatus.PENDING)
                                .build();

                return ChallengeResponse.from(challengeRepository.save(challenge));
        }

        /**
         * 단일 챌린지 상세 조회 (캐시 적용)
         * - 첫 조회: DB Hit → Redis 저장
         * - 이후 조회: Redis Hit (TTL: 10분)
         */
        @Cacheable(value = "challengeInfo", key = "#challengeId")
        @Transactional(readOnly = true)
        public ChallengeResponse getChallengeDetail(Long challengeId) {
                log.info("Cache MISS - Fetching challenge from DB: challengeId={}", challengeId);
                Challenge challenge = findChallengeById(challengeId);
                return ChallengeResponse.from(challenge);
        }

        /**
         * 챌린지 완료 요청 - Verifier 전략에 따라 검증 후 처리
         * 캐시 무효화: 상태 변경으로 인해 캐시 삭제
         */
        @CacheEvict(value = "challengeInfo", key = "#challengeId")
        @Transactional
        public ChallengeResponse verifyAndComplete(Long challengeId) {
                log.info("Cache EVICT - verifyAndComplete: challengeId={}", challengeId);
                Challenge challenge = findChallengeById(challengeId);

                // 1. ChallengeType에 맞는 Verifier 조회
                ChallengeVerifier verifier = verifierFactory.getVerifier(challenge.getChallengeType());

                // 2. 검증 수행
                boolean success = verifier.verify(challenge);

                if (success) {
                        challenge.complete();
                        walletService.charge(new CreditChargeRequest(challenge.getUser().getId(), REWARD_AMOUNT));
                        log.info("Challenge [{}] completed successfully. +{} credits", challengeId, REWARD_AMOUNT);
                } else {
                        challenge.fail();
                        walletService.deduct(new CreditDeductRequest(
                                        challenge.getUser().getId(),
                                        PENALTY_AMOUNT,
                                        CreditLogReason.TASK_FAIL_PENALTY));
                        log.info("Challenge [{}] verification failed. -{} credits", challengeId, PENALTY_AMOUNT);
                }

                return ChallengeResponse.from(challenge);
        }

        /**
         * 수동 완료 (Legacy 호환) - 검증 없이 바로 완료 처리
         * 캐시 무효화: 상태 변경
         */
        @CacheEvict(value = "challengeInfo", key = "#challengeId")
        @Transactional
        public ChallengeResponse completeChallenge(Long challengeId) {
                log.info("Cache EVICT - completeChallenge: challengeId={}", challengeId);
                Challenge challenge = findChallengeById(challengeId);

                challenge.complete();
                walletService.charge(new CreditChargeRequest(challenge.getUser().getId(), REWARD_AMOUNT));

                return ChallengeResponse.from(challenge);
        }

        /**
         * 챌린지 실패 처리
         * 캐시 무효화: 상태 변경
         */
        @CacheEvict(value = "challengeInfo", key = "#challengeId")
        @Transactional
        public ChallengeResponse failChallenge(Long challengeId) {
                log.info("Cache EVICT - failChallenge: challengeId={}", challengeId);
                Challenge challenge = findChallengeById(challengeId);

                challenge.fail();

                walletService.deduct(new CreditDeductRequest(
                                challenge.getUser().getId(),
                                PENALTY_AMOUNT,
                                CreditLogReason.TASK_FAIL_PENALTY));

                return ChallengeResponse.from(challenge);
        }

        @Transactional(readOnly = true)
        public List<ChallengeResponse> getChallenges(Long userId, ChallengeStatus status) {
                List<Challenge> challenges = (status != null)
                                ? challengeRepository.findAllByUserIdAndStatus(userId, status)
                                : challengeRepository.findAllByUserId(userId);

                return challenges.stream()
                                .map(ChallengeResponse::from)
                                .collect(Collectors.toList());
        }

        /**
         * [Admin] 전체 챌린지 조회
         */
        @Transactional(readOnly = true)
        public List<ChallengeResponse> getAllChallenges(ChallengeStatus status) {
                List<Challenge> challenges = (status != null)
                                ? challengeRepository.findAllByStatus(status)
                                : challengeRepository.findAll();

                return challenges.stream()
                                .map(ChallengeResponse::from)
                                .collect(Collectors.toList());
        }

        // ===== Private Helper Methods =====

        private Challenge findChallengeById(Long challengeId) {
                return challengeRepository.findById(challengeId)
                                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND,
                                                "Challenge not found"));
        }
}
