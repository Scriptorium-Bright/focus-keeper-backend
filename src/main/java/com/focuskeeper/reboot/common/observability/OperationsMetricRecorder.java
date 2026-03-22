package com.focuskeeper.reboot.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class OperationsMetricRecorder {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, AtomicInteger> dqIssueGaugeByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> watermarkLagGaugeByUser = new ConcurrentHashMap<>();

    public OperationsMetricRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    public void recordRecoveryLoopAction(Timer.Sample sample, String action, String status) {
        Counter.builder("reboot_recovery_loop_actions_total")
                .description("Recovery loop API action count")
                .tag("action", action)
                .tag("status", status)
                .register(meterRegistry)
                .increment();

        Timer.builder("reboot_recovery_loop_action_duration")
                .description("Recovery loop API action duration")
                .tag("action", action)
                .tag("status", status)
                .register(meterRegistry);
        sample.stop(meterRegistry.timer(
                "reboot_recovery_loop_action_duration",
                "action",
                action,
                "status",
                status
        ));
    }

    public void recordBatchStage(Timer.Sample sample, String pipelineKey, String stage, String status) {
        sample.stop(meterRegistry.timer(
                "reboot_batch_duration",
                "pipeline",
                pipelineKey,
                "stage",
                stage,
                "status",
                status
        ));

        if ("failure".equals(status)) {
            Counter.builder("reboot_batch_failed_runs_total")
                    .description("Failed batch stage count")
                    .tag("pipeline", pipelineKey)
                    .tag("stage", stage)
                    .register(meterRegistry)
                    .increment();
        }
    }

    public void recordDqIssueCount(String pipelineKey, String userId, int totalIssueCount) {
        dqGauge(pipelineKey, userId).set(totalIssueCount);
    }

    public void recordWatermarkLagSeconds(String pipelineKey, String userId, long lagSeconds) {
        watermarkGauge(pipelineKey, userId).set(Math.max(lagSeconds, 0));
    }

    public void recordBackfillProcessedDays(String pipelineKey, int processedDays) {
        meterRegistry.summary(
                        "reboot_backfill_processed_days",
                        "pipeline",
                        pipelineKey
                )
                .record(processedDays);
    }

    private AtomicInteger dqGauge(String pipelineKey, String userId) {
        String key = pipelineKey + ":" + userId;
        return dqIssueGaugeByUser.computeIfAbsent(key, unused -> {
            AtomicInteger gauge = new AtomicInteger();
            Gauge.builder("reboot_dq_issue_count", gauge, AtomicInteger::get)
                    .description("Current DQ issue count")
                    .tag("pipeline", pipelineKey)
                    .tag("user_id", userId)
                    .register(meterRegistry);
            return gauge;
        });
    }

    private AtomicLong watermarkGauge(String pipelineKey, String userId) {
        String key = pipelineKey + ":" + userId;
        return watermarkLagGaugeByUser.computeIfAbsent(key, unused -> {
            AtomicLong gauge = new AtomicLong();
            Gauge.builder("reboot_batch_watermark_lag_seconds", gauge, AtomicLong::get)
                    .description("Current watermark lag in seconds")
                    .tag("pipeline", pipelineKey)
                    .tag("user_id", userId)
                    .baseUnit(TimeUnit.SECONDS.name().toLowerCase())
                    .register(meterRegistry);
            return gauge;
        });
    }
}
