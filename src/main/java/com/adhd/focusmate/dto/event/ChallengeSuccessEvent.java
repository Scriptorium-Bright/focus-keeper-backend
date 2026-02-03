package com.adhd.focusmate.dto.event;

import java.time.Instant;

/**
 * 챌린지 성공 이벤트
 * Kafka를 통해 발행되는 이벤트 메시지
 *
 * @param userId       사용자 ID (Partition Key로 사용 → 동일 사용자 순서 보장)
 * @param challengeId  챌린지 ID
 * @param title        챌린지 제목
 * @param rewardPoints 보상 포인트
 * @param timestamp    이벤트 발생 시간
 */
public record ChallengeSuccessEvent(
        Long userId,
        Long challengeId,
        String title,
        Integer rewardPoints,
        Instant timestamp) {
    /**
     * Factory method: 현재 시간으로 이벤트 생성
     */
    public static ChallengeSuccessEvent of(Long userId, Long challengeId, String title, Integer rewardPoints) {
        return new ChallengeSuccessEvent(userId, challengeId, title, rewardPoints, Instant.now());
    }
}
