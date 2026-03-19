package com.focuskeeper.reboot.recovery.analytics.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyFunnelReportRepository;
import com.focuskeeper.reboot.recovery.execution.FailureReason;
import com.focuskeeper.reboot.recovery.execution.RestartType;
import com.focuskeeper.reboot.recovery.execution.entity.FailureEvent;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import com.focuskeeper.reboot.recovery.execution.entity.RestartEvent;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import com.focuskeeper.reboot.recovery.inbox.entity.InboxItem;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Selection;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.Big3SelectionRepository;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class DailyFunnelControllerIntegrationTest {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DailyFunnelReportRepository dailyFunnelReportRepository;

    @Autowired
    private InboxItemRepository inboxItemRepository;

    @Autowired
    private Big3SelectionRepository big3SelectionRepository;

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
        restartEventRepository.deleteAll();
        failureEventRepository.deleteAll();
        recoverySessionRepository.deleteAll();
        timeboxRepository.deleteAll();
        big3SelectionRepository.deleteAll();
        inboxItemRepository.deleteAll();
        dailyFunnelReportRepository.deleteAll();
    }

    @Test
    void generateDailyFunnelCalculatesStageCountsAndRates() throws Exception {
        LocalDate metricDate = LocalDate.of(2026, 3, 19);
        seedFunnel(metricDate);

        mockMvc.perform(
                        post("/api/v1/recovery/analytics/funnels/daily")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "metricDate": "%s"
                                        }
                                        """.formatted(metricDate))
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("DAILY_FUNNEL_GENERATED"))
                .andExpect(jsonPath("$.data.metricDate").value(metricDate.toString()))
                .andExpect(jsonPath("$.data.brainDumpUsers").value(3))
                .andExpect(jsonPath("$.data.big3Users").value(2))
                .andExpect(jsonPath("$.data.timeboxUsers").value(2))
                .andExpect(jsonPath("$.data.sessionStartedUsers").value(2))
                .andExpect(jsonPath("$.data.failureUsers").value(1))
                .andExpect(jsonPath("$.data.restartUsers").value(1))
                .andExpect(jsonPath("$.data.big3SelectionRate").value(0.6667))
                .andExpect(jsonPath("$.data.timeboxPlanningRate").value(1.0000))
                .andExpect(jsonPath("$.data.sessionStartRate").value(1.0000))
                .andExpect(jsonPath("$.data.failureRate").value(0.5000))
                .andExpect(jsonPath("$.data.restartRate").value(1.0000));

        assertThat(dailyFunnelReportRepository.findByMetricDate(metricDate)).isPresent();
    }

    @Test
    void generateDailyFunnelUpsertsSameMetricDate() throws Exception {
        LocalDate metricDate = LocalDate.of(2026, 3, 20);
        seedFunnel(metricDate);

        MvcResult first = mockMvc.perform(
                        post("/api/v1/recovery/analytics/funnels/daily")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "metricDate": "%s"
                                        }
                                        """.formatted(metricDate))
                )
                .andExpect(status().isOk())
                .andReturn();

        MvcResult second = mockMvc.perform(
                        post("/api/v1/recovery/analytics/funnels/daily")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "metricDate": "%s"
                                        }
                                        """.formatted(metricDate))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());

        assertThat(secondBody.path("data").path("dailyFunnelId").asText())
                .isEqualTo(firstBody.path("data").path("dailyFunnelId").asText());
    }

    @Test
    void getDailyFunnelReturnsGeneratedReport() throws Exception {
        LocalDate metricDate = LocalDate.of(2026, 3, 21);
        seedFunnel(metricDate);
        generateDailyFunnel(metricDate);

        mockMvc.perform(
                        get("/api/v1/recovery/analytics/funnels/daily")
                                .param("metricDate", metricDate.toString())
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("DAILY_FUNNEL_FETCHED"))
                .andExpect(jsonPath("$.data.metricDate").value(metricDate.toString()));
    }

    @Test
    void getDailyFunnelReturnsNotFoundWhenReportDoesNotExist() throws Exception {
        mockMvc.perform(
                        get("/api/v1/recovery/analytics/funnels/daily")
                                .param("metricDate", "2026-03-22")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE-404"));
    }

    @Test
    void getDailyFunnelReturnsBadRequestWhenDateIsInvalid() throws Exception {
        mockMvc.perform(
                        get("/api/v1/recovery/analytics/funnels/daily")
                                .param("metricDate", "2026/03/19")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400"))
                .andExpect(jsonPath("$.error.details.metricDate").value("yyyy-MM-dd 형식의 날짜여야 합니다."));
    }

    private void generateDailyFunnel(LocalDate metricDate) throws Exception {
        mockMvc.perform(
                        post("/api/v1/recovery/analytics/funnels/daily")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "metricDate": "%s"
                                        }
                                        """.formatted(metricDate))
                )
                .andExpect(status().isOk());
    }

    private void seedFunnel(LocalDate metricDate) {
        OffsetDateTime morning = metricDate.atTime(9, 0).atOffset(SEOUL_OFFSET);

        InboxItem userAInbox = inboxItemRepository.save(InboxItem.create("funnel-user-a", "작업 A", morning));
        InboxItem userBInbox = inboxItemRepository.save(InboxItem.create("funnel-user-b", "작업 B", morning.plusMinutes(5)));
        inboxItemRepository.save(InboxItem.create("funnel-user-c", "작업 C", morning.plusMinutes(10)));

        Big3Selection userABig3 = Big3Selection.create("funnel-user-a", metricDate, morning.plusMinutes(15));
        userABig3.replaceItems(List.of(userAInbox), morning.plusMinutes(15));
        big3SelectionRepository.save(userABig3);

        Big3Selection userBBig3 = Big3Selection.create("funnel-user-b", metricDate, morning.plusMinutes(20));
        userBBig3.replaceItems(List.of(userBInbox), morning.plusMinutes(20));
        big3SelectionRepository.save(userBBig3);

        Timebox userATimebox = timeboxRepository.save(Timebox.create(
                "funnel-user-a",
                userAInbox.getId(),
                userAInbox.getContent(),
                TimeboxType.WORK,
                morning.plusMinutes(30),
                morning.plusMinutes(55),
                true,
                morning.plusMinutes(25)
        ));
        Timebox userBTimebox = timeboxRepository.save(Timebox.create(
                "funnel-user-b",
                userBInbox.getId(),
                userBInbox.getContent(),
                TimeboxType.WORK,
                morning.plusHours(1),
                morning.plusHours(1).plusMinutes(25),
                true,
                morning.plusHours(1).minusMinutes(5)
        ));

        RecoverySession userASession = recoverySessionRepository.save(
                RecoverySession.start("funnel-user-a", userATimebox.getId(), morning.plusMinutes(30))
        );
        RecoverySession userBSession = recoverySessionRepository.save(
                RecoverySession.start("funnel-user-b", userBTimebox.getId(), morning.plusHours(1))
        );

        String userASessionId = recoverySessionRepository.findSlicesByUserIdAndStartedAtBetween(
                        "funnel-user-a",
                        morning,
                        morning.plusDays(1)
                ).stream()
                .findFirst()
                .map(RecoverySessionRepository.SessionSlice::getSessionId)
                .orElseThrow();

        FailureEvent failureEvent = failureEventRepository.save(FailureEvent.create(
                "funnel-user-a",
                userASessionId,
                userATimebox.getId(),
                FailureReason.TOO_BIG,
                "다시 쪼개야 했다",
                morning.plusMinutes(40)
        ));

        String failureEventId = failureEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                        "funnel-user-a",
                        morning,
                        morning.plusDays(1)
                ).stream()
                .findFirst()
                .map(FailureEventRepository.FailureSlice::getFailureEventId)
                .orElseThrow();

        restartEventRepository.save(RestartEvent.create(
                "funnel-user-a",
                failureEventId,
                RestartType.TEN_MINUTE_RESTART,
                10,
                morning.plusMinutes(48)
        ));
    }
}
