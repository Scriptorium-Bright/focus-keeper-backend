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
/**
 * 파이프라인이 한 사용자에 대해 마지막으로 어디까지 처리 완료했는지 기록하는 상태 엔티티다.
 *
 * 스트리밍 watermark처럼 late event를 제어하는 개념이 아니라,
 * 배치/백필 파이프라인이 어디까지 성공적으로 반영됐는지 저장하는 운영 메타데이터다.
 */
public class DailyKpiLastProcessedDate {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "pipeline_key", nullable = false, length = 50)
    private String pipelineKey;
    // pipeline_key는 metric과 조인하려는 key가 아니라, “어떤 파이프라인의 처리 진도/상태인지”를 식별하는 운영 메타데이터 key
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
     *
     * 더 과거 날짜가 들어오면 무시해 상태 회귀를 막는다.
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
