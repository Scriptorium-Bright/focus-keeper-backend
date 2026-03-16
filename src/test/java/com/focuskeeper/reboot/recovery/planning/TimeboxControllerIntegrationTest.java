package com.focuskeeper.reboot.recovery.planning;

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
class TimeboxControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void allocateTimeboxesReturnsStandardSuccessResponse() throws Exception {
        List<String> itemIds = saveInboxItems("timebox-success-user");
        selectBig3("timebox-success-user", itemIds.subList(0, 3));

        String requestBody = """
                {
                  "userId": "timebox-success-user",
                  "timeboxes": [
                    {
                      "itemId": "%s",
                      "startAt": "2026-03-16T09:00:00+09:00",
                      "endAt": "2026-03-16T09:30:00+09:00",
                      "firstRecoveryBlock": true
                    },
                    {
                      "itemId": "%s",
                      "startAt": "2026-03-16T10:00:00+09:00",
                      "endAt": "2026-03-16T10:25:00+09:00",
                      "firstRecoveryBlock": false
                    }
                  ]
                }
                """.formatted(itemIds.get(0), itemIds.get(1));

        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/timeboxes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("TIMEBOXES_ALLOCATED"))
                .andExpect(jsonPath("$.data.allocatedCount").value(2))
                .andExpect(jsonPath("$.data.plannedDate").value("2026-03-16"))
                .andExpect(jsonPath("$.data.timeboxes[0].timeboxId").isString())
                .andExpect(jsonPath("$.data.timeboxes[0].firstRecoveryBlock").value(true))
                .andExpect(jsonPath("$.traceId").isString())
                .andReturn();

        String responseTraceId = readTraceIdFromBody(result);
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertThat(responseTraceId).isEqualTo(headerTraceId);
    }

    @Test
    void allocateTimeboxesReturnsBadRequestWhenFirstRecoveryBlockCountIsInvalid() throws Exception {
        List<String> itemIds = saveInboxItems("timebox-invalid-first-user");
        selectBig3("timebox-invalid-first-user", itemIds.subList(0, 2));

        String requestBody = """
                {
                  "userId": "timebox-invalid-first-user",
                  "timeboxes": [
                    {
                      "itemId": "%s",
                      "startAt": "2026-03-16T09:00:00+09:00",
                      "endAt": "2026-03-16T09:30:00+09:00",
                      "firstRecoveryBlock": false
                    },
                    {
                      "itemId": "%s",
                      "startAt": "2026-03-16T10:00:00+09:00",
                      "endAt": "2026-03-16T10:25:00+09:00",
                      "firstRecoveryBlock": false
                    }
                  ]
                }
                """.formatted(itemIds.get(0), itemIds.get(1));

        mockMvc.perform(
                        post("/api/v1/recovery/timeboxes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400"))
                .andExpect(jsonPath("$.error.details.timeboxes").value("첫 복귀 블록은 정확히 1개여야 합니다."));
    }

    @Test
    void allocateTimeboxesReturnsConflictWhenBlocksOverlap() throws Exception {
        List<String> itemIds = saveInboxItems("timebox-conflict-user");
        selectBig3("timebox-conflict-user", itemIds.subList(0, 2));

        String requestBody = """
                {
                  "userId": "timebox-conflict-user",
                  "timeboxes": [
                    {
                      "itemId": "%s",
                      "startAt": "2026-03-16T09:00:00+09:00",
                      "endAt": "2026-03-16T09:30:00+09:00",
                      "firstRecoveryBlock": true
                    },
                    {
                      "itemId": "%s",
                      "startAt": "2026-03-16T09:20:00+09:00",
                      "endAt": "2026-03-16T09:50:00+09:00",
                      "firstRecoveryBlock": false
                    }
                  ]
                }
                """.formatted(itemIds.get(0), itemIds.get(1));

        mockMvc.perform(
                        post("/api/v1/recovery/timeboxes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isConflict())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("CONFLICT-409"))
                .andExpect(jsonPath("$.error.details.startAt").value("2026-03-16T09:00+09:00"));
    }

    @Test
    void allocateTimeboxesReturnsBadRequestWhenItemIsNotIncludedInTodayBig3() throws Exception {
        List<String> itemIds = saveInboxItems("timebox-non-big3-user");
        selectBig3("timebox-non-big3-user", itemIds.subList(0, 2));

        String requestBody = """
                {
                  "userId": "timebox-non-big3-user",
                  "timeboxes": [
                    {
                      "itemId": "%s",
                      "startAt": "2026-03-16T09:00:00+09:00",
                      "endAt": "2026-03-16T09:30:00+09:00",
                      "firstRecoveryBlock": true
                    }
                  ]
                }
                """.formatted(itemIds.get(2));

        mockMvc.perform(
                        post("/api/v1/recovery/timeboxes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400"))
                .andExpect(jsonPath("$.error.details.invalidItemIds[0]").value(itemIds.get(2)));
    }

    private List<String> saveInboxItems(String userId) throws Exception {
        String requestBody = """
                {
                  "userId": "%s",
                  "items": [
                    {"content": "복귀 대상 업무 정리"},
                    {"content": "문서 초안 보완"},
                    {"content": "API 예외 테스트 작성"}
                  ]
                }
                """.formatted(userId);

        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/inbox-items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
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
        String requestBody = """
                {
                  "userId": "%s",
                  "itemIds": ["%s", "%s"%s]
                }
                """.formatted(
                userId,
                itemIds.get(0),
                itemIds.get(1),
                itemIds.size() == 3 ? ", \"%s\"".formatted(itemIds.get(2)) : ""
        );

        mockMvc.perform(
                        post("/api/v1/recovery/big3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk());
    }

    private String readTraceIdFromBody(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("traceId").asText();
    }
}
