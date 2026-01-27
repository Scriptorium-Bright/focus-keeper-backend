package com.adhd.focusmate.dto.challenge;

import com.adhd.focusmate.domain.model.Challenge;
import com.adhd.focusmate.domain.model.type.ChallengeStatus;
import com.adhd.focusmate.domain.model.type.ChallengeType;

import java.time.LocalDateTime;

public record ChallengeResponse(
        Long id,
        Long userId,
        String title,
        String description,
        ChallengeType challengeType,
        String targetValue,
        ChallengeStatus status,
        Integer estimatedMinutes,
        Integer energyLevel,
        LocalDateTime deadline,
        LocalDateTime createdAt) {
    public static ChallengeResponse from(Challenge challenge) {
        return new ChallengeResponse(
                challenge.getId(),
                challenge.getUser().getId(),
                challenge.getTitle(),
                challenge.getDescription(),
                challenge.getChallengeType(),
                challenge.getTargetValue(),
                challenge.getStatus(),
                challenge.getEstimatedTime(),
                challenge.getEnergyLevel(),
                challenge.getDeadline(),
                challenge.getCreatedAt());
    }
}
