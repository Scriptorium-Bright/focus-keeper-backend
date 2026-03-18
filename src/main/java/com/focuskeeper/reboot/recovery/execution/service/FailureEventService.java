package com.focuskeeper.reboot.recovery.execution.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.execution.FailureReason;
import com.focuskeeper.reboot.recovery.execution.dto.FailureEventResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RecoverySessionResponse;
import com.focuskeeper.reboot.recovery.execution.entity.FailureEventEntity;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FailureEventService {

    private final RecoverySessionService recoverySessionService;
    private final FailureEventRepository failureEventRepository;

    public FailureEventService(
            RecoverySessionService recoverySessionService,
            FailureEventRepository failureEventRepository
    ) {
        this.recoverySessionService = recoverySessionService;
        this.failureEventRepository = failureEventRepository;
    }

    @Transactional
    public FailureCheckInResult checkIn(String userId, String sessionId, String reasonValue, String note) {
        FailureReason reason = parseReason(reasonValue);
        RecoverySessionResponse interruptedSession = recoverySessionService.interruptSession(userId, sessionId);

        FailureEventResponse failureEvent = failureEventRepository.save(FailureEventEntity.create(
                userId,
                interruptedSession.sessionId(),
                interruptedSession.timeboxId(),
                reason,
                note,
                OffsetDateTime.now()
        )).toResponse();

        return new FailureCheckInResult(failureEvent, interruptedSession);
    }

    private FailureReason parseReason(String reasonValue) {
        try {
            return FailureReason.from(reasonValue);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("reason", "지원하지 않는 failure reason입니다.")
            );
        }
    }

    public record FailureCheckInResult(
            FailureEventResponse failureEvent,
            RecoverySessionResponse recoverySession
    ) {
    }
}
