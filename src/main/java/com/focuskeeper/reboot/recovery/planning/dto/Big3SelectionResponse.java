package com.focuskeeper.reboot.recovery.planning.dto;

import com.focuskeeper.reboot.recovery.planning.Big3ItemCompletionStatus;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Selection;
import com.focuskeeper.reboot.recovery.planning.entity.Big3SelectionItem;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

public record Big3SelectionResponse(
        String userId,
        LocalDate selectedDate,
        OffsetDateTime selectedAt,
        List<Big3ItemResponse> selectedItems,
        Big3ItemCompletionStatus status
) {
    public static Big3SelectionResponse from(Big3Selection selection) {
        List<Big3ItemResponse> items = selection.getSelectedItems().stream()
                .sorted(Comparator.comparingInt(Big3SelectionItem::getSortOrder))
                .map(Big3ItemResponse::from) // Big3ItemResponse도 내부 from 사용
                .toList();
        return new Big3SelectionResponse(
                selection.getUserId(),
                selection.getSelectedDate(),
                selection.getSelectedAt(),
                items,
                selection.getStatus() // 엔티티가 계산해 준 상태 사용
        );
    }
}
