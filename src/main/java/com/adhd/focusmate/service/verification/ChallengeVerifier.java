package com.adhd.focusmate.service.verification;

import com.adhd.focusmate.domain.model.Challenge;
import com.adhd.focusmate.domain.model.type.ChallengeType;

/**
 * 챌린지 검증 전략 인터페이스 (Strategy Pattern)
 */
public interface ChallengeVerifier {

    /**
     * 챌린지 성공 여부를 검증한다.
     * 
     * @param challenge 검증할 챌린지
     * @return 성공 시 true, 실패 시 false
     */
    boolean verify(Challenge challenge);

    /**
     * 이 Verifier가 처리할 수 있는 ChallengeType을 반환한다.
     * 
     * @return 지원하는 ChallengeType
     */
    ChallengeType getSupportedType();
}
