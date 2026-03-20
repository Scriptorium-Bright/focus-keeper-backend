package com.focuskeeper.reboot.recovery.analytics.friction.repository;

import com.focuskeeper.reboot.recovery.analytics.friction.FrictionSignalType;
import com.focuskeeper.reboot.recovery.analytics.friction.entity.RecoveryFrictionSignal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecoveryFrictionSignalRepository extends JpaRepository<RecoveryFrictionSignal, String> {

    Optional<RecoveryFrictionSignal> findByUserIdAndMetricDateAndSignalType(
            String userId,
            LocalDate metricDate,
            FrictionSignalType signalType
    );

    List<RecoveryFrictionSignal> findAllByUserIdAndMetricDateOrderBySignalTypeAsc(String userId, LocalDate metricDate);
}
