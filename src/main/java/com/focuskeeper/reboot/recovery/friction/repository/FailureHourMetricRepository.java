package com.focuskeeper.reboot.recovery.friction.repository;

import com.focuskeeper.reboot.recovery.friction.entity.FailureHourMetric;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 시간대별 실패 metric row를 조회/삭제하는 JPA 저장소다.
 */
public interface FailureHourMetricRepository extends JpaRepository<FailureHourMetric, String> {

    /**
     * 같은 날짜를 다시 생성하기 전에 기존 시간대별 metric row를 모두 비운다.
     */
    void deleteAllByUserIdAndMetricDate(String userId, LocalDate metricDate);

    /**
     * 한 날짜의 시간대별 metric row를 localHour 오름차순으로 읽어 리포트 응답에 사용한다.
     */
    List<FailureHourMetric> findAllByUserIdAndMetricDateOrderByLocalHourAsc(String userId, LocalDate metricDate);
}
