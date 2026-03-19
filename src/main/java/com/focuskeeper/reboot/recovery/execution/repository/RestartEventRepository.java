package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.entity.RestartEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestartEventRepository extends JpaRepository<RestartEventEntity, String> {
}
