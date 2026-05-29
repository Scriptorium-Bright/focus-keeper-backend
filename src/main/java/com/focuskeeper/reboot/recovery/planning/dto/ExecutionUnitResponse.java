package com.focuskeeper.reboot.recovery.planning.dto;

import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;

public record ExecutionUnitResponse(
        String executionUnitId,
        String big3SelectionItemId,
        String title,
        String status,
        String completedAt,
        String createdAt
) {
    public static ExecutionUnitResponse toResponse(ExecutionUnit executionUnit) {
        return new ExecutionUnitResponse(
                executionUnit.getId(),
                executionUnit.getBig3SelectionItemId(),
                executionUnit.getTitle(),
                executionUnit.getStatus().name(),
                executionUnit.getCompletedAt() == null ? null : executionUnit.getCompletedAt().toString(),
                executionUnit.getCreatedAt().toString()
        );
    }
}

