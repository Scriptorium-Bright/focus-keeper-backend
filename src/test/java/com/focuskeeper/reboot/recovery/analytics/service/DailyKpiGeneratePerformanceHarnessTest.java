package com.focuskeeper.reboot.recovery.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiMetricRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiQualityReportRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiLastProcessedDateRepository;
import com.focuskeeper.reboot.recovery.execution.FailureReason;
import com.focuskeeper.reboot.recovery.execution.RestartType;
import com.focuskeeper.reboot.recovery.execution.entity.FailureEvent;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import com.focuskeeper.reboot.recovery.execution.entity.RestartEvent;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@Tag("perf")
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "PERF_DAILY_KPI_ENABLED", matches = "true")
class DailyKpiGeneratePerformanceHarnessTest {

    // Perf-only harness for P-001. Run with:
    // PERF_DAILY_KPI_ENABLED=true PERF_DAILY_KPI_BLOCKS=1200 PERF_DAILY_KPI_SPACING_SECONDS=60
    //   PERF_DAILY_KPI_MEASURE_RUNS=7 ./gradlew test --no-daemon --rerun-tasks
    //   --tests com.focuskeeper.reboot.recovery.analytics.service.DailyKpiGeneratePerformanceHarnessTest

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);
    private static final long SECONDS_PER_DAY = 24L * 60L * 60L;

    @Autowired
    private DailyKpiPipelineService dailyKpiPipelineService;

    @Autowired
    private DailyKpiMetricRepository dailyKpiMetricRepository;

    @Autowired
    private DailyKpiQualityReportRepository dailyKpiQualityReportRepository;

    @Autowired
    private DailyKpiLastProcessedDateRepository dailyKpiLastProcessedDateRepository;

    @Autowired
    private TimeboxRepository timeboxRepository;

    @Autowired
    private RecoverySessionRepository recoverySessionRepository;

    @Autowired
    private FailureEventRepository failureEventRepository;

    @Autowired
    private RestartEventRepository restartEventRepository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @BeforeEach
    void setUp() {
        clearAll();
    }

    @Test
    void measureGenerateDurationAndSqlCount() {
        int blocks = readInt("PERF_DAILY_KPI_BLOCKS", 12);
        int spacingSeconds = readInt("PERF_DAILY_KPI_SPACING_SECONDS", 60);
        int workMinutes = readInt("PERF_DAILY_KPI_WORK_MINUTES", 20);
        int failureEvery = readInt("PERF_DAILY_KPI_FAILURE_EVERY", 3);
        int warmupRuns = readInt("PERF_DAILY_KPI_WARMUP_RUNS", 1);
        int measureRuns = readInt("PERF_DAILY_KPI_MEASURE_RUNS", 5);
        String userId = System.getenv().getOrDefault("PERF_DAILY_KPI_USER_ID", "perf-daily-kpi-user");
        LocalDate metricDate = LocalDate.of(2026, 3, 21);

        validateSeedConfig(blocks, spacingSeconds, workMinutes, warmupRuns, measureRuns);
        seedWorkday(userId, metricDate, blocks, spacingSeconds, workMinutes, failureEvery);

        for (int run = 0; run < warmupRuns; run++) {
            dailyKpiPipelineService.generate(userId, metricDate);
            clearGeneratedAnalytics();
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        List<Long> durations = new ArrayList<>();
        List<Long> preparedStatementCounts = new ArrayList<>();

        for (int run = 0; run < measureRuns; run++) {
            statistics.clear();

            long startedAt = System.nanoTime();
            dailyKpiPipelineService.generate(userId, metricDate);
            long durationMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

            durations.add(durationMillis);
            preparedStatementCounts.add(statistics.getPrepareStatementCount());

            assertThat(dailyKpiMetricRepository.findByUserIdAndMetricDate(userId, metricDate)).isPresent();
            assertThat(dailyKpiQualityReportRepository.findByUserIdAndMetricDate(userId, metricDate)).isPresent();
            assertThat(dailyKpiLastProcessedDateRepository.findByPipelineKeyAndUserId("daily_kpi_pipeline", userId))
                    .isPresent()
                    .get()
                    .extracting("lastProcessedDate")
                    .isEqualTo(metricDate);

            if (run < measureRuns - 1) {
                clearGeneratedAnalytics();
            }
        }

        System.out.printf(
                "PERF_DAILY_KPI blocks=%d spacingSeconds=%d workMinutes=%d warmups=%d runs=%d "
                        + "rawRows[timeboxes=%d,sessions=%d,failures=%d,restarts=%d] "
                        + "durationMs[p50=%d,p95=%d,max=%d,avg=%d] "
                        + "preparedStatements[p50=%d,p95=%d,max=%d,avg=%d] metrics=%d qualityReports=%d%n",
                blocks,
                spacingSeconds,
                workMinutes,
                warmupRuns,
                measureRuns,
                timeboxRepository.count(),
                recoverySessionRepository.count(),
                failureEventRepository.count(),
                restartEventRepository.count(),
                percentile(durations, 0.50),
                percentile(durations, 0.95),
                Collections.max(durations),
                average(durations),
                percentile(preparedStatementCounts, 0.50),
                percentile(preparedStatementCounts, 0.95),
                Collections.max(preparedStatementCounts),
                average(preparedStatementCounts),
                dailyKpiMetricRepository.count(),
                dailyKpiQualityReportRepository.count()
        );
    }

    private void seedWorkday(
            String userId,
            LocalDate metricDate,
            int blocks,
            int spacingSeconds,
            int workMinutes,
            int failureEvery
    ) {
        OffsetDateTime baseStart = metricDate.atStartOfDay().atOffset(SEOUL_OFFSET);
        for (int index = 0; index < blocks; index++) {
            OffsetDateTime startAt = baseStart.plusSeconds((long) index * spacingSeconds);
            OffsetDateTime endAt = startAt.plusMinutes(workMinutes);

            Timebox timebox = timeboxRepository.save(Timebox.create(
                    userId,
                    "perf-item-%s-%d".formatted(metricDate, index),
                    "성능 측정 작업 %s-%d".formatted(metricDate, index),
                    TimeboxType.WORK,
                    startAt,
                    endAt,
                    index == 0,
                    startAt.minusMinutes(5)
            ));

            RecoverySession session = RecoverySession.start(userId, timebox.getId(), startAt);
            if (shouldInterrupt(index, failureEvery)) {
                session.interrupt(interruptedAt(startAt, workMinutes));
            } else {
                session.complete(endAt);
            }
            recoverySessionRepository.save(session);

            if (shouldInterrupt(index, failureEvery)) {
                String sessionId = findSessionId(userId, startAt, timebox.getId());
                OffsetDateTime interruptedAt = interruptedAt(startAt, workMinutes);
                FailureEvent failureEvent = failureEventRepository.save(FailureEvent.create(
                        userId,
                        sessionId,
                        timebox.getId(),
                        FailureReason.TOO_BIG,
                        "범위를 줄여야 했다",
                        interruptedAt
                ));

                restartEventRepository.save(RestartEvent.create(
                        userId,
                        failureEvent.toResponse().id(),
                        RestartType.TEN_MINUTE_RESTART,
                        10,
                        interruptedAt.plusMinutes(8)
                ));
            }
        }
    }

    private boolean shouldInterrupt(int index, int failureEvery) {
        return failureEvery > 0 && index % failureEvery == 1;
    }

    private OffsetDateTime interruptedAt(OffsetDateTime startAt, int workMinutes) {
        return startAt.plusMinutes(Math.max(1, Math.min(workMinutes, 15)));
    }

    private void validateSeedConfig(
            int blocks,
            int spacingSeconds,
            int workMinutes,
            int warmupRuns,
            int measureRuns
    ) {
        if (blocks < 1) {
            throw new IllegalArgumentException("PERF_DAILY_KPI_BLOCKS는 1 이상이어야 합니다.");
        }
        if (spacingSeconds < 1) {
            throw new IllegalArgumentException("PERF_DAILY_KPI_SPACING_SECONDS는 1 이상이어야 합니다.");
        }
        if (workMinutes < 1) {
            throw new IllegalArgumentException("PERF_DAILY_KPI_WORK_MINUTES는 1 이상이어야 합니다.");
        }
        if (warmupRuns < 0) {
            throw new IllegalArgumentException("PERF_DAILY_KPI_WARMUP_RUNS는 0 이상이어야 합니다.");
        }
        if (measureRuns < 1) {
            throw new IllegalArgumentException("PERF_DAILY_KPI_MEASURE_RUNS는 1 이상이어야 합니다.");
        }

        long lastStartOffset = (long) Math.max(blocks - 1, 0) * spacingSeconds;
        if (lastStartOffset >= SECONDS_PER_DAY) {
            throw new IllegalArgumentException(
                    "PERF_DAILY_KPI_BLOCKS와 PERF_DAILY_KPI_SPACING_SECONDS 조합이 하루 범위를 넘습니다."
            );
        }
    }

    private int readInt(String envName, int defaultValue) {
        return Integer.parseInt(System.getenv().getOrDefault(envName, Integer.toString(defaultValue)));
    }

    private long percentile(List<Long> values, double ratio) {
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int index = (int) Math.ceil(ratio * sorted.size()) - 1;
        int boundedIndex = Math.max(0, Math.min(index, sorted.size() - 1));
        return sorted.get(boundedIndex);
    }

    private long average(List<Long> values) {
        return Math.round(values.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0));
    }

    private String findSessionId(String userId, OffsetDateTime startedAt, String timeboxId) {
        return recoverySessionRepository.findSlicesByUserIdAndStartedAtBetween(
                        userId,
                        startedAt.minusMinutes(1),
                        startedAt.plusMinutes(1)
                ).stream()
                .filter(slice -> slice.getTimeboxId().equals(timeboxId))
                .findFirst()
                .orElseThrow()
                .getSessionId();
    }

    private void clearGeneratedAnalytics() {
        dailyKpiLastProcessedDateRepository.deleteAll();
        dailyKpiQualityReportRepository.deleteAll();
        dailyKpiMetricRepository.deleteAll();
    }

    private void clearAll() {
        clearGeneratedAnalytics();
        restartEventRepository.deleteAll();
        failureEventRepository.deleteAll();
        recoverySessionRepository.deleteAll();
        timeboxRepository.deleteAll();
    }
}
