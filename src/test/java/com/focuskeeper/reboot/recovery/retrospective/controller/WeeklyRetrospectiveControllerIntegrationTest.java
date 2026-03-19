package com.focuskeeper.reboot.recovery.retrospective.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focuskeeper.reboot.recovery.retrospective.repository.WeeklyRetrospectiveRepository;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class WeeklyRetrospectiveControllerIntegrationTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private WeeklyRetrospectiveRepository weeklyRetrospectiveRepository;

    @Test
    void generateWeeklyRetrospectiveAggregatesRecoveryLoopForCurrentWeek() throws Exception {
        String userId = "weekly-retrospective-user";
        List<String> timeboxIds = allocateRecoveryTimeboxes(userId, 9);

        String sessionId = startSession(userId, timeboxIds.get(0));
        completeSession(userId, sessionId);

        String failedSessionId = startSession(userId, timeboxIds.get(1));
        String failureEventId = checkInFailure(userId, failedSessionId);
        restart(userId, failureEventId);

        String weekStart = currentWeekStart();

        mockMvc.perform(
                        post("/api/v1/recovery/retrospectives/weekly")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "weekStart": "%s"
                                        }
                                        """.formatted(userId, weekStart))
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("WEEKLY_RETROSPECTIVE_GENERATED"))
                .andExpect(jsonPath("$.data.weekStart").value(weekStart))
                .andExpect(jsonPath("$.data.weekEnd").value(LocalDate.parse(weekStart).plusDays(6).toString()))
                .andExpect(jsonPath("$.data.sessionStartedCount").value(3))
                .andExpect(jsonPath("$.data.sessionCompletedCount").value(1))
                .andExpect(jsonPath("$.data.sessionInterruptedCount").value(1))
                .andExpect(jsonPath("$.data.failureCount").value(1))
                .andExpect(jsonPath("$.data.restartCount").value(1))
                .andExpect(jsonPath("$.data.dominantFailureReason").value("TOO_BIG"))
                .andExpect(jsonPath("$.data.summary").value("이번 주에는 일이 너무 크게 느껴져 첫 복귀 블록 진입 장벽이 높았다."))
                .andExpect(jsonPath("$.data.antiSlipAction.actionCode").value("SPLIT_FIRST_BLOCK"))
                .andExpect(jsonPath("$.data.antiSlipAction.title").value("첫 복귀 블록을 25분 이하로 쪼개기"));

        assertThat(weeklyRetrospectiveRepository.findByUserIdAndWeekStart(userId, LocalDate.parse(weekStart))).isPresent();
    }

    @Test
    void generateWeeklyRetrospectiveUpsertsSameWeekInsteadOfCreatingDuplicate() throws Exception {
        String userId = "weekly-retrospective-upsert-user";
        String sessionId = startSessionForUser(userId, 9);
        checkInFailure(userId, sessionId);
        String weekStart = currentWeekStart();

        MvcResult first = mockMvc.perform(
                        post("/api/v1/recovery/retrospectives/weekly")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "weekStart": "%s"
                                        }
                                        """.formatted(userId, weekStart))
                )
                .andExpect(status().isOk())
                .andReturn();

        MvcResult second = mockMvc.perform(
                        post("/api/v1/recovery/retrospectives/weekly")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "weekStart": "%s"
                                        }
                                        """.formatted(userId, weekStart))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());

        assertThat(weeklyRetrospectiveRepository.findByUserIdAndWeekStart(userId, LocalDate.parse(weekStart))).isPresent();
        assertThat(secondBody.path("data").path("retrospectiveId").asText())
                .isEqualTo(firstBody.path("data").path("retrospectiveId").asText());
    }

    @Test
    void getWeeklyRetrospectiveReturnsGeneratedRetrospective() throws Exception {
        String userId = "weekly-retrospective-get-user";
        String sessionId = startSessionForUser(userId, 9);
        completeSession(userId, sessionId);
        String weekStart = currentWeekStart();

        mockMvc.perform(
                        post("/api/v1/recovery/retrospectives/weekly")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "weekStart": "%s"
                                        }
                                        """.formatted(userId, weekStart))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        get("/api/v1/recovery/retrospectives/weekly")
                                .param("userId", userId)
                                .param("weekStart", weekStart)
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("WEEKLY_RETROSPECTIVE_FETCHED"))
                .andExpect(jsonPath("$.data.weekStart").value(weekStart))
                .andExpect(jsonPath("$.data.sessionCompletedCount").value(1))
                .andExpect(jsonPath("$.data.antiSlipAction.actionCode").value("KEEP_RESTART_SMALL"));
    }

    @Test
    void getWeeklyRetrospectiveReturnsNotFoundWhenRetrospectiveDoesNotExist() throws Exception {
        mockMvc.perform(
                        get("/api/v1/recovery/retrospectives/weekly")
                                .param("userId", "missing-weekly-retro-user")
                                .param("weekStart", currentWeekStart())
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE-404"));
    }

    @Test
    void getWeeklyRetrospectiveReturnsBadRequestWhenWeekStartIsInvalid() throws Exception {
        mockMvc.perform(
                        get("/api/v1/recovery/retrospectives/weekly")
                                .param("userId", "invalid-week-start-user")
                                .param("weekStart", "2026/03/16")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400"))
                .andExpect(jsonPath("$.error.details.weekStart").value("yyyy-MM-dd 형식의 날짜여야 합니다."));
    }

    private String currentWeekStart() {
        return LocalDate.now(SEOUL).with(DayOfWeek.MONDAY).toString();
    }

    private String startSessionForUser(String userId, int startHour) throws Exception {
        String timeboxId = allocateFirstRecoveryTimebox(userId, startHour);
        return startSession(userId, timeboxId);
    }

    private String startSession(String userId, String timeboxId) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/sessions/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "timeboxId": "%s"
                                        }
                                        """.formatted(userId, timeboxId))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("sessionId").asText();
    }

    private void completeSession(String userId, String sessionId) throws Exception {
        mockMvc.perform(
                        post("/api/v1/recovery/sessions/complete")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "sessionId": "%s"
                                        }
                                        """.formatted(userId, sessionId))
                )
                .andExpect(status().isOk());
    }

    private String checkInFailure(String userId, String sessionId) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/failures/check-in")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "sessionId": "%s",
                                          "reason": "TOO_BIG",
                                          "note": "이번 주에도 범위를 줄여야 했다"
                                        }
                                        """.formatted(userId, sessionId))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("failureEventId").asText();
    }

    private void restart(String userId, String failureEventId) throws Exception {
        mockMvc.perform(
                        post("/api/v1/recovery/restarts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "failureEventId": "%s"
                                        }
                                        """.formatted(userId, failureEventId))
                )
                .andExpect(status().isOk());
    }

    private String allocateFirstRecoveryTimebox(String userId, int startHour) throws Exception {
        return allocateRecoveryTimeboxes(userId, startHour).get(0);
    }

    private List<String> allocateRecoveryTimeboxes(String userId, int startHour) throws Exception {
        List<String> itemIds = saveInboxItems(userId);
        selectBig3(userId, itemIds.subList(0, 2));

        int secondHour = startHour + 1;
        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/timeboxes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "timeboxes": [
                                            {
                                              "itemId": "%s",
                                              "startAt": "2026-03-19T%02d:00:00+09:00",
                                              "endAt": "2026-03-19T%02d:30:00+09:00",
                                              "type": "WORK",
                                              "firstRecoveryBlock": true
                                            },
                                            {
                                              "itemId": "%s",
                                              "startAt": "2026-03-19T%02d:00:00+09:00",
                                              "endAt": "2026-03-19T%02d:25:00+09:00",
                                              "type": "WORK",
                                              "firstRecoveryBlock": false
                                            }
                                          ]
                                        }
                                        """.formatted(userId, itemIds.get(0), startHour, startHour, itemIds.get(1), secondHour, secondHour))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode timeboxes = body.path("data").path("timeboxes");

        List<String> timeboxIds = new ArrayList<>();
        for (JsonNode timebox : timeboxes) {
            timeboxIds.add(timebox.path("timeboxId").asText());
        }
        return timeboxIds;
    }

    private List<String> saveInboxItems(String userId) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/inbox-items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "items": [
                                            {"content": "주간 회고 집계"},
                                            {"content": "실패 원인 분석"},
                                            {"content": "다음 주 anti-slip action"}
                                          ]
                                        }
                                        """.formatted(userId))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode savedItems = body.path("data").path("savedItems");

        List<String> itemIds = new ArrayList<>();
        for (JsonNode savedItem : savedItems) {
            itemIds.add(savedItem.path("id").asText());
        }
        return itemIds;
    }

    private void selectBig3(String userId, List<String> itemIds) throws Exception {
        mockMvc.perform(
                        post("/api/v1/recovery/big3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "itemIds": ["%s", "%s"]
                                        }
                                        """.formatted(userId, itemIds.get(0), itemIds.get(1)))
                )
                .andExpect(status().isOk());
    }
}
