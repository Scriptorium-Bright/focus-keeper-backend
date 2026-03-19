package com.focuskeeper.reboot.recovery.analytics.repository;

import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiWatermark;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyKpiWatermarkRepository extends JpaRepository<DailyKpiWatermark, String> {

    Optional<DailyKpiWatermark> findByPipelineKeyAndUserId(String pipelineKey, String userId);
}
