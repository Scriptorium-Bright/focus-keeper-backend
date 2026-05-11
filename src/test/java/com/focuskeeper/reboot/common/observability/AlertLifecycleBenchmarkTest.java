package com.focuskeeper.reboot.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Alert Lifecycle FSM의 중복 제거·동시성 안전·상태 전이 정확성을 정량 검증한다.
 *
 * 포트폴리오 수치 근거용 벤치마크 테스트.
 */
class AlertLifecycleBenchmarkTest {

    private CountingTransitionPublisher transitionPublisher;
    private OperationsAlertService operationsAlertService;

    @BeforeEach
    void setUp() {
        transitionPublisher = new CountingTransitionPublisher();
        operationsAlertService = new OperationsAlertService(transitionPublisher);
    }

    /**
     * 동일 pipeline/stage/userId로 10,000건의 장애 신호를 보내도
     * alert 레코드는 1건만 유지되고, OPENED 이벤트는 최초 1회만 발행된다.
     */
    @Test
    void repeatedFailureReportsProduceExactlyOneAlertAndOneOpenedEvent() {
        int totalReports = 10_000;

        long startNanos = System.nanoTime();
        for (int i = 0; i < totalReports; i++) {
            operationsAlertService.reportBatchFailure(
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "launch",
                    "benchmark-user",
                    "failure #" + i,
                    Map.of("metricDate", "2026-05-08", "attempt", Integer.toString(i))
            );
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(operationsAlertService.getAlerts(false, null)).hasSize(1);
        assertThat(operationsAlertService.getAlerts(true, null)).hasSize(1);

        // occurrenceCount는 최초 1 + 이후 9,999 refresh = 10,000
        assertThat(operationsAlertService.getAlerts(true, null).getFirst().occurrenceCount())
                .isEqualTo(totalReports);

        // 전이 이벤트는 최초 OPENED 1건만 발행
        assertThat(transitionPublisher.openedCount.get()).isEqualTo(1);
        assertThat(transitionPublisher.totalEventCount.get()).isEqualTo(1);

        System.out.printf("""
                === 중복 제거 벤치마크 ===
                총 장애 신호: %,d건
                생성된 alert: %d건
                발행된 OPENED 이벤트: %d건
                불필요한 이벤트 발행: %d건
                occurrenceCount: %,d
                소요 시간: %dms
                %n""", totalReports, 1, 1, 0, totalReports, elapsedMs);
    }

    /**
     * 10개 스레드가 동시에 같은 alert key로 1,000건씩 총 10,000건을 쏴도
     * alert 레코드는 정확히 1건, OPENED 이벤트도 정확히 1건이다.
     */
    @Test
    void concurrentReportsFromMultipleThreadsProduceExactlyOneAlert() throws InterruptedException {
        int threadCount = 10;
        int reportsPerThread = 1_000;
        int totalReports = threadCount * reportsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        long startNanos = System.nanoTime();
        for (int t = 0; t < threadCount; t++) {
            final int threadId = t;
            executor.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < reportsPerThread; i++) {
                    operationsAlertService.reportBatchFailure(
                            OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                            "launch",
                            "concurrent-user",
                            "thread-" + threadId + "-report-" + i,
                            Map.of("metricDate", "2026-05-08")
                    );
                }
                done.countDown();
            });
        }

        ready.await();
        go.countDown();
        done.await();
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        executor.shutdown();

        assertThat(operationsAlertService.getAlerts(false, null)).hasSize(1);

        int actualOccurrences = operationsAlertService.getAlerts(true, null).getFirst().occurrenceCount();
        assertThat(actualOccurrences).isEqualTo(totalReports);

        assertThat(transitionPublisher.openedCount.get()).isEqualTo(1);

        System.out.printf("""
                === 동시성 안전 벤치마크 ===
                스레드 수: %d
                스레드당 호출: %,d건
                총 장애 신호: %,d건
                생성된 alert: %d건
                발행된 OPENED 이벤트: %d건
                occurrenceCount 정확도: %,d / %,d
                데이터 유실: %d건
                소요 시간: %dms
                %n""", threadCount, reportsPerThread, totalReports, 1, 1,
                actualOccurrences, totalReports,
                totalReports - actualOccurrences, elapsedMs);
    }

    /**
     * OPENED → ESCALATED → RESOLVED → REOPENED → RESOLVED 전체 라이프사이클을
     * 정확한 순서로 전이하는지 검증한다.
     */
    @Test
    void fullLifecycleTransitionsAreEmittedInCorrectOrder() {
        LocalDate today = LocalDate.now();

        // 1) OPENED (WARNING)
        operationsAlertService.evaluateProcessingLag(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                "lifecycle-user",
                today.minusDays(2)
        );

        // 2) ESCALATED (WARNING → CRITICAL)
        operationsAlertService.evaluateProcessingLag(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                "lifecycle-user",
                today.minusDays(3)
        );

        // 3) RESOLVED
        operationsAlertService.evaluateProcessingLag(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                "lifecycle-user",
                today
        );

        // 4) REOPENED
        operationsAlertService.evaluateProcessingLag(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                "lifecycle-user",
                today.minusDays(2)
        );

        // 5) RESOLVED again
        operationsAlertService.evaluateProcessingLag(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                "lifecycle-user",
                today
        );

        assertThat(transitionPublisher.eventTypes).containsExactly(
                OperationsAlertTransitionType.OPENED,
                OperationsAlertTransitionType.ESCALATED,
                OperationsAlertTransitionType.RESOLVED,
                OperationsAlertTransitionType.REOPENED,
                OperationsAlertTransitionType.RESOLVED
        );

        assertThat(operationsAlertService.getAlerts(false, null).getFirst().reopenCount()).isEqualTo(1);

        System.out.printf("""
                === 라이프사이클 전이 검증 ===
                전이 시퀀스: OPENED → ESCALATED → RESOLVED → REOPENED → RESOLVED
                전이 이벤트 총 발행: %d건
                reopenCount: %d
                상태 전이 정확도: 100%%
                %n""", transitionPublisher.totalEventCount.get(),
                operationsAlertService.getAlerts(false, null).getFirst().reopenCount());
    }

    /**
     * N개의 서로 다른 pipeline/userId 조합이 각각 독립된 alert를 유지하는지 검증한다.
     */
    @Test
    void distinctKeysProduceIndependentAlerts() {
        int distinctUsers = 100;
        int reportsPerUser = 50;

        long startNanos = System.nanoTime();
        for (int u = 0; u < distinctUsers; u++) {
            for (int r = 0; r < reportsPerUser; r++) {
                operationsAlertService.reportBatchFailure(
                        OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                        "launch",
                        "user-" + u,
                        "failure",
                        Map.of("metricDate", "2026-05-08")
                );
            }
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(operationsAlertService.getAlerts(true, null)).hasSize(distinctUsers);
        assertThat(transitionPublisher.openedCount.get()).isEqualTo(distinctUsers);

        System.out.printf("""
                === 독립 alert 격리 벤치마크 ===
                사용자 수: %d
                사용자당 장애 신호: %d건
                총 호출: %,d건
                생성된 alert: %d건 (사용자당 1건)
                발행된 OPENED 이벤트: %d건
                alert 간 간섭: 0건
                소요 시간: %dms
                %n""", distinctUsers, reportsPerUser,
                distinctUsers * reportsPerUser, distinctUsers, distinctUsers, elapsedMs);
    }

    private static final class CountingTransitionPublisher implements OperationsAlertTransitionPublisher {

        private final AtomicInteger totalEventCount = new AtomicInteger();
        private final AtomicInteger openedCount = new AtomicInteger();
        private final AtomicInteger escalatedCount = new AtomicInteger();
        private final AtomicInteger resolvedCount = new AtomicInteger();
        private final AtomicInteger reopenedCount = new AtomicInteger();
        private final List<OperationsAlertTransitionType> eventTypes = Collections.synchronizedList(new ArrayList<>());

        @Override
        public void publish(OperationsAlertTransitionEvent event) {
            totalEventCount.incrementAndGet();
            eventTypes.add(event.eventType());
            switch (event.eventType()) {
                case OPENED -> openedCount.incrementAndGet();
                case ESCALATED -> escalatedCount.incrementAndGet();
                case RESOLVED -> resolvedCount.incrementAndGet();
                case REOPENED -> reopenedCount.incrementAndGet();
            }
        }
    }
}
