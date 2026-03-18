package com.focuskeeper.reboot.recovery.planning.dto;

import java.util.List;

public record SelectBig3Response(
        String selectedDate,
        String selectedAt,
        int selectedCount,
        List<Big3ItemResponse> selectedItems
) {
}
