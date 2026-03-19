package com.focuskeeper.reboot.recovery.planning.validation;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class TimeboxOverlapValidator {

    public void validate(List<Timebox> existingTimeboxes, List<Timebox> requestedTimeboxes) {
        for (int index = 0; index < requestedTimeboxes.size(); index++) {
            Timebox current = requestedTimeboxes.get(index);

            for (int otherIndex = index + 1; otherIndex < requestedTimeboxes.size(); otherIndex++) {
                Timebox other = requestedTimeboxes.get(otherIndex);
                if (overlaps(current, other)) {
                    throw conflictException(current);
                }
            }

            for (Timebox existing : existingTimeboxes) {
                if (overlaps(existing, current)) {
                    throw conflictException(existing);
                }
            }
        }
    }

    private boolean overlaps(Timebox left, Timebox right) {
        return left.getStartAt().isBefore(right.getEndAt()) && left.getEndAt().isAfter(right.getStartAt());
    }

    private BusinessException conflictException(Timebox conflictingTimebox) {
        return new BusinessException(
                ErrorCode.CONFLICT,
                Map.of(
                        "conflictingTimeboxId", conflictingTimebox.getId(),
                        "startAt", conflictingTimebox.getStartAt().toString(),
                        "endAt", conflictingTimebox.getEndAt().toString()
                )
        );
    }
}
