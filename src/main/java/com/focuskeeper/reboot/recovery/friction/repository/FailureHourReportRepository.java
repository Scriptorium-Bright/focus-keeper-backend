package com.focuskeeper.reboot.recovery.friction.repository;

import com.focuskeeper.reboot.recovery.friction.entity.FailureHourReport;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 하루 실패 요약 리포트를 조회/저장하는 JPA 저장소다.
 */
public interface FailureHourReportRepository extends JpaRepository<FailureHourReport, String> {

    /**
     * 특정 사용자와 날짜에 대한 failure-hour 요약 리포트를 한 건 조회한다.
     */
    Optional<FailureHourReport> findByUserIdAndMetricDate(String userId, LocalDate metricDate);
}
