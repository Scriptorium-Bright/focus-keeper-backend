package com.adhd.focusmate.domain.model.type;

/**
 * 챌린지 상태
 * - PENDING: 생성됨, 아직 시작 안함
 * - IN_PROGRESS: 진행 중
 * - PENDING_VERIFICATION: 검증 대기 중 (비동기 검증용)
 * - COMPLETED: 완료 (보상 지급)
 * - FAILED: 실패 (페널티 차감)
 */
public enum ChallengeStatus {
    PENDING,
    IN_PROGRESS,
    PENDING_VERIFICATION,
    COMPLETED,
    FAILED
}
