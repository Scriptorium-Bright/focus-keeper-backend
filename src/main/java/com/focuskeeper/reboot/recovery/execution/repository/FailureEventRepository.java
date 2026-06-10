package com.focuskeeper.reboot.recovery.execution.repository;

import com.focuskeeper.reboot.recovery.execution.entity.FailureEvent;
import com.focuskeeper.reboot.recovery.execution.FailureReason;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * failure event 원천 데이터를 읽고 집계하는 JPA 저장소다.
 *
 * 단순 CRUD뿐 아니라 analytics용 slice/projection 조회와 이유별 집계도 함께 제공한다.
 */
public interface FailureEventRepository extends JpaRepository<FailureEvent, String> {

    /**
     * 사용자 소유의 failure event 단건을 조회한다.
     */
    Optional<FailureEvent> findByIdAndUserId(String id, String userId);

    long countByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThan(
            String userId,
            OffsetDateTime start,
            OffsetDateTime end
    );

    @Query("""
            select count(distinct f.userId)
            from FailureEvent f
            where f.occurredAt >= :start
              and f.occurredAt < :end
            """)
    long countDistinctUsersOccurredBetween(
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            select f.id as failureEventId,
                   f.sessionId as sessionId,
                   f.timeboxId as timeboxId,
                   f.reason as reason,
                   f.occurredAt as occurredAt
            from FailureEvent f
            where f.userId = :userId
              and f.occurredAt >= :start
              and f.occurredAt < :end
            order by f.occurredAt asc
            """)
    List<FailureSlice> findSlicesByUserIdAndOccurredAtBetween(
            @Param("userId") String userId,
            @Param("start") OffsetDateTime start,
            @Param("end") OffsetDateTime end
    );

    @Query("""
            select f.id as failureEventId,
                   f.occurredAt as occurredAt
            from FailureEvent f
            where f.userId = :userId
              and f.id in :failureEventIds
            """)
    List<FailureReference> findReferencesByUserIdAndIdIn(
            @Param("userId") String userId,
            @Param("failureEventIds") Set<String> failureEventIds
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

    @Query("""
        select s.status
        from FailureEvent f
        JOIN RecoverySession s
        ON s.id = :sessionId
        WHERE s.status = "STARTED" AND f.userId = :userId
        """)
    boolean existsByStatusIsStarted(String sessionId, String userId);

    /**
     * 기간 내 가장 많이 발생한 failure reason을 반환한다.
     */
    default FailureReason findDominantReason(String userId, OffsetDateTime start, OffsetDateTime end) {
        return countReasons(userId, start, end).stream()
                .findFirst()
                .map(FailureReasonCount::getReason)
                .orElse(null);
    }

    /**
     * 이유별 실패 건수 집계를 위한 projection이다.
     */
    interface FailureReasonCount {
        FailureReason getReason();

        long getTotal();
    }

    /**
     * KPI/friction 계산에 필요한 최소 failure 필드만 담는 projection이다.
     */
    interface FailureSlice {
        String getFailureEventId();

        String getSessionId();

        String getTimeboxId();

        FailureReason getReason();

        OffsetDateTime getOccurredAt();
    }

    /**
     * restart quality 검증에서 사용하는 failure 발생 시각 참조 projection이다.
     */
    interface FailureReference {
        String getFailureEventId();

        OffsetDateTime getOccurredAt();
    }
}
