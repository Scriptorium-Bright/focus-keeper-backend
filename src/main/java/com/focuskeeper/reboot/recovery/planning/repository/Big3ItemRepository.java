package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.entity.Big3Item;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Big3 선택 항목을 기준으로 하위 실행 단위를 만들 때 사용하는 저장소다.
 */
public interface Big3ItemRepository extends JpaRepository<Big3Item, String> {

    Optional<Big3Item> findByIdAndUserId(String id, String userId);

    @EntityGraph(attributePaths = "originInboxItem")
    List<Big3Item> findAllByUserIdAndWeekStartAndOriginInboxItem_IdIn(
            String userId,
            LocalDate weekStart,
            Collection<String> inboxItemIds
    );

    long countById(String id);

}
