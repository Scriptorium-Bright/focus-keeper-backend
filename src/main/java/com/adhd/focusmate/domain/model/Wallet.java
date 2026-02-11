package com.adhd.focusmate.domain.model;

import com.adhd.focusmate.common.exception.BusinessException;
import com.adhd.focusmate.common.exception.ErrorCode;
import com.adhd.focusmate.common.exception.InsufficientBalanceException;
import com.adhd.focusmate.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
public class Wallet extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    /**
     * 예치금/잔액 (현금성)
     */
    @Builder.Default
    @Column(name = "balance")
    private Integer balance = 0;

    /**
     * 포인트 (보상용, 아이템 구매용)
     */
    @Builder.Default
    @Column(name = "point")
    private Long point = 0L;

    // ===== Balance (예치금) 메서드 =====

    public void addBalance(int amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Amount must be positive");
        }
        try {
            this.balance = Math.addExact(this.balance, amount);
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Balance overflow");
        }
    }

    public void subtractBalance(int amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Amount must be positive");
        }
        if (this.balance < amount) {
            throw new InsufficientBalanceException();
        }
        this.balance -= amount;
    }

    /**
     * 예치금 환급 (정산 시 사용)
     */
    public void refund(int amount) {
        addBalance(amount);
    }

    // ===== Point (포인트) 메서드 =====

    public void addPoint(long amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Amount must be positive");
        }
        try {
            this.point = Math.addExact(this.point, amount);
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Point overflow");
        }
    }

    public void subtractPoint(long amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Amount must be positive");
        }
        if (this.point < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_BALANCE, "Insufficient points");
        }
        this.point -= amount;
    }
}
