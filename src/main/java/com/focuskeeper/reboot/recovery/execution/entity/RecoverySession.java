package com.focuskeeper.reboot.recovery.execution.entity;

import com.focuskeeper.reboot.recovery.execution.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.dto.RecoverySessionResponse;
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
/**
 * 특정 timebox 실행을 나타내는 복귀 세션 엔티티다.
 *
 * started/completed/interrupted 상태 전이를 통해 사용자가 실제로 계획한 블록을 수행했는지 기록한다.
 */
public class RecoverySession {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "timebox_id", nullable = false, length = 36)
    private String timeboxId;
    // RecoverySession은 Timebox를 소유하거나 탐색하는 객체가 아니라 “어떤 timebox를 실행했는지”만 기록하면 되므로, JPA 연관관계 대신 timeboxId 참조로 느슨하게 연결했다.

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private RecoverySessionStatus status;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected RecoverySession() {
    }

    private RecoverySession(
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

    /**
     * 새 복귀 세션을 STARTED 상태로 생성한다.
     */
    public static RecoverySession start(String userId, String timeboxId, OffsetDateTime startedAt) {
        return new RecoverySession(
                UUID.randomUUID().toString(),
                userId,
                timeboxId,
                RecoverySessionStatus.STARTED,
                startedAt,
                null,
                startedAt
        );
    }

    /**
     * 세션을 완료 상태로 전이한다.
     */
    public void complete(OffsetDateTime endedAt) {
        this.status = RecoverySessionStatus.COMPLETED;
        this.endedAt = endedAt;
    }

    /**
     * 세션을 중단 상태로 전이한다.
     */
    public void interrupt(OffsetDateTime endedAt) {
        this.status = RecoverySessionStatus.INTERRUPTED;
        this.endedAt = endedAt;
    }

    /**
     * 엔티티를 API 응답 DTO로 변환한다.
     */
    public RecoverySessionResponse toResponse() {
        return new RecoverySessionResponse(
                id,
                timeboxId,
                status.name(),
                startedAt.toString(),
                endedAt == null ? null : endedAt.toString(),
                createdAt.toString()
        );
    }

    /**
     * 현재 세션 상태를 반환한다.
     */
    public RecoverySessionStatus getStatus() {
        return status;
    }
}
