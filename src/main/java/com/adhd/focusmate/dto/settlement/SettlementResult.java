package com.adhd.focusmate.dto.settlement;

import com.adhd.focusmate.domain.model.type.ChallengeStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * 정산 결과 DTO
 */
@Getter
@Builder
public class SettlementResult {
    private final Long challengeId;
    private final ChallengeStatus status;
    private final boolean depositRefunded;
    private final int refundAmount;
    private final long pointsAwarded;
    private final boolean savedByItem; // 면제권으로 구제되었는지
}
