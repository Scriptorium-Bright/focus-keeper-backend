package com.adhd.focusmate.service.verification;

import com.adhd.focusmate.common.exception.BusinessException;
import com.adhd.focusmate.common.exception.ErrorCode;
import com.adhd.focusmate.domain.model.Challenge;
import com.adhd.focusmate.domain.model.type.ChallengeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * GitHub 커밋 검증기
 * 사용자의 GitHub 이벤트를 조회하여 오늘(KST) PushEvent가 있는지 확인
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GitHubVerifier implements ChallengeVerifier {

    private final RestClient githubRestClient;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    @Override
    public boolean verify(Challenge challenge) {
        String username = challenge.getTargetValue();

        if (username == null || username.isBlank()) {
            log.warn("GitHubVerifier: Challenge [{}] has no targetValue (GitHub username)",
                    challenge.getId());
            throw new BusinessException(ErrorCode.INVALID_INPUT, "GitHub username is required");
        }

        try {
            // GitHub Events API 호출
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> events = githubRestClient.get()
                    .uri("/users/{username}/events", username)
                    .retrieve()
                    .body(List.class);

            if (events == null || events.isEmpty()) {
                log.info("GitHubVerifier: No events found for user [{}]", username);
                return false;
            }

            // 오늘 날짜 (KST 기준)
            LocalDate today = LocalDate.now(KST);

            // PushEvent 중 오늘 발생한 것이 있는지 확인
            for (Map<String, Object> event : events) {
                String type = (String) event.get("type");
                String createdAt = (String) event.get("created_at");

                if ("PushEvent".equals(type) && createdAt != null) {
                    // UTC 시간을 KST로 변환
                    Instant eventInstant = Instant.parse(createdAt);
                    LocalDate eventDate = eventInstant.atZone(KST).toLocalDate();

                    if (eventDate.equals(today)) {
                        log.info("GitHubVerifier: Found PushEvent on {} for user [{}] - SUCCESS",
                                eventDate, username);
                        return true;
                    }
                }
            }

            log.info("GitHubVerifier: No PushEvent today for user [{}] - FAILED", username);
            return false;

        } catch (HttpClientErrorException.NotFound e) {
            log.error("GitHubVerifier: GitHub user not found [{}]", username);
            throw new BusinessException(ErrorCode.ENTITY_NOT_FOUND,
                    "GitHub user not found: " + username);

        } catch (RestClientException e) {
            log.error("GitHubVerifier: GitHub API error for user [{}]: {}", username, e.getMessage());
            // 네트워크 오류 등은 false 반환 (나중에 재시도 로직 추가 가능)
            return false;
        }
    }

    @Override
    public ChallengeType getSupportedType() {
        return ChallengeType.GITHUB_COMMIT;
    }
}
