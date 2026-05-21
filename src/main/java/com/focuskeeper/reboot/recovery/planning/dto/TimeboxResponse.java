package com.focuskeeper.reboot.recovery.planning.dto;


// timebox에 대한 응답 보내주는거
public record TimeboxResponse(
        String timeboxId,
        String itemId,
        String content,
        String startAt,
        String endAt,
        boolean firstRecoveryBlock,
        String type,
        String createdAt
) {
}
