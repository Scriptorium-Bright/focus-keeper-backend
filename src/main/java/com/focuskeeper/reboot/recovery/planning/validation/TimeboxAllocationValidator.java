package com.focuskeeper.reboot.recovery.planning.validation;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.inbox.dto.InboxItemResponse;
import com.focuskeeper.reboot.recovery.planning.service.TimeboxCommand;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TimeboxAllocationValidator {

    // 
    public void validateFirstRecoveryBlock(List<TimeboxCommand> commands) {
        long firstRecoveryBlockCount = commands.stream()
                .filter(TimeboxCommand::firstRecoveryBlock)
                .count();
        if (firstRecoveryBlockCount != 1) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("timeboxes", "첫 복귀 블록은 정확히 1개여야 합니다.")
            );
        }
    }

    public void validateSelectedItems(List<TimeboxCommand> commands, Map<String, InboxItemResponse> selectedItems) {
        List<String> invalidItemIds = commands.stream()
                .map(TimeboxCommand::itemId)
                .filter(itemId -> !selectedItems.containsKey(itemId))
                .distinct()
                .toList();

        if (!invalidItemIds.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of(
                            "invalidItemIds", invalidItemIds,
                            "itemIds", "오늘의 Big3에 포함된 항목만 timebox로 배정할 수 있습니다."
                    )
            );
        }
    }
}
