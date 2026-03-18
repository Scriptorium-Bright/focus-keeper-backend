package com.focuskeeper.reboot.recovery.planning;

import java.time.OffsetDateTime;

public record TimeboxDto(
        String id,
        String userId,
        String itemId,
        String itemContent,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        boolean firstRecoveryBlock,
        OffsetDateTime createdAt
) {
}
