package com.adhd.focusmate.controller.wallet;

import com.adhd.focusmate.dto.wallet.CreditChargeRequest;
import com.adhd.focusmate.dto.wallet.CreditDeductRequest;
import com.adhd.focusmate.dto.wallet.WalletResponse;
import com.adhd.focusmate.service.wallet.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Wallet System", description = "Gamified credit system with locking")
@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    @Operation(summary = "Charge Credits", description = "Add credits to user wallet")
    @PostMapping("/charge")
    public ResponseEntity<WalletResponse> charge(@RequestBody CreditChargeRequest request) {
        return ResponseEntity.ok(walletService.charge(request));
    }

    @Operation(summary = "Deduct Credits", description = "Deduct credits (penalty or spend)")
    @PostMapping("/deduct")
    public ResponseEntity<WalletResponse> deduct(@RequestBody CreditDeductRequest request) {
        return ResponseEntity.ok(walletService.deduct(request));
    }

    @Operation(summary = "Get Balance", description = "Get current wallet balance")
    @GetMapping("/{userId}")
    public ResponseEntity<WalletResponse> getBalance(@PathVariable Long userId) {
        return ResponseEntity.ok(walletService.getBalance(userId));
    }
}
