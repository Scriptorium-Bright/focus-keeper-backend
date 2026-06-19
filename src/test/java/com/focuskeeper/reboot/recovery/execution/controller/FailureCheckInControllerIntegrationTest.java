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
class FailureCheckInControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void checkInReturnsStandardSuccessResponse() throws Exception {
        String sessionId = startSessionForUser("failure-success-user");

        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/failures/check-in")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "failure-success-user",
                                          "sessionId": "%s",
                                          "reason": "INTERRUPTION",
                                          "note": "긴급 슬랙 메시지 대응"
                                        }
                                        """.formatted(sessionId))
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("FAILURE_CHECKED_IN"))
                .andExpect(jsonPath("$.data.failureEventId").isString())
                .andExpect(jsonPath("$.data.sessionId").value(sessionId))
                .andExpect(jsonPath("$.data.reason").value("INTERRUPTION"))
                .andExpect(jsonPath("$.data.sessionStatus").value("INTERRUPTED"))
                .andExpect(jsonPath("$.data.restartSuggestion.restartType").value("TEN_MINUTE_RESTART"))
                .andExpect(jsonPath("$.data.restartSuggestion.suggestedMinutes").value(10))
                .andExpect(jsonPath("$.data.restartSuggestion.message").isString())
                .andReturn();

        assertThat(readTraceIdFromBody(result)).isEqualTo(result.getResponse().getHeader("X-Trace-Id"));
    }

    @Test
    void checkInReturnsBadRequestWhenReasonIsInvalid() throws Exception {
        String sessionId = startSessionForUser("failure-invalid-reason-user");

        mockMvc.perform(
                        post("/api/v1/recovery/failures/check-in")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "failure-invalid-reason-user",
                                          "sessionId": "%s",
                                          "reason": "UNKNOWN_REASON",
                                          "note": "사유 미정"
                                        }
                                        """.formatted(sessionId))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400"))
                .andExpect(jsonPath("$.error.details.reason").value("지원하지 않는 failure reason입니다."));
    }

    @Test
    void checkInReturnsConflictWhenSessionIsAlreadyCompleted() throws Exception {
        String userId = "failure-completed-user";
        String sessionId = startSessionForUser(userId);
        completeSession(userId, sessionId);

        mockMvc.perform(
                        post("/api/v1/recovery/failures/check-in")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "sessionId": "%s",
                                          "reason": "LOW_ENERGY",
                                          "note": "에너지 저하"
                                        }
                                        """.formatted(userId, sessionId))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONFLICT-409"))
                .andExpect(jsonPath("$.error.details.currentStatus").value("COMPLETED"));
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

    private String allocateFirstRecoveryTimebox(String userId) throws Exception {
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
                                              "startAt": "2026-03-16T10:00:00+09:00",
                                              "endAt": "2026-03-16T10:25:00+09:00",
                                              "type": "WORK",
                                              "firstRecoveryBlock": false
                                            }
                                          ]
                                        }
                                        """.formatted(userId, executionUnitIds.get(0), executionUnitIds.get(1)))
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
                                            {"content": "복귀 대상 업무 정리"},
                                            {"content": "실패 체크인 API 구현"},
                                            {"content": "실패 사유 enum 점검"}
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
        List<String> big3ItemIds = new ArrayList<>();
        for (JsonNode selectedItem : selectedItems) {
            big3ItemIds.add(selectedItem.path("big3ItemId").asText());
        }
        return big3ItemIds;
    }

    private List<String> createExecutionUnits(String userId, List<String> big3ItemIds) throws Exception {
        List<String> executionUnitIds = new ArrayList<>();
        for (int index = 0; index < big3ItemIds.size(); index++) {
            MvcResult result = mockMvc.perform(
                            post("/api/v1/recovery/execution-units")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "userId": "%s",
                                              "big3ItemId": "%s",
                                              "title": "복귀 실행 단위 %d"
                                            }
                                            """.formatted(userId, big3ItemIds.get(index), index + 1))
                    )
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            executionUnitIds.add(body.path("data").path("executionUnitId").asText());
        }
        return executionUnitIds;
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
