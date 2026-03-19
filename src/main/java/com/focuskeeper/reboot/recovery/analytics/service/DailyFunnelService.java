package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyFunnelResponse;
import com.focuskeeper.reboot.recovery.analytics.entity.DailyFunnelReport;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyFunnelReportRepository;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import com.focuskeeper.reboot.recovery.inbox.repository.InboxItemRepository;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.repository.Big3SelectionRepository;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DailyFunnelService {

    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(9);

    private final InboxItemRepository inboxItemRepository;
    private final Big3SelectionRepository big3SelectionRepository;
    private final TimeboxRepository timeboxRepository;
    private final RecoverySessionRepository recoverySessionRepository;
    private final FailureEventRepository failureEventRepository;
    private final RestartEventRepository restartEventRepository;
    private final DailyFunnelReportRepository dailyFunnelReportRepository;

    public DailyFunnelService(
            InboxItemRepository inboxItemRepository,
            Big3SelectionRepository big3SelectionRepository,
            TimeboxRepository timeboxRepository,
            RecoverySessionRepository recoverySessionRepository,
            FailureEventRepository failureEventRepository,
            RestartEventRepository restartEventRepository,
            DailyFunnelReportRepository dailyFunnelReportRepository
    ) {
        this.inboxItemRepository = inboxItemRepository;
        this.big3SelectionRepository = big3SelectionRepository;
        this.timeboxRepository = timeboxRepository;
        this.recoverySessionRepository = recoverySessionRepository;
        this.failureEventRepository = failureEventRepository;
        this.restartEventRepository = restartEventRepository;
        this.dailyFunnelReportRepository = dailyFunnelReportRepository;
    }

    @Transactional
    public DailyFunnelResponse generate(LocalDate metricDate) {
        OffsetDateTime periodStart = metricDate.atStartOfDay().atOffset(DEFAULT_OFFSET);
        OffsetDateTime periodEndExclusive = metricDate.plusDays(1).atStartOfDay().atOffset(DEFAULT_OFFSET);

        long brainDumpUsers = inboxItemRepository.countDistinctUsersCreatedBetween(periodStart, periodEndExclusive);
        long big3Users = big3SelectionRepository.countBySelectedDate(metricDate);
        long timeboxUsers = timeboxRepository.countDistinctUsersByTypeAndStartAtBetween(
                TimeboxType.WORK,
                periodStart,
                periodEndExclusive
        );
        long sessionStartedUsers = recoverySessionRepository.countDistinctUsersStartedBetween(periodStart, periodEndExclusive);
        long failureUsers = failureEventRepository.countDistinctUsersOccurredBetween(periodStart, periodEndExclusive);
        long restartUsers = restartEventRepository.countDistinctUsersOccurredBetween(periodStart, periodEndExclusive);

        BigDecimal big3SelectionRate = ratio(big3Users, brainDumpUsers);
        BigDecimal timeboxPlanningRate = ratio(timeboxUsers, big3Users);
        BigDecimal sessionStartRate = ratio(sessionStartedUsers, timeboxUsers);
        BigDecimal failureRate = ratio(failureUsers, sessionStartedUsers);
        BigDecimal restartRate = ratio(restartUsers, failureUsers);

        OffsetDateTime generatedAt = OffsetDateTime.now();
        DailyFunnelReport report = dailyFunnelReportRepository.findByMetricDate(metricDate)
                .map(existing -> {
                    existing.regenerate(
                            brainDumpUsers,
                            big3Users,
                            timeboxUsers,
                            sessionStartedUsers,
                            failureUsers,
                            restartUsers,
                            big3SelectionRate,
                            timeboxPlanningRate,
                            sessionStartRate,
                            failureRate,
                            restartRate,
                            generatedAt
                    );
                    return existing;
                })
                .orElseGet(() -> DailyFunnelReport.create(
                        metricDate,
                        brainDumpUsers,
                        big3Users,
                        timeboxUsers,
                        sessionStartedUsers,
                        failureUsers,
                        restartUsers,
                        big3SelectionRate,
                        timeboxPlanningRate,
                        sessionStartRate,
                        failureRate,
                        restartRate,
                        generatedAt
                ));

        return dailyFunnelReportRepository.save(report).toResponse();
    }

    public DailyFunnelResponse get(LocalDate metricDate) {
        return dailyFunnelReportRepository.findByMetricDate(metricDate)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("metricDate", metricDate.toString())
                ))
                .toResponse();
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator == 0L) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }
}
