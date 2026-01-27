package com.adhd.focusmate.controller.challenge;

import com.adhd.focusmate.domain.model.type.ChallengeStatus;
import com.adhd.focusmate.dto.challenge.ChallengeCreateRequest;
import com.adhd.focusmate.dto.challenge.ChallengeResponse;
import com.adhd.focusmate.service.challenge.ChallengeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Challenge Management", description = "Core loop: Create -> Complete/Fail -> Reward/Penalty")
@RestController
@RequestMapping("/api/v1/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    @Operation(summary = "Create Challenge", description = "Create a new challenge with verification type")
    @PostMapping
    public ResponseEntity<ChallengeResponse> createChallenge(@RequestBody ChallengeCreateRequest request) {
        return ResponseEntity.ok(challengeService.createChallenge(request));
    }

    @Operation(summary = "Verify & Complete", description = "Run verification strategy and complete/fail based on result")
    @PatchMapping("/{challengeId}/verify")
    public ResponseEntity<ChallengeResponse> verifyChallenge(@PathVariable Long challengeId) {
        return ResponseEntity.ok(challengeService.verifyAndComplete(challengeId));
    }

    @Operation(summary = "Complete Challenge", description = "Force complete (bypass verification, +100 credits)")
    @PatchMapping("/{challengeId}/complete")
    public ResponseEntity<ChallengeResponse> completeChallenge(@PathVariable Long challengeId) {
        return ResponseEntity.ok(challengeService.completeChallenge(challengeId));
    }

    @Operation(summary = "Fail Challenge", description = "Mark as failed (-500 credits)")
    @PatchMapping("/{challengeId}/fail")
    public ResponseEntity<ChallengeResponse> failChallenge(@PathVariable Long challengeId) {
        return ResponseEntity.ok(challengeService.failChallenge(challengeId));
    }

    @Operation(summary = "List Challenges", description = "Get challenges by user and optionally filter by status")
    @GetMapping
    public ResponseEntity<List<ChallengeResponse>> getChallenges(
            @RequestParam Long userId,
            @RequestParam(required = false) ChallengeStatus status) {
        return ResponseEntity.ok(challengeService.getChallenges(userId, status));
    }
}
