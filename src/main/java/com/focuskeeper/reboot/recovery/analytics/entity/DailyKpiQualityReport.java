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
/**
 * 하루치 KPI 계산 결과가 얼마나 신뢰 가능한지 요약한 품질 리포트 엔티티다.
 *
 * KPI 값 자체와 분리해서 유지함으로써, 숫자는 존재하더라도 참조 무결성이나 timezone 문제가 있었는지를 따로 판단할 수 있다.
 */
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

    /**
     * 새로 생성된 KPI 품질 검사 결과를 저장할 엔티티를 만든다.
     */
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

    /**
     * 같은 날짜의 기존 품질 리포트를 최신 검사 결과로 갱신한다.
     */
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

    /**
     * 품질 리포트 엔티티를 API 응답 DTO로 변환한다.
     */
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
