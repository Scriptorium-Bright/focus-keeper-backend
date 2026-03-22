package com.focuskeeper.reboot.recovery.analytics.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DailyKpiMetricUpsertJdbcRepository {

    private static final String UPSERT_SQL = """
            insert into daily_kpi_metrics (
                id,
                user_id,
                metric_date,
                activation,
                failure_count,
                recovery24,
                recovery48,
                restart_count_24,
                restart_count_48,
                ttr_minutes,
                cycle_completion_rate,
                plan_execution_rate,
                planned_work_minutes,
                actual_work_minutes,
                estimation_error_minutes,
                generated_at
            )
            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            on conflict (user_id, metric_date) do update set
                activation = excluded.activation,
                failure_count = excluded.failure_count,
                recovery24 = excluded.recovery24,
                recovery48 = excluded.recovery48,
                restart_count_24 = excluded.restart_count_24,
                restart_count_48 = excluded.restart_count_48,
                ttr_minutes = excluded.ttr_minutes,
                cycle_completion_rate = excluded.cycle_completion_rate,
                plan_execution_rate = excluded.plan_execution_rate,
                planned_work_minutes = excluded.planned_work_minutes,
                actual_work_minutes = excluded.actual_work_minutes,
                estimation_error_minutes = excluded.estimation_error_minutes,
                generated_at = excluded.generated_at
            returning id
            """;

    private final JdbcTemplate jdbcTemplate;

    public DailyKpiMetricUpsertJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String upsert(
            String userId,
            LocalDate metricDate,
            boolean activation,
            int failureCount,
            boolean recovery24,
            boolean recovery48,
            int restartCount24,
            int restartCount48,
            Long ttrMinutes,
            BigDecimal cycleCompletionRate,
            BigDecimal planExecutionRate,
            long plannedWorkMinutes,
            long actualWorkMinutes,
            long estimationErrorMinutes,
            OffsetDateTime generatedAt
    ) {
        return jdbcTemplate.queryForObject(
                UPSERT_SQL,
                String.class,
                UUID.randomUUID().toString(),
                userId,
                metricDate,
                activation,
                failureCount,
                recovery24,
                recovery48,
                restartCount24,
                restartCount48,
                ttrMinutes,
                cycleCompletionRate,
                planExecutionRate,
                plannedWorkMinutes,
                actualWorkMinutes,
                estimationErrorMinutes,
                generatedAt
        );
    }
}
