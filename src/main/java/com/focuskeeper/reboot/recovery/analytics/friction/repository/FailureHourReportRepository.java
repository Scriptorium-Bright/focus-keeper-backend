package com.focuskeeper.reboot.recovery.analytics.friction.repository;

import com.focuskeeper.reboot.recovery.analytics.friction.entity.FailureHourReport;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailureHourReportRepository extends JpaRepository<FailureHourReport, String> {

    Optional<FailureHourReport> findByUserIdAndMetricDate(String userId, LocalDate metricDate);
}
