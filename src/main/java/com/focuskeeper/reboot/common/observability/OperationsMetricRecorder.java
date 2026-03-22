package com.focuskeeper.reboot.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

@Service
public class OperationsMetricRecorder {

    private final MeterRegistry meterRegistry;

    public OperationsMetricRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    public void recordRecoveryLoopAction(Timer.Sample sample, String action, String status) {
        Counter.builder("reboot_recovery_loop_actions_total")
                .description("Recovery loop API action count")
                .tag("action", action)
                .tag("status", status)
                .register(meterRegistry)
                .increment();

        Timer.builder("reboot_recovery_loop_action_duration")
                .description("Recovery loop API action duration")
                .tag("action", action)
                .tag("status", status)
                .register(meterRegistry);
        sample.stop(meterRegistry.timer(
                "reboot_recovery_loop_action_duration",
                "action",
                action,
                "status",
                status
        ));
    }
}
