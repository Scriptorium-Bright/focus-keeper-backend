package com.focuskeeper.reboot.recovery.inbox.dto;

public record SavedInboxItemResponse(
        String id,
        String content,
        String createdAt
) {
}
