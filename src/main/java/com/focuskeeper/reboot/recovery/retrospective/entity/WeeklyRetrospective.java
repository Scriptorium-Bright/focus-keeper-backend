package com.focuskeeper.reboot.recovery.retrospective.entity;

import com.focuskeeper.reboot.recovery.retrospective.dto.WeeklyRetrospectiveResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "weekly_retrospectives")
public class WeeklyRetrospective {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart;

    @Column(name = "week_end", nullable = false)
    private LocalDate weekEnd;

    @Column(name = "session_started_count", nullable = false)
    private long sessionStartedCount;

    @Column(name = "session_completed_count", nullable = false)
    private long sessionCompletedCount;

    @Column(name = "session_interrupted_count", nullable = false)
    private long sessionInterruptedCount;

    @Column(name = "failure_count", nullable = false)
    private long failureCount;

    @Column(name = "restart_count", nullable = false)
    private long restartCount;

    @Column(name = "dominant_failure_reason", length = 30)
    private String dominantFailureReason;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    protected WeeklyRetrospective() {
    }

    private WeeklyRetrospective(
            String id,
            String userId,
            LocalDate weekStart,
            LocalDate weekEnd,
            long sessionStartedCount,
            long sessionCompletedCount,
            long sessionInterruptedCount,
            long failureCount,
            long restartCount,
            String dominantFailureReason,
            String summary,
            OffsetDateTime generatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.weekStart = weekStart;
        this.weekEnd = weekEnd;
        this.sessionStartedCount = sessionStartedCount;
        this.sessionCompletedCount = sessionCompletedCount;
        this.sessionInterruptedCount = sessionInterruptedCount;
        this.failureCount = failureCount;
        this.restartCount = restartCount;
        this.dominantFailureReason = dominantFailureReason;
        this.summary = summary;
        this.generatedAt = generatedAt;
    }

    public static WeeklyRetrospective create(
            String userId,
            LocalDate weekStart,
            LocalDate weekEnd,
            long sessionStartedCount,
            long sessionCompletedCount,
            long sessionInterruptedCount,
            long failureCount,
            long restartCount,
            String dominantFailureReason,
            String summary,
            OffsetDateTime generatedAt
    ) {
        return new WeeklyRetrospective(
                UUID.randomUUID().toString(),
                userId,
                weekStart,
                weekEnd,
                sessionStartedCount,
                sessionCompletedCount,
                sessionInterruptedCount,
                failureCount,
                restartCount,
                dominantFailureReason,
                summary,
                generatedAt
        );
    }

    public void regenerate(
            long sessionStartedCount,
            long sessionCompletedCount,
            long sessionInterruptedCount,
            long failureCount,
            long restartCount,
            String dominantFailureReason,
            String summary,
            OffsetDateTime generatedAt
    ) {
        this.sessionStartedCount = sessionStartedCount;
        this.sessionCompletedCount = sessionCompletedCount;
        this.sessionInterruptedCount = sessionInterruptedCount;
        this.failureCount = failureCount;
        this.restartCount = restartCount;
        this.dominantFailureReason = dominantFailureReason;
        this.summary = summary;
        this.generatedAt = generatedAt;
    }

    public WeeklyRetrospectiveResponse toResponse() {
        return new WeeklyRetrospectiveResponse(
                id,
                weekStart.toString(),
                weekEnd.toString(),
                sessionStartedCount,
                sessionCompletedCount,
                sessionInterruptedCount,
                failureCount,
                restartCount,
                dominantFailureReason,
                summary,
                generatedAt.toString()
        );
    }
}
