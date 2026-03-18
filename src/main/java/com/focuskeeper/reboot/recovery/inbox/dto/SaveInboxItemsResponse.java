package com.focuskeeper.reboot.recovery.inbox.dto;

import java.util.List;

public record SaveInboxItemsResponse(
        int savedCount,
        List<SavedInboxItemResponse> savedItems
) {
}
