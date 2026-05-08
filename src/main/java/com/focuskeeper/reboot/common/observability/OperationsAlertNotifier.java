package com.focuskeeper.reboot.common.observability;

public interface OperationsAlertNotifier {

    void notify(OperationsAlertTransitionEvent event);
}
