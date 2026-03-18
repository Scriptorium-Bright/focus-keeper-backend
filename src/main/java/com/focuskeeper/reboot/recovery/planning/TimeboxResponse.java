package com.focuskeeper.reboot.recovery.planning;

public record TimeboxResponse(
        String timeboxId,
        String itemId,
        String content,
        String startAt,
        String endAt,
        boolean firstRecoveryBlock,
        String createdAt
) {
}
