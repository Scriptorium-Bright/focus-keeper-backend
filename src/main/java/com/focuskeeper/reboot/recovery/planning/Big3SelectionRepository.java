package com.focuskeeper.reboot.recovery.planning;

import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface Big3SelectionRepository extends JpaRepository<Big3SelectionEntity, String> {

    @EntityGraph(attributePaths = {"selectedItems", "selectedItems.inboxItem"})
    Optional<Big3SelectionEntity> findByUserIdAndSelectedDate(String userId, LocalDate selectedDate);
}
