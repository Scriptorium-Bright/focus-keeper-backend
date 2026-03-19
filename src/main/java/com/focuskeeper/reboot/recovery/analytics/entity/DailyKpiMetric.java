package com.focuskeeper.reboot.recovery.analytics.entity;

import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiResponse;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "daily_kpi_metrics",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_daily_kpi_metrics_user_date", columnNames = {"user_id", "metric_date"})
        }
)
public class DailyKpiMetric {

    @Id
    @Column(nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(nullable = false)
    private boolean activation;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "recovery24", nullable = false)
    private boolean recovery24;

    @Column(name = "recovery48", nullable = false)
    private boolean recovery48;

    @Column(name = "restart_count_24", nullable = false)
    private int restartCount24;

    @Column(name = "restart_count_48", nullable = false)
    private int restartCount48;

    @Column(name = "ttr_minutes")
    private Long ttrMinutes;

    @Column(name = "cycle_completion_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal cycleCompletionRate;

    @Column(name = "plan_execution_rate", nullable = false, precision = 8, scale = 4)
    private BigDecimal planExecutionRate;

    @Column(name = "planned_work_minutes", nullable = false)
    private long plannedWorkMinutes;

    @Column(name = "actual_work_minutes", nullable = false)
    private long actualWorkMinutes;

    @Column(name = "estimation_error_minutes", nullable = false)
    private long estimationErrorMinutes;

    @Column(name = "generated_at", nullable = false)
    private OffsetDateTime generatedAt;

    protected DailyKpiMetric() {
    }

    private DailyKpiMetric(
            String id,
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
        this.id = id;
        this.userId = userId;
        this.metricDate = metricDate;
        this.activation = activation;
        this.failureCount = failureCount;
        this.recovery24 = recovery24;
        this.recovery48 = recovery48;
        this.restartCount24 = restartCount24;
        this.restartCount48 = restartCount48;
        this.ttrMinutes = ttrMinutes;
        this.cycleCompletionRate = cycleCompletionRate;
        this.planExecutionRate = planExecutionRate;
        this.plannedWorkMinutes = plannedWorkMinutes;
        this.actualWorkMinutes = actualWorkMinutes;
        this.estimationErrorMinutes = estimationErrorMinutes;
        this.generatedAt = generatedAt;
    }

    public static DailyKpiMetric create(
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
        return new DailyKpiMetric(
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

    public void regenerate(
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
        this.activation = activation;
        this.failureCount = failureCount;
        this.recovery24 = recovery24;
        this.recovery48 = recovery48;
        this.restartCount24 = restartCount24;
        this.restartCount48 = restartCount48;
        this.ttrMinutes = ttrMinutes;
        this.cycleCompletionRate = cycleCompletionRate;
        this.planExecutionRate = planExecutionRate;
        this.plannedWorkMinutes = plannedWorkMinutes;
        this.actualWorkMinutes = actualWorkMinutes;
        this.estimationErrorMinutes = estimationErrorMinutes;
        this.generatedAt = generatedAt;
    }

    public DailyKpiResponse toResponse() {
        return new DailyKpiResponse(
                id,
                userId,
                metricDate.toString(),
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
                generatedAt.toString()
        );
    }
}
