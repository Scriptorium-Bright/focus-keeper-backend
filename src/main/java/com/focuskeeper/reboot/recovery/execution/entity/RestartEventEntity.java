package com.focuskeeper.reboot.recovery.execution.entity;

import com.focuskeeper.reboot.recovery.execution.RestartType;
import com.focuskeeper.reboot.recovery.execution.dto.RestartEventResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "restart_events")
public class RestartEventEntity {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "failure_event_id", nullable = false, length = 36)
    private String failureEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "restart_type", nullable = false, length = 30)
    private RestartType restartType;

    @Column(name = "suggested_minutes", nullable = false)
    private int suggestedMinutes;

    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    protected RestartEventEntity() {
    }

    private RestartEventEntity(
            String id,
            String userId,
            String failureEventId,
            RestartType restartType,
            int suggestedMinutes,
            OffsetDateTime occurredAt
    ) {
        this.id = id;
        this.userId = userId;
        this.failureEventId = failureEventId;
        this.restartType = restartType;
        this.suggestedMinutes = suggestedMinutes;
        this.occurredAt = occurredAt;
    }

    public static RestartEventEntity create(
            String userId,
            String failureEventId,
            RestartType restartType,
            int suggestedMinutes,
            OffsetDateTime occurredAt
    ) {
        return new RestartEventEntity(
                UUID.randomUUID().toString(),
                userId,
                failureEventId,
                restartType,
                suggestedMinutes,
                occurredAt
        );
    }

    public RestartEventResponse toResponse() {
        return new RestartEventResponse(id, failureEventId, restartType, suggestedMinutes, occurredAt);
    }
}
