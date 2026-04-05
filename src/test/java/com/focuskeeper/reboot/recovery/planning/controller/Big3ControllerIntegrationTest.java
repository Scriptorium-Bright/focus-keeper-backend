package com.focuskeeper.reboot.recovery.planning.controller;

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
class Big3ControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void selectBig3ReturnsStandardSuccessResponse() throws Exception {
        List<String> savedItemIds = saveInboxItems("big3-success-user");
        String requestBody = """
                {
                  "userId": "big3-success-user",
                  "itemIds": ["%s", "%s", "%s"]
                }
                """.formatted(savedItemIds.get(0), savedItemIds.get(1), savedItemIds.get(2));

        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/big3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("BIG3_SELECTED"))
                .andExpect(jsonPath("$.data.selectedCount").value(3))
                .andExpect(jsonPath("$.data.selectedDate").isString())
                .andExpect(jsonPath("$.data.selectedAt").isString())
                .andExpect(jsonPath("$.data.selectedItems[0].itemId").value(savedItemIds.get(0)))
                .andExpect(jsonPath("$.traceId").isString())
                .andReturn();

        String responseTraceId = readTraceIdFromBody(result);
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertThat(responseTraceId).isEqualTo(headerTraceId);
    }

    @Test
    void selectBig3ReplacesSameDaySelectionWithoutDuplicateSortOrderConflict() throws Exception {
        List<String> savedItemIds = saveInboxItems("big3-reselect-user");

        String firstRequestBody = """
                {
                  "userId": "big3-reselect-user",
                  "itemIds": ["%s", "%s", "%s"]
                }
                """.formatted(savedItemIds.get(0), savedItemIds.get(1), savedItemIds.get(2));

        String secondRequestBody = """
                {
                  "userId": "big3-reselect-user",
                  "itemIds": ["%s", "%s"]
                }
                """.formatted(savedItemIds.get(2), savedItemIds.get(1));

        mockMvc.perform(
                        post("/api/v1/recovery/big3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(firstRequestBody)
                )
                .andExpect(status().isOk());

        mockMvc.perform(
                        post("/api/v1/recovery/big3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(secondRequestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.selectedItems.length()").value(2))
                .andExpect(jsonPath("$.data.selectedItems[0].itemId").value(savedItemIds.get(2)))
                .andExpect(jsonPath("$.data.selectedItems[1].itemId").value(savedItemIds.get(1)));
    }

    @Test
    void selectBig3ReturnsValidationErrorWhenMoreThanThreeItemsRequested() throws Exception {
        String invalidRequestBody = """
                {
                  "userId": "big3-invalid-user",
                  "itemIds": ["1", "2", "3", "4"]
                }
                """;

        mockMvc.perform(
                        post("/api/v1/recovery/big3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(invalidRequestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400"))
                .andExpect(jsonPath("$.error.details.itemIds").exists());
    }

    @Test
    void selectBig3ReturnsBadRequestWhenDuplicateItemIdsAreRequested() throws Exception {
        String duplicateRequestBody = """
                {
                  "userId": "big3-duplicate-user",
                  "itemIds": ["10", "10"]
                }
                """;

        mockMvc.perform(
                        post("/api/v1/recovery/big3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(duplicateRequestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400"))
                .andExpect(jsonPath("$.error.details.itemIds").value("중복된 itemId는 허용되지 않습니다."));
    }

    @Test
    void selectBig3ReturnsNotFoundWhenInboxItemDoesNotExist() throws Exception {
        String notFoundRequestBody = """
                {
                  "userId": "big3-not-found-user",
                  "itemIds": ["999999"]
                }
                """;

        mockMvc.perform(
                        post("/api/v1/recovery/big3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(notFoundRequestBody)
                )
                .andExpect(status().isNotFound())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE-404"))
                .andExpect(jsonPath("$.error.details.missingItemIds[0]").value("999999"));
    }

    private List<String> saveInboxItems(String userId) throws Exception {
        String requestBody = """
                {
                  "userId": "%s",
                  "items": [
                    {"content": "업무 우선순위 정리"},
                    {"content": "Big3 연동 API 구현"},
                    {"content": "회귀 테스트 점검"}
                  ]
                }
                """.formatted(userId);

        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/inbox-items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode savedItems = body.path("data").path("savedItems");

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
