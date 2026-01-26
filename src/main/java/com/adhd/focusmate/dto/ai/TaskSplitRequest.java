package com.adhd.focusmate.dto.ai;

public record TaskSplitRequest(
        String userGoal,
        int energyLevel // 1-100
) {
}
