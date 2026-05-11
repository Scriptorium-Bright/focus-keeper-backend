package com.focuskeeper.reboot.common.observability;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class OperationsAlertNotifierPublisher implements OperationsAlertTransitionPublisher {

    private final List<OperationsAlertNotifier> notifiers;

    public OperationsAlertNotifierPublisher(List<OperationsAlertNotifier> notifiers) {
        this.notifiers = List.copyOf(notifiers);
    }

    @Override
    public void publish(OperationsAlertTransitionEvent event) {
        for (OperationsAlertNotifier notifier : notifiers) {
            notifier.notify(event);
        }
    }
}
