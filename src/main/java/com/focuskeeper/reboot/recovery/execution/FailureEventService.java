package com.focuskeeper.reboot.recovery.execution;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class FailureEventService {

    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<String, FailureEvent> failureEventStore = new ConcurrentHashMap<>();
    private final RecoverySessionService recoverySessionService;

    public FailureEventService(RecoverySessionService recoverySessionService) {
        this.recoverySessionService = recoverySessionService;
    }

    public FailureCheckInResult checkIn(String userId, String sessionId, String reasonValue, String note) {
        FailureReason reason = parseReason(reasonValue);
        RecoverySession interruptedSession = recoverySessionService.interruptSession(userId, sessionId);

        FailureEvent failureEvent = new FailureEvent(
                String.valueOf(sequence.getAndIncrement()),
                userId,
                interruptedSession.id(),
                interruptedSession.timeboxId(),
                reason,
                note,
                OffsetDateTime.now()
        );
        failureEventStore.put(failureEvent.id(), failureEvent);

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
