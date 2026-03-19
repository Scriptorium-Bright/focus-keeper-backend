package com.focuskeeper.reboot.recovery.execution.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focuskeeper.reboot.recovery.execution.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
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
class RestartControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestartEventRepository restartEventRepository;

    @Autowired
    private RecoverySessionRepository recoverySessionRepository;

    @Test
    void restartReturnsRestartEventAndNewRecoverySession() throws Exception {
        String userId = "restart-success-user";
        String sessionId = startSessionForUser(userId);
        String failureEventId = checkInFailure(userId, sessionId);
        long restartEventCountBefore = restartEventRepository.count();

        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/restarts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "failureEventId": "%s"
                                        }
                                        """.formatted(userId, failureEventId))
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("RECOVERY_RESTARTED"))
                .andExpect(jsonPath("$.data.restartEvent.id").isString())
                .andExpect(jsonPath("$.data.restartEvent.failureEventId").value(failureEventId))
                .andExpect(jsonPath("$.data.restartEvent.restartType").value("TEN_MINUTE_RESTART"))
                .andExpect(jsonPath("$.data.restartEvent.suggestedMinutes").value(10))
                .andExpect(jsonPath("$.data.recoverySession.sessionId").isString())
                .andExpect(jsonPath("$.data.recoverySession.status").value("STARTED"))
                .andExpect(jsonPath("$.data.restartSuggestion.restartType").value("TEN_MINUTE_RESTART"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String restartedSessionId = body.path("data").path("recoverySession").path("sessionId").asText();

        assertThat(readTraceIdFromBody(result)).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
        assertThat(restartEventRepository.count()).isEqualTo(restartEventCountBefore + 1);

        List<RecoverySession> sessions = recoverySessionRepository.findAllByUserIdOrderByStartedAtAsc(userId);
        assertThat(sessions).hasSize(2);
        assertThat(restartedSessionId).isNotEqualTo(sessionId);
        assertThat(sessions.get(sessions.size() - 1).getStatus()).isEqualTo(RecoverySessionStatus.STARTED);
    }

    @Test
    void restartReturnsNotFoundWhenFailureEventDoesNotExist() throws Exception {
        mockMvc.perform(
                        post("/api/v1/recovery/restarts")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "restart-not-found-user",
                                          "failureEventId": "missing-failure-event"
                                        }
                                        """)
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE-404"))
                .andExpect(jsonPath("$.error.details.failureEventId").value("missing-failure-event"));
    }

    @Test
    void restartReturnsConflictWhenAnotherRecoverySessionIsAlreadyActive() throws Exception {
        String userId = "restart-conflict-user";
        String sessionId = startSessionForUser(userId);
        String failureEventId = checkInFailure(userId, sessionId);

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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONFLICT-409"))
                .andExpect(jsonPath("$.error.details.session").value("이미 진행 중인 복귀 세션이 있습니다."));
    }

    private String startSessionForUser(String userId) throws Exception {
        String timeboxId = allocateFirstRecoveryTimebox(userId);

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

    private String checkInFailure(String userId, String sessionId) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/failures/check-in")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "sessionId": "%s",
                                          "reason": "TOO_BIG",
                                          "note": "범위를 줄여서 다시 시작해야 함"
                                        }
                                        """.formatted(userId, sessionId))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("failureEventId").asText();
    }

    private String allocateFirstRecoveryTimebox(String userId) throws Exception {
        List<String> itemIds = saveInboxItems(userId);
        selectBig3(userId, itemIds.subList(0, 2));

        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/timeboxes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "timeboxes": [
                                            {
                                              "itemId": "%s",
                                              "startAt": "2026-03-19T09:00:00+09:00",
                                              "endAt": "2026-03-19T09:30:00+09:00",
                                              "firstRecoveryBlock": true
                                            },
                                            {
                                              "itemId": "%s",
                                              "startAt": "2026-03-19T10:00:00+09:00",
                                              "endAt": "2026-03-19T10:25:00+09:00",
                                              "firstRecoveryBlock": false
                                            }
                                          ]
                                        }
                                        """.formatted(userId, itemIds.get(0), itemIds.get(1)))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("timeboxes").get(0).path("timeboxId").asText();
    }

    private List<String> saveInboxItems(String userId) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/inbox-items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "items": [
                                            {"content": "재시작 대상 업무 정리"},
                                            {"content": "실패 후 재시작 API 구현"},
                                            {"content": "Recovery24 입력 검증"}
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

    private String readTraceIdFromBody(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("traceId").asText();
    }
}
