package com.adhd.focusmate.dto.wallet;

public record CreditChargeRequest(
        Long userId,
        int amount) {
}
