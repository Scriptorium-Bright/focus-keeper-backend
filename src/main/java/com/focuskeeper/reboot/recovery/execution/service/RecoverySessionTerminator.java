package com.focuskeeper.reboot.recovery.execution.service;

import com.focuskeeper.reboot.recovery.execution.constant.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.planning.port.ActiveSessionTerminator;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

/**
 * planning이 execution 저장 구조를 알지 않도록 활성 session 종료 port를 구현한다.
 */
@Component
public class RecoverySessionTerminator implements ActiveSessionTerminator {

    private final RecoverySessionRepository recoverySessionRepository;

    public RecoverySessionTerminator(RecoverySessionRepository recoverySessionRepository) {
        this.recoverySessionRepository = recoverySessionRepository;
    }

    @Override
    public void completeIfActive(String timeboxId, String userId, OffsetDateTime completedAt) {
        recoverySessionRepository.findByTimeboxIdAndUserIdAndStatus(
                timeboxId,
                userId,
                RecoverySessionStatus.STARTED
        ).ifPresent(session -> session.complete(completedAt));
    }
}
