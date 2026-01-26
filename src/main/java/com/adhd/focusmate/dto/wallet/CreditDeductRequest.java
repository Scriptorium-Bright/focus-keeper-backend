package com.adhd.focusmate.dto.wallet;

import com.adhd.focusmate.domain.model.type.CreditLogReason;

public record CreditDeductRequest(
        Long userId,
        int amount,
        CreditLogReason reason) {
}
