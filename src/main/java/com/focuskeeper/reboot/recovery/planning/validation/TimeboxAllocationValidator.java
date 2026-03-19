package com.focuskeeper.reboot.recovery.planning.validation;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.inbox.dto.InboxItemResponse;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.service.TimeboxCommand;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TimeboxAllocationValidator {

    // Q. '첫' 복귀 블록이라는게, 사용자가 집중력을 잃어 나가게 되었을 때, 복귀를 해야하는데 만약 복귀 블록이 두 개일 경우 블록간의 시간이 겹치게 된다는 의미도 될탠데 그럼 시간 겹침으로 처리할 수도 있지 않나?
    // A. 시간 겹침은 일정 충돌이고, "첫 복귀 블록이 1개여야 한다"는 건 역할 충돌이라 성질이 다르다.
    // A. 두 블록이 안 겹쳐도 "무엇이 첫 복귀인가"가 모호해지므로, overlap 검증과 별도로 여기서 먼저 막는 게 맞다.
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

        boolean invalidFirstRecoveryType = commands.stream()
                .filter(TimeboxCommand::firstRecoveryBlock)
                .map(command -> parseType(command.type()))
                .anyMatch(type -> type != TimeboxType.WORK);
        if (invalidFirstRecoveryType) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("timeboxes", "첫 복귀 블록은 WORK type이어야 합니다.")
            );
        }
    }

    public void validateTypes(List<TimeboxCommand> commands) {
        boolean hasBreakFirstRecovery = commands.stream()
                .anyMatch(command -> parseType(command.type()) == TimeboxType.BREAK && command.firstRecoveryBlock());
        if (hasBreakFirstRecovery) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("timeboxes", "BREAK timebox는 첫 복귀 블록이 될 수 없습니다.")
            );
        }
    }

    private TimeboxType parseType(String rawType) {
        try {
            return TimeboxType.valueOf(rawType);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("type", "지원하지 않는 timebox type입니다.")
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
