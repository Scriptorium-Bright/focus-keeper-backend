package com.adhd.focusmate.domain.model;

import com.adhd.focusmate.domain.common.BaseEntity;
import com.adhd.focusmate.domain.model.type.ItemType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/**
 * 아이템 정의 엔티티
 * 범용 아이템 - 특정 챌린지 타입에 종속되지 않음
 */
@Entity
@Getter
@Builder
@NoArgsConstructor(access = PROTECTED)
@AllArgsConstructor
@Table(name = "item")
public class Item extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_type", nullable = false)
    private ItemType itemType;

    @Column(name = "price")
    private Integer price; // 구매 가격 (포인트)

    @Column(name = "active")
    @Builder.Default
    private Boolean active = true;
}
