package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoverySessionRepository extends JpaRepository<RecoverySession, String> {

    boolean existsByUserIdAndStatus(String userId, RecoverySessionStatus status);

    Optional<RecoverySession> findByIdAndUserId(String id, String userId);

    List<RecoverySession> findAllByUserIdOrderByStartedAtAsc(String userId);
}
