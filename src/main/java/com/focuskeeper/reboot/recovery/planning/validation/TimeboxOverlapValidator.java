package com.focuskeeper.reboot.recovery.planning.validation;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
/**
 * 새로 배정하려는 timebox가 기존 일정 또는 같은 요청 안의 다른 블록과 겹치는지 검사하는 validator다.
 */
public class TimeboxOverlapValidator {

    /**
     * 요청 블록끼리의 충돌과 기존 저장 블록과의 충돌을 모두 검사한다.
     * 시간 겹치는거 방지
     */
    // critical
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

    /**
     * 두 timebox의 시간 구간이 실제로 겹치는지 판정한다.
     */
    private boolean overlaps(Timebox left, Timebox right) {
        return left.getStartAt().isBefore(right.getEndAt()) && left.getEndAt().isAfter(right.getStartAt());
    }

    /**
     * 충돌한 기존/요청 timebox의 식별자와 구간을 담은 공통 충돌 예외를 만든다.
     */
    private BusinessException conflictException(Timebox conflictingTimebox) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("conflictingTimeboxId", conflictingTimebox.getId());
        details.put("startAt", conflictingTimebox.getStartAt().toString());
        details.put("endAt", conflictingTimebox.getEndAt().toString());

        return new BusinessException(
                ErrorCode.CONFLICT,
                details
        );
    }
}
