package com.adhd.focusmate.service.settlement;

import com.adhd.focusmate.common.exception.BusinessException;
import com.adhd.focusmate.domain.model.Challenge;
import com.adhd.focusmate.domain.model.User;
import com.adhd.focusmate.domain.model.Wallet;
import com.adhd.focusmate.domain.model.type.ChallengeStatus;
import com.adhd.focusmate.repository.ChallengeRepository;
import com.adhd.focusmate.repository.UserRepository;
import com.adhd.focusmate.repository.WalletRepository;
import com.adhd.focusmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class SettlementServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SettlementService settlementService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private ChallengeRepository challengeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @org.springframework.boot.test.mock.mockito.SpyBean
    private com.adhd.focusmate.service.verification.ManualVerifier manualVerifier;

    @Test
    @DisplayName("Healing Pivot: 챌린지 실패 시 포인트가 즉시 차감되지 않고 FROZEN 상태가 된다 (Grace Period)")
    void testPointFreezingOnFailure() {
        // Given
        User user = userRepository.save(User.builder().email("healing@test.com").nickname("Healer").build());
        Wallet wallet = walletRepository.save(Wallet.builder().user(user).balance(5000).build());

        Challenge challenge = challengeRepository.save(Challenge.builder()
                .user(user)
                .title("Early Morning")
                .status(ChallengeStatus.IN_PROGRESS)
                .deadline(LocalDateTime.now().minusHours(1))
                .build());

        // Spy: verify() 메서드가 false를 반환하도록 조작
        org.mockito.BDDMockito.willReturn(false).given(manualVerifier).verify(org.mockito.ArgumentMatchers.any());

        // When: 정산 실행 (실패 유도)
        settlementService.settleChallenge(challenge.getId());

        // Then
        // 1. 지갑 잔액 유지 확인 (5000 -> 5000)
        Wallet updatedWallet = walletRepository.findById(wallet.getId()).get();
        assertThat(updatedWallet.getBalance()).isEqualTo(5000);

        // 2. Redis에 FROZEN 상태 확인
        String frozenKey = "frozen:challenge:" + challenge.getId();
        Boolean hasKey = redisTemplate.hasKey(frozenKey);
        assertThat(hasKey).isTrue();

        // 3. 챌린지 상태 확인
        Challenge updatedChallenge = challengeRepository.findById(challenge.getId()).get();
        assertThat(updatedChallenge.getStatus()).isEqualTo(ChallengeStatus.FROZEN);
    }

    @Test
    @DisplayName("Recovery Quest: 패자부활 퀘스트 완료 시 FROZEN 상태가 해제되고 포인트가 유지된다")
    void testRecoveryQuestSuccess() {
        // Given
        User user = userRepository.save(User.builder().email("recovery@test.com").nickname("Phoenix").build());
        Wallet wallet = walletRepository.save(Wallet.builder().user(user).balance(5000).build());

        Challenge challenge = challengeRepository.save(Challenge.builder()
                .user(user)
                .title("Recovery Challenge")
                .status(ChallengeStatus.FROZEN)
                .deadline(LocalDateTime.now().minusHours(1))
                .build());

        // Redis에 FROZEN 키 강제 설정 (Service 로직 상 선행 조건)
        String frozenKey = "frozen:challenge:" + challenge.getId();
        redisTemplate.opsForValue().set(frozenKey, "FROZEN", java.time.Duration.ofHours(24));

        // When: 패자부활 퀘스트 완료
        settlementService.completeRecoveryQuest(challenge.getId(), user.getId());

        // Then
        // 1. 챌린지 상태 COMPLETED로 변경 확인
        Challenge updatedChallenge = challengeRepository.findById(challenge.getId()).get();
        assertThat(updatedChallenge.getStatus()).isEqualTo(ChallengeStatus.COMPLETED);

        // 2. Redis 키 삭제 확인
        Boolean hasKey = redisTemplate.hasKey(frozenKey);
        assertThat(hasKey).isFalse();

        // 3. 포인트 차감 없음 확인 (보상은 없음, 몰수 면제)
        Wallet updatedWallet = walletRepository.findById(wallet.getId()).get();
        assertThat(updatedWallet.getBalance()).isEqualTo(5000);
    }
}
