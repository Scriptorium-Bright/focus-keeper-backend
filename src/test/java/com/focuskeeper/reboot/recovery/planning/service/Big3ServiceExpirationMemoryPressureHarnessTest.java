package com.focuskeeper.reboot.recovery.planning.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Tag("perf")
@SpringBootTest
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "PERF_EXPIRATION_MEMORY_ENABLED", matches = "true")
class Big3ServiceExpirationMemoryPressureHarnessTest {

    private static final String INBOX_ITEM_ID = "oom-harness-inbox";
    private static final String USER_ID = "oom-memory-harness-user";

    // Run with:
//     PERF_EXPIRATION_ROWS=300000 PERF_EXPIRATION_MAX_HEAP=512m \
//       ./gradlew expirationMemoryHarness --no-daemon --rerun-tasks

    @Autowired
    private Big3Service big3Service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        truncateDedicatedDatabaseRows();
    }

    @AfterEach
    void tearDown() {
        truncateDedicatedDatabaseRows();
    }

    @Test
    @DisplayName("과거 OPEN 항목 대량 만료 시 peak heap, GC, 처리 시간을 측정한다")
    void measuresExpirationMemoryPressureAndFinalState() {
        int targetRows = readPositiveInt("PERF_EXPIRATION_ROWS", 1_000_000);
        LocalDate currentWeekStart = LocalDate.now()
                .with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        LocalDate pastWeekStart = currentWeekStart.minusWeeks(2);
        OffsetDateTime createdAt = OffsetDateTime.now().minusWeeks(2);

        seedInboxItem(createdAt);
        seedExpiredCandidates(targetRows, pastWeekStart, createdAt);
        seedControlRows(currentWeekStart, pastWeekStart, createdAt);

        assertThat(countByStatusAndWeek("OPEN", pastWeekStart)).isEqualTo(targetRows);

        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        long maxHeapBytes = memory.getHeapMemoryUsage().getMax();
        GcSnapshot gcBefore = captureGc();
        long heapBeforeBytes = memory.getHeapMemoryUsage().getUsed();

        printScenario(targetRows, pastWeekStart, currentWeekStart, maxHeapBytes);

        long startedAt = System.nanoTime();

        HeapSampler sampler = HeapSampler.start(memory);
        try {
            big3Service.expireLastWeekTasks();
        } catch (OutOfMemoryError error) {
            System.err.printf(
                    "%n[OOM 판정] OOM 발생%n"
                            + "- 조건: 만료 대상 %,d건, JVM 최대 heap %s%n"
                            + "- 의미: 현재 JPA 조회/dirty checking 방식이 이 조건의 메모리 한계를 초과했습니다.%n"
                            + "- Gradle task는 실패가 정상이며, 이 결과를 변경 전 failure drill로 기록합니다.%n%n",
                    targetRows,
                    formatBytes(maxHeapBytes)
            );
            throw error;
        } finally {
            sampler.close();
        }
        long peakHeapBytes = sampler.peakBytes();

        long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        long heapAfterBytes = memory.getHeapMemoryUsage().getUsed();
        GcSnapshot gcAfter = captureGc();

        long expiredRows = countByStatusAndWeek("EXPIRED", pastWeekStart);
        long remainingPastOpenRows = countByStatusAndWeek("OPEN", pastWeekStart);
        long currentWeekOpenRows = countByIdAndStatus("oom-control-current", "OPEN");
        long completedControlRows = countByIdAndStatus("oom-control-completed", "COMPLETED");

        assertThat(expiredRows).isEqualTo(targetRows);
        assertThat(remainingPastOpenRows).isZero();
        assertThat(currentWeekOpenRows).isOne();
        assertThat(completedControlRows).isOne();

        printKoreanResult(
                targetRows,
                maxHeapBytes,
                elapsedMillis,
                heapBeforeBytes,
                peakHeapBytes,
                heapAfterBytes,
                gcAfter.count() - gcBefore.count(),
                gcAfter.timeMillis() - gcBefore.timeMillis(),
                expiredRows,
                remainingPastOpenRows
        );

        System.out.printf(
                "EXPIRATION_MEMORY rows=%d elapsedMs=%d "
                        + "heapBytes[max=%d,before=%d,peak=%d,after=%d,peakIncrease=%d] "
                        + "gc[countDelta=%d,timeMsDelta=%d] "
                        + "final[expired=%d,pastOpen=%d]%n",
                targetRows,
                elapsedMillis,
                maxHeapBytes,
                heapBeforeBytes,
                peakHeapBytes,
                heapAfterBytes,
                Math.max(0L, peakHeapBytes - heapBeforeBytes),
                gcAfter.count() - gcBefore.count(),
                gcAfter.timeMillis() - gcBefore.timeMillis(),
                expiredRows,
                remainingPastOpenRows
        );
    }

    private void printScenario(
            int targetRows,
            LocalDate pastWeekStart,
            LocalDate currentWeekStart,
            long maxHeapBytes
    ) {
        System.out.printf(
                "%n========== Big3 대량 만료 메모리 테스트 ==========%n"
                        + "[준비 데이터]%n"
                        + "- 만료 대상: 과거 주차(%s)의 OPEN 항목 %,d건%n"
                        + "- 제외 확인 1: 현재 주차(%s)의 OPEN 항목 1건%n"
                        + "- 제외 확인 2: 과거 주차의 COMPLETED 항목 1건%n"
                        + "[실행 조건]%n"
                        + "- JVM 최대 heap: %s (%,d bytes)%n"
                        + "[확인 목적]%n"
                        + "- 현재 로직이 대상 엔티티를 한 번에 적재할 때 OOM이 발생하는지 확인%n"
                        + "- OOM이 없으면 peak heap, GC, 처리 시간과 최종 상태를 측정%n"
                        + "=================================================%n",
                pastWeekStart,
                targetRows,
                currentWeekStart,
                formatBytes(maxHeapBytes),
                maxHeapBytes
        );
    }

    private void printKoreanResult(
            int targetRows,
            long maxHeapBytes,
            long elapsedMillis,
            long heapBeforeBytes,
            long peakHeapBytes,
            long heapAfterBytes,
            long gcCountDelta,
            long gcTimeMillisDelta,
            long expiredRows,
            long remainingPastOpenRows
    ) {
        long peakIncreaseBytes = Math.max(0L, peakHeapBytes - heapBeforeBytes);
        double peakHeapRatio = maxHeapBytes > 0
                ? peakHeapBytes * 100.0 / maxHeapBytes
                : Double.NaN;

        System.out.printf(
                "%n[OOM 판정] OOM 발생하지 않음%n"
                        + "- 조건: 만료 대상 %,d건, JVM 최대 heap %s%n"
                        + "- 처리 결과: EXPIRED %,d건, 남은 과거 OPEN %,d건%n"
                        + "- 처리 시간: %,d ms%n"
                        + "- heap: 실행 전 %s -> peak %s -> 실행 후 %s%n"
                        + "- peak 증가량: %s, 최대 heap 대비 peak %.2f%%%n"
                        + "- GC 증가: %,d회, GC 시간 증가 %,d ms%n"
                        + "- 해석: 이 조건에서는 완료됐지만, bulk update 전후 비교값으로 사용해야 합니다.%n",
                targetRows,
                formatBytes(maxHeapBytes),
                expiredRows,
                remainingPastOpenRows,
                elapsedMillis,
                formatBytes(heapBeforeBytes),
                formatBytes(peakHeapBytes),
                formatBytes(heapAfterBytes),
                formatBytes(peakIncreaseBytes),
                peakHeapRatio,
                gcCountDelta,
                gcTimeMillisDelta
        );
    }

    private String formatBytes(long bytes) {
        double mebibytes = bytes / 1024.0 / 1024.0;
        return "%,.2f MiB".formatted(mebibytes);
    }

    private void seedInboxItem(OffsetDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO inbox_items (id, user_id, content, created_at)
                VALUES (?, ?, ?, ?)
                """,
                INBOX_ITEM_ID,
                USER_ID,
                "Expiration memory pressure harness",
                createdAt
        );
    }

    private void seedExpiredCandidates(int rows, LocalDate pastWeekStart, OffsetDateTime createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO big3_items (
                    id,
                    origin_inbox_item_id,
                    user_id,
                    week_start,
                    title_snapshot,
                    status,
                    created_at,
                    updated_at,
                    version
                )
                SELECT
                    'oom-' || lpad(sequence_number::text, 32, '0'),
                    ?,
                    ?,
                    ?,
                    'Expiration candidate',
                    'OPEN',
                    ?,
                    ?,
                    0
                FROM generate_series(1, ?) AS sequence_number
                """,
                INBOX_ITEM_ID,
                USER_ID,
                pastWeekStart,
                createdAt,
                createdAt,
                rows
        );
    }

    private void seedControlRows(
            LocalDate currentWeekStart,
            LocalDate pastWeekStart,
            OffsetDateTime createdAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO big3_items (
                    id,
                    origin_inbox_item_id,
                    user_id,
                    week_start,
                    title_snapshot,
                    status,
                    created_at,
                    updated_at,
                    version
                )
                VALUES
                    ('oom-control-current', ?, ?, ?, 'Current week control', 'OPEN', ?, ?, 0),
                    ('oom-control-completed', ?, ?, ?, 'Completed control', 'COMPLETED', ?, ?, 0)
                """,
                INBOX_ITEM_ID,
                USER_ID,
                currentWeekStart,
                createdAt,
                createdAt,
                INBOX_ITEM_ID,
                USER_ID,
                pastWeekStart,
                createdAt,
                createdAt
        );
    }

    private long countByStatusAndWeek(String status, LocalDate weekStart) {
        return jdbcTemplate.queryForObject(
                """
                SELECT count(*)
                FROM big3_items
                WHERE id LIKE 'oom-%'
                  AND status = ?
                  AND week_start = ?
                """,
                Long.class,
                status,
                weekStart
        );
    }

    private long countByIdAndStatus(String id, String status) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM big3_items WHERE id = ? AND status = ?",
                Long.class,
                id,
                status
        );
    }

    private void truncateDedicatedDatabaseRows() {
        jdbcTemplate.execute("TRUNCATE TABLE inbox_items CASCADE");
    }

    private int readPositiveInt(String name, int defaultValue) {
        int value = Integer.parseInt(System.getenv().getOrDefault(name, Integer.toString(defaultValue)));
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be greater than zero");
        }
        return value;
    }

    private GcSnapshot captureGc() {
        long count = 0L;
        long timeMillis = 0L;
        for (GarbageCollectorMXBean collector : ManagementFactory.getGarbageCollectorMXBeans()) {
            count += Math.max(collector.getCollectionCount(), 0L);
            timeMillis += Math.max(collector.getCollectionTime(), 0L);
        }
        return new GcSnapshot(count, timeMillis);
    }

    private record GcSnapshot(long count, long timeMillis) {
    }

    private static final class HeapSampler implements AutoCloseable {

        private final ScheduledExecutorService executor;
        private final AtomicLong peakBytes;

        private HeapSampler(MemoryMXBean memory) {
            this.peakBytes = new AtomicLong(memory.getHeapMemoryUsage().getUsed());
            this.executor = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "expiration-heap-sampler");
                thread.setDaemon(true);
                return thread;
            });
            this.executor.scheduleAtFixedRate(
                    () -> peakBytes.accumulateAndGet(
                            memory.getHeapMemoryUsage().getUsed(),
                            Math::max
                    ),
                    0L,
                    10L,
                    TimeUnit.MILLISECONDS
            );
        }

        private static HeapSampler start(MemoryMXBean memory) {
            return new HeapSampler(memory);
        }

        private long peakBytes() {
            return peakBytes.get();
        }

        @Override
        public void close() {
            executor.shutdownNow();
        }
    }
}
