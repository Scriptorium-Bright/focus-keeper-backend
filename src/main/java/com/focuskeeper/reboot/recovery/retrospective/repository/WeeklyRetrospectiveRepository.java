package com.focuskeeper.reboot.recovery.retrospective.repository;

import com.focuskeeper.reboot.recovery.retrospective.entity.WeeklyRetrospective;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeeklyRetrospectiveRepository extends JpaRepository<WeeklyRetrospective, String> {

    Optional<WeeklyRetrospective> findByUserIdAndWeekStart(String userId, LocalDate weekStart);
}
