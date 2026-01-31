package com.adhd.focusmate.controller.admin;

import com.adhd.focusmate.common.dto.ApiResponse;
import com.adhd.focusmate.service.ranking.RankingMigrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Admin", description = "관리자 API")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
// @PreAuthorize("hasRole('ADMIN')") // TODO: OAuth2 구현 후 활성화
public class AdminController {

    private final RankingMigrationService rankingMigrationService;

    @Operation(summary = "[Admin] 랭킹 데이터 마이그레이션", description = "DB의 모든 사용자 점수를 Redis 랭킹으로 동기화합니다.")
    @PostMapping("/ranking/migrate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> migrateRanking() {
        int count = rankingMigrationService.migrateAllUsersToRedis();
        return ResponseEntity.ok(ApiResponse.success(Map.of(
                "migratedUsers", count,
                "message", "Migration completed")));
    }
}
