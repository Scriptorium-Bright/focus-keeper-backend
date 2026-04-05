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
/**
 * timebox 배정 요청 자체의 도메인 규칙을 검증하는 validator다.
 *
 * 시간 겹침처럼 일정 충돌을 보는 validator와는 별도로,
 * "첫 복귀 블록은 정확히 하나" 같은 역할 규칙을 담당한다.
 */
public class TimeboxAllocationValidator {

    /**
     * 첫 복귀 블록의 개수와 타입 규칙을 검증한다.
     */
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

    /**
     * BREAK 블록이 첫 복귀 블록으로 지정되는 잘못된 조합을 막는다.
     */
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

    /**
     * 문자열 타입을 TimeboxType enum으로 변환한다.
     */
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

    /**
     * timebox 요청에 포함된 itemId가 오늘의 Big3에 실제로 포함되는지 검증한다.
     */
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
