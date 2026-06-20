package com.focuskeeper.reboot.common.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PrometheusEndpointIntegrationTest {

    @Autowired
    private OperationsMetricRecorder operationsMetricRecorder;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorPrometheusExposesCustomOperationalMetrics() throws Exception {
        Timer.Sample sample = operationsMetricRecorder.startSample();
        operationsMetricRecorder.recordRecoveryLoopAction(sample, "integration_probe", "success");
        operationsMetricRecorder.recordDqIssueCount("daily_kpi_quality", "metrics-user", 2);
        operationsMetricRecorder.recordBackfillProcessedDays("backfill_reprocess", 3);
        Timer.Sample expirationSample = operationsMetricRecorder.startSample();
        operationsMetricRecorder.recordExpirationSuccess(expirationSample, 12);
        operationsMetricRecorder.recordExpirationSkipped("already_running");

        assertThat(meterRegistry.find("reboot_recovery_loop_actions_total").counter()).isNotNull();
        assertThat(meterRegistry.find("reboot_recovery_loop_action_duration").timer()).isNotNull();
        assertThat(meterRegistry.find("reboot_dq_issue_count").gauge()).isNotNull();
        assertThat(meterRegistry.find("reboot_backfill_processed_days").summary()).isNotNull();
        assertThat(meterRegistry.find("reboot_expiration_runs_total").counter()).isNotNull();
        assertThat(meterRegistry.find("reboot_expiration_duration").timer()).isNotNull();
        assertThat(meterRegistry.find("reboot_expiration_processed_items").summary()).isNotNull();
        assertThat(meterRegistry.find("reboot_expiration_running").gauge()).isNotNull();
        assertThat(meterRegistry.find("reboot_expiration_last_duration_seconds").gauge()).isNotNull();
        assertThat(meterRegistry.find("jvm.memory.used").gauge()).isNotNull();

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "reboot_expiration_runs_total"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "reboot_expiration_duration_seconds"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "reboot_expiration_processed_items_sum"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "reboot_expiration_skipped_runs_total"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "reboot_expiration_last_success_timestamp_seconds"
                )))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "reboot_expiration_last_duration_seconds"
                )));
    }
}
