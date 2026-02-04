package com.adhd.focusmate.dto.follow;

/**
 * 팔로우 토글 응답 DTO
 */
public record FollowResponse(
        Long followerId,
        Long followeeId,
        boolean following,
        String message) {
    public static FollowResponse followed(Long followerId, Long followeeId) {
        return new FollowResponse(followerId, followeeId, true, "팔로우했습니다.");
    }

    public static FollowResponse unfollowed(Long followerId, Long followeeId) {
        return new FollowResponse(followerId, followeeId, false, "언팔로우했습니다.");
    }
}
