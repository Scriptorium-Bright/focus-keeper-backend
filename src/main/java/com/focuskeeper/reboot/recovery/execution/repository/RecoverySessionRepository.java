package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecoverySessionRepository extends JpaRepository<RecoverySession, String> {

    boolean existsByUserIdAndStatus(String userId, RecoverySessionStatus status);

    Optional<RecoverySession> findByIdAndUserId(String id, String userId);

    List<RecoverySession> findAllByUserIdOrderByStartedAtAsc(String userId);

    long countByUserIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            String userId,
            OffsetDateTime start,
            OffsetDateTime end
    );

    long countByUserIdAndStatusAndStartedAtGreaterThanEqualAndStartedAtLessThan(
            String userId,
            RecoverySessionStatus status,
            OffsetDateTime start,
            OffsetDateTime end
    );

    @Query("""
            select s.id as sessionId,
                   s.timeboxId as timeboxId,
                   s.status as status,
                   s.startedAt as startedAt,
                   s.endedAt as endedAt
            from RecoverySession s
            where s.userId = :userId
              and s.startedAt >= :start
              and s.startedAt < :end
            order by s.startedAt asc
            """)
    List<SessionSlice> findSlicesByUserIdAndStartedAtBetween(
            @Param("userId") String userId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    interface SessionSlice {
        String getSessionId();

        String getTimeboxId();

        RecoverySessionStatus getStatus();

        OffsetDateTime getStartedAt();

        OffsetDateTime getEndedAt();
    }
}
