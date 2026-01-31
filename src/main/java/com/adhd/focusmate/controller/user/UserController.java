package com.adhd.focusmate.controller.user;

import com.adhd.focusmate.common.dto.ApiResponse;
import com.adhd.focusmate.dto.user.UserProfileResponse;
import com.adhd.focusmate.service.user.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "User", description = "사용자 프로필 API")
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "프로필 조회", description = "사용자 프로필을 조회합니다. (Redis 캐시 적용)")
    @GetMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
            @Parameter(description = "사용자 ID", required = true) @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserProfile(userId)));
    }

    @Operation(summary = "프로필 조회 (캐시 없음)", description = "캐시 없이 직접 DB 조회 - 성능 비교용")
    @GetMapping("/{userId}/profile-nocache")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfileNoCache(
            @Parameter(description = "사용자 ID", required = true) @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(userService.getUserProfileNoCache(userId)));
    }
}
