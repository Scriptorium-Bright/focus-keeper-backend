package com.focuskeeper.reboot.recovery.planning.dto;

import com.focuskeeper.reboot.recovery.inbox.dto.InboxItemResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record Big3SelectionResponse(
        String userId,
        LocalDate selectedDate,
        OffsetDateTime selectedAt,
        List<InboxItemResponse> selectedItems
) {
}
