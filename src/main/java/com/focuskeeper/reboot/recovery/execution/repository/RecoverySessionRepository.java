package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySessionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoverySessionRepository extends JpaRepository<RecoverySessionEntity, String> {

    boolean existsByUserIdAndStatus(String userId, RecoverySessionStatus status);

    Optional<RecoverySessionEntity> findByIdAndUserId(String id, String userId);

    List<RecoverySessionEntity> findAllByUserIdOrderByStartedAtAsc(String userId);
}
