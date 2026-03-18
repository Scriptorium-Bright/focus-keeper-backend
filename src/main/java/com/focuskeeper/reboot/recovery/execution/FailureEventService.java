package com.focuskeeper.reboot.recovery.execution;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
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
        RecoverySession interruptedSession = recoverySessionService.interruptSession(userId, sessionId);

        FailureEvent failureEvent = failureEventRepository.save(FailureEventEntity.create(
                userId,
                interruptedSession.id(),
                interruptedSession.timeboxId(),
                reason,
                note,
                OffsetDateTime.now()
        )).toDomain();

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
            FailureEvent failureEvent,
            RecoverySession recoverySession
    ) {
    }
}
