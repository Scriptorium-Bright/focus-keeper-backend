package com.focuskeeper.reboot.common.observability;

public enum OperationsAlertSeverity {
    WARNING,
    CRITICAL;

    public boolean isHigherThan(OperationsAlertSeverity other) {
        return this.ordinal() > other.ordinal();
    }
}
