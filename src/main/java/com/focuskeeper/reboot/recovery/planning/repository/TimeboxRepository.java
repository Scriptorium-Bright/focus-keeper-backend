package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.entity.TimeboxEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeboxRepository extends JpaRepository<TimeboxEntity, String> {

    List<TimeboxEntity> findAllByUserIdOrderByStartAtAsc(String userId);

    Optional<TimeboxEntity> findByIdAndUserId(String id, String userId);
}
