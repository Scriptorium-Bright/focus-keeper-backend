package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.entity.FailureEventEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailureEventRepository extends JpaRepository<FailureEventEntity, String> {

    Optional<FailureEventEntity> findByIdAndUserId(String id, String userId);
}
