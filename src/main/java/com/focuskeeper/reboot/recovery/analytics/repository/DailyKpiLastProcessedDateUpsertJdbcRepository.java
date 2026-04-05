package com.focuskeeper.reboot.recovery.analytics.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
/**
 * lastProcessedDate를 PostgreSQL의 monotonic upsert로 저장하는 JDBC 저장소다.
 *
 * 같은 파이프라인/사용자 조합에 대해 더 과거 날짜가 다시 들어와도
 * 마지막 처리 날짜가 뒤로 가지 않게 `greatest(...)` 규칙을 DB 레벨에서 보장한다.
 */
public class DailyKpiLastProcessedDateUpsertJdbcRepository {

    private static final String UPSERT_SQL = """
            insert into daily_kpi_last_processed_dates (
                id,
                pipeline_key,
                user_id,
                last_processed_date,
                updated_at
            )
            values (?, ?, ?, ?, ?)
            on conflict (pipeline_key, user_id) do update set
                last_processed_date = greatest(daily_kpi_last_processed_dates.last_processed_date, excluded.last_processed_date),
                updated_at = case
                    when excluded.last_processed_date >= daily_kpi_last_processed_dates.last_processed_date
                        then excluded.updated_at
                    else daily_kpi_last_processed_dates.updated_at
                end
            """;

    private final JdbcTemplate jdbcTemplate;

    public DailyKpiLastProcessedDateUpsertJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 파이프라인의 마지막 처리 날짜를 monotonic upsert 방식으로 저장한다.
     *
     * 새 날짜가 더 미래면 전진시키고, 과거 재처리 날짜면 기존 값을 유지한다.
     * updatedAt 역시 실제로 날짜가 전진하는 경우에만 갱신해 운영자가 상태 회귀를 오해하지 않게 한다.
     */
    public void upsert(
            String pipelineKey,
            String userId,
            LocalDate lastProcessedDate,
            OffsetDateTime updatedAt
    ) {
        jdbcTemplate.update(
                UPSERT_SQL,
                UUID.randomUUID().toString(),
                pipelineKey,
                userId,
                lastProcessedDate,
                updatedAt
        );
    }
}
