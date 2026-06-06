package com.focuskeeper.reboot.recovery.planning.entity;

import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.dto.TimeboxResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "recovery_timeboxes")
@Getter
/**
 * Big3 항목을 실제 수행 시간 구간으로 옮긴 recovery timebox 엔티티다.
 */
public class Timebox extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execution_unit_id", nullable = false)
    private ExecutionUnit executionUnit;

    @Column(name = "item_content", nullable = false, length = 200)
    private String itemContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "timebox_type", nullable = false, length = 20)
    private TimeboxType type;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(name = "first_recovery_block", nullable = false)
    private boolean firstRecoveryBlock;

    protected Timebox() {
    }

    private Timebox(
            String userId,
            ExecutionUnit executionUnit,
            String itemContent,
            TimeboxType type,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            boolean firstRecoveryBlock,
            OffsetDateTime createdAt
    ) {
        this.userId = userId;
        this.executionUnit = executionUnit;
        this.itemContent = itemContent;
        this.type = type;
        this.startAt = startAt;
        this.endAt = endAt;
        this.firstRecoveryBlock = firstRecoveryBlock;
        initializeCreatedAt(createdAt);
    }

    /**
     * 새 timebox 엔티티를 생성한다.
     */
    public static Timebox create(
            String userId,
            ExecutionUnit executionUnit,
            TimeboxType type,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            boolean firstRecoveryBlock,
            OffsetDateTime createdAt
    ) {
        return new Timebox(
                userId,
                executionUnit,
                executionUnit.getTitle(),
                type,
                startAt,
                endAt,
                firstRecoveryBlock,
                createdAt
        );
    }

    /**
     * 엔티티를 외부 응답 DTO로 변환한다.
     */
    public TimeboxResponse toResponse() {
        return new TimeboxResponse(
                id,
                executionUnit.getId(),
                itemContent,
                startAt.toString(),
                endAt.toString(),
                firstRecoveryBlock,
                type.name(),
                getCreatedAt().toString()
        );
    }

}
