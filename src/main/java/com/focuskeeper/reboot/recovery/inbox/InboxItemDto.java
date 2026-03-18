package com.focuskeeper.reboot.recovery.inbox;

public record InboxItemDto(
        String id,
        String userId,
        String content,
        String createdAt
) {
}
