package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.entity.FailureEvent;
import com.focuskeeper.reboot.recovery.execution.FailureReason;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FailureEventRepository extends JpaRepository<FailureEvent, String> {

    Optional<FailureEvent> findByIdAndUserId(String id, String userId);

    long countByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            String userId,
            OffsetDateTime start,
            OffsetDateTime end
    );

    @Query("""
            select f.reason as reason, count(f) as total
            from FailureEvent f
            where f.userId = :userId
              and f.occurredAt >= :start
              and f.occurredAt < :end
            group by f.reason
            order by count(f) desc
            """)
    List<FailureReasonCount> countReasons(
            @Param("userId") String userId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    default FailureReason findDominantReason(String userId, OffsetDateTime start, OffsetDateTime end) {
        return countReasons(userId, start, end).stream()
                .findFirst()
                .map(FailureReasonCount::getReason)
                .orElse(null);
    }

    interface FailureReasonCount {
        FailureReason getReason();

        long getTotal();
    }
}
