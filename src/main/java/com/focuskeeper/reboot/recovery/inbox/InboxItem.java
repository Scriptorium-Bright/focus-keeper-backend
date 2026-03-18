package com.focuskeeper.reboot.recovery.inbox;

public record InboxItem(
        String id,
        String userId,
        String content,
        String createdAt
) {
}
