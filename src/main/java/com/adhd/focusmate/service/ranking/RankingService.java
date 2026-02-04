package com.adhd.focusmate.service.ranking;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 실시간 랭킹 서비스 (Redis Sorted Set)
 * - Key: "leaderboard:streak"
 * - Member: userId (String)
 * - Score: currentStreak (Double)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LEADERBOARD_KEY = "leaderboard:streak";

    /**
     * 사용자 점수 업데이트 (ZADD)
     * - 새 유저: 랭킹에 추가
     * - 기존 유저: 점수 갱신
     */
    public void updateUserScore(Long userId, int streak) {
        String member = userId.toString();
        redisTemplate.opsForZSet().add(LEADERBOARD_KEY, member, streak);
        log.info("Ranking updated: userId={}, streak={}", userId, streak);
    }

    /**
     * 사용자 랭킹 조회 (1-based)
     * - Higher score = Better rank (reverseRank 사용)
     * 
     * @return 랭킹 (1부터 시작), 없으면 null
     */
    public Long getUserRank(Long userId) {
        String member = userId.toString();
        Long rank = redisTemplate.opsForZSet().reverseRank(LEADERBOARD_KEY, member);

        if (rank == null) {
            log.info("User not in ranking: userId={}", userId);
            return null;
        }

        // Redis rank는 0-based → 1-based로 변환
        return rank + 1;
    }

    /**
     * 사용자 점수 조회
     * 
     * @return 점수, 없으면 null
     */
    public Double getUserScore(Long userId) {
        String member = userId.toString();
        return redisTemplate.opsForZSet().score(LEADERBOARD_KEY, member);
    }

    /**
     * Top 10 랭킹 조회
     * - 점수 높은 순으로 정렬 (reverseRangeWithScores)
     * 
     * @return (userId, score) 쌍 리스트
     */
    public List<RankingEntry> getTop10() {
        Set<ZSetOperations.TypedTuple<Object>> result = redisTemplate.opsForZSet()
                .reverseRangeWithScores(LEADERBOARD_KEY, 0, 9);

        if (result == null || result.isEmpty()) {
            log.info("Leaderboard is empty");
            return Collections.emptyList();
        }

        int rank = 1;
        return result.stream()
                .map(tuple -> new RankingEntry(
                        rank,
                        Long.parseLong((String) tuple.getValue()),
                        tuple.getScore() != null ? tuple.getScore().intValue() : 0))
                .toList();
    }

    /**
     * 특정 사용자 주변 랭킹 조회 (본인 ± 2명)
     */
    public List<RankingEntry> getRankingAroundUser(Long userId) {
        Long rank = getUserRank(userId);
        if (rank == null) {
            return Collections.emptyList();
        }

        // rank는 1-based, Redis는 0-based
        long start = Math.max(0, rank - 3); // 본인 위 2명, 음수처리를 위해 복잡한 if문 대신 Math.max로 처리
        long end = rank + 1; // 본인 아래 2명

        // 주변 랭킹 조회를 위해, Redis의 zSet을 통해 정렬되어있는 list를 가져와 조회
        Set<ZSetOperations.TypedTuple<Object>> result = redisTemplate.opsForZSet()
                .reverseRangeWithScores(LEADERBOARD_KEY, start, end);

        if (result == null) {
            return Collections.emptyList();
        }

        final long[] currentRank = { start + 1 };
        return result.stream()
                .map(tuple -> new RankingEntry(
                        (int) currentRank[0]++,
                        Long.parseLong((String) Objects.requireNonNull(tuple.getValue())),
                        tuple.getScore() != null ? tuple.getScore().intValue() : 0))
                .toList();
    }

    /**
     * 사용자 랭킹에서 제거
     */
    public void removeUser(Long userId) {
        String member = userId.toString();
        redisTemplate.opsForZSet().remove(LEADERBOARD_KEY, member);
        log.info("User removed from ranking: userId={}", userId);
    }

    /**
     * 전체 랭킹 참여자 수
     */
    public Long getTotalParticipants() {
        return redisTemplate.opsForZSet().size(LEADERBOARD_KEY);
    }

    /**
     * 랭킹 엔트리 DTO
     */
    public record RankingEntry(
            int rank,
            Long userId,
            int streak) {
    }
}
