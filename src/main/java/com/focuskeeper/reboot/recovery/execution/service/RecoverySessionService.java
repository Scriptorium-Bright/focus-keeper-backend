package com.focuskeeper.reboot.recovery.execution.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.execution.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.dto.RecoverySessionResponse;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.planning.service.TimeboxService;
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
    public RecoverySessionResponse startSession(String userId, String timeboxId) {
        timeboxService.getTimebox(userId, timeboxId);

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
                RecoverySession.start(userId, timeboxId, OffsetDateTime.now())
        ).toResponse();
    }

    @Transactional
    public RecoverySessionResponse completeSession(String userId, String sessionId) {
        RecoverySession session = getSessionRecordOrThrow(userId, sessionId);
        if (session.getStatus() != RecoverySessionStatus.STARTED) {
            throw invalidTransition(sessionId, session.getStatus(), "COMPLETED");
        }

        session.complete(OffsetDateTime.now());
        return recoverySessionRepository.save(session).toResponse();
    }

    @Transactional
    public RecoverySessionResponse interruptSession(String userId, String sessionId) {
        RecoverySession session = getSessionRecordOrThrow(userId, sessionId);
        if (session.getStatus() != RecoverySessionStatus.STARTED) {
            throw invalidTransition(sessionId, session.getStatus(), "INTERRUPTED");
        }

        session.interrupt(OffsetDateTime.now());
        return recoverySessionRepository.save(session).toResponse();
    }

    public RecoverySessionResponse getSessionOrThrow(String userId, String sessionId) {
        return getSessionRecordOrThrow(userId, sessionId).toResponse();
    }

    public List<RecoverySessionResponse> findSessions(String userId) {
        return recoverySessionRepository.findAllByUserIdOrderByStartedAtAsc(userId).stream()
                .map(RecoverySession::toResponse)
                .toList();
    }

    private RecoverySession getSessionRecordOrThrow(String userId, String sessionId) {
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
