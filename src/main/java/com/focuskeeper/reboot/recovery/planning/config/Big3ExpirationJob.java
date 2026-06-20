package com.focuskeeper.reboot.recovery.planning.config;

import com.focuskeeper.reboot.common.observability.OperationsMetricRecorder;
import com.focuskeeper.reboot.recovery.planning.service.Big3Service;
import io.micrometer.core.instrument.Timer;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class Big3ExpirationJob {

    private static final Logger log = LoggerFactory.getLogger(Big3ExpirationJob.class);

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Big3Service big3Service;
    private final OperationsMetricRecorder operationsMetricRecorder;

    public Big3ExpirationJob(
            Big3Service big3Service,
            OperationsMetricRecorder operationsMetricRecorder
    ) {
        this.big3Service = big3Service;
        this.operationsMetricRecorder = operationsMetricRecorder;
    }

    public ExpirationJobResult run() {
        return run("manual");
    }

    public ExpirationJobResult run(String trigger) {
        String runId = UUID.randomUUID().toString();
        if (!running.compareAndSet(false, true)) {
            operationsMetricRecorder.recordExpirationSkipped("already_running");
            log.info(
                    "job=big3_expiration runId={} trigger={} status=skipped reason=already_running",
                    runId,
                    trigger
            );
            return ExpirationJobResult.skipped("already_running");
        }

        Timer.Sample sample = operationsMetricRecorder.startSample();
        operationsMetricRecorder.setExpirationRunning(true);
        long startedAt = System.nanoTime();
        try {
            int processedItems = big3Service.expireLastWeekTasks();
            operationsMetricRecorder.recordExpirationSuccess(sample, processedItems);
            log.info(
                    "job=big3_expiration runId={} trigger={} status=success processedItems={} durationMs={}",
                    runId,
                    trigger,
                    processedItems,
                    elapsedMillis(startedAt)
            );
            return ExpirationJobResult.succeeded(processedItems);
        } catch (RuntimeException | Error exception) {
            operationsMetricRecorder.recordExpirationFailure(sample);
            log.error(
                    "job=big3_expiration runId={} trigger={} status=failure durationMs={} errorCode={}",
                    runId,
                    trigger,
                    elapsedMillis(startedAt),
                    exception.getClass().getSimpleName(),
                    exception
            );
            throw exception;
        } finally {
            operationsMetricRecorder.setExpirationRunning(false);
            running.set(false);
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
