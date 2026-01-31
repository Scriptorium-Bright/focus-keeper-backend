package com.adhd.focusmate.service.user;

import com.adhd.focusmate.common.exception.BusinessException;
import com.adhd.focusmate.common.exception.ErrorCode;
import com.adhd.focusmate.domain.model.User;
import com.adhd.focusmate.dto.user.UserProfileResponse;
import com.adhd.focusmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 프로필 서비스
 * - Look-Aside 캐싱 패턴 적용
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * 사용자 프로필 조회 (캐시 적용)
     * - 첫 조회: DB Hit → Redis 저장
     * - 이후 조회: Redis Hit (TTL: 30분)
     */
    @Cacheable(value = "userProfile", key = "#userId")
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfile(Long userId) {
        log.info("Cache MISS - Fetching user profile from DB: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "User not found"));

        return UserProfileResponse.from(user);
    }

    /**
     * 캐시 없이 직접 DB 조회 (성능 비교용)
     */
    @Transactional(readOnly = true)
    public UserProfileResponse getUserProfileNoCache(Long userId) {
        log.info("No Cache - Direct DB query: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "User not found"));

        return UserProfileResponse.from(user);
    }

    /**
     * 사용자 프로필 업데이트 (캐시 무효화)
     * - 업데이트 후 캐시 삭제 → 다음 조회 시 최신 데이터 fetch
     */
    @CacheEvict(value = "userProfile", key = "#userId")
    @Transactional
    public UserProfileResponse updateProfile(Long userId, String nickname) {
        log.info("Cache EVICT - Updating user profile: userId={}", userId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ENTITY_NOT_FOUND, "User not found"));

        // TODO: User 엔티티에 updateNickname 메서드 추가 필요
        // user.updateNickname(nickname);

        return UserProfileResponse.from(user);
    }
}
