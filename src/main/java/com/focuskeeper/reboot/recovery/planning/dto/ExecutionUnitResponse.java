package com.focuskeeper.reboot.recovery.planning.dto;

public record ExecutionUnitResponse(
        String executionUnitId,
        String big3SelectionItemId,
        String title,
        String status,
        String completedAt,
        String createdAt
) {
}
