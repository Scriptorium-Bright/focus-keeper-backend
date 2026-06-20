package com.focuskeeper.reboot.recovery.planning.config;

import com.focuskeeper.reboot.recovery.planning.constant.ExpirationJobStatus;

public record ExpirationJobResult(
        ExpirationJobStatus expirationJobStatus,
        int processedItems,
        String reason
) {

    public static ExpirationJobResult skipped(String reason) {
        return new ExpirationJobResult(ExpirationJobStatus.SKIPPED, 0, reason);
    }

    public static ExpirationJobResult succeeded(int processedItems) {
        return new ExpirationJobResult(ExpirationJobStatus.SUCCEEDED, processedItems, null);
    }
}
