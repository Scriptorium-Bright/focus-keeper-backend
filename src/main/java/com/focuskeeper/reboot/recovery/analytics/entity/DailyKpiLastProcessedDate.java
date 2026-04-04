package com.focuskeeper.reboot.recovery.analytics.entity;

import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiLastProcessedDateResponse;
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
        name = "daily_kpi_last_processed_dates",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_daily_kpi_last_processed_dates_pipeline_user", columnNames = {"pipeline_key", "user_id"})
        }
)
public class DailyKpiLastProcessedDate {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "pipeline_key", nullable = false, length = 50)
    private String pipelineKey;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "last_processed_date", nullable = false)
    private LocalDate lastProcessedDate;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected DailyKpiLastProcessedDate() {
    }

    private DailyKpiLastProcessedDate(
            String id,
            String pipelineKey,
            String userId,
            LocalDate lastProcessedDate,
            OffsetDateTime updatedAt
    ) {
        this.id = id;
        this.pipelineKey = pipelineKey;
        this.userId = userId;
        this.lastProcessedDate = lastProcessedDate;
        this.updatedAt = updatedAt;
    }

    /**
     * 파이프라인별 최초 lastProcessedDate 레코드를 생성한다.
     */
    public static DailyKpiLastProcessedDate create(
            String pipelineKey,
            String userId,
            LocalDate lastProcessedDate,
            OffsetDateTime updatedAt
    ) {
        return new DailyKpiLastProcessedDate(
                UUID.randomUUID().toString(),
                pipelineKey,
                userId,
                lastProcessedDate,
                updatedAt
        );
    }

    /**
     * 마지막 처리 날짜와 갱신 시각을 최신 값으로 전진시킨다.
     */
    public void advance(LocalDate lastProcessedDate, OffsetDateTime updatedAt) {
        if (this.lastProcessedDate == null || !lastProcessedDate.isBefore(this.lastProcessedDate)) {
            this.lastProcessedDate = lastProcessedDate;
            this.updatedAt = updatedAt;
        }
    }

    /**
     * 마지막 처리 날짜 엔티티를 조회 응답 DTO로 변환한다.
     */
    public DailyKpiLastProcessedDateResponse toResponse() {
        return new DailyKpiLastProcessedDateResponse(
                pipelineKey,
                userId,
                lastProcessedDate.toString(),
                updatedAt.toString()
        );
    }
}
