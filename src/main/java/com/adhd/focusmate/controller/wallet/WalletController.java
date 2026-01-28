package com.adhd.focusmate.controller.wallet;

import com.adhd.focusmate.common.dto.ApiResponse;
import com.adhd.focusmate.dto.wallet.CreditChargeRequest;
import com.adhd.focusmate.dto.wallet.CreditDeductRequest;
import com.adhd.focusmate.dto.wallet.WalletResponse;
import com.adhd.focusmate.service.wallet.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Wallet", description = "지갑 API - 잔액/포인트 관리")
@RestController
@RequestMapping("/api/v1/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "내 지갑 조회", description = "현재 사용자의 잔액과 포인트를 조회합니다.")
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(
            @Parameter(description = "사용자 ID (임시 - OAuth 구현 후 토큰에서 추출)", required = true) @RequestHeader("X-User-Id") Long userId) {
        WalletResponse response = walletService.getBalance(userId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Operation(summary = "잔액 조회 (ID)", description = "특정 사용자의 지갑 잔액을 조회합니다.")
    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<WalletResponse>> getBalance(
            @Parameter(description = "조회할 사용자 ID", required = true) @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.success(walletService.getBalance(userId)));
    }

    @Operation(summary = "크레딧 충전", description = "사용자 지갑에 크레딧을 충전합니다.")
    @PostMapping("/charge")
    public ResponseEntity<ApiResponse<WalletResponse>> charge(
            @RequestBody CreditChargeRequest request) {
        return ResponseEntity.ok(ApiResponse.success(walletService.charge(request)));
    }

    @Operation(summary = "크레딧 차감", description = "사용자 지갑에서 크레딧을 차감합니다. (페널티 또는 소비)")
    @PostMapping("/deduct")
    public ResponseEntity<ApiResponse<WalletResponse>> deduct(
            @RequestBody CreditDeductRequest request) {
        return ResponseEntity.ok(ApiResponse.success(walletService.deduct(request)));
    }
}
