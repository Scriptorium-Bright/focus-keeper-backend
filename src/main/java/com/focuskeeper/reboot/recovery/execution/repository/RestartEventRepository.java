package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.entity.RestartEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestartEventRepository extends JpaRepository<RestartEvent, String> {
}
