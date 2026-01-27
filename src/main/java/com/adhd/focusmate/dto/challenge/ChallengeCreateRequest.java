package com.adhd.focusmate.dto.challenge;

import com.adhd.focusmate.domain.model.type.ChallengeType;

import java.time.LocalDateTime;

public record ChallengeCreateRequest(
        Long userId,
        String title,
        String description,
        ChallengeType challengeType,
        Integer estimatedMinutes,
        Integer energyLevel,
        LocalDateTime deadline) {
}
