package com.focuskeeper.reboot.recovery.inbox;

public record InboxItemResponse(
        String id,
        String content,
        String createdAt
) {
}
