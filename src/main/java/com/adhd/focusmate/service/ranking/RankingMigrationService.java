package com.adhd.focusmate.service.ranking;

import com.adhd.focusmate.domain.model.User;
import com.adhd.focusmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DB → Redis 랭킹 데이터 마이그레이션 서비스
 * - 서버 시작 시 또는 수동으로 실행하여 Redis 동기화
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RankingMigrationService {

    private final UserRepository userRepository;
    private final RankingService rankingService;

    /**
     * 전체 사용자 점수를 Redis로 마이그레이션
     * - 현재 User 엔티티에 streak 필드가 없으면 기본값 사용
     * 
     * @return 마이그레이션된 사용자 수
     */
    @Transactional(readOnly = true)
    public int migrateAllUsersToRedis() {
        List<User> users = userRepository.findAll();

        int count = 0;
        for (User user : users) {
            // TODO: User 엔티티에 streak 필드 추가 후 수정
            // 현재는 임시로 userId를 기반으로 더미 스코어 생성
            int streak = (int) (user.getId() * 10); // 임시 더미 점수

            rankingService.updateUserScore(user.getId(), streak);
            count++;
        }

        log.info("Migrated {} users to Redis ranking", count);
        return count;
    }

    /**
     * 단일 사용자 점수 동기화
     */
    public void syncUserScore(Long userId, int streak) {
        rankingService.updateUserScore(userId, streak);
        log.info("Synced user score to Redis: userId={}, streak={}", userId, streak);
    }
}
