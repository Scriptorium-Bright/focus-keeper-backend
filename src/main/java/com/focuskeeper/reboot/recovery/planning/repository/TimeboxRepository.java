package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TimeboxRepository extends JpaRepository<Timebox, String> {

    List<Timebox> findAllByUserIdOrderByStartAtAsc(String userId);

    List<Timebox> findAllByUserIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
            String userId,
            OffsetDateTime start,
            OffsetDateTime end
    );

    @Query("""
            select count(distinct t.userId)
            from Timebox t
            where t.type = :type
              and t.startAt >= :start
              and t.startAt < :end
            """)
    long countDistinctUsersByTypeAndStartAtBetween(
            @Param("type") TimeboxType type,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    Optional<Timebox> findByIdAndUserId(String id, String userId);
}
