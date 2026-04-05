package com.focuskeeper.reboot.recovery.friction.entity;

import com.focuskeeper.reboot.recovery.friction.dto.FailureHourDistributionResponse;
import com.focuskeeper.reboot.recovery.friction.dto.FailureHourMetricResponse;
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
/**
 * 하루 실패 패턴의 대표 요약 정보를 담는 상위 리포트 엔티티다.
 *
 * 총 실패 수, 가장 많이 실패한 시각, 사람이 읽기 쉬운 peak window를 저장한다.
 */
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

    /**
     * 하루 실패 분포의 첫 요약 리포트를 생성한다.
     */
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

    /**
     * 같은 날짜의 기존 리포트를 최신 분석 결과로 갱신한다.
     */
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

    /**
     * 해당 날짜의 총 실패 건수를 반환한다.
     */
    public int getTotalFailureCount() {
        return totalFailureCount;
    }

    /**
     * 가장 실패가 많이 몰린 시(hour)를 반환한다.
     */
    public Integer getPeakFailureHour() {
        return peakFailureHour;
    }

    /**
     * peak hour를 넓은 해석 구간으로 바꾼 window 문자열을 반환한다.
     */
    public String getPeakFailureWindow() {
        return peakFailureWindow;
    }

    /**
     * 요약 리포트와 시간대별 metric 목록을 합쳐 최종 조회 응답으로 변환한다.
     */
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
