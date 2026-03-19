package com.focuskeeper.reboot.recovery.planning.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.inbox.dto.InboxItemResponse;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.service.TimeboxCommand;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TimeboxAllocationValidatorTest {

    private final TimeboxAllocationValidator validator = new TimeboxAllocationValidator();

    @Test
    void validateFirstRecoveryBlockRejectsWhenNoFirstBlockExists() {
        List<TimeboxCommand> commands = List.of(
                new TimeboxCommand("item-1", "2026-03-19T09:00:00+09:00", "2026-03-19T09:25:00+09:00", false, TimeboxType.WORK.name()),
                new TimeboxCommand("item-2", "2026-03-19T10:00:00+09:00", "2026-03-19T10:25:00+09:00", false, TimeboxType.WORK.name())
        );

        assertThatThrownBy(() -> validator.validateFirstRecoveryBlock(commands))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.COMMON_BAD_REQUEST);
                });
    }

    @Test
    void validateSelectedItemsRejectsWhenCommandContainsItemOutsideTodayBig3() {
        Map<String, InboxItemResponse> selectedItems = Map.of(
                "item-1", new InboxItemResponse("item-1", "제안서 수정", "2026-03-19T08:00:00+09:00")
        );
        List<TimeboxCommand> commands = List.of(
                new TimeboxCommand("item-2", "2026-03-19T09:00:00+09:00", "2026-03-19T09:25:00+09:00", true, TimeboxType.WORK.name())
        );

        assertThatThrownBy(() -> validator.validateSelectedItems(commands, selectedItems))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.COMMON_BAD_REQUEST);
                    assertThat(businessException.getDetails()).isEqualTo(Map.of(
                            "invalidItemIds", List.of("item-2"),
                            "itemIds", "오늘의 Big3에 포함된 항목만 timebox로 배정할 수 있습니다."
                    ));
                });
    }

    @Test
    void validateTypesRejectsWhenBreakTimeboxIsMarkedAsFirstRecoveryBlock() {
        List<TimeboxCommand> commands = List.of(
                new TimeboxCommand("item-1", "2026-03-19T09:00:00+09:00", "2026-03-19T09:10:00+09:00", true, TimeboxType.BREAK.name())
        );

        assertThatThrownBy(() -> validator.validateTypes(commands))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.COMMON_BAD_REQUEST);
                    assertThat(businessException.getDetails()).isEqualTo(Map.of("timeboxes", "BREAK timebox는 첫 복귀 블록이 될 수 없습니다."));
                });
    }
}
