package com.adhd.focusmate.domain.model;

import com.adhd.focusmate.domain.common.BaseEntity;
import com.adhd.focusmate.domain.model.type.CreditLogReason;
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
public class CreditLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id")
    private Wallet wallet;

    @Column(name = "amount")
    private Integer amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason")
    private CreditLogReason reason;
}
