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
/**
 * 복귀 세션의 실패 체크인 유스케이스를 담당하는 서비스다.
 *
 * 세션 중단, failure event 저장, 재시작 제안 생성이 항상 함께 움직여야 하므로
 * 컨트롤러가 아닌 이 서비스에서 하나의 유스케이스로 묶어 처리한다.
 */
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

    /**
     * 진행 중인 복귀 세션을 실패로 체크인하고, 실패 이벤트와 재시작 제안을 함께 만든다.
     *
     * 이 메소드는 단순 insert가 아니라 "세션을 끊고 왜 실패했는지 기록한 뒤,
     * 다음 행동을 바로 제안한다"는 recovery loop 전환 지점을 구현한다.
     */
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

    /**
     * 사용자 소유의 failure event를 한 건 조회한다.
     */
    public FailureEventResponse getFailureEvent(String userId, String failureEventId) {
        return failureEventRepository.findByIdAndUserId(failureEventId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("failureEventId", failureEventId)
                ))
                .toResponse();
    }


    /**
     * 외부 요청 문자열을 도메인 FailureReason enum으로 변환한다.
     */
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

    /**
     * 실패 체크인 유스케이스가 한 번에 만들어낸 결과 묶음이다.
     */
    public record FailureCheckInResult(
            FailureEventResponse failureEvent,
            RecoverySessionResponse recoverySession,
            RestartSuggestionResponse restartSuggestion
    ) {
    }
}
