package com.focuskeeper.reboot.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * planning과 execution의 핵심 write 흐름만 기록하는 공통 기술 컴포넌트다.
 */
@Component
public class CoreMetricRecorder {

    private final MeterRegistry meterRegistry;
    private final AtomicInteger expirationRunning = new AtomicInteger();
    private final AtomicLong expirationLastSuccessTimestampSeconds = new AtomicLong();
    private final AtomicLong expirationLastDurationNanos = new AtomicLong();

    public CoreMetricRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("focusloop_expiration_running", expirationRunning, AtomicInteger::get)
                .description("Whether the Big3 expiration job is currently running")
                .register(meterRegistry);
        Gauge.builder(
                        "focusloop_expiration_last_success_timestamp_seconds",
                        expirationLastSuccessTimestampSeconds,
                        AtomicLong::get
                )
                .baseUnit(TimeUnit.SECONDS.name().toLowerCase())
                .register(meterRegistry);
        Gauge.builder(
                        "focusloop_expiration_last_duration_seconds",
                        expirationLastDurationNanos,
                        value -> value.get() / 1_000_000_000.0
                )
                .baseUnit(TimeUnit.SECONDS.name().toLowerCase())
                .register(meterRegistry);
    }

    public Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    public void recordExecutionAction(Timer.Sample sample, String action, String status) {
        Counter.builder("focusloop_execution_actions_total")
                .tag("action", action)
                .tag("status", status)
                .register(meterRegistry)
                .increment();
        sample.stop(meterRegistry.timer(
                "focusloop_execution_action_duration",
                "action", action,
                "status", status
        ));
    }

    public void setExpirationRunning(boolean running) {
        expirationRunning.set(running ? 1 : 0);
    }

    public void recordExpirationSuccess(Timer.Sample sample, int processedItems) {
        recordExpirationRun(sample, "success");
        DistributionSummary.builder("focusloop_expiration_processed_items")
                .register(meterRegistry)
                .record(processedItems);
        expirationLastSuccessTimestampSeconds.set(Instant.now().getEpochSecond());
    }

    public void recordExpirationFailure(Timer.Sample sample) {
        recordExpirationRun(sample, "failure");
    }

    public void recordExpirationSkipped(String reason) {
        Counter.builder("focusloop_expiration_skipped_runs_total")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    private void recordExpirationRun(Timer.Sample sample, String status) {
        Counter.builder("focusloop_expiration_runs_total")
                .tag("status", status)
                .register(meterRegistry)
                .increment();
        Timer timer = Timer.builder("focusloop_expiration_duration")
                .tag("status", status)
                .publishPercentileHistogram()
                .serviceLevelObjectives(Duration.ofSeconds(30), Duration.ofMinutes(1), Duration.ofMinutes(5))
                .register(meterRegistry);
        expirationLastDurationNanos.set(sample.stop(timer));
    }
}
