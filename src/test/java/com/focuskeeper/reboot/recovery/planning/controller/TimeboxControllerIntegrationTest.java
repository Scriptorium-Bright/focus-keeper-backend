package com.focuskeeper.reboot.recovery.planning.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focuskeeper.reboot.recovery.planning.constant.TimeboxStatus;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import java.time.LocalDate;
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

    @Autowired
    private TimeboxRepository timeboxRepository;

    @Test
    void allocateTimeboxesReturnsStandardSuccessResponse() throws Exception {
        List<String> itemIds = saveInboxItems("timebox-success-user");
        List<String> big3ItemIds = selectBig3("timebox-success-user", itemIds.subList(0, 3));
        List<String> executionUnitIds = createExecutionUnits("timebox-success-user", big3ItemIds);

        String requestBody = """
                {
                  "userId": "timebox-success-user",
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
                """.formatted(executionUnitIds.get(0), executionUnitIds.get(1));

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
                .andExpect(jsonPath("$.data.timeboxes[0].type").value("WORK"))
                .andExpect(jsonPath("$.traceId").isString())
                .andReturn();

        String responseTraceId = readTraceIdFromBody(result);
        String headerTraceId = result.getResponse().getHeader("X-Trace-Id");
        assertThat(responseTraceId).isEqualTo(headerTraceId);
    }

    @Test
    void allocateTimeboxesReturnsBadRequestWhenFirstRecoveryBlockCountIsInvalid() throws Exception {
        List<String> itemIds = saveInboxItems("timebox-invalid-first-user");
        List<String> executionUnitIds = createExecutionUnits(
                "timebox-invalid-first-user",
                selectBig3("timebox-invalid-first-user", itemIds.subList(0, 2))
        );

        String requestBody = """
                {
                  "userId": "timebox-invalid-first-user",
                  "timeboxes": [
                    {
                      "executionUnitId": "%s",
                      "startAt": "2026-03-16T09:00:00+09:00",
                      "endAt": "2026-03-16T09:30:00+09:00",
                      "type": "WORK",
                      "firstRecoveryBlock": false
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
                """.formatted(executionUnitIds.get(0), executionUnitIds.get(1));

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
        List<String> executionUnitIds = createExecutionUnits(
                "timebox-conflict-user",
                selectBig3("timebox-conflict-user", itemIds.subList(0, 2))
        );

        String requestBody = """
                {
                  "userId": "timebox-conflict-user",
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
                      "startAt": "2026-03-16T09:20:00+09:00",
                      "endAt": "2026-03-16T09:50:00+09:00",
                      "type": "WORK",
                      "firstRecoveryBlock": false
                    }
                  ]
                }
                """.formatted(executionUnitIds.get(0), executionUnitIds.get(1));

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
                      "executionUnitId": "%s",
                      "startAt": "2026-03-16T09:00:00+09:00",
                      "endAt": "2026-03-16T09:30:00+09:00",
                      "type": "WORK",
                      "firstRecoveryBlock": true
                    }
                  ]
                }
                """.formatted("missing-execution-unit");

        mockMvc.perform(
                        post("/api/v1/recovery/timeboxes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400"))
                .andExpect(jsonPath("$.error.details.invalidExecutionUnitIds[0]").value("missing-execution-unit"));
    }

    @Test
    void allocateTimeboxesReturnsBadRequestWhenBreakIsMarkedAsFirstRecoveryBlock() throws Exception {
        List<String> itemIds = saveInboxItems("timebox-break-first-user");
        List<String> executionUnitIds = createExecutionUnits(
                "timebox-break-first-user",
                selectBig3("timebox-break-first-user", itemIds.subList(0, 2))
        );

        String requestBody = """
                {
                  "userId": "timebox-break-first-user",
                  "timeboxes": [
                    {
                      "executionUnitId": "%s",
                      "startAt": "2026-03-16T09:00:00+09:00",
                      "endAt": "2026-03-16T09:10:00+09:00",
                      "type": "BREAK",
                      "firstRecoveryBlock": true
                    }
                  ]
                }
                """.formatted(executionUnitIds.get(0));

        mockMvc.perform(
                        post("/api/v1/recovery/timeboxes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400"))
                .andExpect(jsonPath("$.error.details.timeboxes").value("BREAK timebox는 첫 복귀 블록이 될 수 없습니다."));
    }

    @Test
    void allocateTimeboxesAllowsAnotherFirstRecoveryBlockOnSameDateAcrossRequests() throws Exception {
        String userId = "timebox-first-block-global-user";
        LocalDate plannedDate = LocalDate.of(2035, 3, 16);
        List<String> itemIds = saveInboxItems(userId);
        List<String> executionUnitIds = createExecutionUnits(
                userId,
                selectBig3(userId, itemIds.subList(0, 2))
        );

        allocateSingleTimebox(
                userId,
                executionUnitIds.get(0),
                plannedDate + "T09:00:00+09:00",
                plannedDate + "T09:30:00+09:00",
                true
        );
        allocateSingleTimebox(
                userId,
                executionUnitIds.get(1),
                plannedDate + "T10:00:00+09:00",
                plannedDate + "T10:30:00+09:00",
                true
        );

        long firstRecoveryBlockCount = timeboxRepository.findAll().stream()
                .filter(timebox -> timebox.getUserId().equals(userId))
                .filter(timebox -> timebox.getStartAt().toLocalDate().equals(plannedDate))
                .filter(timebox -> timebox.isFirstRecoveryBlock())
                .count();
        assertThat(firstRecoveryBlockCount).isEqualTo(2);
    }

    @Test
    void cancelTimeboxesReturnsNotFoundWhenRequestedIdsArePartiallyMissing() throws Exception {
        String userId = "timebox-cancel-missing-user";
        LocalDate plannedDate = LocalDate.of(2036, 3, 16);
        List<String> itemIds = saveInboxItems(userId);
        List<String> executionUnitIds = createExecutionUnits(
                userId,
                selectBig3(userId, itemIds.subList(0, 2))
        );

        String timeboxId = allocateSingleTimebox(
                userId,
                executionUnitIds.get(0),
                plannedDate + "T09:00:00+09:00",
                plannedDate + "T09:30:00+09:00",
                true
        );

        mockMvc.perform(
                        post("/api/v1/recovery/cancelled")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "ids": ["%s", "missing-timebox-id"]
                                        }
                                        """.formatted(userId, timeboxId))
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE-404"))
                .andExpect(jsonPath("$.error.details.missingTimeboxIds[0]").value("missing-timebox-id"));

        assertThat(timeboxRepository.findById(timeboxId).orElseThrow().getStatus())
                .isEqualTo(TimeboxStatus.PLANNED);
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

    private List<String> selectBig3(String userId, List<String> itemIds) throws Exception {
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

        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/big3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(requestBody)
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
            String requestBody = """
                    {
                      "userId": "%s",
                      "big3ItemId": "%s",
                      "title": "실행 단위 %d"
                    }
                    """.formatted(userId, big3ItemIds.get(index), index + 1);

            MvcResult result = mockMvc.perform(
                            post("/api/v1/recovery/execution-units")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody)
                    )
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
            executionUnitIds.add(body.path("data").path("executionUnitId").asText());
        }
        return executionUnitIds;
    }

    private String allocateSingleTimebox(
            String userId,
            String executionUnitId,
            String startAt,
            String endAt,
            boolean firstRecoveryBlock
    ) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/timeboxes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "timeboxes": [
                                            {
                                              "executionUnitId": "%s",
                                              "startAt": "%s",
                                              "endAt": "%s",
                                              "type": "WORK",
                                              "firstRecoveryBlock": %s
                                            }
                                          ]
                                        }
                                        """.formatted(userId, executionUnitId, startAt, endAt, firstRecoveryBlock))
                )
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("timeboxes")
                .get(0)
                .path("timeboxId")
                .asText();
    }

    private String readTraceIdFromBody(MvcResult result) throws Exception {
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("traceId").asText();
    }
}
