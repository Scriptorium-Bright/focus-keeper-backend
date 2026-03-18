package com.focuskeeper.reboot.recovery.execution;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.planning.TimeboxService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecoverySessionService {

    private final TimeboxService timeboxService;
    private final RecoverySessionRepository recoverySessionRepository;

    public RecoverySessionService(
            TimeboxService timeboxService,
            RecoverySessionRepository recoverySessionRepository
    ) {
        this.timeboxService = timeboxService;
        this.recoverySessionRepository = recoverySessionRepository;
    }

    @Transactional
    public RecoverySessionDto startSession(String userId, String timeboxId) {
        timeboxService.getTimeboxOrThrow(userId, timeboxId);

        boolean hasActiveSession = recoverySessionRepository.existsByUserIdAndStatus(
                userId,
                RecoverySessionStatus.STARTED
        );
        if (hasActiveSession) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    Map.of("session", "이미 진행 중인 복귀 세션이 있습니다.")
            );
        }

        return recoverySessionRepository.save(
                RecoverySessionEntity.start(userId, timeboxId, OffsetDateTime.now())
        ).toDto();
    }

    @Transactional
    public RecoverySessionDto completeSession(String userId, String sessionId) {
        RecoverySessionEntity session = getSessionEntityOrThrow(userId, sessionId);
        if (session.getStatus() != RecoverySessionStatus.STARTED) {
            throw invalidTransition(sessionId, session.getStatus(), "COMPLETED");
        }

        session.complete(OffsetDateTime.now());
        return recoverySessionRepository.save(session).toDto();
    }

    @Transactional
    public RecoverySessionDto interruptSession(String userId, String sessionId) {
        RecoverySessionEntity session = getSessionEntityOrThrow(userId, sessionId);
        if (session.getStatus() != RecoverySessionStatus.STARTED) {
            throw invalidTransition(sessionId, session.getStatus(), "INTERRUPTED");
        }

        session.interrupt(OffsetDateTime.now());
        return recoverySessionRepository.save(session).toDto();
    }

    public RecoverySessionDto getSessionOrThrow(String userId, String sessionId) {
        return getSessionEntityOrThrow(userId, sessionId).toDto();
    }

    public List<RecoverySessionDto> findSessions(String userId) {
        return recoverySessionRepository.findAllByUserIdOrderByStartedAtAsc(userId).stream()
                .map(RecoverySessionEntity::toDto)
                .toList();
    }

    private RecoverySessionEntity getSessionEntityOrThrow(String userId, String sessionId) {
        return recoverySessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("sessionId", sessionId)
                ));
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
