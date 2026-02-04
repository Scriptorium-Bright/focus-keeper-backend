package com.adhd.focusmate.service.feed;

import com.adhd.focusmate.domain.type.FeedType;
import com.adhd.focusmate.dto.event.ChallengeSuccessEvent;
import com.adhd.focusmate.dto.feed.FeedItemDto;
import com.adhd.focusmate.repository.FollowRepository;
import com.adhd.focusmate.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Feed Fan-out Consumer
 * 
 * 챌린지 성공 이벤트를 받아서 팔로워들의 피드에 Push하는 Consumer
 * 
 * Fan-out on Write 패턴:
 * - 이벤트 발생 시점에 모든 팔로워의 피드에 Push
 * - 읽기 시에는 자신의 피드만 조회하면 됨 (O(1))
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedNotificationConsumer {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String FEED_KEY_PREFIX = "u:feed:";
    private static final int MAX_FEED_SIZE = 50;
    private static final String DEFAULT_PROFILE_URL = "https://cdn.focuskeeper.io/default-profile.png";
    private static final String DEFAULT_VERIFICATION_IMAGE = "https://cdn.focuskeeper.io/default-verification.png";

    /**
     * 챌린지 성공 이벤트 처리 (Feed Fan-out)
     * 
     * NotificationConsumer와 다른 GroupId를 사용하여 독립적으로 소비
     */
    @KafkaListener(topics = "challenge-success", groupId = "feed-group", containerFactory = "kafkaListenerContainerFactory")
    public void handleChallengeSuccess(ChallengeSuccessEvent event) {
        log.info("[Feed] Received challenge success event - UserId: {}, ChallengeId: {}",
                event.userId(), event.challengeId());

        try {
            // 1. 이벤트 → FeedItemDto 변환
            FeedItemDto feedItem = convertToFeedItem(event);

            // 2. 팔로워 ID 목록 조회
            List<Long> followerIds = followRepository.findFollowerIdsByFolloweeId(event.userId());

            if (followerIds.isEmpty()) {
                log.info("[Feed] No followers for user {}. Skipping fan-out.", event.userId());
                return;
            }

            log.info("[Feed] Fan-out to {} followers for user {}", followerIds.size(), event.userId());

            // 3. Redis Pipeline으로 Fan-out (최적화)
            fanOutWithPipeline(feedItem, followerIds);

            log.info("[Feed] Fan-out completed. {} feeds pushed.", followerIds.size());

        } catch (Exception e) {
            log.error("[Feed] Failed to process fan-out for event: {}", event, e);
            throw e; // Retry/DLT 처리를 위해 re-throw
        }
    }

    /**
     * ChallengeSuccessEvent → FeedItemDto 변환
     */
    private FeedItemDto convertToFeedItem(ChallengeSuccessEvent event) {
        // 사용자 정보 조회 (닉네임, 프로필 이미지)
        String writerName = "User" + event.userId();
        String writerProfileUrl = DEFAULT_PROFILE_URL;

        var userOpt = userRepository.findById(event.userId());
        if (userOpt.isPresent()) {
            writerName = userOpt.get().getNickname();
            // profileUrl이 User 엔티티에 있다면 여기서 가져옴
        }

        return FeedItemDto.builder()
                .feedId(UUID.randomUUID().toString())
                .writerId(event.userId())
                .writerName(writerName)
                .writerProfileUrl(writerProfileUrl)
                .challengeId(event.challengeId())
                .challengeTitle(event.title())
                .betPoints(event.rewardPoints() != null ? event.rewardPoints().longValue() : 0L)
                .verificationImageUrl(DEFAULT_VERIFICATION_IMAGE) // TODO: 실제 인증 이미지 URL
                .type(FeedType.CHALLENGE_SUCCESS)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * Redis Pipeline을 사용한 Fan-out (네트워크 RTT 최적화)
     * 
     * 각 팔로워의 피드에:
     * 1. leftPush: 최신 피드를 왼쪽에 추가
     * 2. trim: 피드 크기를 50개로 제한
     */
    private void fanOutWithPipeline(FeedItemDto feedItem, List<Long> followerIds) {
        String feedJson;
        try {
            feedJson = objectMapper.writeValueAsString(feedItem);
        } catch (JsonProcessingException e) {
            log.error("[Feed] Failed to serialize FeedItemDto", e);
            throw new RuntimeException("Feed serialization failed", e);
        }

        // Redis Pipeline 실행 (모든 명령을 한 번에 전송)
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            byte[] feedBytes = feedJson.getBytes();

            for (Long followerId : followerIds) {
                String feedKey = FEED_KEY_PREFIX + followerId;
                byte[] keyBytes = feedKey.getBytes();

                // 1. leftPush: 피드 추가
                connection.listCommands().lPush(keyBytes, feedBytes);

                // 2. trim: 최대 50개만 유지 (0 ~ 49)
                connection.listCommands().lTrim(keyBytes, 0, MAX_FEED_SIZE - 1);
            }

            return null;
        });
    }
}
