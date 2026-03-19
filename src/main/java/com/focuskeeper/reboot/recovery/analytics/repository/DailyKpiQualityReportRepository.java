package com.focuskeeper.reboot.recovery.analytics.repository;

import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiQualityReport;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyKpiQualityReportRepository extends JpaRepository<DailyKpiQualityReport, String> {

    Optional<DailyKpiQualityReport> findByUserIdAndMetricDate(String userId, LocalDate metricDate);
}
