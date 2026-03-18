package com.focuskeeper.reboot.recovery.planning;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
public class TimeboxEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "item_id", nullable = false, length = 36)
    private String itemId;

    @Column(name = "item_content", nullable = false, length = 200)
    private String itemContent;

    @Column(name = "start_at", nullable = false)
    private OffsetDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private OffsetDateTime endAt;

    @Column(name = "first_recovery_block", nullable = false)
    private boolean firstRecoveryBlock;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected TimeboxEntity() {
    }

    private TimeboxEntity(
            String id,
            String userId,
            String itemId,
            String itemContent,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            boolean firstRecoveryBlock,
            OffsetDateTime createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.itemId = itemId;
        this.itemContent = itemContent;
        this.startAt = startAt;
        this.endAt = endAt;
        this.firstRecoveryBlock = firstRecoveryBlock;
        this.createdAt = createdAt;
    }

    public static TimeboxEntity create(
            String userId,
            String itemId,
            String itemContent,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            boolean firstRecoveryBlock,
            OffsetDateTime createdAt
    ) {
        return new TimeboxEntity(
                UUID.randomUUID().toString(),
                userId,
                itemId,
                itemContent,
                startAt,
                endAt,
                firstRecoveryBlock,
                createdAt
        );
    }

    public TimeboxDto toDto() {
        return new TimeboxDto(id, userId, itemId, itemContent, startAt, endAt, firstRecoveryBlock, createdAt);
    }
}
