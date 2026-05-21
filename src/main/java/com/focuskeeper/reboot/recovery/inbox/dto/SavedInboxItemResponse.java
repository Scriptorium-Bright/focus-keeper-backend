package com.focuskeeper.reboot.recovery.inbox.dto;

// 단일 BrainDump건에 대한
public record SavedInboxItemResponse (
        String id,
        String content,
        String createdAt
) {
}
