package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.entity.FailureEvent;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 실행 실패 사건을 사용자 소유 범위에서 저장하고 조회한다.
 */
public interface FailureEventRepository extends JpaRepository<FailureEvent, String> {

    Optional<FailureEvent> findByIdAndUserId(String id, String userId);
}
