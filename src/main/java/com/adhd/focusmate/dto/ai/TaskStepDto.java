package com.adhd.focusmate.dto.ai;

public record TaskStepDto(
        int stepOrder,
        String content,
        int estimatedMinutes) {
}
