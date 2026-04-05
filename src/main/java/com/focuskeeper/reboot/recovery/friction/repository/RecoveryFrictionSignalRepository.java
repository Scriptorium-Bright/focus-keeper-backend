package com.focuskeeper.reboot.recovery.friction.repository;

import com.focuskeeper.reboot.recovery.friction.FrictionSignalType;
import com.focuskeeper.reboot.recovery.friction.entity.RecoveryFrictionSignal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 날짜별 friction signal row를 조회/저장하는 JPA 저장소다.
 */
public interface RecoveryFrictionSignalRepository extends JpaRepository<RecoveryFrictionSignal, String> {

    /**
     * 특정 날짜와 signal type에 해당하는 단일 friction signal row를 조회한다.
     */
    Optional<RecoveryFrictionSignal> findByUserIdAndMetricDateAndSignalType(
            String userId,
            LocalDate metricDate,
            FrictionSignalType signalType
    );

    /**
     * 특정 날짜에 저장된 모든 friction signal을 signal type 순으로 조회한다.
     */
    List<RecoveryFrictionSignal> findAllByUserIdAndMetricDateOrderBySignalTypeAsc(String userId, LocalDate metricDate);
}
