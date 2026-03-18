package com.focuskeeper.reboot.recovery.planning.dto;

import com.focuskeeper.reboot.recovery.inbox.dto.InboxItemResponse;

public record Big3ItemResponse(
        String itemId,
        String content
) {
    public static Big3ItemResponse from(InboxItemResponse item) {
        return new Big3ItemResponse(item.id(), item.content());
    }
}
