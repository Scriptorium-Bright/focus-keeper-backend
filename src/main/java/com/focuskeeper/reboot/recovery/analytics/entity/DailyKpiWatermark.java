package com.focuskeeper.reboot.recovery.analytics.entity;

import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiWatermarkResponse;
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
        name = "daily_kpi_watermarks",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_daily_kpi_watermarks_pipeline_user", columnNames = {"pipeline_key", "user_id"})
        }
)
public class DailyKpiWatermark {

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

    protected DailyKpiWatermark() {
    }

    private DailyKpiWatermark(
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
     * 파이프라인별 최초 워터마크 레코드를 생성한다.
     */
    public static DailyKpiWatermark create(
            String pipelineKey,
            String userId,
            LocalDate lastProcessedDate,
            OffsetDateTime updatedAt
    ) {
        return new DailyKpiWatermark(
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
     * 워터마크 엔티티를 조회 응답 DTO로 변환한다.
     */
    public DailyKpiWatermarkResponse toResponse() {
        return new DailyKpiWatermarkResponse(
                pipelineKey,
                userId,
                lastProcessedDate.toString(),
                updatedAt.toString()
        );
    }
}
