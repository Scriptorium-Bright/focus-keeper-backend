package com.adhd.focusmate.service.feed;

import com.adhd.focusmate.dto.feed.FeedItemDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Feed Service - 피드 조회 서비스
 * 
 * Redis에서만 조회 (PostgreSQL 쿼리 없음 → O(1) 성능)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeedService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String FEED_KEY_PREFIX = "u:feed:";

    /**
     * 내 피드 조회 (페이지네이션)
     * 
     * @param userId 사용자 ID
     * @param page   페이지 번호 (0-indexed)
     * @param size   페이지 크기
     * @return 피드 목록
     */
    public List<FeedItemDto> getMyFeed(Long userId, int page, int size) {
        String feedKey = FEED_KEY_PREFIX + userId;

        // Redis LRANGE: start ~ end (inclusive)
        long start = (long) page * size;
        long end = start + size - 1;

        log.debug("[Feed] Fetching feed for user {} (page={}, size={}, range={}-{})",
                userId, page, size, start, end);

        List<String> feedJsonList = redisTemplate.opsForList().range(feedKey, start, end);

        if (feedJsonList == null || feedJsonList.isEmpty()) {
            log.debug("[Feed] No feed found for user {}", userId);
            return Collections.emptyList();
        }

        // JSON → FeedItemDto 역직렬화
        List<FeedItemDto> feeds = feedJsonList.stream()
                .map(this::deserializeFeedItem)
                .filter(Objects::nonNull)
                .toList();

        log.debug("[Feed] Returned {} feed items for user {}", feeds.size(), userId);
        return feeds;
    }

    /**
     * 피드 총 개수 조회
     */
    public long getFeedCount(Long userId) {
        String feedKey = FEED_KEY_PREFIX + userId;
        Long size = redisTemplate.opsForList().size(feedKey);
        return size != null ? size : 0;
    }

    /**
     * JSON 문자열 → FeedItemDto 변환
     */
    private FeedItemDto deserializeFeedItem(String json) {
        try {
            return objectMapper.readValue(json, FeedItemDto.class);
        } catch (JsonProcessingException e) {
            log.error("[Feed] Failed to deserialize feed item: {}", json, e);
            return null;
        }
    }
}
