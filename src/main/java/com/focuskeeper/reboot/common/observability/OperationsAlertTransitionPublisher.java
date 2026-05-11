package com.focuskeeper.reboot.common.observability;

public interface OperationsAlertTransitionPublisher {

    void publish(OperationsAlertTransitionEvent event);
}
