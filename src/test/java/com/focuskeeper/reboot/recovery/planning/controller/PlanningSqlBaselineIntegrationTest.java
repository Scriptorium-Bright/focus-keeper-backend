package com.focuskeeper.reboot.recovery.planning.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class PlanningSqlBaselineIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();
    }

    @Test
    void recordsBig3SelectionSqlBaseline() throws Exception {
        String userId = "sql-baseline-big3-user";
        List<String> inboxItemIds = saveInboxItems(userId, 3);
        statistics.clear();

        mockMvc.perform(
                        post("/api/v1/recovery/big3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "itemIds": ["%s", "%s", "%s"]
                                        }
                                        """.formatted(
                                                userId,
                                                inboxItemIds.get(0),
                                                inboxItemIds.get(1),
                                                inboxItemIds.get(2)
                                        ))
                )
                .andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(8);
    }

    @Test
    void recordsSingleExecutionUnitCreationSqlBaseline() throws Exception {
        String userId = "sql-baseline-unit-user";
        String big3ItemId = selectFirstBig3Item(userId);
        statistics.clear();

        mockMvc.perform(
                        post("/api/v1/recovery/execution-units")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "big3ItemId": "%s",
                                          "title": "SQL 기준선 실행 단위"
                                        }
                                        """.formatted(userId, big3ItemId))
                )
                .andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(3);
    }

    @Test
    void recordsSingleTimeboxAllocationSqlBaseline() throws Exception {
        String userId = "sql-baseline-timebox-user";
        String big3ItemId = selectFirstBig3Item(userId);
        String executionUnitId = createExecutionUnit(userId, big3ItemId);
        statistics.clear();

        mockMvc.perform(
                        post("/api/v1/recovery/timeboxes")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "timeboxes": [
                                            {
                                              "executionUnitId": "%s",
                                              "startAt": "2026-06-08T09:00:00+09:00",
                                              "endAt": "2026-06-08T09:30:00+09:00",
                                              "type": "WORK",
                                              "firstRecoveryBlock": true
                                            }
                                          ]
                                        }
                                        """.formatted(userId, executionUnitId))
                )
                .andExpect(status().isOk());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(4);
    }

    private String selectFirstBig3Item(String userId) throws Exception {
        List<String> inboxItemIds = saveInboxItems(userId, 1);
        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/big3")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "itemIds": ["%s"]
                                        }
                                        """.formatted(userId, inboxItemIds.getFirst()))
                )
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("selectedItems")
                .get(0)
                .path("big3ItemId")
                .asText();
    }

    private String createExecutionUnit(String userId, String big3ItemId) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/execution-units")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "big3ItemId": "%s",
                                          "title": "SQL 기준선 실행 단위"
                                        }
                                        """.formatted(userId, big3ItemId))
                )
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data")
                .path("executionUnitId")
                .asText();
    }

    private List<String> saveInboxItems(String userId, int count) throws Exception {
        StringBuilder itemsJson = new StringBuilder();
        for (int index = 0; index < count; index++) {
            if (index > 0) {
                itemsJson.append(", ");
            }
            itemsJson.append("""
                    {"content": "SQL 기준선 Inbox %d"}
                    """.formatted(index + 1).trim());
        }

        MvcResult result = mockMvc.perform(
                        post("/api/v1/recovery/inbox-items")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "items": [%s]
                                        }
                                        """.formatted(userId, itemsJson))
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
}
