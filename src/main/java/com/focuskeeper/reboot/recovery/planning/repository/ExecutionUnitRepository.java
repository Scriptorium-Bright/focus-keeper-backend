package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.ExecutionUnitStatus;
import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

/**
 * Big3 하위 실행 단위를 저장하고 사용자 소유 범위로 조회하는 저장소다.
 */
public interface ExecutionUnitRepository extends JpaRepository<ExecutionUnit, String> {

    List<ExecutionUnit> findAllByBig3Item_IdInOrderByCreatedAtAsc(Collection<String> big3ItemIds);

    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    List<ExecutionUnit> findAllByIdInAndBig3Item_UserId(
            Collection<String> ids,
            String userId
    );

    Optional<ExecutionUnit> findByIdAndBig3Item_UserId(String id, String userId);

    // big3Item의 ExecutionUnits
    List<ExecutionUnit> findAllByBig3Item_IdAndBig3Item_UserIdOrderByCreatedAtAsc(
            String big3ItemId,
            String userId
    );

    @Modifying // ??
    @Query("""
    UPDATE ExecutionUnit e
    SET e.status = :newStatus , e.completedAt = :now
    WHERE e.status = :beforeStatus AND e.id = :id
    """)
    int updateExeucutionUnitStatus(ExecutionUnitStatus newStatus, OffsetDateTime now, ExecutionUnitStatus beforeStatus, String id);

}
