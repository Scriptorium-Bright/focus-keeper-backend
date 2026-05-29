package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.entity.ExecutionUnit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Big3 하위 실행 단위를 저장하고 사용자 소유 범위로 조회하는 저장소다.
 */
public interface ExecutionUnitRepository extends JpaRepository<ExecutionUnit, String> {

    List<ExecutionUnit> findAllByBig3SelectionItem_IdInOrderByCreatedAtAsc(Collection<String> big3SelectionItemIds);

    List<ExecutionUnit> findAllByIdInAndBig3SelectionItem_Selection_UserId(
            Collection<String> ids,
            String userId
    );

    Optional<ExecutionUnit> findByIdAndBig3SelectionItem_Selection_UserId(String id, String userId);

    List<ExecutionUnit> findAllByBig3SelectionItem_IdAndBig3SelectionItem_Selection_UserIdOrderByCreatedAtAsc(
            String big3SelectionItemId,
            String userId
    );
}
