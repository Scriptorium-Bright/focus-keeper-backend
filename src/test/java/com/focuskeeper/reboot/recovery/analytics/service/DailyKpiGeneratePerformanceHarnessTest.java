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
    // PERF_DAILY_KPI_ENABLED=true PERF_DAILY_KPI_BLOCKS=12 ./gradlew test --no-daemon --rerun-tasks
    //   --tests com.focuskeeper.reboot.recovery.analytics.service.DailyKpiGeneratePerformanceHarnessTest

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

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
        int blocks = Integer.parseInt(System.getenv().getOrDefault("PERF_DAILY_KPI_BLOCKS", "12"));
        String userId = System.getenv().getOrDefault("PERF_DAILY_KPI_USER_ID", "perf-daily-kpi-user");
        LocalDate metricDate = LocalDate.of(2026, 3, 21);

        seedWorkday(userId, metricDate, blocks);

        // warm-up
        dailyKpiPipelineService.generate(userId, metricDate);
        clearGeneratedAnalytics();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        long startedAt = System.nanoTime();
        dailyKpiPipelineService.generate(userId, metricDate);
        long durationMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        long preparedStatementCount = statistics.getPrepareStatementCount();

        assertThat(dailyKpiMetricRepository.findByUserIdAndMetricDate(userId, metricDate)).isPresent();
        assertThat(dailyKpiQualityReportRepository.findByUserIdAndMetricDate(userId, metricDate)).isPresent();
        assertThat(dailyKpiLastProcessedDateRepository.findByPipelineKeyAndUserId("daily_kpi_pipeline", userId))
                .isPresent()
                .get()
                .extracting("lastProcessedDate")
                .isEqualTo(metricDate);

        System.out.printf(
                "PERF_DAILY_KPI blocks=%d durationMs=%d preparedStatements=%d metrics=%d qualityReports=%d%n",
                blocks,
                durationMillis,
                preparedStatementCount,
                dailyKpiMetricRepository.count(),
                dailyKpiQualityReportRepository.count()
        );
    }

    private void seedWorkday(String userId, LocalDate metricDate, int blocks) {
        for (int index = 0; index < blocks; index++) {
            OffsetDateTime startAt = metricDate.atTime(9 + (index / 2), (index % 2) * 30).atOffset(SEOUL_OFFSET);
            OffsetDateTime endAt = startAt.plusMinutes(25);

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
            if (index % 3 == 1) {
                session.interrupt(startAt.plusMinutes(15));
            } else {
                session.complete(endAt);
            }
            recoverySessionRepository.save(session);

            if (index % 3 == 1) {
                String sessionId = findSessionId(userId, startAt, timebox.getId());
                FailureEvent failureEvent = failureEventRepository.save(FailureEvent.create(
                        userId,
                        sessionId,
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
                        startAt.plusMinutes(23)
                ));
            }
        }
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
