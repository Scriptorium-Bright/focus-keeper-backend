package com.adhd.focusmate.controller.feed;

import com.adhd.focusmate.common.dto.ApiResponse;
import com.adhd.focusmate.dto.feed.FeedItemDto;
import com.adhd.focusmate.service.feed.FeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Feed API Controller
 * 
 * Redis 기반 피드 조회 (PostgreSQL 쿼리 없음)
 */
@Tag(name = "Feed", description = "소셜 피드 API")
@RestController
@RequestMapping("/api/v1/feeds")
@RequiredArgsConstructor
public class FeedController {

    private final FeedService feedService;

    /**
     * 내 피드 조회 (타임라인)
     */
    @Operation(summary = "내 피드 조회", description = "팔로우한 사용자들의 챌린지 활동 피드를 조회합니다.")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<FeedItemDto>>> getMyFeed(
            @Parameter(description = "사용자 ID (OAuth 구현 전 임시)", required = true) @RequestHeader("X-User-Id") Long userId,
            @Parameter(description = "페이지 번호 (0부터 시작)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "20") int size) {
        List<FeedItemDto> feeds = feedService.getMyFeed(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(feeds));
    }

    /**
     * 피드 메타 정보 조회 (총 개수 등)
     */
    @Operation(summary = "피드 메타 정보", description = "피드 총 개수 등 메타 정보를 조회합니다.")
    @GetMapping("/my/meta")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFeedMeta(
            @RequestHeader("X-User-Id") Long userId) {
        long totalCount = feedService.getFeedCount(userId);
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "totalCount", totalCount)));
    }
}
