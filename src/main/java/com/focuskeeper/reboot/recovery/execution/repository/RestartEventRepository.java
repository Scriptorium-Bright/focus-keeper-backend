package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.entity.RestartEvent;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestartEventRepository extends JpaRepository<RestartEvent, String> {

    long countByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            String userId,
            OffsetDateTime start,
            OffsetDateTime end
    );

    @Query("""
            select r.failureEventId as failureEventId,
                   r.restartType as restartType,
                   r.occurredAt as occurredAt
            from RestartEvent r
            where r.userId = :userId
              and r.occurredAt >= :start
              and r.occurredAt < :end
            order by r.occurredAt asc
            """)
    List<RestartSlice> findSlicesByUserIdAndOccurredAtBetween(
            @Param("userId") String userId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    interface RestartSlice {
        String getFailureEventId();

        OffsetDateTime getOccurredAt();
    }
}
