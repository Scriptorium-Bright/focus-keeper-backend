package com.focuskeeper.reboot.recovery.planning.dto;

import com.focuskeeper.reboot.recovery.planning.Big3ItemCompletionStatus;

import java.util.List;

public record SelectBig3Response(
        String selectedDate,
        String selectedAt,
        int selectedCount,
        List<Big3ItemResponse> selectedItems,
        Big3ItemCompletionStatus status
) {
}
