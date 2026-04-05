package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.entity.Big3Selection;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 날짜별 Big3 selection을 조회/저장하는 JPA 저장소다.
 */
public interface Big3SelectionRepository extends JpaRepository<Big3Selection, String> {

    /**
     * 선택 항목과 원본 inbox item까지 함께 eager하게 읽어온다.
     */
    @EntityGraph(attributePaths = {"selectedItems", "selectedItems.inboxItem"})
    Optional<Big3Selection> findByUserIdAndSelectedDate(String userId, LocalDate selectedDate);

    /**
     * 특정 날짜에 Big3를 설정한 사용자 수를 센다.
     */
    long countBySelectedDate(LocalDate selectedDate);
}
