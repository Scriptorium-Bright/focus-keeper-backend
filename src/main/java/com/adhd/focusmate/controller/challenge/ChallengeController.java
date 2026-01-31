package com.adhd.focusmate.controller.challenge;

import com.adhd.focusmate.common.dto.ApiResponse;
import com.adhd.focusmate.domain.model.type.ChallengeStatus;
import com.adhd.focusmate.dto.challenge.ChallengeCreateRequest;
import com.adhd.focusmate.dto.challenge.ChallengeResponse;
import com.adhd.focusmate.service.challenge.ChallengeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Challenge", description = "챌린지 API - 생성/검증/완료/실패 처리")
@RestController
@RequestMapping("/api/v1/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    @Operation(summary = "챌린지 생성", description = "새 챌린지를 생성합니다. challengeType에 따라 검증 방식이 달라집니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ChallengeResponse>> createChallenge(
            @RequestBody ChallengeCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(challengeService.createChallenge(request)));
    }

    @Operation(summary = "챌린지 상세 조회", description = "단일 챌린지를 조회합니다. (Redis 캐시 적용)")
    @GetMapping("/{challengeId}")
    public ResponseEntity<ApiResponse<ChallengeResponse>> getChallengeDetail(
            @Parameter(description = "챌린지 ID", required = true) @PathVariable Long challengeId) {
        return ResponseEntity.ok(ApiResponse.success(challengeService.getChallengeDetail(challengeId)));
    }

    @Operation(summary = "챌린지 검증 및 완료", description = "Strategy Pattern으로 검증 후 자동으로 완료/실패 처리합니다.")
    @PatchMapping("/{challengeId}/verify")
    public ResponseEntity<ApiResponse<ChallengeResponse>> verifyChallenge(
            @Parameter(description = "챌린지 ID", required = true) @PathVariable Long challengeId) {
        return ResponseEntity.ok(ApiResponse.success(challengeService.verifyAndComplete(challengeId)));
    }

    @Operation(summary = "강제 완료", description = "검증을 우회하고 챌린지를 완료 처리합니다. (+100 크레딧)")
    @PatchMapping("/{challengeId}/complete")
    public ResponseEntity<ApiResponse<ChallengeResponse>> completeChallenge(
            @Parameter(description = "챌린지 ID", required = true) @PathVariable Long challengeId) {
        return ResponseEntity.ok(ApiResponse.success(challengeService.completeChallenge(challengeId)));
    }

    @Operation(summary = "강제 실패", description = "챌린지를 실패 처리합니다. (-500 크레딧)")
    @PatchMapping("/{challengeId}/fail")
    public ResponseEntity<ApiResponse<ChallengeResponse>> failChallenge(
            @Parameter(description = "챌린지 ID", required = true) @PathVariable Long challengeId) {
        return ResponseEntity.ok(ApiResponse.success(challengeService.failChallenge(challengeId)));
    }

    @Operation(summary = "챌린지 목록 조회", description = "사용자별 챌린지 목록을 조회합니다. status로 필터링 가능합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChallengeResponse>>> getChallenges(
            @Parameter(description = "사용자 ID", required = true) @RequestParam Long userId,
            @Parameter(description = "상태 필터 (선택)") @RequestParam(required = false) ChallengeStatus status) {
        return ResponseEntity.ok(ApiResponse.success(challengeService.getChallenges(userId, status)));
    }

    // ===== Admin Endpoints =====

    @Operation(summary = "[Admin] 전체 챌린지 조회", description = "관리자용: 모든 챌린지를 조회합니다.")
    // @PreAuthorize("hasRole('ADMIN')") // TODO: OAuth2 구현 후 활성화
    @GetMapping("/admin/all")
    public ResponseEntity<ApiResponse<List<ChallengeResponse>>> getAllChallenges(
            @Parameter(description = "상태 필터 (선택)") @RequestParam(required = false) ChallengeStatus status) {
        return ResponseEntity.ok(ApiResponse.success(challengeService.getAllChallenges(status)));
    }
}
