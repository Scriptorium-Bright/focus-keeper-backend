package com.focuskeeper.reboot.recovery.planning.dto;

import com.focuskeeper.reboot.recovery.planning.Big3ItemCompletionStatus;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Selection;
import com.focuskeeper.reboot.recovery.planning.entity.Big3SelectionItem;

import java.util.Comparator;
import java.util.List;

public record Big3ItemResponse(
        String big3SelectionItemId,
        String itemId,
        String content,
        String completionStatus
) {
    public Big3ItemResponse(String big3SelectionItemId, String itemId, String content) {
        this(big3SelectionItemId, itemId, content, "NOT_STARTED");
    }

    public static Big3ItemResponse from(Big3SelectionItem selectionItem) {
        return new Big3ItemResponse(
                selectionItem.getId(),
                selectionItem.getInboxItem().getId(),
                selectionItem.getInboxItem().getContent(),
                selectionItem.getStatus().name()
        );
    }
}
