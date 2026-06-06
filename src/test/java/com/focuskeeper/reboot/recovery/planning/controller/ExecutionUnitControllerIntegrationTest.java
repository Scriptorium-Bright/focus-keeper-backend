package com.focuskeeper.reboot.recovery.planning.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class ExecutionUnitControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createExecutionUnitReturnsStandardSuccessResponse() throws Exception {
        String userId = "execution-unit-create-user";
        String big3ItemId = selectFirstBig3Item(userId);

        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/execution-units")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "big3ItemId": "%s",
                                          "title": "README 문제 섹션 초안 작성"
                                        }
                                        """.formatted(userId, big3ItemId))
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("EXECUTION_UNIT_CREATED"))
                .andExpect(jsonPath("$.data.executionUnitId").isString())
                .andExpect(jsonPath("$.data.big3ItemId").value(big3ItemId))
                .andExpect(jsonPath("$.data.title").value("README 문제 섹션 초안 작성"))
                .andExpect(jsonPath("$.data.status").value("PLANNED"))
                .andExpect(jsonPath("$.data.completedAt").isEmpty())
                .andExpect(jsonPath("$.traceId").isString())
                .andReturn();

        String responseTraceId = readTraceIdFromBody(result);
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertThat(responseTraceId).isEqualTo(headerTraceId);
    }

    @Test
    void updateExecutionUnitRenamesExistingUnit() throws Exception {
        String userId = "execution-unit-update-user";
        String big3ItemId = selectFirstBig3Item(userId);
        String executionUnitId = createExecutionUnit(userId, big3ItemId, "초안 작성");

        mockMvc.perform(
                        patch("/api/v1/recovery/execution-units/{executionUnitId}", executionUnitId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "title": "초안 검토까지 완료"
                                        }
                                        """.formatted(userId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("EXECUTION_UNIT_UPDATED"))
                .andExpect(jsonPath("$.data.executionUnitId").value(executionUnitId))
                .andExpect(jsonPath("$.data.title").value("초안 검토까지 완료"))
                .andExpect(jsonPath("$.data.status").value("PLANNED"));
    }

    @Test
    void completeExecutionUnitMarksUnitCompletedWithoutSessionCompletion() throws Exception {
        String userId = "execution-unit-complete-user";
        String big3ItemId = selectFirstBig3Item(userId);
        String executionUnitId = createExecutionUnit(userId, big3ItemId, "작은 실행 완료");

        mockMvc.perform(
                        post("/api/v1/recovery/execution-units/{executionUnitId}/complete", executionUnitId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s"
                                        }
                                        """.formatted(userId))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("EXECUTION_UNIT_COMPLETED"))
                .andExpect(jsonPath("$.data.executionUnitId").value(executionUnitId))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.completedAt").isString());
    }

    @Test
    void completeExecutionUnitReturnsConflictWhenAlreadyCompleted() throws Exception {
        String userId = "execution-unit-complete-conflict-user";
        String big3ItemId = selectFirstBig3Item(userId);
        String executionUnitId = createExecutionUnit(userId, big3ItemId, "작은 실행 완료");

        mockMvc.perform(
                        post("/api/v1/recovery/execution-units/{executionUnitId}/complete", executionUnitId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s"
                                        }
                                        """.formatted(userId))
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/v1/recovery/execution-units/{executionUnitId}/complete", executionUnitId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s"
                                        }
                                        """.formatted(userId))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONFLICT-409"))
                .andExpect(jsonPath("$.error.details.currentStatus").value("COMPLETED"));
    }

    @Test
    void createExecutionUnitReturnsNotFoundWhenBig3ItemIsNotOwnedByUser() throws Exception {
        mockMvc.perform(
                        post("/api/v1/recovery/execution-units")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "execution-unit-not-found-user",
                                          "big3ItemId": "missing-big3-item",
                                          "title": "작은 실행 단위"
                                        }
                                        """)
                )
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE-404"))
                .andExpect(jsonPath("$.error.details.big3ItemId").value("missing-big3-item"));
    }

    private String createExecutionUnit(String userId, String big3ItemId, String title) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/execution-units")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "big3ItemId": "%s",
                                          "title": "%s"
                                        }
                                        """.formatted(userId, big3ItemId, title))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("executionUnitId").asText();
    }

    private String selectFirstBig3Item(String userId) throws Exception {
        List<String> itemIds = saveInboxItems(userId);
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

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("selectedItems").get(0).path("big3ItemId").asText();
    }

    private List<String> saveInboxItems(String userId) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/inbox-items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "items": [
                                            {"content": "포트폴리오"},
                                            {"content": "면접 준비"}
                                          ]
                                        }
                                        """.formatted(userId))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode savedItems = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("savedItems");
        List<String> itemIds = new ArrayList<>();
        for (JsonNode savedItem : savedItems) {
            itemIds.add(savedItem.path("id").asText());
        }
        return itemIds;
    }

    private String readTraceIdFromBody(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("traceId").asText();
    }
}
