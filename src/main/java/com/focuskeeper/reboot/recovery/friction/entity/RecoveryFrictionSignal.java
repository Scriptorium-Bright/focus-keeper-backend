package com.focuskeeper.reboot.recovery.friction.entity;

import com.focuskeeper.reboot.recovery.friction.FrictionSignalType;
import com.focuskeeper.reboot.recovery.friction.dto.FrictionSignalResponse;
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
/**
 * 하루 복귀 흐름에서 감지된 friction signal 한 종류를 저장하는 엔티티다.
 *
 * signal type별 활성 여부와 증거 수를 별도 row로 관리해, 날짜 단위로 다시 계산해도 추적이 쉽도록 한다.
 */
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

    /**
     * 특정 날짜/신호 유형에 대한 friction signal row를 새로 만든다.
     */
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

    /**
     * 같은 날짜/유형의 기존 signal row를 최신 계산 결과로 덮어쓴다.
     */
    public void regenerate(boolean active, int evidenceCount, OffsetDateTime generatedAt) {
        this.active = active;
        this.evidenceCount = evidenceCount;
        this.generatedAt = generatedAt;
    }

    /**
     * 이 row가 표현하는 signal 유형을 반환한다.
     */
    public FrictionSignalType getSignalType() {
        return signalType;
    }

    /**
     * 현재 날짜에 이 신호가 실제로 활성 상태인지 반환한다.
     */
    public boolean isActive() {
        return active;
    }

    /**
     * 신호를 활성화한 근거 건수 또는 누적 횟수를 반환한다.
     */
    public int getEvidenceCount() {
        return evidenceCount;
    }

    /**
     * 엔티티를 API 응답용 signal DTO로 변환한다.
     */
    public FrictionSignalResponse toResponse() {
        return new FrictionSignalResponse(
                signalType.name(),
                active,
                evidenceCount,
                generatedAt.toString()
        );
    }
}
