package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.analytics.dto.CohortRetentionResponse;
import com.focuskeeper.reboot.recovery.analytics.entity.CohortRetentionReport;
import com.focuskeeper.reboot.recovery.analytics.repository.CohortRetentionReportRepository;
import com.focuskeeper.reboot.recovery.analytics.repository.DailyKpiMetricRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CohortRetentionService {

    private final DailyKpiMetricRepository dailyKpiMetricRepository;
    private final CohortRetentionReportRepository cohortRetentionReportRepository;

    public CohortRetentionService(
            DailyKpiMetricRepository dailyKpiMetricRepository,
            CohortRetentionReportRepository cohortRetentionReportRepository
    ) {
        this.dailyKpiMetricRepository = dailyKpiMetricRepository;
        this.cohortRetentionReportRepository = cohortRetentionReportRepository;
    }

    @Transactional
    public CohortRetentionResponse generate(LocalDate cohortDate) {
        List<com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiMetric> activationMetrics =
                dailyKpiMetricRepository.findAllByMetricDateLessThanEqualAndActivationIsTrueOrderByUserIdAscMetricDateAsc(
                        cohortDate.plusDays(30)
                );

        Map<String, List<com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiMetric>> metricsByUser =
                activationMetrics.stream().collect(Collectors.groupingBy(
                        com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiMetric::getUserId,
                        Collectors.toList()
                ));

        Set<String> cohortUsers = metricsByUser.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty())
                .filter(entry -> entry.getValue().getFirst().getMetricDate().equals(cohortDate))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        int cohortSize = cohortUsers.size();
        int retainedDay1Users = retainedUsers(metricsByUser, cohortUsers, cohortDate.plusDays(1));
        int retainedDay7Users = retainedUsers(metricsByUser, cohortUsers, cohortDate.plusDays(7));
        int retainedDay30Users = retainedUsers(metricsByUser, cohortUsers, cohortDate.plusDays(30));

        BigDecimal day1Rate = ratio(retainedDay1Users, cohortSize);
        BigDecimal day7Rate = ratio(retainedDay7Users, cohortSize);
        BigDecimal day30Rate = ratio(retainedDay30Users, cohortSize);

        OffsetDateTime generatedAt = OffsetDateTime.now();
        CohortRetentionReport report = cohortRetentionReportRepository.findByCohortDate(cohortDate)
                .map(existing -> {
                    existing.regenerate(
                            cohortSize,
                            retainedDay1Users,
                            retainedDay7Users,
                            retainedDay30Users,
                            day1Rate,
                            day7Rate,
                            day30Rate,
                            generatedAt
                    );
                    return existing;
                })
                .orElseGet(() -> CohortRetentionReport.create(
                        cohortDate,
                        cohortSize,
                        retainedDay1Users,
                        retainedDay7Users,
                        retainedDay30Users,
                        day1Rate,
                        day7Rate,
                        day30Rate,
                        generatedAt
                ));

        return cohortRetentionReportRepository.save(report).toResponse();
    }

    public CohortRetentionResponse get(LocalDate cohortDate) {
        return cohortRetentionReportRepository.findByCohortDate(cohortDate)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RESOURCE_NOT_FOUND,
                        Map.of("cohortDate", cohortDate.toString())
                ))
                .toResponse();
    }

    private int retainedUsers(
            Map<String, List<com.focuskeeper.reboot.recovery.analytics.entity.DailyKpiMetric>> metricsByUser,
            Set<String> cohortUsers,
            LocalDate targetDate
    ) {
        return (int) cohortUsers.stream()
                .filter(userId -> metricsByUser.getOrDefault(userId, List.of()).stream()
                        .anyMatch(metric -> metric.getMetricDate().equals(targetDate) && metric.isActivation()))
                .count();
    }

    private BigDecimal ratio(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }
}
