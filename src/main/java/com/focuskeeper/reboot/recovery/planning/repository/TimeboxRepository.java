package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeboxRepository extends JpaRepository<Timebox, String> {

    List<Timebox> findAllByUserIdOrderByStartAtAsc(String userId);

    Optional<Timebox> findByIdAndUserId(String id, String userId);
}
