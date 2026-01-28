package com.adhd.focusmate.service.settlement;

import com.adhd.focusmate.domain.model.Challenge;
import com.adhd.focusmate.domain.model.Item;
import com.adhd.focusmate.domain.model.User;
import com.adhd.focusmate.domain.model.UserItem;
import com.adhd.focusmate.domain.model.Wallet;
import com.adhd.focusmate.domain.model.type.ChallengeStatus;
import com.adhd.focusmate.domain.model.type.ChallengeType;
import com.adhd.focusmate.domain.model.type.ItemType;
import com.adhd.focusmate.repository.ChallengeRepository;
import com.adhd.focusmate.repository.UserItemRepository;
import com.adhd.focusmate.repository.WalletRepository;
import com.adhd.focusmate.service.verification.ChallengeVerifier;
import com.adhd.focusmate.service.verification.VerifierFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SettlementService 단위 테스트")
class SettlementServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private UserItemRepository userItemRepository;

    @Mock
    private VerifierFactory verifierFactory;

    @Mock
    private ChallengeVerifier mockVerifier;

    @InjectMocks
    private SettlementService settlementService;

    private User testUser;
    private Challenge testChallenge;
    private Wallet testWallet;
    private Item passTicketItem;
    private UserItem userPassTicket;

    private static final Long CHALLENGE_ID = 1L;
    private static final Long USER_ID = 100L;
    private static final int DEFAULT_DEPOSIT = 1000;
    private static final long SUCCESS_REWARD_POINTS = 100L;

    @BeforeEach
    void setUp() {
        // Test User
        testUser = User.builder()
                .id(USER_ID)
                .email("test@example.com")
                .build();

        // Test Challenge
        testChallenge = Challenge.builder()
                .id(CHALLENGE_ID)
                .user(testUser)
                .title("테스트 챌린지")
                .challengeType(ChallengeType.MANUAL)
                .status(ChallengeStatus.PENDING)
                .build();

        // Test Wallet (초기 잔액 0)
        testWallet = Wallet.builder()
                .id(1L)
                .user(testUser)
                .balance(0)
                .point(0L)
                .build();

        // Pass Ticket Item
        passTicketItem = Item.builder()
                .id(1L)
                .name("면제권")
                .itemType(ItemType.PASS_TICKET)
                .build();

        // User's Pass Ticket (quantity=1)
        userPassTicket = UserItem.builder()
                .id(1L)
                .user(testUser)
                .item(passTicketItem)
                .quantity(1)
                .build();
    }

    @Nested
    @DisplayName("성공 시나리오")
    class SuccessScenarios {

        @Test
        @DisplayName("검증 성공 시 예치금 환급 + 포인트 지급")
        void success_should_refund_deposit_and_award_points() {
            // Given: Verifier가 true 반환 (성공)
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(testChallenge));
            when(verifierFactory.getVerifier(ChallengeType.MANUAL)).thenReturn(mockVerifier);
            when(mockVerifier.verify(testChallenge)).thenReturn(true); // ✅ 성공
            when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(testWallet));

            // When
            SettlementResult result = settlementService.settleChallenge(CHALLENGE_ID);

            // Then: 결과 확인
            assertThat(result.getStatus()).isEqualTo(ChallengeStatus.COMPLETED);
            assertThat(result.isDepositRefunded()).isTrue();
            assertThat(result.getRefundAmount()).isEqualTo(DEFAULT_DEPOSIT);
            assertThat(result.getPointsAwarded()).isEqualTo(SUCCESS_REWARD_POINTS);
            assertThat(result.isSavedByItem()).isFalse(); // 아이템 미사용

            // Then: Wallet 상태 확인
            assertThat(testWallet.getBalance()).isEqualTo(DEFAULT_DEPOSIT);
            assertThat(testWallet.getPoint()).isEqualTo(SUCCESS_REWARD_POINTS);

            // Then: 아이템은 조회하지 않음 (중요!)
            verify(userItemRepository, never()).findByUserIdAndItemTypeForUpdate(any(), any());
        }

        @Test
        @DisplayName("성공 시 Challenge 상태가 COMPLETED로 변경")
        void success_should_update_challenge_status_to_completed() {
            // Given
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(testChallenge));
            when(verifierFactory.getVerifier(ChallengeType.MANUAL)).thenReturn(mockVerifier);
            when(mockVerifier.verify(testChallenge)).thenReturn(true);
            when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(testWallet));

            // When
            settlementService.settleChallenge(CHALLENGE_ID);

            // Then
            assertThat(testChallenge.getStatus()).isEqualTo(ChallengeStatus.COMPLETED);
        }
    }

    @Nested
    @DisplayName("실패 시나리오 - 아이템 없음")
    class FailureWithoutItemScenarios {

        @Test
        @DisplayName("검증 실패 + 아이템 없음 → 예치금 몰수")
        void failure_without_item_should_NOT_refund() {
            // Given: Verifier가 false 반환 (실패)
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(testChallenge));
            when(verifierFactory.getVerifier(ChallengeType.MANUAL)).thenReturn(mockVerifier);
            when(mockVerifier.verify(testChallenge)).thenReturn(false); // ❌ 실패
            when(userItemRepository.findByUserIdAndItemTypeForUpdate(USER_ID, ItemType.PASS_TICKET))
                    .thenReturn(Optional.empty()); // 아이템 없음
            // Wallet은 조회되지만 환급되지 않음
            when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(testWallet));

            // When
            SettlementResult result = settlementService.settleChallenge(CHALLENGE_ID);

            // Then: 실패 결과
            assertThat(result.getStatus()).isEqualTo(ChallengeStatus.FAILED);
            assertThat(result.isDepositRefunded()).isFalse();
            assertThat(result.getRefundAmount()).isEqualTo(0);
            assertThat(result.getPointsAwarded()).isEqualTo(0L);
            assertThat(result.isSavedByItem()).isFalse();

            // Then: Wallet 잔액 변동 없음
            assertThat(testWallet.getBalance()).isEqualTo(0);
            assertThat(testWallet.getPoint()).isEqualTo(0L);
        }

        @Test
        @DisplayName("실패 시 Challenge 상태가 FAILED로 변경")
        void failure_should_update_challenge_status_to_failed() {
            // Given
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(testChallenge));
            when(verifierFactory.getVerifier(ChallengeType.MANUAL)).thenReturn(mockVerifier);
            when(mockVerifier.verify(testChallenge)).thenReturn(false);
            when(userItemRepository.findByUserIdAndItemTypeForUpdate(USER_ID, ItemType.PASS_TICKET))
                    .thenReturn(Optional.empty());
            when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(testWallet));

            // When
            settlementService.settleChallenge(CHALLENGE_ID);

            // Then
            assertThat(testChallenge.getStatus()).isEqualTo(ChallengeStatus.FAILED);
        }
    }

    @Nested
    @DisplayName("부활 시나리오 - 아이템으로 구제")
    class ResurrectionScenarios {

        @Test
        @DisplayName("검증 실패 + 아이템 있음 → 아이템 소비 후 성공 처리")
        void failure_with_item_should_consume_and_succeed() {
            // Given: Verifier 실패, 하지만 아이템 보유
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(testChallenge));
            when(verifierFactory.getVerifier(ChallengeType.MANUAL)).thenReturn(mockVerifier);
            when(mockVerifier.verify(testChallenge)).thenReturn(false); // ❌ 실패
            when(userItemRepository.findByUserIdAndItemTypeForUpdate(USER_ID, ItemType.PASS_TICKET))
                    .thenReturn(Optional.of(userPassTicket)); // 아이템 있음!
            when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(testWallet));

            int originalQuantity = userPassTicket.getQuantity();

            // When
            SettlementResult result = settlementService.settleChallenge(CHALLENGE_ID);

            // Then: 결과는 성공으로 오버라이드
            assertThat(result.getStatus()).isEqualTo(ChallengeStatus.COMPLETED);
            assertThat(result.isDepositRefunded()).isTrue(); // ✅ 환급됨
            assertThat(result.getRefundAmount()).isEqualTo(DEFAULT_DEPOSIT);
            assertThat(result.getPointsAwarded()).isEqualTo(SUCCESS_REWARD_POINTS);
            assertThat(result.isSavedByItem()).isTrue(); // 아이템으로 구제됨

            // Then: 아이템 수량 감소 확인 (중요!)
            assertThat(userPassTicket.getQuantity()).isEqualTo(originalQuantity - 1);

            // Then: Wallet 상태 확인 (환급 + 포인트 지급)
            assertThat(testWallet.getBalance()).isEqualTo(DEFAULT_DEPOSIT);
            assertThat(testWallet.getPoint()).isEqualTo(SUCCESS_REWARD_POINTS);
        }

        @Test
        @DisplayName("부활 성공 시 Challenge 상태가 COMPLETED로 변경")
        void resurrection_should_update_challenge_status_to_completed() {
            // Given
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(testChallenge));
            when(verifierFactory.getVerifier(ChallengeType.MANUAL)).thenReturn(mockVerifier);
            when(mockVerifier.verify(testChallenge)).thenReturn(false);
            when(userItemRepository.findByUserIdAndItemTypeForUpdate(USER_ID, ItemType.PASS_TICKET))
                    .thenReturn(Optional.of(userPassTicket));
            when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(testWallet));

            // When
            settlementService.settleChallenge(CHALLENGE_ID);

            // Then
            assertThat(testChallenge.getStatus()).isEqualTo(ChallengeStatus.COMPLETED);
        }

        @Test
        @DisplayName("아이템 수량 0인 경우 부활 실패")
        void zero_quantity_item_should_not_save() {
            // Given: 아이템 존재하지만 수량 0
            UserItem emptyPassTicket = UserItem.builder()
                    .id(1L)
                    .user(testUser)
                    .item(passTicketItem)
                    .quantity(0) // 수량 0
                    .build();

            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(testChallenge));
            when(verifierFactory.getVerifier(ChallengeType.MANUAL)).thenReturn(mockVerifier);
            when(mockVerifier.verify(testChallenge)).thenReturn(false);
            when(userItemRepository.findByUserIdAndItemTypeForUpdate(USER_ID, ItemType.PASS_TICKET))
                    .thenReturn(Optional.of(emptyPassTicket));
            when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(testWallet));

            // When
            SettlementResult result = settlementService.settleChallenge(CHALLENGE_ID);

            // Then: 여전히 실패
            assertThat(result.getStatus()).isEqualTo(ChallengeStatus.FAILED);
            assertThat(result.isSavedByItem()).isFalse();
        }
    }

    @Nested
    @DisplayName("로직 순서 검증")
    class OrderVerification {

        @Test
        @DisplayName("실패 시 순서: 검증 → 아이템 확인 → 아이템 소비 → Wallet 조회")
        void failure_with_item_should_follow_correct_order() {
            // Given
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(testChallenge));
            when(verifierFactory.getVerifier(ChallengeType.MANUAL)).thenReturn(mockVerifier);
            when(mockVerifier.verify(testChallenge)).thenReturn(false);
            when(userItemRepository.findByUserIdAndItemTypeForUpdate(USER_ID, ItemType.PASS_TICKET))
                    .thenReturn(Optional.of(userPassTicket));
            when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(testWallet));

            // When
            settlementService.settleChallenge(CHALLENGE_ID);

            // Then: 호출 순서 검증
            var inOrder = inOrder(mockVerifier, userItemRepository, walletRepository);

            // 1. 먼저 검증
            inOrder.verify(mockVerifier).verify(testChallenge);

            // 2. 실패 시 아이템 확인
            inOrder.verify(userItemRepository).findByUserIdAndItemTypeForUpdate(USER_ID, ItemType.PASS_TICKET);

            // 3. 마지막에 Wallet 조회
            inOrder.verify(walletRepository).findByUserIdForUpdate(USER_ID);
        }

        @Test
        @DisplayName("성공 시 아이템 조회 안 함 (아이템 보존)")
        void success_should_NOT_access_item_repository() {
            // Given: 성공
            when(challengeRepository.findById(CHALLENGE_ID)).thenReturn(Optional.of(testChallenge));
            when(verifierFactory.getVerifier(ChallengeType.MANUAL)).thenReturn(mockVerifier);
            when(mockVerifier.verify(testChallenge)).thenReturn(true); // ✅ 성공
            when(walletRepository.findByUserIdForUpdate(USER_ID)).thenReturn(Optional.of(testWallet));

            // When
            settlementService.settleChallenge(CHALLENGE_ID);

            // Then: 아이템 저장소 접근 안 함
            verify(userItemRepository, never()).findByUserIdAndItemTypeForUpdate(any(), any());
        }
    }
}
