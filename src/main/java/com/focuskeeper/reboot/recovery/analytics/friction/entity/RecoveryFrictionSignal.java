package com.focuskeeper.reboot.recovery.analytics.friction.entity;

import com.focuskeeper.reboot.recovery.analytics.friction.FrictionSignalType;
import com.focuskeeper.reboot.recovery.analytics.friction.dto.FrictionSignalResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "recovery_friction_signals",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_recovery_friction_signals_user_date_type", columnNames = {"user_id", "metric_date", "signal_type"})
        }
)
public class RecoveryFrictionSignal {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "signal_type", nullable = false, length = 50)
    private FrictionSignalType signalType;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    protected RecoveryFrictionSignal() {
    }

    private RecoveryFrictionSignal(
            String id,
            String userId,
            LocalDate metricDate,
            FrictionSignalType signalType,
            boolean active,
            int evidenceCount,
            OffsetDateTime generatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.metricDate = metricDate;
        this.signalType = signalType;
        this.active = active;
        this.evidenceCount = evidenceCount;
        this.generatedAt = generatedAt;
    }

    public static RecoveryFrictionSignal create(
            String userId,
            LocalDate metricDate,
            FrictionSignalType signalType,
            boolean active,
            int evidenceCount,
            OffsetDateTime generatedAt
    ) {
        return new RecoveryFrictionSignal(
                UUID.randomUUID().toString(),
                userId,
                metricDate,
                signalType,
                active,
                evidenceCount,
                generatedAt
        );
    }

    public void regenerate(boolean active, int evidenceCount, OffsetDateTime generatedAt) {
        this.active = active;
        this.evidenceCount = evidenceCount;
        this.generatedAt = generatedAt;
    }

    public FrictionSignalResponse toResponse() {
        return new FrictionSignalResponse(
                signalType.name(),
                active,
                evidenceCount,
                generatedAt.toString()
        );
    }
}
