package com.focuskeeper.reboot.recovery.execution;

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
        name = "recovery_sessions",
        indexes = {
                @Index(name = "idx_recovery_sessions_user_status", columnList = "user_id, status")
        }
)
public class RecoverySessionEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "timebox_id", nullable = false, length = 36)
    private String timeboxId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecoverySessionStatus status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected RecoverySessionEntity() {
    }

    private RecoverySessionEntity(
            String id,
            String userId,
            String timeboxId,
            RecoverySessionStatus status,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            OffsetDateTime createdAt
    ) {
        this.id = id;
        this.userId = userId;
        this.timeboxId = timeboxId;
        this.status = status;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.createdAt = createdAt;
    }

    public static RecoverySessionEntity start(String userId, String timeboxId, OffsetDateTime startedAt) {
        return new RecoverySessionEntity(
                UUID.randomUUID().toString(),
                userId,
                timeboxId,
                RecoverySessionStatus.STARTED,
                startedAt,
                null,
                startedAt
        );
    }

    public void complete(OffsetDateTime endedAt) {
        this.status = RecoverySessionStatus.COMPLETED;
        this.endedAt = endedAt;
    }

    public void interrupt(OffsetDateTime endedAt) {
        this.status = RecoverySessionStatus.INTERRUPTED;
        this.endedAt = endedAt;
    }

    public RecoverySession toDomain() {
        return new RecoverySession(id, userId, timeboxId, status, startedAt, endedAt, createdAt);
    }

    public RecoverySessionStatus getStatus() {
        return status;
    }
}
