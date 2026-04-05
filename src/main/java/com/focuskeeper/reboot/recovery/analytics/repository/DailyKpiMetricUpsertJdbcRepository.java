package com.focuskeeper.reboot.recovery.analytics.repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
/**
 * daily KPI mart를 PostgreSQL `ON CONFLICT`로 저장하기 위한 JDBC 전용 저장소다.
 *
 * 자연키(user_id, metric_date) 기준으로 같은 KPI를 여러 번 계산해도
 * insert/update 분기를 애플리케이션에서 나누지 않고 DB가 원자적으로 처리하게 만든다.
 */
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

    /**
     * KPI mart 한 행을 자연키 기준으로 upsert한다.
     *
     * 새 row면 insert하고, 이미 같은 사용자/날짜 row가 있으면 최신 계산값으로 덮어쓴다.
     * returning id는 기존 row identity를 유지한 채 저장이 성공했는지를 DB 한 문장으로 끝내기 위한 장치다.
     */
    public void upsert(
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
        jdbcTemplate.queryForObject(
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
