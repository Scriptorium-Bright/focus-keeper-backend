package com.focuskeeper.reboot.common.observability;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiMetricRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiQualityReportRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiWatermarkRepository;
import com.focuskeeper.reboot.recovery.execution.FailureReason;
import com.focuskeeper.reboot.recovery.execution.RestartType;
import com.focuskeeper.reboot.recovery.execution.entity.FailureEvent;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import com.focuskeeper.reboot.recovery.execution.entity.RestartEvent;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import com.focuskeeper.reboot.recovery.analytics.friction.repository.FailureHourMetricRepository;
import com.focuskeeper.reboot.recovery.analytics.friction.repository.FailureHourReportRepository;
import com.focuskeeper.reboot.recovery.analytics.friction.repository.RecoveryFrictionSignalRepository;
import com.focuskeeper.reboot.recovery.analytics.friction.service.FailureHourAnalyticsService;
import com.focuskeeper.reboot.recovery.analytics.friction.service.FrictionSignalAnalyticsService;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import com.focuskeeper.reboot.recovery.retrospective.repository.WeeklyRetrospectiveRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class OperationsControllerIntegrationTest {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OperationsAlertService operationsAlertService;

    @Autowired
    private FailureHourAnalyticsService failureHourAnalyticsService;

    @Autowired
    private FrictionSignalAnalyticsService frictionSignalAnalyticsService;

    @Autowired
    private DailyKpiMetricRepository dailyKpiMetricRepository;

    @Autowired
    private DailyKpiQualityReportRepository dailyKpiQualityReportRepository;

    @Autowired
    private DailyKpiWatermarkRepository dailyKpiWatermarkRepository;

    @Autowired
    private FailureHourMetricRepository failureHourMetricRepository;

    @Autowired
    private FailureHourReportRepository failureHourReportRepository;

    @Autowired
    private RecoveryFrictionSignalRepository recoveryFrictionSignalRepository;

    @Autowired
    private WeeklyRetrospectiveRepository weeklyRetrospectiveRepository;

    @Autowired
    private TimeboxRepository timeboxRepository;

    @Autowired
    private RecoverySessionRepository recoverySessionRepository;

    @Autowired
    private FailureEventRepository failureEventRepository;

    @Autowired
    private RestartEventRepository restartEventRepository;

    @BeforeEach
    void setUp() {
        operationsAlertService.clearAll();
        recoveryFrictionSignalRepository.deleteAll();
        failureHourMetricRepository.deleteAll();
        failureHourReportRepository.deleteAll();
        dailyKpiQualityReportRepository.deleteAll();
        dailyKpiMetricRepository.deleteAll();
        dailyKpiWatermarkRepository.deleteAll();
        weeklyRetrospectiveRepository.deleteAll();
        restartEventRepository.deleteAll();
        failureEventRepository.deleteAll();
        recoverySessionRepository.deleteAll();
        timeboxRepository.deleteAll();
    }

    @Test
    void recoveryLoopOverviewReturnsDailyKpiAndFrictionSnapshots() throws Exception {
        String userId = "ops-overview-user";
        LocalDate metricDate = LocalDate.of(2026, 3, 21);

        seedWorkday(userId, metricDate);
        generateDailyKpi(userId, metricDate);
        failureHourAnalyticsService.generate(userId, metricDate);
        frictionSignalAnalyticsService.generate(userId, metricDate);

        mockMvc.perform(
                        get("/api/v1/ops/overview/recovery-loop")
                                .param("userId", userId)
                                .param("metricDate", metricDate.toString())
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OPS_RECOVERY_LOOP_OVERVIEW_FETCHED"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.dailyKpi.userId").value(userId))
                .andExpect(jsonPath("$.data.failureHour.userId").value(userId))
                .andExpect(jsonPath("$.data.frictionSignals.userId").value(userId))
                .andExpect(jsonPath("$.data.frictionSegments.userId").value(userId))
                .andExpect(jsonPath("$.data.metricNames[0]").value("http.server.requests"));
    }

    @Test
    void batchOverviewReturnsQualityAndWatermarkSnapshots() throws Exception {
        String userId = "ops-batch-user";
        LocalDate metricDate = LocalDate.of(2026, 3, 21);

        seedWorkday(userId, metricDate);
        generateDailyKpi(userId, metricDate);

        mockMvc.perform(
                        get("/api/v1/ops/overview/batch")
                                .param("userId", userId)
                                .param("metricDate", metricDate.toString())
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OPS_BATCH_OVERVIEW_FETCHED"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.qualityReport.userId").value(userId))
                .andExpect(jsonPath("$.data.watermark.pipelineKey").value("daily_kpi_pipeline"))
                .andExpect(jsonPath("$.data.metricNames[0]").value("reboot_batch_duration"));
    }

    @Test
    void alertsEndpointReturnsActiveAlerts() throws Exception {
        operationsAlertService.reportBatchFailure(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                "launch",
                "ops-alert-user",
                "test alert",
                Map.of("metricDate", "2026-03-21")
        );

        mockMvc.perform(get("/api/v1/ops/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OPS_ALERTS_FETCHED"))
                .andExpect(jsonPath("$.data[0].pipelineKey").value("daily_kpi_pipeline"))
                .andExpect(jsonPath("$.data[0].stage").value("launch"))
                .andExpect(jsonPath("$.data[0].active").value(true));
    }

    @Test
    void runbooksEndpointReturnsCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/ops/runbooks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OPS_RUNBOOKS_FETCHED"))
                .andExpect(jsonPath("$.data[0].scenarioKey").value("daily_kpi_pipeline_failure"));
    }

    private void generateDailyKpi(String userId, LocalDate metricDate) throws Exception {
        mockMvc.perform(
                        post("/api/v1/recovery/analytics/kpis/daily")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "metricDate": "%s"
                                        }
                                        """.formatted(userId, metricDate))
                )
                .andExpect(status().isOk());
    }

    private void seedWorkday(String userId, LocalDate metricDate) {
        OffsetDateTime firstStart = metricDate.atTime(9, 0).atOffset(SEOUL_OFFSET);
        OffsetDateTime firstEnd = metricDate.atTime(9, 25).atOffset(SEOUL_OFFSET);

        Timebox timebox = timeboxRepository.save(Timebox.create(
                userId,
                "item-%s".formatted(metricDate),
                "핵심 작업 %s".formatted(metricDate),
                TimeboxType.WORK,
                firstStart,
                firstEnd,
                true,
                firstStart.minusMinutes(5)
        ));

        RecoverySession session = RecoverySession.start(userId, timebox.getId(), firstStart);
        session.interrupt(firstStart.plusMinutes(15));
        recoverySessionRepository.save(session);
        String sessionId = recoverySessionRepository.findSlicesByUserIdAndStartedAtBetween(
                        userId,
                        firstStart.minusHours(1),
                        firstStart.plusHours(2)
                ).stream()
                .findFirst()
                .map(RecoverySessionRepository.SessionSlice::getSessionId)
                .orElseThrow();

        failureEventRepository.save(FailureEvent.create(
                userId,
                sessionId,
                timebox.getId(),
                FailureReason.TOO_BIG,
                "범위를 줄여야 했다",
                firstStart.plusMinutes(15)
        ));
        String failureEventId = failureEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                        userId,
                        firstStart.minusHours(1),
                        firstStart.plusHours(2)
                ).stream()
                .findFirst()
                .map(FailureEventRepository.FailureSlice::getFailureEventId)
                .orElseThrow();

        restartEventRepository.save(RestartEvent.create(
                userId,
                failureEventId,
                RestartType.TEN_MINUTE_RESTART,
                10,
                firstStart.plusMinutes(22)
        ));
    }
}
