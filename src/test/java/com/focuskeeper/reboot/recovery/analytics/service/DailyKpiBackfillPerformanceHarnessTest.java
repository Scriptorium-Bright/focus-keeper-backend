package com.focuskeeper.reboot.recovery.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.focuskeeper.reboot.recovery.analytics.dto.BackfillDailyKpiResponse;
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
@EnabledIfEnvironmentVariable(named = "PERF_BACKFILL_ENABLED", matches = "true")
class DailyKpiBackfillPerformanceHarnessTest {
    // Perf-only harness for P-006. Run with:
    // PERF_BACKFILL_ENABLED=true PERF_BACKFILL_DAYS=30 ./gradlew test --no-daemon --rerun-tasks
    //   --tests com.focuskeeper.reboot.recovery.analytics.service.DailyKpiBackfillPerformanceHarnessTest

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

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

    @BeforeEach
    void setUp() {
        clearAll();
    }

    @Test
    void measureBackfillDurationAndSqlCount() {
        int days = Integer.parseInt(System.getenv().getOrDefault("PERF_BACKFILL_DAYS", "30"));
        String userId = System.getenv().getOrDefault("PERF_BACKFILL_USER_ID", "perf-backfill-user");
        LocalDate endDate = LocalDate.of(2026, 3, 21);
        LocalDate startDate = endDate.minusDays(days - 1L);

        seedRange(userId, startDate, endDate);

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
                "PERF_BACKFILL days=%d durationMs=%d preparedStatements=%d metrics=%d qualityReports=%d%n",
                days,
                durationMillis,
                preparedStatementCount,
                dailyKpiMetricRepository.count(),
                dailyKpiQualityReportRepository.count()
        );
    }

    private void seedRange(String userId, LocalDate startDate, LocalDate endDate) {
        LocalDate current = startDate;
        while (!current.isAfter(endDate)) {
            seedWorkday(userId, current);
            current = current.plusDays(1);
        }
    }

    private void seedWorkday(String userId, LocalDate metricDate) {
        for (int index = 0; index < 3; index++) {
            OffsetDateTime startAt = metricDate.atTime(9 + (index * 2), 0).atOffset(SEOUL_OFFSET);
            OffsetDateTime endAt = startAt.plusMinutes(25);

            Timebox timebox = timeboxRepository.save(Timebox.create(
                    userId,
                    "item-%s-%d".formatted(metricDate, index),
                    "핵심 작업 %s-%d".formatted(metricDate, index),
                    TimeboxType.WORK,
                    startAt,
                    endAt,
                    index == 0,
                    startAt.minusMinutes(5)
            ));

            RecoverySession session = RecoverySession.start(userId, timebox.getId(), startAt);
            if (index == 1) {
                session.interrupt(startAt.plusMinutes(15));
            } else {
                session.complete(endAt);
            }
            recoverySessionRepository.save(session);

            if (index == 1) {
                FailureEvent failureEvent = failureEventRepository.save(FailureEvent.create(
                        userId,
                        findSessionId(userId, startAt),
                        timebox.getId(),
                        FailureReason.TOO_BIG,
                        "범위를 줄여야 했다",
                        startAt.plusMinutes(15)
                ));

                restartEventRepository.save(RestartEvent.create(
                        userId,
                        failureEvent.toResponse().id(),
                        RestartType.TEN_MINUTE_RESTART,
                        10,
                        startAt.plusMinutes(22)
                ));
            }
        }
    }

    private String findSessionId(String userId, OffsetDateTime startedAt) {
        return recoverySessionRepository.findSlicesByUserIdAndStartedAtBetween(
                        userId,
                        startedAt.minusMinutes(1),
                        startedAt.plusMinutes(1)
                ).stream()
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
