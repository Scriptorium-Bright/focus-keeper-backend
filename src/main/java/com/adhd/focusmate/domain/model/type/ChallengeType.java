package com.adhd.focusmate.domain.model.type;

/**
 * 챌린지 유형 - Strategy Pattern의 타입 식별자
 */
public enum ChallengeType {
    MANUAL, // 수동 완료 (초기 MVP)
    TIME_LOG, // 시간 기반 인증 (미라클 모닝 등)
    GITHUB_COMMIT, // GitHub 커밋 확인 (개발자용)
    COMMUNITY_POST // 커뮤니티 글쓰기 (향후)
}
