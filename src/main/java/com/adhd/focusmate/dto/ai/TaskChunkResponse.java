package com.adhd.focusmate.dto.ai;

import java.util.List;

public record TaskChunkResponse(
        String originalGoal,
        List<TaskStepDto> steps,
        String cheerUpMessage) {
}
