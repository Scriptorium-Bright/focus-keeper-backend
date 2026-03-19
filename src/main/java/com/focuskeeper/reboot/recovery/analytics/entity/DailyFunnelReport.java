package com.focuskeeper.reboot.recovery.analytics.entity;

import com.focuskeeper.reboot.recovery.analytics.dto.DailyFunnelResponse;
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
        name = "daily_funnel_reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_daily_funnel_reports_metric_date", columnNames = {"metric_date"})
        }
)
public class DailyFunnelReport {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "brain_dump_users", nullable = false)
    private long brainDumpUsers;

    @Column(name = "big3_users", nullable = false)
    private long big3Users;

    @Column(name = "timebox_users", nullable = false)
    private long timeboxUsers;

    @Column(name = "session_started_users", nullable = false)
    private long sessionStartedUsers;

    @Column(name = "failure_users", nullable = false)
    private long failureUsers;

    @Column(name = "restart_users", nullable = false)
    private long restartUsers;

    @Column(name = "big3_selection_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal big3SelectionRate;

    @Column(name = "timebox_planning_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal timeboxPlanningRate;

    @Column(name = "session_start_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal sessionStartRate;

    @Column(name = "failure_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal failureRate;

    @Column(name = "restart_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal restartRate;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    protected DailyFunnelReport() {
    }

    private DailyFunnelReport(
            String id,
            LocalDate metricDate,
            long brainDumpUsers,
            long big3Users,
            long timeboxUsers,
            long sessionStartedUsers,
            long failureUsers,
            long restartUsers,
            BigDecimal big3SelectionRate,
            BigDecimal timeboxPlanningRate,
            BigDecimal sessionStartRate,
            BigDecimal failureRate,
            BigDecimal restartRate,
            OffsetDateTime generatedAt
    ) {
        this.id = id;
        this.metricDate = metricDate;
        this.brainDumpUsers = brainDumpUsers;
        this.big3Users = big3Users;
        this.timeboxUsers = timeboxUsers;
        this.sessionStartedUsers = sessionStartedUsers;
        this.failureUsers = failureUsers;
        this.restartUsers = restartUsers;
        this.big3SelectionRate = big3SelectionRate;
        this.timeboxPlanningRate = timeboxPlanningRate;
        this.sessionStartRate = sessionStartRate;
        this.failureRate = failureRate;
        this.restartRate = restartRate;
        this.generatedAt = generatedAt;
    }

    public static DailyFunnelReport create(
            LocalDate metricDate,
            long brainDumpUsers,
            long big3Users,
            long timeboxUsers,
            long sessionStartedUsers,
            long failureUsers,
            long restartUsers,
            BigDecimal big3SelectionRate,
            BigDecimal timeboxPlanningRate,
            BigDecimal sessionStartRate,
            BigDecimal failureRate,
            BigDecimal restartRate,
            OffsetDateTime generatedAt
    ) {
        return new DailyFunnelReport(
                UUID.randomUUID().toString(),
                metricDate,
                brainDumpUsers,
                big3Users,
                timeboxUsers,
                sessionStartedUsers,
                failureUsers,
                restartUsers,
                big3SelectionRate,
                timeboxPlanningRate,
                sessionStartRate,
                failureRate,
                restartRate,
                generatedAt
        );
    }

    public void regenerate(
            long brainDumpUsers,
            long big3Users,
            long timeboxUsers,
            long sessionStartedUsers,
            long failureUsers,
            long restartUsers,
            BigDecimal big3SelectionRate,
            BigDecimal timeboxPlanningRate,
            BigDecimal sessionStartRate,
            BigDecimal failureRate,
            BigDecimal restartRate,
            OffsetDateTime generatedAt
    ) {
        this.brainDumpUsers = brainDumpUsers;
        this.big3Users = big3Users;
        this.timeboxUsers = timeboxUsers;
        this.sessionStartedUsers = sessionStartedUsers;
        this.failureUsers = failureUsers;
        this.restartUsers = restartUsers;
        this.big3SelectionRate = big3SelectionRate;
        this.timeboxPlanningRate = timeboxPlanningRate;
        this.sessionStartRate = sessionStartRate;
        this.failureRate = failureRate;
        this.restartRate = restartRate;
        this.generatedAt = generatedAt;
    }

    public DailyFunnelResponse toResponse() {
        return new DailyFunnelResponse(
                id,
                metricDate.toString(),
                brainDumpUsers,
                big3Users,
                timeboxUsers,
                sessionStartedUsers,
                failureUsers,
                restartUsers,
                big3SelectionRate,
                timeboxPlanningRate,
                sessionStartRate,
                failureRate,
                restartRate,
                generatedAt.toString()
        );
    }
}
