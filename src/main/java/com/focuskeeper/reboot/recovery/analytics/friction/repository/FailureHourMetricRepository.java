package com.focuskeeper.reboot.recovery.analytics.friction.repository;

import com.focuskeeper.reboot.recovery.analytics.friction.entity.FailureHourMetric;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FailureHourMetricRepository extends JpaRepository<FailureHourMetric, String> {

    void deleteAllByUserIdAndMetricDate(String userId, LocalDate metricDate);

    List<FailureHourMetric> findAllByUserIdAndMetricDateOrderByLocalHourAsc(String userId, LocalDate metricDate);
}
