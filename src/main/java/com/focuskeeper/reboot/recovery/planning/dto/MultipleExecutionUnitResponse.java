package com.focuskeeper.reboot.recovery.planning.dto;

import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;

public record MultipleExecutionUnitResponse(
        String executionUnitId,
        String big3ItemId,
        String title,
        String status,
        String completedAt,
        String createdAt
) {
    public static MultipleExecutionUnitResponse toResponse(ExecutionUnit executionUnit) {
        return new MultipleExecutionUnitResponse(
                executionUnit.getId(),
                executionUnit.getBig3ItemId(),
                executionUnit.getTitle(),
                executionUnit.getStatus().name(),
                executionUnit.getCompletedAt() == null ? null : executionUnit.getCompletedAt().toString(),
                executionUnit.getCreatedAt().toString()
        );
    }
}
