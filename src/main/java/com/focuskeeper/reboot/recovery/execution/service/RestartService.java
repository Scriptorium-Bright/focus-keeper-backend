package com.focuskeeper.reboot.recovery.execution.service;

import com.focuskeeper.reboot.recovery.execution.FailureReason;
import com.focuskeeper.reboot.recovery.execution.RestartType;
import com.focuskeeper.reboot.recovery.execution.dto.FailureEventResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RecoverySessionResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RestartEventResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RestartSuggestionResponse;
import com.focuskeeper.reboot.recovery.execution.entity.RestartEventEntity;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RestartService {

    private final FailureEventService failureEventService;
    private final RecoverySessionService recoverySessionService;
    private final RestartEventRepository restartEventRepository;
    private final RestartSuggestionPolicy restartSuggestionPolicy;

    public RestartService(
            FailureEventService failureEventService,
            RecoverySessionService recoverySessionService,
            RestartEventRepository restartEventRepository,
            RestartSuggestionPolicy restartSuggestionPolicy
    ) {
        this.failureEventService = failureEventService;
        this.recoverySessionService = recoverySessionService;
        this.restartEventRepository = restartEventRepository;
        this.restartSuggestionPolicy = restartSuggestionPolicy;
    }

    @Transactional
    public RestartRecoveryResult restart(String userId, String failureEventId) {
        FailureEventResponse failureEvent = failureEventService.getFailureEventOrThrow(userId, failureEventId);
        RestartSuggestionResponse suggestion = restartSuggestionPolicy.suggest(failureEvent.reason());
        RecoverySessionResponse recoverySession = recoverySessionService.startSession(userId, failureEvent.timeboxId());
        RestartEventResponse restartEvent = restartEventRepository.save(
                RestartEventEntity.create(
                        userId,
                        failureEvent.id(),
                        RestartType.TEN_MINUTE_RESTART,
                        suggestion.suggestedMinutes(),
                        OffsetDateTime.now()
                )
        ).toResponse();

        return new RestartRecoveryResult(restartEvent, recoverySession, suggestion);
    }

    public record RestartRecoveryResult(
            RestartEventResponse restartEvent,
            RecoverySessionResponse recoverySession,
            RestartSuggestionResponse restartSuggestion
    ) {
    }
}
