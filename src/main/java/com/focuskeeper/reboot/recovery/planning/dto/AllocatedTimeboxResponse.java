package com.focuskeeper.reboot.recovery.planning.dto;

public record AllocatedTimeboxResponse(
        String timeboxId,
        String executionUnitId,
        String content,
        String startAt,
        String endAt,
        boolean firstRecoveryBlock,
        String type,
        String createdAt
) {
}
