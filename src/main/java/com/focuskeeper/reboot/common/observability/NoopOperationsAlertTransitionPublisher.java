package com.focuskeeper.reboot.common.observability;

import org.springframework.stereotype.Component;

@Component
public class NoopOperationsAlertTransitionPublisher implements OperationsAlertTransitionPublisher {

    @Override
    public void publish(OperationsAlertTransitionEvent event) {
        // Phase 3에서는 event contract만 고정하고, 실제 소비자는 Phase 4에서 연결한다.
    }
}
