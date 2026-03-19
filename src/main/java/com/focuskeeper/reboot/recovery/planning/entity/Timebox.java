package com.focuskeeper.reboot.recovery.planning.entity;

import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.dto.TimeboxResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "recovery_timeboxes",
        indexes = {
                @Index(name = "idx_recovery_timeboxes_user_start_at", columnList = "user_id, start_at")
        }
)
public class Timebox {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "item_id", nullable = false, length = 36)
    private String itemId;

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

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Timebox() {
    }

    private Timebox(
            String id,
            String userId,
            String itemId,
            String itemContent,
            TimeboxType type,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            boolean firstRecoveryBlock,
            OffsetDateTime createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.itemId = itemId;
        this.itemContent = itemContent;
        this.type = type;
        this.startAt = startAt;
        this.endAt = endAt;
        this.firstRecoveryBlock = firstRecoveryBlock;
        this.createdAt = createdAt;
    }

    public static Timebox create(
            String userId,
            String itemId,
            String itemContent,
            TimeboxType type,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            boolean firstRecoveryBlock,
            OffsetDateTime createdAt
    ) {
        return new Timebox(
                UUID.randomUUID().toString(),
                userId,
                itemId,
                itemContent,
                type,
                startAt,
                endAt,
                firstRecoveryBlock,
                createdAt
        );
    }

    public TimeboxResponse toResponse() {
        return new TimeboxResponse(
                id,
                itemId,
                itemContent,
                startAt.toString(),
                endAt.toString(),
                firstRecoveryBlock,
                type.name(),
                createdAt.toString()
        );
    }

    public String getId() {
        return id;
    }

    public OffsetDateTime getStartAt() {
        return startAt;
    }

    public OffsetDateTime getEndAt() {
        return endAt;
    }

    public TimeboxType getType() {
        return type;
    }
}
