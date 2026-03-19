package com.focuskeeper.reboot.recovery.analytics.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.focuskeeper.reboot.recovery.analytics.entity.CohortRetentionReport;
import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiMetric;
import com.focuskeeper.reboot.recovery.analytics.repository.CohortRetentionReportRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiMetricRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
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
class CohortRetentionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DailyKpiMetricRepository dailyKpiMetricRepository;

    @Autowired
    private CohortRetentionReportRepository cohortRetentionReportRepository;

    @BeforeEach
    void setUp() {
        cohortRetentionReportRepository.deleteAll();
        dailyKpiMetricRepository.deleteAll();
    }

    @Test
    void generateCohortRetentionCalculatesD1D7D30ForFirstActivationCohort() throws Exception {
        LocalDate cohortDate = LocalDate.of(2026, 3, 1);
        seedCohortMetrics(cohortDate);

        mockMvc.perform(
                        post("/api/v1/recovery/analytics/cohorts/retention")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cohortDate": "%s"
                                        }
                                        """.formatted(cohortDate))
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("COHORT_RETENTION_GENERATED"))
                .andExpect(jsonPath("$.data.cohortDate").value(cohortDate.toString()))
                .andExpect(jsonPath("$.data.cohortSize").value(2))
                .andExpect(jsonPath("$.data.retainedDay1Users").value(1))
                .andExpect(jsonPath("$.data.retainedDay7Users").value(1))
                .andExpect(jsonPath("$.data.retainedDay30Users").value(1))
                .andExpect(jsonPath("$.data.retentionDay1Rate").value(0.5000))
                .andExpect(jsonPath("$.data.retentionDay7Rate").value(0.5000))
                .andExpect(jsonPath("$.data.retentionDay30Rate").value(0.5000));

        assertThat(cohortRetentionReportRepository.findByCohortDate(cohortDate)).isPresent();
    }

    @Test
    void generateCohortRetentionUpsertsSameCohortDate() throws Exception {
        LocalDate cohortDate = LocalDate.of(2026, 3, 2);
        seedCohortMetrics(cohortDate);

        MvcResult first = mockMvc.perform(
                        post("/api/v1/recovery/analytics/cohorts/retention")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cohortDate": "%s"
                                        }
                                        """.formatted(cohortDate))
                )
                .andExpect(status().isOk())
                .andReturn();

        MvcResult second = mockMvc.perform(
                        post("/api/v1/recovery/analytics/cohorts/retention")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cohortDate": "%s"
                                        }
                                        """.formatted(cohortDate))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());

        assertThat(secondBody.path("data").path("cohortRetentionId").asText())
                .isEqualTo(firstBody.path("data").path("cohortRetentionId").asText());
    }

    @Test
    void getCohortRetentionReturnsGeneratedReport() throws Exception {
        LocalDate cohortDate = LocalDate.of(2026, 3, 3);
        seedCohortMetrics(cohortDate);
        generateCohortRetention(cohortDate);

        mockMvc.perform(
                        get("/api/v1/recovery/analytics/cohorts/retention")
                                .param("cohortDate", cohortDate.toString())
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("COHORT_RETENTION_FETCHED"))
                .andExpect(jsonPath("$.data.cohortDate").value(cohortDate.toString()));
    }

    @Test
    void getCohortRetentionReturnsNotFoundWhenReportDoesNotExist() throws Exception {
        mockMvc.perform(
                        get("/api/v1/recovery/analytics/cohorts/retention")
                                .param("cohortDate", "2026-03-10")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE-404"));
    }

    @Test
    void getCohortRetentionReturnsBadRequestWhenDateIsInvalid() throws Exception {
        mockMvc.perform(
                        get("/api/v1/recovery/analytics/cohorts/retention")
                                .param("cohortDate", "2026/03/01")
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("COMMON-400"))
                .andExpect(jsonPath("$.error.details.cohortDate").value("yyyy-MM-dd 형식의 날짜여야 합니다."));
    }

    private void generateCohortRetention(LocalDate cohortDate) throws Exception {
        mockMvc.perform(
                        post("/api/v1/recovery/analytics/cohorts/retention")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cohortDate": "%s"
                                        }
                                        """.formatted(cohortDate))
                )
                .andExpect(status().isOk());
    }

    private void seedCohortMetrics(LocalDate cohortDate) {
        OffsetDateTime generatedAt = OffsetDateTime.parse("2026-03-19T21:00:00+09:00");

        dailyKpiMetricRepository.save(DailyKpiMetric.create(
                "cohort-user-a",
                cohortDate,
                true,
                1,
                false,
                false,
                0,
                0,
                null,
                BigDecimal.ONE,
                BigDecimal.ONE,
                25,
                25,
                0,
                generatedAt
        ));
        dailyKpiMetricRepository.save(DailyKpiMetric.create(
                "cohort-user-a",
                cohortDate.plusDays(1),
                true,
                0,
                false,
                false,
                0,
                0,
                null,
                BigDecimal.ONE,
                BigDecimal.ONE,
                25,
                25,
                0,
                generatedAt
        ));
        dailyKpiMetricRepository.save(DailyKpiMetric.create(
                "cohort-user-a",
                cohortDate.plusDays(7),
                true,
                0,
                false,
                false,
                0,
                0,
                null,
                BigDecimal.ONE,
                BigDecimal.ONE,
                25,
                25,
                0,
                generatedAt
        ));

        dailyKpiMetricRepository.save(DailyKpiMetric.create(
                "cohort-user-b",
                cohortDate,
                true,
                1,
                false,
                false,
                0,
                0,
                null,
                BigDecimal.ONE,
                BigDecimal.ONE,
                25,
                25,
                0,
                generatedAt
        ));
        dailyKpiMetricRepository.save(DailyKpiMetric.create(
                "cohort-user-b",
                cohortDate.plusDays(30),
                true,
                0,
                false,
                false,
                0,
                0,
                null,
                BigDecimal.ONE,
                BigDecimal.ONE,
                25,
                25,
                0,
                generatedAt
        ));

        dailyKpiMetricRepository.save(DailyKpiMetric.create(
                "earlier-user",
                cohortDate.minusDays(1),
                true,
                0,
                false,
                false,
                0,
                0,
                null,
                BigDecimal.ONE,
                BigDecimal.ONE,
                25,
                25,
                0,
                generatedAt
        ));
        dailyKpiMetricRepository.save(DailyKpiMetric.create(
                "later-user",
                cohortDate.plusDays(2),
                true,
                0,
                false,
                false,
                0,
                0,
                null,
                BigDecimal.ONE,
                BigDecimal.ONE,
                25,
                25,
                0,
                generatedAt
        ));
    }
}
