package com.focuskeeper.reboot.recovery.analytics.repository;

import com.focuskeeper.reboot.recovery.analytics.entity.DailyFunnelReport;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyFunnelReportRepository extends JpaRepository<DailyFunnelReport, String> {

    Optional<DailyFunnelReport> findByMetricDate(LocalDate metricDate);
}
