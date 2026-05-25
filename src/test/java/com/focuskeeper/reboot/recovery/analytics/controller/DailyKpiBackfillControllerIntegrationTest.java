package com.focuskeeper.reboot.recovery.analytics.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiMetricRepository;
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
class DailyKpiBackfillControllerIntegrationTest {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DailyKpiMetricRepository dailyKpiMetricRepository;

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
        dailyKpiMetricRepository.deleteAll();
        dailyKpiLastProcessedDateRepository.deleteAll();
    }

    @Test
    void backfillDailyKpiGeneratesMetricsForDateRangeAndUpdatesLastProcessedDate() throws Exception {
        String userId = "daily-kpi-backfill-user";
        LocalDate startDate = LocalDate.of(2026, 3, 17);
        LocalDate endDate = LocalDate.of(2026, 3, 18);

        seedWorkday(userId, startDate);
        seedWorkday(userId, endDate);

        mockMvc.perform(
                        post("/api/v1/recovery/analytics/kpis/daily/backfill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "startDate": "%s",
                                          "endDate": "%s"
                                        }
                                        """.formatted(userId, startDate, endDate))
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("DAILY_KPI_BACKFILL_COMPLETED"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.processedDays").value(2))
                .andExpect(jsonPath("$.data.processedMetricDates[0]").value(startDate.toString()))
                .andExpect(jsonPath("$.data.processedMetricDates[1]").value(endDate.toString()))
                .andExpect(jsonPath("$.data.lastProcessedDate.pipelineKey").value("daily_kpi_pipeline"))
                .andExpect(jsonPath("$.data.lastProcessedDate.userId").value(userId))
                .andExpect(jsonPath("$.data.lastProcessedDate.lastProcessedDate").value(endDate.toString()));

        assertThat(dailyKpiMetricRepository.findByUserIdAndMetricDate(userId, startDate)).isPresent();
        assertThat(dailyKpiMetricRepository.findByUserIdAndMetricDate(userId, endDate)).isPresent();
        assertThat(dailyKpiLastProcessedDateRepository.findByPipelineKeyAndUserId("daily_kpi_pipeline", userId)).isPresent();
    }

    @Test
    void getDailyKpiLastProcessedDateReturnsLatestProcessedDate() throws Exception {
        String userId = "daily-kpi-last-processed-date-user";
        LocalDate metricDate = LocalDate.of(2026, 3, 19);

        seedWorkday(userId, metricDate);

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
                        get("/api/v1/recovery/analytics/kpis/daily/last-processed-date")
                                .param("userId", userId)
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("DAILY_KPI_LAST_PROCESSED_DATE_FETCHED"))
                .andExpect(jsonPath("$.data.pipelineKey").value("daily_kpi_pipeline"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.lastProcessedDate").value(metricDate.toString()));
    }

    @Test
    void lastProcessedDateDoesNotRegressWhenEarlierMetricDateIsGeneratedLater() throws Exception {
        String userId = "daily-kpi-last-processed-date-monotonic-user";
        LocalDate laterDate = LocalDate.of(2026, 3, 21);
        LocalDate earlierDate = LocalDate.of(2026, 3, 20);

        seedWorkday(userId, laterDate);
        seedWorkday(userId, earlierDate);

        mockMvc.perform(
                        post("/api/v1/recovery/analytics/kpis/daily")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "metricDate": "%s"
                                        }
                                        """.formatted(userId, laterDate))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/v1/recovery/analytics/kpis/daily")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "metricDate": "%s"
                                        }
                                        """.formatted(userId, earlierDate))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/v1/recovery/analytics/kpis/daily/last-processed-date")
                                .param("userId", userId)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastProcessedDate").value(laterDate.toString()));
    }

    @Test
    void backfillDailyKpiReturnsBadRequestWhenDateRangeIsInvalid() throws Exception {
        mockMvc.perform(
                        post("/api/v1/recovery/analytics/kpis/daily/backfill")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "invalid-backfill-user",
                                          "startDate": "2026-03-20",
                                          "endDate": "2026-03-19"
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400"))
                .andExpect(jsonPath("$.error.details.dateRange").value("endDate는 startDate보다 빠를 수 없습니다."));
    }

    private void seedWorkday(String userId, LocalDate metricDate) {
        OffsetDateTime firstStart = metricDate.atTime(9, 0).atOffset(SEOUL_OFFSET);
        OffsetDateTime firstEnd = metricDate.atTime(9, 25).atOffset(SEOUL_OFFSET);

        ExecutionUnit executionUnit = planningTestFixtures.saveExecutionUnit(
                userId,
                "핵심 작업 %s".formatted(metricDate)
        );
        Timebox timebox = timeboxRepository.save(Timebox.create(
                userId,
                executionUnit,
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
