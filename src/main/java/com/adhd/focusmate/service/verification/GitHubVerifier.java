package com.adhd.focusmate.service.verification;

import com.adhd.focusmate.domain.model.Challenge;
import com.adhd.focusmate.domain.model.type.ChallengeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * GitHub 커밋 검증기 (개발자용)
 * TODO: GitHub API 연동하여 오늘 커밋 여부 확인
 */
@Slf4j
@Component
public class GitHubVerifier implements ChallengeVerifier {

    @Override
    public boolean verify(Challenge challenge) {
        // TODO: GitHub API 연동
        // 1. 사용자의 GitHub OAuth 토큰 조회
        // 2. GitHub API 호출 (GET /users/{username}/events)
        // 3. 오늘 날짜의 PushEvent 확인

        log.warn("GitHubVerifier: Not implemented yet. Returning false for Challenge [{}]",
                challenge.getId());
        return false; // 미구현 상태에서는 실패 반환
    }

    @Override
    public ChallengeType getSupportedType() {
        return ChallengeType.GITHUB_COMMIT;
    }
}
