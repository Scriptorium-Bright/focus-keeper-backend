package com.focuskeeper.reboot.recovery.analytics.repository;

import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiMetric;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyKpiMetricRepository extends JpaRepository<DailyKpiMetric, String> {

    Optional<DailyKpiMetric> findByUserIdAndMetricDate(String userId, LocalDate metricDate);
}
