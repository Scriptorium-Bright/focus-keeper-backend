package com.focuskeeper.reboot.recovery.planning;

import com.focuskeeper.reboot.recovery.inbox.InboxItemDto;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record Big3SelectionDto(
        String userId,
        LocalDate selectedDate,
        OffsetDateTime selectedAt,
        List<InboxItemDto> selectedItems
) {
}
