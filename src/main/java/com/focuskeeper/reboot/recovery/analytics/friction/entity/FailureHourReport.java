package com.focuskeeper.reboot.recovery.analytics.friction.entity;

import com.focuskeeper.reboot.recovery.analytics.friction.dto.FailureHourDistributionResponse;
import com.focuskeeper.reboot.recovery.analytics.friction.dto.FailureHourMetricResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
        name = "failure_hour_reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_failure_hour_reports_user_date", columnNames = {"user_id", "metric_date"})
        }
)
public class FailureHourReport {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "total_failure_count", nullable = false)
    private int totalFailureCount;

    @Column(name = "peak_failure_hour")
    private Integer peakFailureHour;

    @Column(name = "peak_failure_window", length = 20)
    private String peakFailureWindow;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    protected FailureHourReport() {
    }

    private FailureHourReport(
            String id,
            String userId,
            LocalDate metricDate,
            int totalFailureCount,
            Integer peakFailureHour,
            String peakFailureWindow,
            OffsetDateTime generatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.metricDate = metricDate;
        this.totalFailureCount = totalFailureCount;
        this.peakFailureHour = peakFailureHour;
        this.peakFailureWindow = peakFailureWindow;
        this.generatedAt = generatedAt;
    }

    public static FailureHourReport create(
            String userId,
            LocalDate metricDate,
            int totalFailureCount,
            Integer peakFailureHour,
            String peakFailureWindow,
            OffsetDateTime generatedAt
    ) {
        return new FailureHourReport(
                UUID.randomUUID().toString(),
                userId,
                metricDate,
                totalFailureCount,
                peakFailureHour,
                peakFailureWindow,
                generatedAt
        );
    }

    public void regenerate(
            int totalFailureCount,
            Integer peakFailureHour,
            String peakFailureWindow,
            OffsetDateTime generatedAt
    ) {
        this.totalFailureCount = totalFailureCount;
        this.peakFailureHour = peakFailureHour;
        this.peakFailureWindow = peakFailureWindow;
        this.generatedAt = generatedAt;
    }

    public int getTotalFailureCount() {
        return totalFailureCount;
    }

    public Integer getPeakFailureHour() {
        return peakFailureHour;
    }

    public String getPeakFailureWindow() {
        return peakFailureWindow;
    }

    public FailureHourDistributionResponse toResponse(List<FailureHourMetricResponse> hourlyMetrics) {
        return new FailureHourDistributionResponse(
                id,
                userId,
                metricDate.toString(),
                totalFailureCount,
                peakFailureHour,
                peakFailureWindow,
                generatedAt.toString(),
                hourlyMetrics
        );
    }
}
