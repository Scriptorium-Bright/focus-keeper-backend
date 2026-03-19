package com.focuskeeper.reboot.recovery.execution.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.execution.FailureReason;
import com.focuskeeper.reboot.recovery.execution.dto.FailureEventResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RecoverySessionResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RestartSuggestionResponse;
import com.focuskeeper.reboot.recovery.execution.entity.FailureEvent;
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
    private final RestartSuggestionPolicy restartSuggestionPolicy;

    public FailureEventService(
            RecoverySessionService recoverySessionService,
            FailureEventRepository failureEventRepository,
            RestartSuggestionPolicy restartSuggestionPolicy
    ) {
        this.recoverySessionService = recoverySessionService;
        this.failureEventRepository = failureEventRepository;
        this.restartSuggestionPolicy = restartSuggestionPolicy;
    }

    @Transactional
    public FailureCheckInResult checkIn(String userId, String sessionId, String reasonValue, String note) {
        FailureReason reason = parseReason(reasonValue);
        RecoverySessionResponse interruptedSession = recoverySessionService.interruptSession(userId, sessionId);

        FailureEventResponse failureEvent = failureEventRepository.save(FailureEvent.create(
                userId,
                interruptedSession.sessionId(),
                interruptedSession.timeboxId(),
                reason,
                note,
                OffsetDateTime.now()
        )).toResponse();

        RestartSuggestionResponse restartSuggestion = restartSuggestionPolicy.suggest(reason);
        return new FailureCheckInResult(failureEvent, interruptedSession, restartSuggestion);
    }

    public FailureEventResponse getFailureEventOrThrow(String userId, String failureEventId) {
        return failureEventRepository.findByIdAndUserId(failureEventId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("failureEventId", failureEventId)
                ))
                .toResponse();
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
            RecoverySessionResponse recoverySession,
            RestartSuggestionResponse restartSuggestion
    ) {
    }
}
