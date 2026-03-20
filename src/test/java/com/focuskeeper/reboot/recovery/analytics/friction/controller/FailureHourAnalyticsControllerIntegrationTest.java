package com.focuskeeper.reboot.recovery.analytics.friction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.focuskeeper.reboot.recovery.analytics.friction.repository.FailureHourMetricRepository;
import com.focuskeeper.reboot.recovery.analytics.friction.repository.FailureHourReportRepository;
import com.focuskeeper.reboot.recovery.execution.FailureReason;
import com.focuskeeper.reboot.recovery.execution.entity.FailureEvent;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FailureHourAnalyticsControllerIntegrationTest {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FailureEventRepository failureEventRepository;

    @Autowired
    private FailureHourReportRepository failureHourReportRepository;

    @Autowired
    private FailureHourMetricRepository failureHourMetricRepository;

    @BeforeEach
    void setUp() {
        failureHourMetricRepository.deleteAll();
        failureHourReportRepository.deleteAll();
        failureEventRepository.deleteAll();
    }

    @Test
    void generateFailureHourDistributionAggregatesByLocalHourAndPersistsReport() throws Exception {
        String userId = "failure-hour-user";
        LocalDate metricDate = LocalDate.of(2026, 3, 21);

        seedFailures(userId, metricDate);

        mockMvc.perform(
                        post("/api/v1/recovery/analytics/failure-hours")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "userId": "%s",
                                          "metricDate": "%s"
                                        }
                                        """.formatted(userId, metricDate))
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("FAILURE_HOUR_DISTRIBUTION_GENERATED"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.metricDate").value(metricDate.toString()))
                .andExpect(jsonPath("$.data.totalFailureCount").value(3))
                .andExpect(jsonPath("$.data.peakFailureHour").value(9))
                .andExpect(jsonPath("$.data.peakFailureWindow").value("09-12"))
                .andExpect(jsonPath("$.data.hourlyMetrics.length()").value(2))
                .andExpect(jsonPath("$.data.hourlyMetrics[0].localHour").value(9))
                .andExpect(jsonPath("$.data.hourlyMetrics[0].failureCount").value(2))
                .andExpect(jsonPath("$.data.hourlyMetrics[0].failureRatio").value(0.6667))
                .andExpect(jsonPath("$.data.hourlyMetrics[0].peakHour").value(true))
                .andExpect(jsonPath("$.data.hourlyMetrics[1].localHour").value(14))
                .andExpect(jsonPath("$.data.hourlyMetrics[1].failureCount").value(1))
                .andExpect(jsonPath("$.data.hourlyMetrics[1].failureRatio").value(0.3333))
                .andExpect(jsonPath("$.data.hourlyMetrics[1].peakHour").value(false));

        assertThat(failureHourReportRepository.findByUserIdAndMetricDate(userId, metricDate)).isPresent();
        assertThat(failureHourMetricRepository.findAllByUserIdAndMetricDateOrderByLocalHourAsc(userId, metricDate))
                .hasSize(2);
    }

    @Test
    void getFailureHourDistributionReturnsNotFoundWhenReportDoesNotExist() throws Exception {
        mockMvc.perform(
                        get("/api/v1/recovery/analytics/failure-hours")
                                .param("userId", "missing-failure-hour-user")
                                .param("metricDate", "2026-03-21")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE-404"));
    }

    private void seedFailures(String userId, LocalDate metricDate) {
        failureEventRepository.save(FailureEvent.create(
                userId,
                "session-1",
                "timebox-1",
                FailureReason.TOO_BIG,
                "오전 첫 실패",
                at(metricDate, 9, 10)
        ));
        failureEventRepository.save(FailureEvent.create(
                userId,
                "session-2",
                "timebox-2",
                FailureReason.INTERRUPTION,
                "오전 두 번째 실패",
                at(metricDate, 9, 50)
        ));
        failureEventRepository.save(FailureEvent.create(
                userId,
                "session-3",
                "timebox-3",
                FailureReason.LOW_ENERGY,
                "오후 실패",
                at(metricDate, 14, 20)
        ));
        failureEventRepository.save(FailureEvent.create(
                userId,
                "session-4",
                "timebox-4",
                FailureReason.TOO_BIG,
                "다른 날짜 실패",
                at(metricDate.plusDays(1), 9, 0)
        ));
    }

    private OffsetDateTime at(LocalDate metricDate, int hour, int minute) {
        return metricDate.atTime(hour, minute).atOffset(SEOUL_OFFSET);
    }
}
