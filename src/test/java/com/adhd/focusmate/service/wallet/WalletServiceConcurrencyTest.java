package com.adhd.focusmate.service.wallet;

import com.adhd.focusmate.domain.model.User;
import com.adhd.focusmate.domain.model.Wallet;
import com.adhd.focusmate.domain.model.type.CreditLogReason;
import com.adhd.focusmate.dto.wallet.CreditDeductRequest;
import com.adhd.focusmate.repository.UserRepository;
import com.adhd.focusmate.repository.WalletRepository;
import com.adhd.focusmate.support.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class WalletServiceConcurrencyTest extends AbstractIntegrationTest {

    @Autowired
    private WalletService walletService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Concurrency: 동시에 100건의 포인트 차감 요청 시 잔액이 정확해야 한다")
    void testConcurrentDeduct() throws InterruptedException {
        // Given
        User user = userRepository.save(User.builder().email("concurrency@test.com").nickname("Runner").build());
        Wallet wallet = walletRepository.save(Wallet.builder().user(user).balance(10000).build()); // 10,000 포인트

        int threadCount = 100;
        int deductAmount = 100; // 100 * 100 = 10,000 (전액 소진 예상)
        ExecutorService executorService = Executors.newFixedThreadPool(32);
        CountDownLatch latch = new CountDownLatch(threadCount);

        // When
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    walletService.deduct(new CreditDeductRequest(user.getId(), deductAmount, CreditLogReason.USE_ITEM));
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();

        // Then
        Wallet updatedWallet = walletRepository.findById(wallet.getId()).get();
        // 만약 락이 없다면 Race Condition으로 인해 0이 되지 않을 수 있음 (일부 차감 누락)
        assertThat(updatedWallet.getBalance()).isEqualTo(0);
    }
}
