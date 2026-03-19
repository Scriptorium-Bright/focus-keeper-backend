package com.focuskeeper.reboot.recovery.analytics.repository;

import com.focuskeeper.reboot.recovery.analytics.entity.CohortRetentionReport;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CohortRetentionReportRepository extends JpaRepository<CohortRetentionReport, String> {

    Optional<CohortRetentionReport> findByCohortDate(LocalDate cohortDate);
}
