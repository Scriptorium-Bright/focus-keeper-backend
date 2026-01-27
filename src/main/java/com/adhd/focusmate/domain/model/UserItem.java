package com.adhd.focusmate.domain.model;

import com.adhd.focusmate.common.exception.BusinessException;
import com.adhd.focusmate.common.exception.ErrorCode;
import com.adhd.focusmate.domain.common.BaseEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/**
 * 사용자 인벤토리 엔티티
 * 사용자가 보유한 아이템과 수량
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
@Table(name = "user_item", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "user_id", "item_id" })
})
public class UserItem extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(name = "quantity", nullable = false)
    @Builder.Default
    private Integer quantity = 0;

    /**
     * 아이템 수량 증가
     */
    public void addQuantity(int amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Amount must be positive");
        }
        this.quantity += amount;
    }

    /**
     * 아이템 소비 (데이터 무결성 보장)
     * 
     * @throws BusinessException 수량 부족 시
     */
    public void consume(int amount) {
        if (amount <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "Amount must be positive");
        }
        if (this.quantity < amount) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_ITEM,
                    "Not enough items. Required: " + amount + ", Available: " + this.quantity);
        }
        this.quantity -= amount;
    }

    /**
     * 아이템 보유 여부 확인
     */
    public boolean hasItem(int amount) {
        return this.quantity >= amount;
    }
}
