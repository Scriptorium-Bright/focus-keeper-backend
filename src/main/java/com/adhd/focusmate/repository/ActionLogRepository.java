package com.adhd.focusmate.repository;

import com.adhd.focusmate.domain.model.ActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ActionLogRepository extends JpaRepository<ActionLog, Long> {
}
