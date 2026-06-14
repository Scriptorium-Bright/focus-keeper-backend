package com.focuskeeper.reboot.recovery.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.focuskeeper.reboot.recovery.analytics.dto.BackfillDailyKpiResponse;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiMetricRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiQualityReportRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiLastProcessedDateRepository;
import com.focuskeeper.reboot.recovery.execution.constant.FailureReason;
import com.focuskeeper.reboot.recovery.execution.constant.RestartType;
import com.focuskeeper.reboot.recovery.execution.entity.FailureEvent;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import com.focuskeeper.reboot.recovery.execution.entity.RestartEvent;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import com.focuskeeper.reboot.recovery.planning.constant.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import com.focuskeeper.reboot.recovery.support.PlanningTestFixtures;
import jakarta.persistence.EntityManagerFactory;
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

@Tag("perf")
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@EnabledIfEnvironmentVariable(named = "PERF_BACKFILL_ENABLED", matches = "true")
class DailyKpiBackfillPerformanceHarnessTest {
    // Perf-only harness for P-006. Run with:
    // PERF_BACKFILL_ENABLED=true PERF_BACKFILL_DAYS=30 PERF_BACKFILL_BLOCKS_PER_DAY=120
    //   PERF_BACKFILL_SPACING_SECONDS=600 PERF_BACKFILL_WORK_MINUTES=20 PERF_BACKFILL_FAILURE_EVERY=4
    //   ./gradlew test --no-daemon --rerun-tasks
    // To run against local Postgres instead of the default test profile, add:
    //   -Dspring.profiles.active=local
    //   --tests com.focuskeeper.reboot.recovery.analytics.service.DailyKpiBackfillPerformanceHarnessTest

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);
    private static final long SECONDS_PER_DAY = 24L * 60L * 60L;

    @Autowired
    private DailyKpiBackfillService dailyKpiBackfillService;

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

    @Autowired
    private PlanningTestFixtures planningTestFixtures;

    @BeforeEach
    void setUp() {
        clearAll();
    }

    @Test
    void measureBackfillDurationAndSqlCount() {
        int days = readInt("PERF_BACKFILL_DAYS", 30);
        int blocksPerDay = readInt("PERF_BACKFILL_BLOCKS_PER_DAY", 120);
        int spacingSeconds = readInt("PERF_BACKFILL_SPACING_SECONDS", 600);
        int workMinutes = readInt("PERF_BACKFILL_WORK_MINUTES", 20);
        int failureEvery = readInt("PERF_BACKFILL_FAILURE_EVERY", 4);
        String userId = System.getenv().getOrDefault("PERF_BACKFILL_USER_ID", "perf-backfill-user");
        LocalDate endDate = LocalDate.of(2026, 3, 21);
        LocalDate startDate = endDate.minusDays(days - 1L);

        validateSeedConfig(days, blocksPerDay, spacingSeconds, workMinutes);
        seedRange(userId, startDate, endDate, blocksPerDay, spacingSeconds, workMinutes, failureEvery);

        // warm-up
        dailyKpiBackfillService.backfill(userId, startDate, endDate);
        clearGeneratedAnalytics();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        long startedAt = System.nanoTime();
        BackfillDailyKpiResponse response = dailyKpiBackfillService.backfill(userId, startDate, endDate);
        long durationMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        long preparedStatementCount = statistics.getPrepareStatementCount();

        assertThat(response.processedDays()).isEqualTo(days);
        assertThat(dailyKpiMetricRepository.count()).isEqualTo(days);
        assertThat(dailyKpiQualityReportRepository.count()).isEqualTo(days);
        assertThat(dailyKpiLastProcessedDateRepository.findByPipelineKeyAndUserId("daily_kpi_pipeline", userId))
                .isPresent()
                .get()
                .extracting("lastProcessedDate")
                .isEqualTo(endDate);

        System.out.printf(
                "PERF_BACKFILL days=%d blocksPerDay=%d spacingSeconds=%d workMinutes=%d failureEvery=%d "
                        + "rawRows[timeboxes=%d,sessions=%d,failures=%d,restarts=%d] "
                        + "durationMs=%d preparedStatements=%d metrics=%d qualityReports=%d%n",
                days,
                blocksPerDay,
                spacingSeconds,
                workMinutes,
                failureEvery,
                timeboxRepository.count(),
                recoverySessionRepository.count(),
                failureEventRepository.count(),
                restartEventRepository.count(),
                durationMillis,
                preparedStatementCount,
                dailyKpiMetricRepository.count(),
                dailyKpiQualityReportRepository.count()
        );
    }

    private void seedRange(
            String userId,
            LocalDate startDate,
            LocalDate endDate,
            int blocksPerDay,
            int spacingSeconds,
            int workMinutes,
            int failureEvery
    ) {
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            seedWorkday(userId, current, blocksPerDay, spacingSeconds, workMinutes, failureEvery);
            current = current.plusDays(1);
        }
    }

    private void seedWorkday(
            String userId,
            LocalDate metricDate,
            int blocksPerDay,
            int spacingSeconds,
            int workMinutes,
            int failureEvery
    ) {
        OffsetDateTime baseStart = metricDate.atStartOfDay().atOffset(SEOUL_OFFSET);
        for (int index = 0; index < blocksPerDay; index++) {
            OffsetDateTime startAt = baseStart.plusSeconds((long) index * spacingSeconds);
            OffsetDateTime endAt = startAt.plusMinutes(workMinutes);

            ExecutionUnit executionUnit = planningTestFixtures.saveExecutionUnit(
                    userId,
                    "핵심 작업 %s-%d".formatted(metricDate, index)
            );
            Timebox timebox = timeboxRepository.save(Timebox.create(
                    userId,
                    executionUnit,
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
                OffsetDateTime interruptedAt = interruptedAt(startAt, workMinutes);
                FailureEvent failureEvent = failureEventRepository.save(FailureEvent.create(
                        userId,
                        findSessionId(userId, startAt, timebox.getId()),
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
            int days,
            int blocksPerDay,
            int spacingSeconds,
            int workMinutes
    ) {
        if (days < 1) {
            throw new IllegalArgumentException("PERF_BACKFILL_DAYS는 1 이상이어야 합니다.");
        }
        if (blocksPerDay < 1) {
            throw new IllegalArgumentException("PERF_BACKFILL_BLOCKS_PER_DAY는 1 이상이어야 합니다.");
        }
        if (spacingSeconds < 1) {
            throw new IllegalArgumentException("PERF_BACKFILL_SPACING_SECONDS는 1 이상이어야 합니다.");
        }
        if (workMinutes < 1) {
            throw new IllegalArgumentException("PERF_BACKFILL_WORK_MINUTES는 1 이상이어야 합니다.");
        }

        long lastStartOffset = (long) Math.max(blocksPerDay - 1, 0) * spacingSeconds;
        long endOffset = lastStartOffset + (workMinutes * 60L);
        if (endOffset > SECONDS_PER_DAY) {
            throw new IllegalArgumentException(
                    "PERF_BACKFILL_BLOCKS_PER_DAY, PERF_BACKFILL_SPACING_SECONDS, PERF_BACKFILL_WORK_MINUTES 조합이 하루 범위를 넘습니다."
            );
        }
    }

    private int readInt(String envName, int defaultValue) {
        return Integer.parseInt(System.getenv().getOrDefault(envName, Integer.toString(defaultValue)));
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
