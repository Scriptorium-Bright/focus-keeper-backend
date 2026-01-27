package com.adhd.focusmate.test.domain.model;

import com.adhd.focusmate.domain.model.Wallet;

import com.adhd.focusmate.common.exception.InsufficientBalanceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletTest {

    @Test
    @DisplayName("지갑 잔액 충전 성공")
    void addBalance_success() {
        // given
        Wallet wallet = Wallet.builder().balance(0).build();

        // when
        wallet.addBalance(100);

        // then
        assertThat(wallet.getBalance()).isEqualTo(100);
    }

    @Test
    @DisplayName("지갑 잔액 차감 성공")
    void subtractBalance_success() {
        // given
        Wallet wallet = Wallet.builder().balance(100).build();

        // when
        wallet.subtractBalance(50);

        // then
        assertThat(wallet.getBalance()).isEqualTo(50);
    }

    @Test
    @DisplayName("잔액 부족 시 예외 발생 (DDD 검증)")
    void subtractBalance_fail_insufficient() {
        // given
        Wallet wallet = Wallet.builder().balance(30).build();

        // when & then
        assertThatThrownBy(() -> wallet.subtractBalance(50))
                .isInstanceOf(InsufficientBalanceException.class);
    }
}
