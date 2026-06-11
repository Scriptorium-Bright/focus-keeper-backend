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
    // high
    public FailureCheckInResult checkIn(String userId, String sessionId, String reasonValue, String note) {
        // Q. 애초에 외부 문자열이 아닌 Enum Type으로 Request를 받을 수는 없나? (드롭다운같은걸로)
        // A. 가능하다. Spring은 @RequestBody 안의 enum 필드도 매핑할 수 있어서 Request DTO를 FailureReason으로 받을 수 있다.
        //    다만 지금처럼 String으로 받은 뒤 parseReason에서 변환하면, 허용 alias/대소문자 처리와 에러 메시지를 서비스가 직접 통제할 수 있다.
        FailureReason reason = parseReason(reasonValue);

        // 세션을 실패로 체크인
        RecoverySessionResponse interruptedSession = recoverySessionService.interruptSession(userId, sessionId);

        // 실패한 이벤트에 대해 저장
        FailureEventResponse failureEvent = failureEventRepository.save(FailureEvent.create(
                userId,
                interruptedSession.sessionId(),
                interruptedSession.timeboxId(),
                reason,
                note,
                OffsetDateTime.now()
        )).toResponse();

        // 재시작 제안을 위한 메시지 등
        RestartSuggestionResponse restartSuggestion = restartSuggestionPolicy.suggest(reason);
        // 종합해서 제안
        return new FailureCheckInResult(failureEvent, interruptedSession, restartSuggestion);
    }

    /**
     * 사용자 소유의 failure event를 한 건 조회한다.
     * Q. 개인적으로 생각이 드는게, 이걸 RestartService에서 쓰는거면, 차라리 Repository를 받아서 하는게 낫지 않나 하는 Service를 받는건 좀 무겁지 않나
     * A. 단순 조회만 보면 Repository를 직접 받아도 된다. 다만 "사용자 소유 failure event인지 검증해서 가져온다"는 유스케이스 규칙을
     *    FailureEventService에 모아두면 RestartService가 저장 구조를 몰라도 되고, 조회 정책 변경도 한 곳에서 처리할 수 있다.
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
     * Q. 얘도 DTO에 Response / Request / Result 이렇게 패키지 나눠서 하는 것도 ㅇㅇ..
     * A. 규모가 더 커지면 나눌 만하다. 지금은 내부 서비스 반환용 Result라 service 안에 둬도 괜찮지만,
     *    컨트롤러/서비스 간 결과 타입이 늘어나면 dto/request, dto/response, dto/result처럼 분리하는 편이 더 선명하다.
     * Q2. 뭔가 Response들 보면, Result를 위한 Response가 있는거같은데 이거도 살짝 애매하다고 봐서
     * A2. 맞다. Response는 API 출력 모델이고 Result는 유스케이스 결과 모델이라 목적이 다르다.
     *     현재는 필드가 같아서 재사용하고 있지만, 서비스 내부 의미가 커지면 별도 Result 전용 타입으로 분리하는 게 더 깔끔하다.
     */
    public record FailureCheckInResult(
            FailureEventResponse failureEvent,
            RecoverySessionResponse recoverySession,
            RestartSuggestionResponse restartSuggestion
    ) {
    }
}
