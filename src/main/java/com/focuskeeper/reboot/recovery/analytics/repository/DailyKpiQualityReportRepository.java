package com.focuskeeper.reboot.recovery.analytics.repository;

import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiQualityReport;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * KPI 품질 리포트 엔티티의 조회/저장을 담당하는 기본 JPA 저장소다.
 */
public interface DailyKpiQualityReportRepository extends JpaRepository<DailyKpiQualityReport, String> {

    /**
     * 특정 사용자와 날짜에 대해 생성된 품질 리포트를 한 건 조회한다.
     */
    Optional<DailyKpiQualityReport> findByUserIdAndMetricDate(String userId, LocalDate metricDate);
}
