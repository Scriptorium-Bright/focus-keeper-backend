package com.focuskeeper.reboot.recovery.planning.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.planning.port.ActiveSessionTerminator;
import com.focuskeeper.reboot.recovery.planning.constant.Big3ItemStatus;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import com.focuskeeper.reboot.recovery.planning.repository.Big3ItemRepository;
import com.focuskeeper.reboot.recovery.planning.repository.ExecutionUnitRepository;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ExecutionUnitServiceTest {

    private final Big3ItemRepository big3ItemRepository = mock(Big3ItemRepository.class);
    private final ExecutionUnitRepository executionUnitRepository = mock(ExecutionUnitRepository.class);
    private final ActiveSessionTerminator activeSessionTerminator = mock(ActiveSessionTerminator.class);
    private final ExecutionUnitService executionUnitService = new ExecutionUnitService(
            big3ItemRepository,
            executionUnitRepository,
            activeSessionTerminator
    );

    @ParameterizedTest
    @EnumSource(
            value = Big3ItemStatus.class,
            names = {"COMPLETED", "ABANDONED", "EXPIRED"}
    )
    void createExecutionUnitRejectsTerminalBig3Item(Big3ItemStatus status) {
        Big3Item big3Item = mock(Big3Item.class);
        when(big3ItemRepository.findByIdAndUserIdForUpdate("item-id", "user-id"))
                .thenReturn(Optional.of(big3Item));
        when(big3Item.getId()).thenReturn("item-id");
        when(big3Item.getStatus()).thenReturn(status);

        assertThatThrownBy(() ->
                executionUnitService.singleInsertUnit("user-id", "item-id", "실행 단위")
        )
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getDetails()).isEqualTo(
                            java.util.Map.of(
                                    "big3ItemId", "item-id",
                                    "status", status.name()
                            )
                    );
                });

        verifyNoInteractions(executionUnitRepository);
    }
}
