package com.focuskeeper.reboot.recovery.execution.entity;

import com.focuskeeper.reboot.recovery.execution.constant.FailureReason;
import com.focuskeeper.reboot.recovery.execution.dto.FailureEventResponse;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.OffsetDateTime;

@Entity
@Table(
        name = "failure_events",
        indexes = {
                @Index(name = "idx_failure_events_user_occurred_at", columnList = "user_id, occurred_at")
        }
)
/**
 * 복귀 세션이 왜 끊겼는지를 남기는 원천 failure event 엔티티다.
 */
@Getter
public class FailureEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
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

    protected FailureEvent() {
    }

    private FailureEvent(
            String userId,
            String sessionId,
            String timeboxId,
            FailureReason reason,
            String note,
            OffsetDateTime occurredAt
    ) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.timeboxId = timeboxId;
        this.reason = reason;
        this.note = note;
        this.occurredAt = occurredAt;
    }

    /**
     * 새로운 실패 체크인 이벤트를 생성한다.
     */
    public static FailureEvent create (
            String userId,
            String sessionId,
            String timeboxId,
            FailureReason reason,
            String note,
            OffsetDateTime occurredAt
    ) {
        return new FailureEvent(
                userId,
                sessionId,
                timeboxId,
                reason,
                note,
                occurredAt
        );
    }

    /**
     * 엔티티를 외부 응답 DTO로 변환한다.
     */
    public FailureEventResponse toResponse() {
        return new FailureEventResponse(id, sessionId, timeboxId, reason, note, occurredAt);
    }
}
