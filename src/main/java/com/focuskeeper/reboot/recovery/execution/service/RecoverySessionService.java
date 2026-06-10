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
/**
 * 복귀 세션의 시작/완료/중단 상태 전이를 담당하는 서비스다.
 *
 * 여기서의 세션은 "특정 timebox를 실제로 수행한 실행 단위"를 의미하며,
 * 일반 타이머가 아니라 실패 후 다시 붙잡는 recovery 흐름의 실행 기록으로 본다.
 */
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

    /**
     * 특정 WORK timebox에 대해 새로운 복귀 세션을 시작한다.
     *
     * 한 사용자는 동시에 하나의 STARTED 세션만 가질 수 있게 막아,
     * 실행 단위와 KPI 집계 단위가 중복되지 않도록 한다.
     */
    @Transactional
    public RecoverySessionResponse startSession(String userId, String timeboxId) {
        timeboxService.getTimebox(userId, timeboxId);
        // T. Timebox에 대한 세션이 지금 활성상태인지 아닌지에 대해 확인하고, 아닐 경우 복귀? 라고 해야하나
        // A. 맞다. 다만 현재 로직은 특정 timebox별 활성 여부가 아니라 "사용자에게 진행 중인 복귀 세션이 하나라도 있는지"를 막는 전역 제약이다.
        //    한 사용자가 동시에 여러 복귀 세션을 열지 못하게 해서 실행 기록과 KPI 집계가 중복되는 것을 방지한다.
        boolean hasActiveSession = recoverySessionRepository.existsByUserIdAndStatus(
                userId,
                RecoverySessionStatus.STARTED
        );

        validateHasActiveSession(hasActiveSession);

        return recoverySessionRepository.save(
                RecoverySession.start(userId, timeboxId, OffsetDateTime.now())
        ).toResponse();
    }


    /**
     * 진행 중인 세션을 완료 상태로 전이한다.
     */
    @Transactional
    public RecoverySessionResponse completeSession(String userId, String sessionId) {
        RecoverySession session = requireSession(userId, sessionId);
        if (session.getStatus() != RecoverySessionStatus.STARTED) {
            throw invalidTransition(sessionId, session.getStatus(), "COMPLETED");
        }

        session.complete(OffsetDateTime.now());
        return recoverySessionRepository.save(session).toResponse();
    }

    /**
     *
     * 진행중인 세션에 대해, 타이머가 전부 흘러갔을 경우
     * @param userId
     * @param sessionId
     * @return
     */
    @Transactional
    public RecoverySessionResponse elapsedSession(String userId, String sessionId) {
        RecoverySession session = requireSession(userId, sessionId);
        if (session.getStatus() != RecoverySessionStatus.STARTED) {
            throw invalidTransition(sessionId, session.getStatus(), "ELAPSED");
        }
        session.elapsed(OffsetDateTime.now());
        return recoverySessionRepository.save(session).toResponse();
    }

    /**
     * 진행 중인 세션을 중단 상태로 전이한다.
     *
     * 현재는 failure check-in처럼 사용자가 명시적으로 실패를 확정했을 때 주로 호출되며,
     * 약한 이탈 신호를 자동 감지해 끊는 용도까지는 확장하지 않았다.
     */
    @Transactional
    public RecoverySessionResponse interruptSession(String userId, String sessionId) {
        RecoverySession session = requireSession(userId, sessionId);
        if (session.getStatus() != RecoverySessionStatus.STARTED) {
            throw invalidTransition(sessionId, session.getStatus(), "INTERRUPTED");
        }

        session.interrupt(OffsetDateTime.now());
        return recoverySessionRepository.save(session).toResponse();
    }

    /**
     *
     * Failure와의 차이, 중단 상태라는게, 실패를 확정하는게 아니라 잠깐 쉬어가는 그런 개념
     *
     * @param userId
     * @param sessionId
     * @return
     */
    @Transactional
    public RecoverySessionResponse stoppedSession(String userId, String sessionId) {
        RecoverySession session = requireSession(userId, sessionId);
        if (session.getStatus() != RecoverySessionStatus.STARTED) {
            throw invalidTransition(sessionId, session.getStatus(), "INTERRUPTED");
        }

        session.stopped(OffsetDateTime.now());
        return recoverySessionRepository.save(session).toResponse();
    }

    /**
     * 단일 복귀 세션을 조회한다.
     */
    public RecoverySessionResponse getSession(String userId, String sessionId) {
        return requireSession(userId, sessionId).toResponse();
    }

    /**
     * 사용자의 모든 복귀 세션을 시작 시각 순으로 조회한다.
     */
    public List<RecoverySessionResponse> findSessions(String userId) {
        return recoverySessionRepository.findAllByUserIdOrderByStartedAtAsc(userId).stream()
                .map(RecoverySession::toResponse)
                .toList();
    }

    /**
     * 사용자 소유의 세션을 강하게(require) 조회한다.
     *
     * 없으면 null을 반환하지 않고 즉시 예외를 던져, 이후 상태 전이 메소드가 전제조건을 단순하게 유지할 수 있게 한다.
     * Q. 강하게 조회한다라는 의미가 뭔지 ..?
     * A. "반드시 존재해야 하는 값"으로 조회한다는 뜻이다. Optional/null로 넘기지 않고 여기서 바로 예외를 던져,
     *    이후 complete/interrupt 같은 상태 전이 코드는 존재 여부 검사를 반복하지 않고 정상 세션만 다룰 수 있다.
     */
    private RecoverySession requireSession(String userId, String sessionId) {

        return recoverySessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("sessionId", sessionId)
                ));
    }


    private static void validateHasActiveSession(boolean hasActiveSession) {
        if (hasActiveSession) {
            throw new BusinessException(
                    ErrorCode.CONFLICT,
                    Map.of("session", "이미 진행 중인 복귀 세션이 있습니다.")
            );
        }
    }

    /**
     * 현재 상태에서 목표 상태로 갈 수 없는 경우 공통 충돌 예외를 만든다.
     */
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
