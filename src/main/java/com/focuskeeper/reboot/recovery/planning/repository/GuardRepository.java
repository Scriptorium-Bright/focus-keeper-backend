/*
package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.entity.TimeboxGuard;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface GuardRepository extends JpaRepository<TimeboxGuard, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TimeboxGuard> findByUserId(String userId);

}
*/
