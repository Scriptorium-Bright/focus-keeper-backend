package com.focuskeeper.reboot.recovery.analytics.repository;

import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiLastProcessedDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyKpiLastProcessedDateRepository extends JpaRepository<DailyKpiLastProcessedDate, String> {

    Optional<DailyKpiLastProcessedDate> findByPipelineKeyAndUserId(String pipelineKey, String userId);
}
