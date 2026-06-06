package com.focuskeeper.reboot.recovery.planning.dto;

import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;

public record Big3ItemResponse(
        String big3ItemId,
        String itemId,
        String content,
        String completionStatus
) {
    public Big3ItemResponse(String big3ItemId, String itemId, String content) {
        this(big3ItemId, itemId, content, "NOT_STARTED");
    }

    public static Big3ItemResponse from(Big3Item big3Item) {
        return new Big3ItemResponse(
                big3Item.getId(),
                big3Item.getOriginInboxItem().getId(),
                big3Item.getTitleSnapshot(),
                big3Item.getCompletionStatus().name()
        );
    }
}
