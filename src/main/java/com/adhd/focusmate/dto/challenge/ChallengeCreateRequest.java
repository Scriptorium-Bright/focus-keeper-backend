package com.adhd.focusmate.dto.challenge;

import com.adhd.focusmate.domain.model.type.ChallengeType;

import java.time.LocalDateTime;

public record ChallengeCreateRequest(
        Long userId,
        String title,
        String description,
        ChallengeType challengeType,
        String targetValue, // GitHub username, 목표 시간 등
        Integer estimatedMinutes,
        Integer energyLevel,
        LocalDateTime deadline) {
}
