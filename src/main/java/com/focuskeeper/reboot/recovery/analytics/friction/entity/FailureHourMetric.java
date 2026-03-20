package com.focuskeeper.reboot.recovery.analytics.friction.entity;

import com.focuskeeper.reboot.recovery.analytics.friction.dto.FailureHourMetricResponse;
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

    public FailureHourMetricResponse toResponse() {
        return new FailureHourMetricResponse(
                localHour,
                failureCount,
                failureRatio,
                peakHour
        );
    }
}
