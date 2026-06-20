package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.constant.TimeboxType;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * recovery timebox 일정 데이터를 읽고 집계하는 JPA 저장소다.
 */
public interface TimeboxRepository extends JpaRepository<Timebox, String> {


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
        select t
        from Timebox t
        where t.userId = :userId
          and t.status = com.focuskeeper.reboot.recovery.planning.constant.TimeboxStatus.PLANNED
          and t.startAt < :newEnd
          and t.endAt > :newStart
        """)
    List<Timebox> findOverlappingForUpdate(
            @Param("userId") String userId,
            @Param("newStart") OffsetDateTime newStart,
            @Param("newEnd") OffsetDateTime newEnd
    );

    /**
     * 특정 구간 안에 들어오는 사용자의 timebox를 시작 시각 순으로 조회한다.
     */
    List<Timebox> findAllByUserIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
            String userId,
            OffsetDateTime start,
            OffsetDateTime end
    );

    List<Timebox> findAllByIdInAndUserId(Collection<String> ids, String userId);

    /**
     * 특정 유형의 timebox를 배정한 사용자 수를 기간 단위로 센다.
     */
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

    /**
     * 사용자 소유의 단일 timebox를 조회한다.
     */
    Optional<Timebox> findByIdAndUserId(String id, String userId);

}
