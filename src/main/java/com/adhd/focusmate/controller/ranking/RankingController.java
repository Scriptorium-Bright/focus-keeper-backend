package com.adhd.focusmate.controller.ranking;

import com.adhd.focusmate.common.dto.ApiResponse;
import com.adhd.focusmate.service.ranking.RankingService;
import com.adhd.focusmate.service.ranking.RankingService.RankingEntry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "Ranking", description = "실시간 랭킹 API (Redis ZSET)")
@RestController
@RequestMapping("/api/v1/ranking")
@RequiredArgsConstructor
public class RankingController {

    private final RankingService rankingService;

    @Operation(summary = "Top 10 랭킹 조회", description = "스트릭 기준 상위 10명을 조회합니다.")
    @GetMapping("/top10")
    public ResponseEntity<ApiResponse<List<RankingEntry>>> getTop10() {
        return ResponseEntity.ok(ApiResponse.success(rankingService.getTop10()));
    }

    @Operation(summary = "내 랭킹 조회", description = "본인의 랭킹과 점수를 조회합니다.")
    @GetMapping("/me/{userId}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyRanking(
            @Parameter(description = "사용자 ID", required = true) @PathVariable Long userId) {

        Long rank = rankingService.getUserRank(userId);
        Double score = rankingService.getUserScore(userId);
        Long total = rankingService.getTotalParticipants();

        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "userId", userId,
                "rank", rank != null ? rank : "unranked",
                "streak", score != null ? score.intValue() : 0,
                "totalParticipants", total != null ? total : 0)));
    }

    @Operation(summary = "주변 랭킹 조회", description = "본인 ± 2명의 랭킹을 조회합니다.")
    @GetMapping("/around/{userId}")
    public ResponseEntity<ApiResponse<List<RankingEntry>>> getRankingAroundUser(
            @Parameter(description = "사용자 ID", required = true) @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(rankingService.getRankingAroundUser(userId)));
    }

    @Operation(summary = "점수 업데이트", description = "사용자의 스트릭 점수를 업데이트합니다.")
    @PostMapping("/update")
    public ResponseEntity<ApiResponse<String>> updateScore(
            @RequestParam Long userId,
            @RequestParam int streak) {
        rankingService.updateUserScore(userId, streak);
        return ResponseEntity.ok(ApiResponse.success("Ranking updated"));
    }
}
