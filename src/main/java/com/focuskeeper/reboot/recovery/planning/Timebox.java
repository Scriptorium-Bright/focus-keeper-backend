package com.focuskeeper.reboot.recovery.planning;

import java.time.OffsetDateTime;

public record Timebox(
        String id,
        String userId,
        String itemId,
        String itemContent,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        boolean firstRecoveryBlock,
        OffsetDateTime createdAt
) {
    boolean overlaps(OffsetDateTime otherStartAt, OffsetDateTime otherEndAt) {
        return startAt.isBefore(otherEndAt) && endAt.isAfter(otherStartAt);
    }
}
