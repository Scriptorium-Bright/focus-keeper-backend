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
        name = "failure_events",
        indexes = {
                @Index(name = "idx_failure_events_user_occurred_at", columnList = "user_id, occurred_at")
        }
)
public class FailureEventEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "session_id", nullable = false, length = 36)
    private String sessionId;

    @Column(name = "timebox_id", nullable = false, length = 36)
    private String timeboxId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FailureReason reason;

    @Column(length = 200)
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    protected FailureEventEntity() {
    }

    private FailureEventEntity(
            String id,
            String userId,
            String sessionId,
            String timeboxId,
            FailureReason reason,
            String note,
            OffsetDateTime occurredAt
    ) {
        this.id = id;
        this.userId = userId;
        this.sessionId = sessionId;
        this.timeboxId = timeboxId;
        this.reason = reason;
        this.note = note;
        this.occurredAt = occurredAt;
    }

    public static FailureEventEntity create(
            String userId,
            String sessionId,
            String timeboxId,
            FailureReason reason,
            String note,
            OffsetDateTime occurredAt
    ) {
        return new FailureEventEntity(
                UUID.randomUUID().toString(),
                userId,
                sessionId,
                timeboxId,
                reason,
                note,
                occurredAt
        );
    }

    public FailureEvent toDomain() {
        return new FailureEvent(id, userId, sessionId, timeboxId, reason, note, occurredAt);
    }
}
