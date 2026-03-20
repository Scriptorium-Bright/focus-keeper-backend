package com.focuskeeper.reboot.recovery.analytics.friction.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.focuskeeper.reboot.recovery.analytics.friction.repository.RecoveryFrictionSignalRepository;
import com.focuskeeper.reboot.recovery.execution.FailureReason;
import com.focuskeeper.reboot.recovery.execution.RestartType;
import com.focuskeeper.reboot.recovery.execution.entity.FailureEvent;
import com.focuskeeper.reboot.recovery.execution.entity.RestartEvent;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
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
class FrictionSignalControllerIntegrationTest {

    private static final ZoneOffset SEOUL_OFFSET = ZoneOffset.ofHours(9);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FailureEventRepository failureEventRepository;

    @Autowired
    private RestartEventRepository restartEventRepository;

    @Autowired
    private RecoveryFrictionSignalRepository recoveryFrictionSignalRepository;

    @BeforeEach
    void setUp() {
        restartEventRepository.deleteAll();
        failureEventRepository.deleteAll();
        recoveryFrictionSignalRepository.deleteAll();
    }

    @Test
    void generateFrictionSignalsBuildsTooBigRepeatAndLateRestartSignals() throws Exception {
        String userId = "friction-signal-user";
        LocalDate metricDate = LocalDate.of(2026, 3, 21);

        seedSignals(userId, metricDate);

        mockMvc.perform(
                        post("/api/v1/recovery/analytics/friction-signals")
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
                .andExpect(jsonPath("$.message").value("FRICTION_SIGNALS_GENERATED"))
                .andExpect(jsonPath("$.data.userId").value(userId))
                .andExpect(jsonPath("$.data.metricDate").value(metricDate.toString()))
                .andExpect(jsonPath("$.data.signals.length()").value(2))
                .andExpect(jsonPath("$.data.signals[0].signalType").value("LATE_RESTART"))
                .andExpect(jsonPath("$.data.signals[0].active").value(true))
                .andExpect(jsonPath("$.data.signals[0].evidenceCount").value(1))
                .andExpect(jsonPath("$.data.signals[1].signalType").value("TOO_BIG_REPEAT"))
                .andExpect(jsonPath("$.data.signals[1].active").value(true))
                .andExpect(jsonPath("$.data.signals[1].evidenceCount").value(2));

        assertThat(recoveryFrictionSignalRepository.findAllByUserIdAndMetricDateOrderBySignalTypeAsc(userId, metricDate))
                .hasSize(2);
    }

    @Test
    void getFrictionSignalsReturnsNotFoundWhenSignalsDoNotExist() throws Exception {
        mockMvc.perform(
                        get("/api/v1/recovery/analytics/friction-signals")
                                .param("userId", "missing-friction-user")
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

        String firstFailureEventId = failureEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                        userId,
                        firstFailureAt.minusMinutes(1),
                        firstFailureAt.plusMinutes(1)
                ).stream()
                .findFirst()
                .map(FailureEventRepository.FailureSlice::getFailureEventId)
                .orElseThrow();
        String thirdFailureEventId = failureEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                        userId,
                        thirdFailureAt.minusMinutes(1),
                        thirdFailureAt.plusMinutes(1)
                ).stream()
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
