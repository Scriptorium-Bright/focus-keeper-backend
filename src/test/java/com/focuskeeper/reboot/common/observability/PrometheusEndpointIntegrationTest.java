package com.focuskeeper.reboot.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class PrometheusEndpointIntegrationTest {

    @Autowired
    private OperationsMetricRecorder operationsMetricRecorder;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void actuatorPrometheusExposesCustomOperationalMetrics() throws Exception {
        Timer.Sample sample = operationsMetricRecorder.startSample();
        operationsMetricRecorder.recordRecoveryLoopAction(sample, "integration_probe", "success");

        assertThat(meterRegistry.find("reboot_recovery_loop_actions_total").counter()).isNotNull();
        assertThat(meterRegistry.find("reboot_recovery_loop_action_duration").timer()).isNotNull();
        assertThat(meterRegistry.find("jvm.memory.used").gauge()).isNotNull();
    }
}
