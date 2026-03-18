package com.focuskeeper.reboot.recovery.planning.dto;

import java.util.List;

public record AllocateTimeboxesResponse(
        String plannedDate,
        int allocatedCount,
        List<AllocatedTimeboxResponse> timeboxes
) {
}
