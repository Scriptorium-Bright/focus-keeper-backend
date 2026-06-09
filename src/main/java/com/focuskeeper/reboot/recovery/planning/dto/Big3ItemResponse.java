package com.focuskeeper.reboot.recovery.planning.dto;

import com.focuskeeper.reboot.recovery.planning.ExecutionUnitStatus;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;

import java.util.List;

public record Big3ItemResponse(
        String big3ItemId,
        String itemId,
        String content,
        String completionStatus
) {
    public Big3ItemResponse(String big3ItemId, String itemId, String content) {
        this(big3ItemId, itemId, content, "NOT_STARTED");
    }

    public static Big3ItemResponse from(Big3Item big3Item) {

        List<ExecutionUnit> units = big3Item.getUnits();

        long completedCount = units.stream()
                .filter(u -> u.getStatus() == ExecutionUnitStatus.COMPLETED)
                .count();

        // total success percentage -> 도입 할 지 안 할지 모름
        double successPer = completedCount == 0 ? 0.0 : (double) completedCount / units.size();


        return new Big3ItemResponse(
                big3Item.getId(),
                big3Item.getOriginInboxItem().getId(),
                big3Item.getTitleSnapshot(),
                big3Item.getCompletionStatus().name()
        );
    }
}
