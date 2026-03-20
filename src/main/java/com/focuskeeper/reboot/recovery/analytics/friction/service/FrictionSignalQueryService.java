package com.focuskeeper.reboot.recovery.analytics.friction.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.analytics.friction.dto.FrictionSignalReportResponse;
import com.focuskeeper.reboot.recovery.analytics.friction.dto.FrictionSignalResponse;
import com.focuskeeper.reboot.recovery.analytics.friction.repository.RecoveryFrictionSignalRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FrictionSignalQueryService {

    private final RecoveryFrictionSignalRepository recoveryFrictionSignalRepository;

    public FrictionSignalQueryService(RecoveryFrictionSignalRepository recoveryFrictionSignalRepository) {
        this.recoveryFrictionSignalRepository = recoveryFrictionSignalRepository;
    }

    /**
     * 저장된 friction signal row들을 날짜 기준으로 묶어 리포트 응답으로 반환한다.
     */
    public FrictionSignalReportResponse get(String userId, LocalDate metricDate) {
        List<FrictionSignalResponse> signals = recoveryFrictionSignalRepository
                .findAllByUserIdAndMetricDateOrderBySignalTypeAsc(userId, metricDate).stream()
                .map(signal -> signal.toResponse())
                .toList();

        if (signals.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    Map.of(
                            "userId", userId,
                            "metricDate", metricDate.toString()
                    )
            );
        }

        return new FrictionSignalReportResponse(userId, metricDate.toString(), signals);
    }
}
