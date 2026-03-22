package com.focuskeeper.reboot.recovery.analytics.repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DailyKpiWatermarkUpsertJdbcRepository {

    private static final String UPSERT_SQL = """
            insert into daily_kpi_watermarks (
                id,
                pipeline_key,
                user_id,
                last_processed_date,
                updated_at
            )
            values (?, ?, ?, ?, ?)
            on conflict (pipeline_key, user_id) do update set
                last_processed_date = greatest(daily_kpi_watermarks.last_processed_date, excluded.last_processed_date),
                updated_at = case
                    when excluded.last_processed_date >= daily_kpi_watermarks.last_processed_date
                        then excluded.updated_at
                    else daily_kpi_watermarks.updated_at
                end
            """;

    private final JdbcTemplate jdbcTemplate;

    public DailyKpiWatermarkUpsertJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

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
