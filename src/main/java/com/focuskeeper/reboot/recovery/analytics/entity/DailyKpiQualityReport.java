package com.focuskeeper.reboot.recovery.analytics.entity;

import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiQualityResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "daily_kpi_quality_reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_daily_kpi_quality_reports_user_date", columnNames = {"user_id", "metric_date"})
        }
)
public class DailyKpiQualityReport {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(nullable = false)
    private boolean healthy;

    @Column(name = "duplicate_restart_link_count", nullable = false)
    private int duplicateRestartLinkCount;

    @Column(name = "orphan_restart_count", nullable = false)
    private int orphanRestartCount;

    @Column(name = "restart_before_failure_count", nullable = false)
    private int restartBeforeFailureCount;

    @Column(name = "late_restart_link_count", nullable = false)
    private int lateRestartLinkCount;

    @Column(name = "break_session_reference_count", nullable = false)
    private int breakSessionReferenceCount;

    @Column(name = "missing_timebox_reference_count", nullable = false)
    private int missingTimeboxReferenceCount;

    @Column(name = "timezone_mismatch_count", nullable = false)
    private int timezoneMismatchCount;

    @Column(name = "total_issue_count", nullable = false)
    private int totalIssueCount;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    protected DailyKpiQualityReport() {
    }

    private DailyKpiQualityReport(
            String id,
            String userId,
            LocalDate metricDate,
            boolean healthy,
            int duplicateRestartLinkCount,
            int orphanRestartCount,
            int restartBeforeFailureCount,
            int lateRestartLinkCount,
            int breakSessionReferenceCount,
            int missingTimeboxReferenceCount,
            int timezoneMismatchCount,
            int totalIssueCount,
            OffsetDateTime generatedAt
    ) {
        this.id = id;
        this.userId = userId;
        this.metricDate = metricDate;
        this.healthy = healthy;
        this.duplicateRestartLinkCount = duplicateRestartLinkCount;
        this.orphanRestartCount = orphanRestartCount;
        this.restartBeforeFailureCount = restartBeforeFailureCount;
        this.lateRestartLinkCount = lateRestartLinkCount;
        this.breakSessionReferenceCount = breakSessionReferenceCount;
        this.missingTimeboxReferenceCount = missingTimeboxReferenceCount;
        this.timezoneMismatchCount = timezoneMismatchCount;
        this.totalIssueCount = totalIssueCount;
        this.generatedAt = generatedAt;
    }

    public static DailyKpiQualityReport create(
            String userId,
            LocalDate metricDate,
            boolean healthy,
            int duplicateRestartLinkCount,
            int orphanRestartCount,
            int restartBeforeFailureCount,
            int lateRestartLinkCount,
            int breakSessionReferenceCount,
            int missingTimeboxReferenceCount,
            int timezoneMismatchCount,
            int totalIssueCount,
            OffsetDateTime generatedAt
    ) {
        return new DailyKpiQualityReport(
                UUID.randomUUID().toString(),
                userId,
                metricDate,
                healthy,
                duplicateRestartLinkCount,
                orphanRestartCount,
                restartBeforeFailureCount,
                lateRestartLinkCount,
                breakSessionReferenceCount,
                missingTimeboxReferenceCount,
                timezoneMismatchCount,
                totalIssueCount,
                generatedAt
        );
    }

    public void regenerate(
            boolean healthy,
            int duplicateRestartLinkCount,
            int orphanRestartCount,
            int restartBeforeFailureCount,
            int lateRestartLinkCount,
            int breakSessionReferenceCount,
            int missingTimeboxReferenceCount,
            int timezoneMismatchCount,
            int totalIssueCount,
            OffsetDateTime generatedAt
    ) {
        this.healthy = healthy;
        this.duplicateRestartLinkCount = duplicateRestartLinkCount;
        this.orphanRestartCount = orphanRestartCount;
        this.restartBeforeFailureCount = restartBeforeFailureCount;
        this.lateRestartLinkCount = lateRestartLinkCount;
        this.breakSessionReferenceCount = breakSessionReferenceCount;
        this.missingTimeboxReferenceCount = missingTimeboxReferenceCount;
        this.timezoneMismatchCount = timezoneMismatchCount;
        this.totalIssueCount = totalIssueCount;
        this.generatedAt = generatedAt;
    }

    public DailyKpiQualityResponse toResponse() {
        return new DailyKpiQualityResponse(
                id,
                userId,
                metricDate.toString(),
                healthy,
                duplicateRestartLinkCount,
                orphanRestartCount,
                restartBeforeFailureCount,
                lateRestartLinkCount,
                breakSessionReferenceCount,
                missingTimeboxReferenceCount,
                timezoneMismatchCount,
                totalIssueCount,
                generatedAt.toString()
        );
    }
}
