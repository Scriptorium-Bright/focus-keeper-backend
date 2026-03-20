package com.focuskeeper.reboot.recovery.analytics.friction.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.analytics.friction.FrictionSegmentType;
import com.focuskeeper.reboot.recovery.analytics.friction.FrictionSignalType;
import com.focuskeeper.reboot.recovery.analytics.friction.dto.FrictionSegmentReportResponse;
import com.focuskeeper.reboot.recovery.analytics.friction.dto.FrictionSegmentResponse;
import com.focuskeeper.reboot.recovery.analytics.friction.entity.FailureHourReport;
import com.focuskeeper.reboot.recovery.analytics.friction.entity.RecoveryFrictionSignal;
import com.focuskeeper.reboot.recovery.analytics.friction.repository.FailureHourReportRepository;
import com.focuskeeper.reboot.recovery.analytics.friction.repository.RecoveryFrictionSignalRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FrictionSegmentQueryService {

    private final FailureHourReportRepository failureHourReportRepository;
    private final RecoveryFrictionSignalRepository recoveryFrictionSignalRepository;

    public FrictionSegmentQueryService(
            FailureHourReportRepository failureHourReportRepository,
            RecoveryFrictionSignalRepository recoveryFrictionSignalRepository
    ) {
        this.failureHourReportRepository = failureHourReportRepository;
        this.recoveryFrictionSignalRepository = recoveryFrictionSignalRepository;
    }

    /**
     * 이미 계산된 failure-hour report와 friction signal을 조합해 최소 세그먼트 리포트를 만든다.
     */
    public FrictionSegmentReportResponse get(String userId, LocalDate metricDate) {
        FailureHourReport failureHourReport = failureHourReportRepository.findByUserIdAndMetricDate(userId, metricDate)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of(
                                "userId", userId,
                                "metricDate", metricDate.toString(),
                                "reason", "failure hour report가 먼저 생성되어야 합니다."
                        )
                ));

        List<RecoveryFrictionSignal> signals = recoveryFrictionSignalRepository
                .findAllByUserIdAndMetricDateOrderBySignalTypeAsc(userId, metricDate);

        if (signals.isEmpty()) {
            throw new BusinessException(
                    ErrorCode.RESOURCE_NOT_FOUND,
                    Map.of(
                            "userId", userId,
                            "metricDate", metricDate.toString(),
                            "reason", "friction signal이 먼저 생성되어야 합니다."
                    )
            );
        }

        List<FrictionSegmentResponse> segments = new ArrayList<>();

        if (failureHourReport.getTotalFailureCount() > 0
                && failureHourReport.getPeakFailureHour() != null
                && failureHourReport.getPeakFailureHour() < 12) {
            segments.add(new FrictionSegmentResponse(
                    FrictionSegmentType.MORNING_SLIP.name(),
                    "오전 실패 집중",
                    "실패가 오전 시간대에 몰려 있습니다. 첫 복귀 블록을 더 작게 시작하는 편이 안전합니다.",
                    "peakFailureHour=%d, peakFailureWindow=%s".formatted(
                            failureHourReport.getPeakFailureHour(),
                            failureHourReport.getPeakFailureWindow()
                    )
            ));
        }

        findActiveSignal(signals, FrictionSignalType.TOO_BIG_REPEAT)
                .ifPresent(signal -> segments.add(new FrictionSegmentResponse(
                        FrictionSegmentType.OVERSIZED_TASK.name(),
                        "과한 작업 크기",
                        "일이 너무 커서 같은 유형의 실패가 반복되고 있습니다. 첫 블록을 더 작게 쪼개는 편이 낫습니다.",
                        "tooBigRepeatCount=%d".formatted(signal.getEvidenceCount())
                )));

        findActiveSignal(signals, FrictionSignalType.LATE_RESTART)
                .ifPresent(signal -> segments.add(new FrictionSegmentResponse(
                        FrictionSegmentType.LATE_RESTART.name(),
                        "늦은 재시작",
                        "실패 후 24시간 이상 지나서야 다시 시작하는 패턴이 보입니다. 같은 날 복귀 장치를 강화할 필요가 있습니다.",
                        "lateRestartCount=%d".formatted(signal.getEvidenceCount())
                )));

        return new FrictionSegmentReportResponse(userId, metricDate.toString(), segments);
    }

    private java.util.Optional<RecoveryFrictionSignal> findActiveSignal(
            List<RecoveryFrictionSignal> signals,
            FrictionSignalType type
    ) {
        return signals.stream()
                .filter(signal -> signal.getSignalType() == type && signal.isActive())
                .findFirst();
    }
}
