package com.focuskeeper.reboot.recovery.inbox.dto;


public record InboxItemResponse (
        String id,
        String content,
        String createdAt
) {
}
