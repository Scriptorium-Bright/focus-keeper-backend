package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.entity.FailureEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailureEventRepository extends JpaRepository<FailureEvent, String> {

    Optional<FailureEvent> findByIdAndUserId(String id, String userId);
}
