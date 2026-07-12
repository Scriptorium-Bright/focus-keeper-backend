package com.focuskeeper.reboot.recovery.planning.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.focuskeeper.reboot.common.metrics.CoreMetricRecorder;
import com.focuskeeper.reboot.recovery.planning.constant.ExpirationJobStatus;
import com.focuskeeper.reboot.recovery.planning.service.Big3Service;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class Big3ExpirationJobTest {

    @Test
    void successfulRunRecordsProcessedItemsDurationAndLastSuccess() {
        Big3Service big3Service = mock(Big3Service.class);
        when(big3Service.expireLastWeekTasks()).thenReturn(42);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Big3ExpirationJob job = job(big3Service, meterRegistry);

        ExpirationJobResult result = job.run("test");

        assertThat(result.expirationJobStatus()).isEqualTo(ExpirationJobStatus.SUCCEEDED);
        assertThat(result.processedItems()).isEqualTo(42);
        assertThat(result.reason()).isNull();
        assertThat(counter(meterRegistry, "focusloop_expiration_runs_total", "status", "success"))
                .isEqualTo(1.0);
        assertThat(meterRegistry.get("focusloop_expiration_duration").tag("status", "success").timer().count())
                .isEqualTo(1);
        assertThat(meterRegistry.get("focusloop_expiration_processed_items").summary().totalAmount())
                .isEqualTo(42.0);
        assertThat(meterRegistry.get("focusloop_expiration_last_success_timestamp_seconds").gauge().value())
                .isPositive();
        assertThat(meterRegistry.get("focusloop_expiration_last_duration_seconds").gauge().value())
                .isGreaterThanOrEqualTo(0.0);
        assertThat(meterRegistry.get("focusloop_expiration_running").gauge().value()).isZero();
    }

    @Test
    void failedRunRecordsFailureAndReleasesRunningState() {
        Big3Service big3Service = mock(Big3Service.class);
        when(big3Service.expireLastWeekTasks()).thenThrow(new IllegalStateException("database unavailable"));
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Big3ExpirationJob job = job(big3Service, meterRegistry);

        assertThatThrownBy(() -> job.run("test"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");

        assertThat(counter(meterRegistry, "focusloop_expiration_runs_total", "status", "failure"))
                .isEqualTo(1.0);
        assertThat(meterRegistry.get("focusloop_expiration_duration").tag("status", "failure").timer().count())
                .isEqualTo(1);
        assertThat(meterRegistry.get("focusloop_expiration_last_duration_seconds").gauge().value())
                .isGreaterThanOrEqualTo(0.0);
        assertThat(meterRegistry.get("focusloop_expiration_running").gauge().value()).isZero();
    }

    @Test
    void concurrentRunIsSkippedWhileFirstRunOwnsTheJob() throws Exception {
        Big3Service big3Service = mock(Big3Service.class);
        CountDownLatch enteredService = new CountDownLatch(1);
        CountDownLatch releaseService = new CountDownLatch(1);
        doAnswer(invocation -> {
            enteredService.countDown();
            if (!releaseService.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("service release timed out");
            }
            return 7;
        }).when(big3Service).expireLastWeekTasks();

        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Big3ExpirationJob job = job(big3Service, meterRegistry);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        try {
            Future<ExpirationJobResult> firstRun = executor.submit(() -> job.run("test"));
            assertThat(enteredService.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(meterRegistry.get("focusloop_expiration_running").gauge().value()).isEqualTo(1.0);

            ExpirationJobResult skipped = job.run("test");
            assertThat(skipped.expirationJobStatus()).isEqualTo(ExpirationJobStatus.SKIPPED);
            assertThat(skipped.reason()).isEqualTo("already_running");

            releaseService.countDown();
            ExpirationJobResult succeeded = firstRun.get(5, TimeUnit.SECONDS);
            assertThat(succeeded.processedItems()).isEqualTo(7);

            verify(big3Service).expireLastWeekTasks();
            assertThat(counter(
                    meterRegistry,
                    "focusloop_expiration_skipped_runs_total",
                    "reason",
                    "already_running"
            )).isEqualTo(1.0);
            assertThat(counter(meterRegistry, "focusloop_expiration_runs_total", "status", "success"))
                    .isEqualTo(1.0);
            assertThat(meterRegistry.get("focusloop_expiration_running").gauge().value()).isZero();
        } finally {
            releaseService.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Big3ExpirationJob job(Big3Service big3Service, SimpleMeterRegistry meterRegistry) {
        return new Big3ExpirationJob(
                big3Service,
                new CoreMetricRecorder(meterRegistry)
        );
    }

    private double counter(
            SimpleMeterRegistry meterRegistry,
            String name,
            String tagKey,
            String tagValue
    ) {
        return meterRegistry.get(name)
                .tag(tagKey, tagValue)
                .counter()
                .count();
    }
}
