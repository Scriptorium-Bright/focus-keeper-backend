package com.focuskeeper.reboot.recovery.planning.dto;

public record AllocatedTimeboxResponse(
        String timeboxId,
        String itemId,
        String content,
        String startAt,
        String endAt,
        boolean firstRecoveryBlock,
        String createdAt
) {
}
