package com.focuskeeper.reboot.recovery.inbox.dto;

import java.util.List;

// 여러 braindump
public record SaveInboxItemsResponse(
        int savedCount,
        List<SavedInboxItemResponse> savedItems
) {
}
