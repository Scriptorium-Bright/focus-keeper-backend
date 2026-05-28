package com.focuskeeper.reboot.recovery.analytics.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiQualityReportRepository;
import com.focuskeeper.reboot.recovery.execution.FailureReason;
import com.focuskeeper.reboot.recovery.execution.RestartType;
import com.focuskeeper.reboot.recovery.execution.entity.FailureEvent;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import com.focuskeeper.reboot.recovery.execution.entity.RestartEvent;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository.FailureSlice;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository.SessionSlice;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository.RestartSlice;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import com.focuskeeper.reboot.recovery.support.PlanningTestFixtures;
import jakarta.persistence.EntityManagerFactory;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
@EnabledIfEnvironmentVariable(named = "PERF_DAILY_KPI_QUALITY_ENABLED", matches = "true")
class DailyKpiQualityPerformanceHarnessTest {

    // Perf-only harness for P-004. Run with:
    // PERF_DAILY_KPI_QUALITY_ENABLED=true PERF_DAILY_KPI_QUALITY_BLOCKS=600 ./gradlew test --no-daemon --rerun-tasks
    //   --tests com.focuskeeper.reboot.recovery.analytics.service.DailyKpiQualityPerformanceHarnessTest

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    @Autowired
    private DailyKpiQualityService dailyKpiQualityService;

    @Autowired
    private DailyKpiQualityReportRepository dailyKpiQualityReportRepository;

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
    void measureQualityDurationAndSqlCount() {
        int blocks = Integer.parseInt(System.getenv().getOrDefault("PERF_DAILY_KPI_QUALITY_BLOCKS", "600"));
        String userId = System.getenv().getOrDefault("PERF_DAILY_KPI_QUALITY_USER_ID", "perf-daily-kpi-quality-user");
        LocalDate metricDate = LocalDate.of(2026, 3, 21);

        seedWorkday(userId, metricDate, blocks);

        OffsetDateTime periodStart = metricDate.atStartOfDay().atOffset(SEOUL_OFFSET);
        OffsetDateTime periodEndExclusive = metricDate.plusDays(1).atStartOfDay().atOffset(SEOUL_OFFSET);
        List<SessionSlice> sessions = recoverySessionRepository.findSlicesByUserIdAndStartedAtBetween(userId, periodStart, periodEndExclusive);
        List<FailureSlice> failures = failureEventRepository.findSlicesByUserIdAndOccurredAtBetween(userId, periodStart, periodEndExclusive);
        List<RestartSlice> restarts = restartEventRepository.findSlicesByUserIdAndOccurredAtBetween(userId, periodStart, periodEndExclusive);
        Map<String, Timebox> timeboxesById = timeboxRepository.findAllByUserIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                        userId,
                        periodStart,
                        periodEndExclusive
                ).stream()
                .collect(Collectors.toMap(Timebox::getId, Function.identity()));
        Map<String, OffsetDateTime> failureOccurredAtById = failures.stream()
                .collect(Collectors.toMap(FailureSlice::getFailureEventId, FailureSlice::getOccurredAt));

        OffsetDateTime generatedAt = OffsetDateTime.now();

        // warm-up
        dailyKpiQualityService.generateFromSlices(
                userId,
                metricDate,
                generatedAt,
                sessions,
                failures,
                restarts,
                timeboxesById,
                failureOccurredAtById
        );
        dailyKpiQualityReportRepository.deleteAll();

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        long startedAt = System.nanoTime();
        dailyKpiQualityService.generateFromSlices(
                userId,
                metricDate,
                OffsetDateTime.now(),
                sessions,
                failures,
                restarts,
                timeboxesById,
                failureOccurredAtById
        );
        long durationMillis = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();

        long preparedStatementCount = statistics.getPrepareStatementCount();

        assertThat(dailyKpiQualityReportRepository.findByUserIdAndMetricDate(userId, metricDate))
                .isPresent()
                .get()
                .satisfies(report -> assertThat(report.toResponse().totalIssueCount()).isGreaterThan(0));

        System.out.printf(
                "PERF_DAILY_KPI_QUALITY blocks=%d durationMs=%d preparedStatements=%d qualityReports=%d totalIssues=%d%n",
                blocks,
                durationMillis,
                preparedStatementCount,
                dailyKpiQualityReportRepository.count(),
                dailyKpiQualityReportRepository.findByUserIdAndMetricDate(userId, metricDate)
                        .orElseThrow()
                        .toResponse()
                        .totalIssueCount()
        );
    }

    private void seedWorkday(String userId, LocalDate metricDate, int blocks) {
        for (int index = 0; index < blocks; index++) {
            OffsetDateTime startAt = metricDate.atStartOfDay()
                    .atOffset(SEOUL_OFFSET)
                    .plusHours(6)
                    .plusMinutes(index);
            OffsetDateTime endAt = startAt.plusMinutes(20);

            ExecutionUnit executionUnit = planningTestFixtures.saveExecutionUnit(
                    userId,
                    "품질 측정 작업 %s-%d".formatted(metricDate, index)
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
            if (index % 4 == 1) {
                session.interrupt(startAt.plusMinutes(10));
            } else {
                session.complete(endAt);
            }
            recoverySessionRepository.save(session);

            if (index % 4 == 1) {
                String sessionId = findSessionId(userId, startAt, timebox.getId());
                OffsetDateTime failureOccurredAt = startAt.plusMinutes(10);
                FailureEvent failureEvent = failureEventRepository.save(FailureEvent.create(
                        userId,
                        sessionId,
                        timebox.getId(),
                        FailureReason.TOO_BIG,
                        "범위를 줄여야 했다",
                        failureOccurredAt
                ));

                restartEventRepository.save(RestartEvent.create(
                        userId,
                        failureEvent.toResponse().id(),
                        RestartType.TEN_MINUTE_RESTART,
                        10,
                        failureOccurredAt.plusMinutes(8)
                ));

                if (index % 12 == 1) {
                    restartEventRepository.save(RestartEvent.create(
                            userId,
                            failureEvent.toResponse().id(),
                            RestartType.TEN_MINUTE_RESTART,
                            10,
                            failureOccurredAt.plusMinutes(12)
                    ));
                }

                if (index % 16 == 1) {
                    restartEventRepository.save(RestartEvent.create(
                            userId,
                            failureEvent.toResponse().id(),
                            RestartType.TEN_MINUTE_RESTART,
                            10,
                            failureOccurredAt.minusMinutes(1)
                    ));
                }
            }
        }

        ExecutionUnit breakUnit = planningTestFixtures.saveExecutionUnit(userId, "품질 측정 휴식 블록");
        Timebox breakTimebox = timeboxRepository.save(Timebox.create(
                userId,
                breakUnit,
                TimeboxType.BREAK,
                metricDate.atTime(23, 0).atOffset(SEOUL_OFFSET),
                metricDate.atTime(23, 10).atOffset(SEOUL_OFFSET),
                false,
                metricDate.atTime(22, 55).atOffset(SEOUL_OFFSET)
        ));
        RecoverySession breakSession = RecoverySession.start(userId, breakTimebox.getId(), breakTimebox.getStartAt());
        breakSession.complete(breakTimebox.getEndAt());
        recoverySessionRepository.save(breakSession);

        RecoverySession missingTimeboxSession = RecoverySession.start(
                userId,
                "missing-timebox-%s".formatted(metricDate),
                metricDate.atTime(23, 20).atOffset(SEOUL_OFFSET)
        );
        missingTimeboxSession.complete(metricDate.atTime(23, 30).atOffset(SEOUL_OFFSET));
        recoverySessionRepository.save(missingTimeboxSession);
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

    private void clearAll() {
        dailyKpiQualityReportRepository.deleteAll();
        restartEventRepository.deleteAll();
        failureEventRepository.deleteAll();
        recoverySessionRepository.deleteAll();
        timeboxRepository.deleteAll();
    }
}
