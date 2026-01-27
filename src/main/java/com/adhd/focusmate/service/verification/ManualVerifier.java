package com.adhd.focusmate.service.verification;

import com.adhd.focusmate.domain.model.Challenge;
import com.adhd.focusmate.domain.model.type.ChallengeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 수동 완료 검증기 (신뢰 기반)
 * 사용자가 "완료" 버튼을 누르면 무조건 성공으로 처리.
 * 초기 MVP 및 복잡한 검증이 불가능한 챌린지용.
 */
@Slf4j
@Component
public class ManualVerifier implements ChallengeVerifier {

    @Override
    public boolean verify(Challenge challenge) {
        log.info("ManualVerifier: Challenge [{}] marked as complete (trust-based)", challenge.getId());
        return true; // 수동 = 항상 성공
    }

    @Override
    public ChallengeType getSupportedType() {
        return ChallengeType.MANUAL;
    }
}
