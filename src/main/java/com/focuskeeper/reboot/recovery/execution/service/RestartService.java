package com.focuskeeper.reboot.recovery.execution.service;

import com.focuskeeper.reboot.recovery.execution.RestartType;
import com.focuskeeper.reboot.recovery.execution.dto.FailureEventResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RecoverySessionResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RestartEventResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RestartSuggestionResponse;
import com.focuskeeper.reboot.recovery.execution.entity.RestartEvent;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
/**
 * 실패 이벤트를 기준으로 10분 재시작을 실행하는 유스케이스 서비스다.
 *
 * failure event 조회, 재시작 제안 확인, 새 세션 시작, restart event 저장을 하나의 흐름으로 묶는다.
 */
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

    /**
     * 특정 failure event를 기준으로 새 복귀 세션을 시작하고 restart event를 남긴다.
     */
    @Transactional
    public RestartRecoveryResult restart(String userId, String failureEventId) {
        FailureEventResponse failureEvent = failureEventService.getFailureEvent(userId, failureEventId);
        RestartSuggestionResponse suggestion = restartSuggestionPolicy.suggest(failureEvent.reason());
        RecoverySessionResponse recoverySession = recoverySessionService.startSession(userId, failureEvent.timeboxId());
        RestartEventResponse restartEvent = restartEventRepository.save(
                RestartEvent.create(
                        userId,
                        failureEvent.id(),
                        RestartType.TEN_MINUTE_RESTART,
                        suggestion.suggestedMinutes(),
                        OffsetDateTime.now()
                )
        ).toResponse();

        return new RestartRecoveryResult(restartEvent, recoverySession, suggestion);
    }

    /**
     * 재시작 유스케이스가 만든 결과 묶음이다.
     */
    public record RestartRecoveryResult(
            RestartEventResponse restartEvent,
            RecoverySessionResponse recoverySession,
            RestartSuggestionResponse restartSuggestion
    ) {
    }
}
