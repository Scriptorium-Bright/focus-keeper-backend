package com.focuskeeper.reboot.recovery.analytics.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiMetricRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class DailyKpiControllerIntegrationTest {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DailyKpiMetricRepository dailyKpiMetricRepository;

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

    @Test
    void generateDailyKpiAggregatesRecoveryMetricsAndPersistsMart() throws Exception {
        String userId = "daily-kpi-user";
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
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("DAILY_KPI_GENERATED"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.metricDate").value(metricDate.toString()))
                .andExpect(jsonPath("$.data.activation").value(true))
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.recovery24").value(true))
                .andExpect(jsonPath("$.data.recovery48").value(true))
                .andExpect(jsonPath("$.data.restartCount24").value(1))
                .andExpect(jsonPath("$.data.restartCount48").value(1))
                .andExpect(jsonPath("$.data.ttrMinutes").value(8))
                .andExpect(jsonPath("$.data.cycleCompletionRate").value(0.5000))
                .andExpect(jsonPath("$.data.planExecutionRate").value(1.0000))
                .andExpect(jsonPath("$.data.plannedWorkMinutes").value(55))
                .andExpect(jsonPath("$.data.actualWorkMinutes").value(40))
                .andExpect(jsonPath("$.data.estimationErrorMinutes").value(15));

        assertThat(dailyKpiMetricRepository.findByUserIdAndMetricDate(userId, metricDate)).isPresent();
    }

    @Test
    void generateDailyKpiUpsertsSameUserAndDate() throws Exception {
        String userId = "daily-kpi-upsert-user";
        LocalDate metricDate = LocalDate.of(2026, 3, 20);

        seedWorkday(userId, metricDate);

        MvcResult first = mockMvc.perform(
                        post("/api/v1/recovery/analytics/kpis/daily")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "metricDate": "%s"
                                        }
                                        """.formatted(userId, metricDate))
                )
                .andExpect(status().isOk())
                .andReturn();

        MvcResult second = mockMvc.perform(
                        post("/api/v1/recovery/analytics/kpis/daily")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "metricDate": "%s"
                                        }
                                        """.formatted(userId, metricDate))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());

        assertThat(secondBody.path("data").path("dailyKpiId").asText())
                .isEqualTo(firstBody.path("data").path("dailyKpiId").asText());
    }

    @Test
    void getDailyKpiReturnsGeneratedMart() throws Exception {
        String userId = "daily-kpi-get-user";
        LocalDate metricDate = LocalDate.of(2026, 3, 21);

        seedWorkday(userId, metricDate);
        generateDailyKpi(userId, metricDate);

        mockMvc.perform(
                        get("/api/v1/recovery/analytics/kpis/daily")
                                .param("userId", userId)
                                .param("metricDate", metricDate.toString())
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("DAILY_KPI_FETCHED"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.metricDate").value(metricDate.toString()));
    }

    @Test
    void getDailyKpiReturnsNotFoundWhenMartDoesNotExist() throws Exception {
        mockMvc.perform(
                        get("/api/v1/recovery/analytics/kpis/daily")
                                .param("userId", "missing-daily-kpi-user")
                                .param("metricDate", "2026-03-19")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE-404"));
    }

    @Test
    void getDailyKpiReturnsBadRequestWhenMetricDateIsInvalid() throws Exception {
        mockMvc.perform(
                        get("/api/v1/recovery/analytics/kpis/daily")
                                .param("userId", "invalid-daily-kpi-user")
                                .param("metricDate", "2026/03/19")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400"))
                .andExpect(jsonPath("$.error.details.metricDate").value("yyyy-MM-dd 형식의 날짜여야 합니다."));
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
        OffsetDateTime firstStart = at(metricDate, 9, 0);
        OffsetDateTime firstEnd = at(metricDate, 9, 25);
        OffsetDateTime secondStart = at(metricDate, 10, 0);
        OffsetDateTime secondEnd = at(metricDate, 10, 30);

        ExecutionUnit firstUnit = planningTestFixtures.saveExecutionUnit(userId, "핵심 작업 1");
        Timebox firstTimebox = timeboxRepository.save(Timebox.create(
                userId,
                firstUnit,
                TimeboxType.WORK,
                firstStart,
                firstEnd,
                true,
                firstStart.minusMinutes(10)
        ));
        ExecutionUnit secondUnit = planningTestFixtures.saveExecutionUnit(userId, "핵심 작업 2");
        Timebox secondTimebox = timeboxRepository.save(Timebox.create(
                userId,
                secondUnit,
                TimeboxType.WORK,
                secondStart,
                secondEnd,
                false,
                secondStart.minusMinutes(10)
        ));

        RecoverySession completedSession = RecoverySession.start(userId, firstTimebox.getId(), firstStart);
        completedSession.complete(firstEnd);
        recoverySessionRepository.save(completedSession);

        RecoverySession interruptedSession = RecoverySession.start(userId, secondTimebox.getId(), secondStart.plusMinutes(5));
        interruptedSession.interrupt(secondStart.plusMinutes(20));
        recoverySessionRepository.save(interruptedSession);
        String interruptedSessionId = recoverySessionRepository.findSlicesByUserIdAndStartedAtBetween(
                        userId,
                        firstStart.minusHours(1),
                        secondEnd.plusHours(1)
                ).stream()
                .filter(slice -> slice.getTimeboxId().equals(secondTimebox.getId()))
                .findFirst()
                .map(RecoverySessionRepository.SessionSlice::getSessionId)
                .orElseThrow();

        FailureEvent failureEvent = failureEventRepository.save(FailureEvent.create(
                userId,
                interruptedSessionId,
                secondTimebox.getId(),
                FailureReason.TOO_BIG,
                "범위를 더 줄여야 했다",
                secondStart.plusMinutes(20)
        ));
        String failureEventId = failureEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                        userId,
                        firstStart.minusHours(1),
                        secondEnd.plusHours(1)
                ).stream()
                .filter(slice -> slice.getSessionId().equals(interruptedSessionId))
                .findFirst()
                .map(FailureEventRepository.FailureSlice::getFailureEventId)
                .orElseThrow();

        restartEventRepository.save(RestartEvent.create(
                userId,
                failureEventId,
                RestartType.TEN_MINUTE_RESTART,
                10,
                secondStart.plusMinutes(28)
        ));
    }

    private OffsetDateTime at(LocalDate metricDate, int hour, int minute) {
        return metricDate.atTime(hour, minute).atOffset(SEOUL_OFFSET);
    }
}
