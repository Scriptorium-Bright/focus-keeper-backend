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
/**
 * failure event 이후 사용자가 실제로 재시작을 눌렀는지 남기는 원천 restart event 엔티티다.
 */
public class RestartEvent {

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

    protected RestartEvent() {
    }

    private RestartEvent(
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

    /**
     * 새 재시작 이벤트를 생성한다.
     */
    public static RestartEvent create(
            String userId,
            String failureEventId,
            RestartType restartType,
            int suggestedMinutes,
            OffsetDateTime occurredAt
    ) {
        return new RestartEvent(
                UUID.randomUUID().toString(),
                userId,
                failureEventId,
                restartType,
                suggestedMinutes,
                occurredAt
        );
    }

    /**
     * 엔티티를 외부 응답 DTO로 변환한다.
     */
    public RestartEventResponse toResponse() {
        return new RestartEventResponse(id, failureEventId, restartType, suggestedMinutes, occurredAt);
    }
}
