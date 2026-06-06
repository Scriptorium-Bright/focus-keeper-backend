package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.Big3ItemStatus;
import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * Big3 선택 항목을 기준으로 하위 실행 단위를 만들 때 사용하는 저장소다.
 */
public interface Big3ItemRepository extends JpaRepository<Big3Item, String> {

    Optional<Big3Item> findByIdAndUserId(String id, String userId);

    List<Big3Item> findAllByIdInAndUserId(Collection<String> ids, String userId);


    // what is that?
    @EntityGraph(attributePaths = "originInboxItem")
    List<Big3Item> findAllByUserIdAndWeekStartAndOriginInboxItem_IdIn(
            String userId,
            LocalDate weekStart,
            Collection<String> inboxItemIds
    );

    long countById(String id);

    @Query("""
    select b
    from Big3Item b
    where b.userId = :userId AND b.status IN :statuses AND b.weekStart < :weekStart
        """)
    List<Big3Item> findPastUnfinishedItems(
            String userId,
            Collection<Big3ItemStatus> statuses,
            LocalDate weekStart
    );

}
