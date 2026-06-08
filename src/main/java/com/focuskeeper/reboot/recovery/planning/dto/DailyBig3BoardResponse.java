package com.focuskeeper.reboot.recovery.planning.dto;

import com.focuskeeper.reboot.recovery.planning.Big3ItemCompletionStatus;
import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Board;
import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Entry;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

public record DailyBig3BoardResponse(
        String userId,
        LocalDate selectedDate,
        OffsetDateTime selectedAt,
        List<Big3ItemResponse> selectedItems,
        Big3ItemCompletionStatus status,
        String dailyBig3boardId,
        List<DailyBig3EntryResponse> dailyBig3Entries
) {
    public static DailyBig3BoardResponse from(
            DailyBig3Board dailyBig3Board,
            List<DailyBig3Entry> activeEntries
    ) {
        List<Big3ItemResponse> items = activeEntries.stream()
                .sorted(Comparator.comparingInt(DailyBig3Entry::getSlotOrder))
                .map(DailyBig3Entry::getBig3Item)
                .map(Big3ItemResponse::from)
                .toList();

        List<DailyBig3EntryResponse> entries = DailyBig3EntryResponse.fromList(activeEntries);

        return new DailyBig3BoardResponse(
                dailyBig3Board.getUserId(),
                dailyBig3Board.getSelectedDate(),
                dailyBig3Board.getSelectedAt(),
                items,
                completionStatus(activeEntries),
                dailyBig3Board.getId(),
                entries
        );
    }

    private static Big3ItemCompletionStatus completionStatus(List<DailyBig3Entry> activeEntries) {
        if (activeEntries.isEmpty()) {
            return Big3ItemCompletionStatus.NOT_STARTED;
        }
        if (activeEntries.stream()
                .allMatch(entry -> entry.getBig3Item().getCompletionStatus() == Big3ItemCompletionStatus.COMPLETED)) {
            return Big3ItemCompletionStatus.COMPLETED;
        }
        if (activeEntries.stream()
                .allMatch(entry -> entry.getBig3Item().getCompletionStatus() == Big3ItemCompletionStatus.NOT_STARTED)) {
            return Big3ItemCompletionStatus.NOT_STARTED;
        }
        return Big3ItemCompletionStatus.IN_PROGRESS;
    }
}
