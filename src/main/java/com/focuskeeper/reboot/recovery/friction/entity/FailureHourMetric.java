package com.focuskeeper.reboot.recovery.friction.entity;

import com.focuskeeper.reboot.recovery.friction.dto.FailureHourMetricResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "failure_hour_metrics",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_failure_hour_metrics_user_date_hour", columnNames = {"user_id", "metric_date", "local_hour"})
        }
)
/**
 * 하루 실패 건수를 시간대별로 분해한 세부 metric row다.
 *
 * FailureHourReport가 하루 요약이라면, 이 엔티티는 각 시(hour)에 몇 번 실패했는지를 저장한다.
 */
public class FailureHourMetric {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "local_hour", nullable = false)
    private int localHour;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "failure_ratio", nullable = false, precision = 8, scale = 4)
    private BigDecimal failureRatio;

    @Column(name = "peak_hour", nullable = false)
    private boolean peakHour;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    protected FailureHourMetric() {
    }

    private FailureHourMetric(
            String id,
            String userId,
            LocalDate metricDate,
            int localHour,
            int failureCount,
            BigDecimal failureRatio,
            boolean peakHour,
            OffsetDateTime generatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.metricDate = metricDate;
        this.localHour = localHour;
        this.failureCount = failureCount;
        this.failureRatio = failureRatio;
        this.peakHour = peakHour;
        this.generatedAt = generatedAt;
    }

    /**
     * 특정 시간대의 실패 건수와 비율을 저장할 metric row를 새로 만든다.
     */
    public static FailureHourMetric create(
            String userId,
            LocalDate metricDate,
            int localHour,
            int failureCount,
            BigDecimal failureRatio,
            boolean peakHour,
            OffsetDateTime generatedAt
    ) {
        return new FailureHourMetric(
                UUID.randomUUID().toString(),
                userId,
                metricDate,
                localHour,
                failureCount,
                failureRatio,
                peakHour,
                generatedAt
        );
    }

    /**
     * 엔티티를 API 응답용 시간대별 metric DTO로 변환한다.
     */
    public FailureHourMetricResponse toResponse() {
        return new FailureHourMetricResponse(
                localHour,
                failureCount,
                failureRatio,
                peakHour
        );
    }
}
