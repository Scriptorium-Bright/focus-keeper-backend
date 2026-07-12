package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.constant.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 실행 세션의 현재 상태와 이력을 사용자 경계 안에서 관리한다.
 */
public interface RecoverySessionRepository extends JpaRepository<RecoverySession, String> {

    boolean existsByUserIdAndStatus(String userId, RecoverySessionStatus status);

    Optional<RecoverySession> findByIdAndUserId(String id, String userId);

    Optional<RecoverySession> findByTimeboxIdAndUserIdAndStatus(
            String timeboxId,
            String userId,
            RecoverySessionStatus status
    );

    long countByUserIdAndStatus(String userId, RecoverySessionStatus status);

    List<RecoverySession> findAllByUserIdOrderByStartedAtAsc(String userId);
}
