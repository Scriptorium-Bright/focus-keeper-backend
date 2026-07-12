package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.entity.RestartEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 실패 사건별 재시작 기록을 저장한다.
 */
public interface RestartEventRepository extends JpaRepository<RestartEvent, String> {

    long countByFailureEventId(String failureEventId);
}
