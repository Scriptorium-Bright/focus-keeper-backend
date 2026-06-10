package com.focuskeeper.reboot.recovery.execution.service;

import com.focuskeeper.reboot.recovery.execution.RestartType;
import com.focuskeeper.reboot.recovery.execution.dto.FailureEventResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RecoverySessionResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RestartEventResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RestartSuggestionResponse;
import com.focuskeeper.reboot.recovery.execution.entity.RestartEvent;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import java.time.OffsetDateTime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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

        // 특정 Failure event 1건
        FailureEventResponse failureEvent = failureEventService.getFailureEvent(userId, failureEventId);
        RestartSuggestionResponse suggestion = restartSuggestionPolicy.suggest(failureEvent.reason());


        // T. FailureEventService의 checkIn과 뭔 차이가 있나 생각을 했었는데, 쟤는 Session을 멈추는 거고 얘는 새로 시작하는거구나 ..
        // A. 맞다. checkIn은 진행 중인 세션을 실패로 중단하고 failure event를 남기는 흐름이고,
        //    restart는 그 failure event를 기준으로 다시 시작할 새 세션과 restart event를 남기는 흐름이다.
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
        log.info("활성확인");

        return new RestartRecoveryResult(restartEvent, recoverySession, suggestion);
    }

    /**
     * 재시작 유스케이스가 만든 결과 묶음이다.
     * Q. 얘도 Result
     * A. 맞다. 외부 응답 DTO라기보다 restart 유스케이스 내부 결과 묶음이므로 Result라는 이름이 더 정확하다.
     *    컨트롤러가 이 Result를 받아 API Response DTO로 변환하면 계층 의미가 더 분명해진다.
     */
    public record RestartRecoveryResult(
            RestartEventResponse restartEvent,
            RecoverySessionResponse recoverySession,
            RestartSuggestionResponse restartSuggestion
    ) {
    }
}
