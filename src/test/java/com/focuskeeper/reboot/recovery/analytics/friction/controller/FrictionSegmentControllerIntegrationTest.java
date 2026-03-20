package com.focuskeeper.reboot.recovery.analytics.friction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.focuskeeper.reboot.recovery.analytics.friction.repository.FailureHourMetricRepository;
import com.focuskeeper.reboot.recovery.analytics.friction.repository.FailureHourReportRepository;
import com.focuskeeper.reboot.recovery.analytics.friction.repository.RecoveryFrictionSignalRepository;
import com.focuskeeper.reboot.recovery.analytics.friction.service.FailureHourAnalyticsService;
import com.focuskeeper.reboot.recovery.analytics.friction.service.FrictionSignalAnalyticsService;
import com.focuskeeper.reboot.recovery.execution.FailureReason;
import com.focuskeeper.reboot.recovery.execution.RestartType;
import com.focuskeeper.reboot.recovery.execution.entity.FailureEvent;
import com.focuskeeper.reboot.recovery.execution.entity.RestartEvent;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class FrictionSegmentControllerIntegrationTest {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FailureEventRepository failureEventRepository;

    @Autowired
    private RestartEventRepository restartEventRepository;

    @Autowired
    private FailureHourReportRepository failureHourReportRepository;

    @Autowired
    private FailureHourMetricRepository failureHourMetricRepository;

    @Autowired
    private RecoveryFrictionSignalRepository recoveryFrictionSignalRepository;

    @Autowired
    private FailureHourAnalyticsService failureHourAnalyticsService;

    @Autowired
    private FrictionSignalAnalyticsService frictionSignalAnalyticsService;

    @BeforeEach
    void setUp() {
        restartEventRepository.deleteAll();
        failureEventRepository.deleteAll();
        recoveryFrictionSignalRepository.deleteAll();
        failureHourMetricRepository.deleteAll();
        failureHourReportRepository.deleteAll();
    }

    @Test
    void getFrictionSegmentsBuildsMorningSlipOversizedTaskAndLateRestartSegments() throws Exception {
        String userId = "friction-segment-user";
        LocalDate metricDate = LocalDate.of(2026, 3, 21);

        seedSignals(userId, metricDate);
        failureHourAnalyticsService.generate(userId, metricDate);
        frictionSignalAnalyticsService.generate(userId, metricDate);

        mockMvc.perform(
                        get("/api/v1/recovery/analytics/friction-segments")
                                .param("userId", userId)
                                .param("metricDate", metricDate.toString())
                )
                .andExpect(status().isOk())
                .andExpect(header().exists("X-Trace-Id"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("FRICTION_SEGMENTS_FETCHED"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.metricDate").value(metricDate.toString()))
                .andExpect(jsonPath("$.data.segments.length()").value(3))
                .andExpect(jsonPath("$.data.segments[0].segmentType").value("MORNING_SLIP"))
                .andExpect(jsonPath("$.data.segments[1].segmentType").value("OVERSIZED_TASK"))
                .andExpect(jsonPath("$.data.segments[2].segmentType").value("LATE_RESTART"));

        assertThat(failureHourMetricRepository.findAll()).hasSize(3);
        assertThat(recoveryFrictionSignalRepository.findAllByUserIdAndMetricDateOrderBySignalTypeAsc(userId, metricDate))
                .hasSize(2);
    }

    @Test
    void getFrictionSegmentsReturnsNotFoundWhenPrerequisiteAnalyticsAreMissing() throws Exception {
        mockMvc.perform(
                        get("/api/v1/recovery/analytics/friction-segments")
                                .param("userId", "missing-segment-user")
                                .param("metricDate", "2026-03-21")
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("RESOURCE-404"));
    }

    private void seedSignals(String userId, LocalDate metricDate) {
        OffsetDateTime firstFailureAt = at(metricDate, 9, 0);
        OffsetDateTime secondFailureAt = at(metricDate, 10, 10);
        OffsetDateTime thirdFailureAt = at(metricDate, 15, 0);

        failureEventRepository.save(FailureEvent.create(
                userId,
                "session-1",
                "timebox-1",
                FailureReason.TOO_BIG,
                "첫 번째 too_big 실패",
                firstFailureAt
        ));
        failureEventRepository.save(FailureEvent.create(
                userId,
                "session-2",
                "timebox-2",
                FailureReason.TOO_BIG,
                "두 번째 too_big 실패",
                secondFailureAt
        ));
        failureEventRepository.save(FailureEvent.create(
                userId,
                "session-3",
                "timebox-3",
                FailureReason.INTERRUPTION,
                "일반 인터럽트 실패",
                thirdFailureAt
        ));

        List<FailureEventRepository.FailureSlice> failures = failureEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                userId,
                firstFailureAt.minusMinutes(1),
                thirdFailureAt.plusMinutes(1)
        );

        String firstFailureEventId = failures.stream()
                .filter(failure -> failure.getOccurredAt().isEqual(firstFailureAt))
                .findFirst()
                .map(FailureEventRepository.FailureSlice::getFailureEventId)
                .orElseThrow();
        String thirdFailureEventId = failures.stream()
                .filter(failure -> failure.getOccurredAt().isEqual(thirdFailureAt))
                .findFirst()
                .map(FailureEventRepository.FailureSlice::getFailureEventId)
                .orElseThrow();

        restartEventRepository.save(RestartEvent.create(
                userId,
                firstFailureEventId,
                RestartType.TEN_MINUTE_RESTART,
                10,
                firstFailureAt.plusHours(25)
        ));
        restartEventRepository.save(RestartEvent.create(
                userId,
                thirdFailureEventId,
                RestartType.TEN_MINUTE_RESTART,
                10,
                thirdFailureAt.plusMinutes(30)
        ));
    }

    private OffsetDateTime at(LocalDate metricDate, int hour, int minute) {
        return metricDate.atTime(hour, minute).atOffset(SEOUL_OFFSET);
    }
}
