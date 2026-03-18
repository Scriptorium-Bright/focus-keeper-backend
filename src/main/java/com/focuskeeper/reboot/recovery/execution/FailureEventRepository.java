package com.focuskeeper.reboot.recovery.execution;

import org.springframework.data.jpa.repository.JpaRepository;

public interface FailureEventRepository extends JpaRepository<FailureEventEntity, String> {
}
