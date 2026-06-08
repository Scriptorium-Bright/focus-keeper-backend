package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.RecoverySessionStatus;
import com.focuskeeper.reboot.recovery.execution.entity.RecoverySession;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 복귀 세션 실행 기록을 읽고 집계하는 JPA 저장소다.
 */
public interface RecoverySessionRepository extends JpaRepository<RecoverySession, String> {

    /**
     * 사용자가 현재 특정 상태의 세션을 이미 가지고 있는지 확인한다.
     */
    boolean existsByUserIdAndStatus(String userId, RecoverySessionStatus status);

    Optional<RecoverySession> findByIdAndUserId(String id, String userId);

    Optional<RecoverySession> findByTimeboxIdAndUserIdAndStatus(
            String timeboxId,
            String userId,
            RecoverySessionStatus status
    );

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
            select count(distinct s.userId)
            from RecoverySession s
            where s.startedAt >= :start
              and s.startedAt < :end
            """)
    long countDistinctUsersStartedBetween(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
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

    /**
     * analytics 계산에 필요한 최소 세션 필드만 담는 projection이다.
     */
    interface SessionSlice {
        String getSessionId();

        String getTimeboxId();

        RecoverySessionStatus getStatus();

        OffsetDateTime getStartedAt();

        OffsetDateTime getEndedAt();
    }
}
