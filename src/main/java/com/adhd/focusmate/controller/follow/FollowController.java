package com.adhd.focusmate.controller.follow;

import com.adhd.focusmate.common.dto.ApiResponse;
import com.adhd.focusmate.dto.follow.FollowResponse;
import com.adhd.focusmate.service.follow.FollowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 팔로우 API Controller
 */
@Tag(name = "Follow", description = "팔로우 API")
@RestController
@RequestMapping("/api/v1/follows")
@RequiredArgsConstructor
public class FollowController {

    private final FollowService followService;

    /**
     * 팔로우/언팔로우 토글
     */
    @Operation(summary = "팔로우 토글", description = "팔로우 상태를 토글합니다. 팔로우 중이면 언팔로우, 아니면 팔로우합니다.")
    @PostMapping("/{targetUserId}")
    public ResponseEntity<ApiResponse<FollowResponse>> toggleFollow(
            @Parameter(description = "현재 사용자 ID (OAuth 구현 전 임시)", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "대상 사용자 ID", required = true) @PathVariable Long targetUserId) {
        FollowResponse response = followService.toggleFollow(userId, targetUserId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 팔로우 상태 확인
     */
    @Operation(summary = "팔로우 상태 확인", description = "특정 사용자를 팔로우 중인지 확인합니다.")
    @GetMapping("/{targetUserId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkFollowStatus(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable Long targetUserId) {
        boolean isFollowing = followService.isFollowing(userId, targetUserId);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "targetUserId", targetUserId,
                "following", isFollowing)));
    }

    /**
     * 팔로워/팔로잉 카운트 조회
     */
    @Operation(summary = "팔로우 카운트", description = "특정 사용자의 팔로워/팔로잉 수를 조회합니다.")
    @GetMapping("/{userId}/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getFollowCount(
            @PathVariable Long userId) {
        long followerCount = followService.getFollowerCount(userId);
        long followingCount = followService.getFollowingCount(userId);

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "followerCount", followerCount,
                "followingCount", followingCount)));
    }
}
