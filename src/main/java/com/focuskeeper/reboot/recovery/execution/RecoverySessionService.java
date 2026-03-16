package com.focuskeeper.reboot.recovery.execution;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.planning.TimeboxService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

@Service
public class RecoverySessionService {

    private final AtomicLong sequence = new AtomicLong(1);
    private final Map<String, RecoverySession> sessionStore = new ConcurrentHashMap<>();
    private final TimeboxService timeboxService;

    public RecoverySessionService(TimeboxService timeboxService) {
        this.timeboxService = timeboxService;
    }

    public RecoverySession startSession(String userId, String timeboxId) {
        timeboxService.getTimeboxOrThrow(userId, timeboxId);

        boolean hasActiveSession = sessionStore.values().stream()
                .anyMatch(session -> session.userId().equals(userId)
                        && session.status() == RecoverySessionStatus.STARTED);
        if (hasActiveSession) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    Map.of("session", "이미 진행 중인 복귀 세션이 있습니다.")
            );
        }

        RecoverySession session = new RecoverySession(
                String.valueOf(sequence.getAndIncrement()),
                userId,
                timeboxId,
                RecoverySessionStatus.STARTED,
                OffsetDateTime.now(),
                null,
                OffsetDateTime.now()
        );
        sessionStore.put(session.id(), session);
        return session;
    }

    public RecoverySession completeSession(String userId, String sessionId) {
        RecoverySession session = getSessionOrThrow(userId, sessionId);
        if (session.status() != RecoverySessionStatus.STARTED) {
            throw invalidTransition(sessionId, session.status(), "COMPLETED");
        }

        RecoverySession completedSession = session.complete(OffsetDateTime.now());
        sessionStore.put(sessionId, completedSession);
        return completedSession;
    }

    public RecoverySession interruptSession(String userId, String sessionId) {
        RecoverySession session = getSessionOrThrow(userId, sessionId);
        if (session.status() != RecoverySessionStatus.STARTED) {
            throw invalidTransition(sessionId, session.status(), "INTERRUPTED");
        }

        RecoverySession interruptedSession = session.interrupt(OffsetDateTime.now());
        sessionStore.put(sessionId, interruptedSession);
        return interruptedSession;
    }

    public RecoverySession getSessionOrThrow(String userId, String sessionId) {
        RecoverySession session = sessionStore.get(sessionId);
        if (session == null || !session.userId().equals(userId)) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    Map.of("sessionId", sessionId)
            );
        }
        return session;
    }

    public List<RecoverySession> findSessions(String userId) {
        return sessionStore.values().stream()
                .filter(session -> session.userId().equals(userId))
                .toList();
    }

    private BusinessException invalidTransition(
            String sessionId,
            RecoverySessionStatus currentStatus,
            String targetStatus
    ) {
        return new BusinessException(
                ErrorCode.CONFLICT,
                Map.of(
                        "sessionId", sessionId,
                        "currentStatus", currentStatus.name(),
                        "targetStatus", targetStatus
                )
        );
    }
}
