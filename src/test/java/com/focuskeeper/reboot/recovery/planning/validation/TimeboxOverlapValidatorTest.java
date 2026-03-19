package com.focuskeeper.reboot.recovery.planning.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeboxOverlapValidatorTest {

    private final TimeboxOverlapValidator validator = new TimeboxOverlapValidator();

    @Test
    void validateRejectsWhenRequestedTimeboxesOverlapEachOther() {
        Timebox first = createTimebox("item-1", "2026-03-19T09:00:00+09:00", "2026-03-19T09:25:00+09:00");
        Timebox second = createTimebox("item-2", "2026-03-19T09:10:00+09:00", "2026-03-19T09:30:00+09:00");

        assertThatThrownBy(() -> validator.validate(List.of(), List.of(first, second)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                });
    }

    @Test
    void validateRejectsWhenRequestedTimeboxOverlapsExistingTimebox() {
        Timebox existing = createTimebox("item-1", "2026-03-19T09:00:00+09:00", "2026-03-19T09:25:00+09:00");
        Timebox requested = createTimebox("item-2", "2026-03-19T09:20:00+09:00", "2026-03-19T09:40:00+09:00");

        assertThatThrownBy(() -> validator.validate(List.of(existing), List.of(requested)))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException businessException = (BusinessException) exception;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
                });
    }

    private Timebox createTimebox(String itemId, String startAt, String endAt) {
        return Timebox.create(
                "user-1",
                itemId,
                "content-" + itemId,
                TimeboxType.WORK,
                OffsetDateTime.parse(startAt),
                OffsetDateTime.parse(endAt),
                false,
                OffsetDateTime.parse("2026-03-19T08:00:00+09:00")
        );
    }
}
