package com.adhd.focusmate.service.follow;

import com.adhd.focusmate.common.exception.BusinessException;
import com.adhd.focusmate.common.exception.ErrorCode;
import com.adhd.focusmate.domain.model.Follow;
import com.adhd.focusmate.domain.model.User;
import com.adhd.focusmate.dto.follow.FollowResponse;
import com.adhd.focusmate.repository.FollowRepository;
import com.adhd.focusmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 팔로우 서비스
 * 
 * Fan-out 아키텍처에서 팔로워 목록 조회에 사용됨
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    /**
     * 팔로우 토글 (팔로우/언팔로우)
     * 
     * @param followerId 팔로우하는 사용자 ID
     * @param followeeId 팔로우 당하는 사용자 ID
     * @return 팔로우 결과
     */
    @Transactional
    public FollowResponse toggleFollow(Long followerId, Long followeeId) {
        // 자기 자신 팔로우 금지
        if (followerId.equals(followeeId)) {
            throw new BusinessException(ErrorCode.SELF_FOLLOW_NOT_ALLOWED);
        }

        // 기존 팔로우 관계 확인
        var existingFollow = followRepository.findByFollowerIdAndFolloweeId(followerId, followeeId);

        if (existingFollow.isPresent()) {
            // 이미 팔로우 중 → 언팔로우
            followRepository.delete(existingFollow.get());
            log.info("[Follow] User {} unfollowed User {}", followerId, followeeId);
            return FollowResponse.unfollowed(followerId, followeeId);
        } else {
            // 팔로우하지 않은 상태 → 팔로우
            User follower = userRepository.findById(followerId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            User followee = userRepository.findById(followeeId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

            Follow follow = Follow.of(follower, followee);
            followRepository.save(follow);

            log.info("[Follow] User {} followed User {}", followerId, followeeId);
            return FollowResponse.followed(followerId, followeeId);
        }
    }

    /**
     * 팔로우 상태 확인
     */
    @Transactional(readOnly = true)
    public boolean isFollowing(Long followerId, Long followeeId) {
        return followRepository.existsByFollowerIdAndFolloweeId(followerId, followeeId);
    }

    /**
     * 특정 사용자의 팔로워 ID 목록 조회 (Fan-out 핵심)
     */
    @Transactional(readOnly = true)
    public List<Long> getFollowerIds(Long followeeId) {
        return followRepository.findFollowerIdsByFolloweeId(followeeId);
    }

    /**
     * 팔로워 수 조회
     */
    @Transactional(readOnly = true)
    public long getFollowerCount(Long userId) {
        return followRepository.countByFolloweeId(userId);
    }

    /**
     * 팔로잉 수 조회
     */
    @Transactional(readOnly = true)
    public long getFollowingCount(Long userId) {
        return followRepository.countByFollowerId(userId);
    }
}
