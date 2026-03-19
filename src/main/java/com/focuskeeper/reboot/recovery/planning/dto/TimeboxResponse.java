package com.focuskeeper.reboot.recovery.planning.dto;

public record TimeboxResponse(
        String timeboxId,
        String itemId,
        String content,
        String startAt,
        String endAt,
        boolean firstRecoveryBlock,
        String type,
        String createdAt
) {
}
