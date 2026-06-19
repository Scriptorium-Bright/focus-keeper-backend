package com.focuskeeper.reboot.recovery.planning.dto;

import com.focuskeeper.reboot.recovery.planning.constant.SelectionSource;
import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Entry;

import java.util.List;
import java.util.stream.Collectors;

public record DailyBig3EntryResponse (
        Integer slotOrder,
        SelectionSource selectionSource,
        String dailyBig3EntryId
) {

    public static List<DailyBig3EntryResponse> fromList(List<DailyBig3Entry> entries) {

        return entries.stream().map(entry -> new DailyBig3EntryResponse(
                entry.getSlotOrder(),
                entry.getSelectionSource(),
                entry.getId()
        )).collect(Collectors.toList());
    }


}
