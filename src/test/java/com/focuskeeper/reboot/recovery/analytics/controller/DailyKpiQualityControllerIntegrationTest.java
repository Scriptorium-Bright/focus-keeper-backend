package com.focuskeeper.reboot.recovery.analytics.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DailyKpiQualityControllerIntegrationTest {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);
    private static final ZoneOffset UTC_OFFSET = ZoneOffset.UTC;

    @Autowired
    private MockMvc mockMvc;

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
    private PlanningTestFixtures planningTestFixtures;

    @BeforeEach
    void setUp() {
        restartEventRepository.deleteAll();
        failureEventRepository.deleteAll();
        recoverySessionRepository.deleteAll();
        timeboxRepository.deleteAll();
        dailyKpiQualityReportRepository.deleteAll();
        dailyKpiMetricRepository.deleteAll();
        dailyKpiLastProcessedDateRepository.deleteAll();
    }

    @Test
    void getDailyKpiQualityReturnsGeneratedReportWithIssueCounts() throws Exception {
        String userId = "daily-kpi-quality-user";
        LocalDate metricDate = LocalDate.of(2026, 3, 19);

        seedQualityIssues(userId, metricDate);

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

        mockMvc.perform(
                        get("/api/v1/recovery/analytics/kpis/daily/quality")
                                .param("userId", userId)
                                .param("metricDate", metricDate.toString())
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("DAILY_KPI_QUALITY_FETCHED"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.metricDate").value(metricDate.toString()))
                .andExpect(jsonPath("$.data.healthy").value(false))
                .andExpect(jsonPath("$.data.duplicateRestartLinkCount").value(1))
                .andExpect(jsonPath("$.data.orphanRestartCount").value(1))
                .andExpect(jsonPath("$.data.restartBeforeFailureCount").value(1))
                .andExpect(jsonPath("$.data.lateRestartLinkCount").value(1))
                .andExpect(jsonPath("$.data.breakSessionReferenceCount").value(1))
                .andExpect(jsonPath("$.data.missingTimeboxReferenceCount").value(0))
                .andExpect(jsonPath("$.data.timezoneMismatchCount").value(2))
                .andExpect(jsonPath("$.data.totalIssueCount").value(7));

        assertThat(dailyKpiQualityReportRepository.findByUserIdAndMetricDate(userId, metricDate)).isPresent();
    }

    @Test
    void getDailyKpiQualityReturnsNotFoundWhenReportDoesNotExist() throws Exception {
        mockMvc.perform(
                        get("/api/v1/recovery/analytics/kpis/daily/quality")
                                .param("userId", "missing-daily-kpi-quality-user")
                                .param("metricDate", "2026-03-19")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE-404"));
    }

    private void seedQualityIssues(String userId, LocalDate metricDate) {
        OffsetDateTime workStart = metricDate.atTime(9, 0).atOffset(SEOUL_OFFSET);
        OffsetDateTime workEnd = metricDate.atTime(9, 25).atOffset(SEOUL_OFFSET);

        ExecutionUnit workUnit = planningTestFixtures.saveExecutionUnit(userId, "복귀 품질 점검 작업");
        Timebox workTimebox = timeboxRepository.save(Timebox.create(
                userId,
                workUnit,
                TimeboxType.WORK,
                workStart,
                workEnd,
                true,
                workStart.minusMinutes(5)
        ));

        RecoverySession workSession = RecoverySession.start(userId, workTimebox.getId(), workStart);
        workSession.interrupt(workStart.plusMinutes(15));
        recoverySessionRepository.save(workSession);
        String workSessionId = recoverySessionRepository.findSlicesByUserIdAndStartedAtBetween(
                        userId,
                        workStart.minusHours(1),
                        workEnd.plusHours(1)
                ).stream()
                .filter(slice -> slice.getTimeboxId().equals(workTimebox.getId()))
                .findFirst()
                .map(RecoverySessionRepository.SessionSlice::getSessionId)
                .orElseThrow();

        failureEventRepository.save(FailureEvent.create(
                userId,
                workSessionId,
                workTimebox.getId(),
                FailureReason.TOO_BIG,
                "범위가 너무 컸다",
                workStart.plusMinutes(15)
        ));
        String sameDayFailureEventId = failureEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                        userId,
                        workStart.minusHours(1),
                        workEnd.plusHours(1)
                ).stream()
                .filter(slice -> slice.getSessionId().equals(workSessionId))
                .findFirst()
                .map(FailureEventRepository.FailureSlice::getFailureEventId)
                .orElseThrow();

        restartEventRepository.save(RestartEvent.create(
                userId,
                sameDayFailureEventId,
                RestartType.TEN_MINUTE_RESTART,
                10,
                workStart.plusMinutes(10)
        ));
        restartEventRepository.save(RestartEvent.create(
                userId,
                sameDayFailureEventId,
                RestartType.TEN_MINUTE_RESTART,
                10,
                workStart.plusMinutes(25)
        ));

        OffsetDateTime breakStartUtc = OffsetDateTime.of(metricDate.atTime(11, 0), UTC_OFFSET);
        OffsetDateTime breakEndUtc = breakStartUtc.plusMinutes(10);
        ExecutionUnit breakUnit = planningTestFixtures.saveExecutionUnit(userId, "잘못 시작된 휴식 블록");
        Timebox breakTimebox = timeboxRepository.save(Timebox.create(
                userId,
                breakUnit,
                TimeboxType.BREAK,
                breakStartUtc,
                breakEndUtc,
                false,
                breakStartUtc.minusMinutes(5)
        ));
        RecoverySession breakSession = RecoverySession.start(userId, breakTimebox.getId(), breakStartUtc);
        breakSession.complete(breakEndUtc);
        recoverySessionRepository.save(breakSession);

        restartEventRepository.save(RestartEvent.create(
                userId,
                "missing-failure-id",
                RestartType.TEN_MINUTE_RESTART,
                10,
                metricDate.atTime(13, 0).atOffset(SEOUL_OFFSET)
        ));

        OffsetDateTime oldFailureAt = metricDate.minusDays(3).atTime(8, 0).atOffset(SEOUL_OFFSET);
        failureEventRepository.save(FailureEvent.create(
                userId,
                "old-session-id",
                "old-timebox-id",
                FailureReason.LOW_ENERGY,
                "이전 실패",
                oldFailureAt
        ));
        String oldFailureEventId = failureEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                        userId,
                        oldFailureAt.minusMinutes(1),
                        oldFailureAt.plusMinutes(1)
                ).stream()
                .findFirst()
                .map(FailureEventRepository.FailureSlice::getFailureEventId)
                .orElseThrow();

        restartEventRepository.save(RestartEvent.create(
                userId,
                oldFailureEventId,
                RestartType.TEN_MINUTE_RESTART,
                10,
                metricDate.atTime(14, 0).atOffset(SEOUL_OFFSET)
        ));
    }
}
