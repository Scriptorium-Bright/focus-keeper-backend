package com.focuskeeper.reboot.recovery.planning.dto;

public record Big3ItemResponse(
        String big3SelectionItemId,
        String itemId,
        String content,
        String completionStatus
) {
    public Big3ItemResponse(String big3SelectionItemId, String itemId, String content) {
        this(big3SelectionItemId, itemId, content, "NOT_STARTED");
    }
}
