package com.focuskeeper.reboot.recovery.planning.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record Big3SelectionResponse(
        String userId,
        LocalDate selectedDate,
        OffsetDateTime selectedAt,
        List<Big3ItemResponse> selectedItems
) {
}
