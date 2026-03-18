package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.entity.FailureEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailureEventRepository extends JpaRepository<FailureEventEntity, String> {
}
