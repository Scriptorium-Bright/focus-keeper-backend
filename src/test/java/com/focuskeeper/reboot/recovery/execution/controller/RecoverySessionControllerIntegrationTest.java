package com.focuskeeper.reboot.recovery.execution.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
class RecoverySessionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void startSessionReturnsStandardSuccessResponse() throws Exception {
        String timeboxId = allocateFirstRecoveryTimebox("session-start-user");

        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/sessions/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "session-start-user",
                                          "timeboxId": "%s"
                                        }
                                        """.formatted(timeboxId))
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("RECOVERY_SESSION_STARTED"))
                .andExpect(jsonPath("$.data.sessionId").isString())
                .andExpect(jsonPath("$.data.timeboxId").value(timeboxId))
                .andExpect(jsonPath("$.data.status").value("STARTED"))
                .andExpect(jsonPath("$.traceId").isString())
                .andReturn();

        assertThat(readTraceIdFromBody(result)).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
    }

    @Test
    void completeSessionReturnsUpdatedStatus() throws Exception {
        String timeboxId = allocateFirstRecoveryTimebox("session-complete-user");
        String sessionId = startSession("session-complete-user", timeboxId);

        mockMvc.perform(
                        post("/api/v1/recovery/sessions/complete")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "session-complete-user",
                                          "sessionId": "%s"
                                        }
                                        """.formatted(sessionId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("RECOVERY_SESSION_COMPLETED"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.endedAt").isString());
    }

    @Test
    void interruptSessionReturnsUpdatedStatus() throws Exception {
        String timeboxId = allocateFirstRecoveryTimebox("session-interrupt-user");
        String sessionId = startSession("session-interrupt-user", timeboxId);

        mockMvc.perform(
                        post("/api/v1/recovery/sessions/interrupt")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "session-interrupt-user",
                                          "sessionId": "%s"
                                        }
                                        """.formatted(sessionId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("RECOVERY_SESSION_INTERRUPTED"))
                .andExpect(jsonPath("$.data.status").value("INTERRUPTED"))
                .andExpect(jsonPath("$.data.endedAt").isString());
    }

    @Test
    void completeSessionReturnsConflictWhenSessionIsAlreadyEnded() throws Exception {
        String timeboxId = allocateFirstRecoveryTimebox("session-conflict-user");
        String sessionId = startSession("session-conflict-user", timeboxId);
        completeSession("session-conflict-user", sessionId);

        mockMvc.perform(
                        post("/api/v1/recovery/sessions/complete")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "session-conflict-user",
                                          "sessionId": "%s"
                                        }
                                        """.formatted(sessionId))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONFLICT-409"))
                .andExpect(jsonPath("$.error.details.currentStatus").value("COMPLETED"));
    }

    @Test
    void startSessionReturnsConflictWhenTargetTimeboxIsBreak() throws Exception {
        String breakTimeboxId = allocateWorkAndBreakTimeboxes("session-break-user").get(1);

        mockMvc.perform(
                        post("/api/v1/recovery/sessions/start")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "session-break-user",
                                          "timeboxId": "%s"
                                        }
                                        """.formatted(breakTimeboxId))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONFLICT-409"))
                .andExpect(jsonPath("$.error.details.timeboxId").value("BREAK timebox로는 복귀 세션을 시작할 수 없습니다."));
    }

    private String allocateFirstRecoveryTimebox(String userId) throws Exception {
        return allocateWorkAndBreakTimeboxes(userId).get(0);
    }

    private List<String> allocateWorkAndBreakTimeboxes(String userId) throws Exception {
        List<String> itemIds = saveInboxItems(userId);
        List<String> executionUnitIds = createExecutionUnits(userId, selectBig3(userId, itemIds.subList(0, 2)));

        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/timeboxes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "timeboxes": [
                                            {
                                              "executionUnitId": "%s",
                                              "startAt": "2026-03-16T09:00:00+09:00",
                                              "endAt": "2026-03-16T09:30:00+09:00",
                                              "type": "WORK",
                                              "firstRecoveryBlock": true
                                            },
                                            {
                                              "executionUnitId": "%s",
                                              "startAt": "2026-03-16T09:30:00+09:00",
                                              "endAt": "2026-03-16T09:40:00+09:00",
                                              "type": "BREAK",
                                              "firstRecoveryBlock": false
                                            }
                                          ]
                                        }
                                        """.formatted(userId, executionUnitIds.get(0), executionUnitIds.get(1)))
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
                                            {"content": "복귀 대상 업무 정리"},
                                            {"content": "세션 상태 전이 테스트 작성"},
                                            {"content": "예외 시나리오 점검"}
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

    private List<String> selectBig3(String userId, List<String> itemIds) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/big3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "itemIds": ["%s", "%s"]
                                        }
                                        """.formatted(userId, itemIds.get(0), itemIds.get(1)))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode selectedItems = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("selectedItems");
        List<String> selectionItemIds = new ArrayList<>();
        for (JsonNode selectedItem : selectedItems) {
            selectionItemIds.add(selectedItem.path("big3SelectionItemId").asText());
        }
        return selectionItemIds;
    }

    private List<String> createExecutionUnits(String userId, List<String> selectionItemIds) throws Exception {
        List<String> executionUnitIds = new ArrayList<>();
        for (int index = 0; index < selectionItemIds.size(); index++) {
            MvcResult result = mockMvc.perform(
                            post("/api/v1/recovery/execution-units")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "userId": "%s",
                                              "big3SelectionItemId": "%s",
                                              "title": "세션 실행 단위 %d"
                                            }
                                            """.formatted(userId, selectionItemIds.get(index), index + 1))
                    )
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            executionUnitIds.add(body.path("data").path("executionUnitId").asText());
        }
        return executionUnitIds;
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

    private String readTraceIdFromBody(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("traceId").asText();
    }
}
