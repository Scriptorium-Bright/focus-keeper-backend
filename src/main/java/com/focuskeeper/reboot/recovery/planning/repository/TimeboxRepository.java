package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeboxRepository extends JpaRepository<Timebox, String> {

    List<Timebox> findAllByUserIdOrderByStartAtAsc(String userId);

    List<Timebox> findAllByUserIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
            String userId,
            OffsetDateTime start,
            OffsetDateTime end
    );

    Optional<Timebox> findByIdAndUserId(String id, String userId);
}
