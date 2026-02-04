package com.adhd.focusmate.domain.type;

/**
 * Feed 타입 - 피드에 표시될 이벤트 유형
 */
public enum FeedType {
    /**
     * 챌린지 성공
     */
    CHALLENGE_SUCCESS,

    /**
     * 챌린지 실패
     */
    CHALLENGE_FAIL,

    /**
     * 새 챌린지 시작
     */
    CHALLENGE_START,

    /**
     * 스트릭 달성 (연속 성공)
     */
    STREAK_ACHIEVED
}
