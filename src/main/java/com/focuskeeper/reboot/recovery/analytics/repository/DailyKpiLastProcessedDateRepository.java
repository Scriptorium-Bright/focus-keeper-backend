package com.focuskeeper.reboot.recovery.analytics.repository;

import com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiLastProcessedDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 파이프라인별 lastProcessedDate 상태를 읽고 저장하는 JPA 저장소다.
 */
public interface DailyKpiLastProcessedDateRepository extends JpaRepository<DailyKpiLastProcessedDate, String> {

    /**
     * 특정 파이프라인 키와 사용자 조합에 대한 마지막 처리 날짜 레코드를 조회한다.
     */
    Optional<DailyKpiLastProcessedDate> findByPipelineKeyAndUserId(String pipelineKey, String userId);
}
