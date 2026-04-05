package com.focuskeeper.reboot.recovery.analytics.repository;

import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiMetric;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * daily KPI mart 엔티티를 JPA로 조회/저장하는 기본 저장소다.
 *
 * 단건 조회와 활성 사용자 기반 조회처럼 ORM에 잘 맞는 읽기 패턴은 여기서 처리한다.
 */
public interface DailyKpiMetricRepository extends JpaRepository<DailyKpiMetric, String> {

    /**
     * 특정 사용자와 날짜에 해당하는 KPI mart 한 행을 찾는다.
     */
    Optional<DailyKpiMetric> findByUserIdAndMetricDate(String userId, LocalDate metricDate);

    /**
     * 기준일 이하에서 activation=true인 KPI 행을 사용자/날짜 순으로 읽어 후속 분석에 사용한다.
     */
    List<DailyKpiMetric> findAllByMetricDateLessThanEqualAndActivationIsTrueOrderByUserIdAscMetricDateAsc(LocalDate metricDate);
}
