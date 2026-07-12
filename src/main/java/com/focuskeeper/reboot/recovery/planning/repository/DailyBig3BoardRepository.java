package com.focuskeeper.reboot.recovery.planning.repository;

import com.focuskeeper.reboot.recovery.planning.entity.DailyBig3Board;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 날짜별 Daily Big3 보드를 조회/저장하는 JPA 저장소다.
 */
public interface DailyBig3BoardRepository extends JpaRepository<DailyBig3Board, String> {

    Optional<DailyBig3Board> findByUserIdAndSelectedDate(String userId, LocalDate selectedDate);
}
