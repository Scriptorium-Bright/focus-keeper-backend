package com.focuskeeper.reboot.recovery.friction.service;

import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository.FailureSlice;
import com.focuskeeper.reboot.recovery.friction.entity.FailureHourMetric;
import com.focuskeeper.reboot.recovery.friction.entity.FailureHourReport;
import com.focuskeeper.reboot.recovery.friction.repository.FailureHourMetricRepository;
import com.focuskeeper.reboot.recovery.friction.repository.FailureHourReportRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
/**
 * 하루치 failure event를 시간대별로 요약해 failure-hour report를 만드는 서비스다.
 *
 * 실패가 어느 시간대에 몰렸는지와 peak hour가 언제인지 계산해,
 * 후속 friction 해석 단계가 raw event 없이도 사용할 수 있는 중간 산출물을 만든다.
 */
public class FailureHourAnalyticsService {

    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(9);

    private final FailureEventRepository failureEventRepository;
    private final FailureHourMetricRepository failureHourMetricRepository;
    private final FailureHourReportRepository failureHourReportRepository;

    public FailureHourAnalyticsService(
            FailureEventRepository failureEventRepository,
            FailureHourMetricRepository failureHourMetricRepository,
            FailureHourReportRepository failureHourReportRepository
    ) {
        this.failureEventRepository = failureEventRepository;
        this.failureHourMetricRepository = failureHourMetricRepository;
        this.failureHourReportRepository = failureHourReportRepository;
    }

    /**
     * 지정한 날짜의 실패 이벤트를 시간대별로 집계하고 peak hour/window를 포함한 보고서를 저장한다.
     */
    // high
    public FailureHourReport generate(String userId, LocalDate metricDate) {
        OffsetDateTime periodStart = metricDate.atStartOfDay().atOffset(DEFAULT_OFFSET);
        OffsetDateTime periodEndExclusive = metricDate.plusDays(1).atStartOfDay().atOffset(DEFAULT_OFFSET);

        List<FailureSlice> failures = failureEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                userId,
                periodStart,
                periodEndExclusive
        );

        Map<Integer, Long> failureCountByHour = failures.stream()
                .collect(Collectors.groupingBy(
                        failure -> failure.getOccurredAt().getHour(),
                        Collectors.counting()
                ));

        int totalFailureCount = failures.size();
        Integer peakFailureHour = failureCountByHour.entrySet().stream()
                .max(Comparator.<Map.Entry<Integer, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(entry -> -entry.getKey()))
                .map(Map.Entry::getKey)
                .orElse(null);
        String peakFailureWindow = peakFailureHour == null ? null : toFailureWindow(peakFailureHour);
        OffsetDateTime generatedAt = OffsetDateTime.now();

        failureHourMetricRepository.deleteAllByUserIdAndMetricDate(userId, metricDate);

        List<FailureHourMetric> metrics = failureCountByHour.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> FailureHourMetric.create(
                        userId,
                        metricDate,
                        entry.getKey(),
                        entry.getValue().intValue(),
                        ratio(entry.getValue().intValue(), totalFailureCount),
                        peakFailureHour != null && peakFailureHour.equals(entry.getKey()),
                        generatedAt
                ))
                .toList();
        failureHourMetricRepository.saveAll(metrics);

        FailureHourReport report = failureHourReportRepository.findByUserIdAndMetricDate(userId, metricDate)
                .map(existing -> {
                    existing.regenerate(
                            totalFailureCount,
                            peakFailureHour,
                            peakFailureWindow,
                            generatedAt
                    );
                    return existing;
                })
                .orElseGet(() -> FailureHourReport.create(
                        userId,
                        metricDate,
                        totalFailureCount,
                        peakFailureHour,
                        peakFailureWindow,
                        generatedAt
                ));

        return failureHourReportRepository.save(report);
    }

    /**
     * 전체 실패 건수 대비 특정 시간대 실패 비중을 소수점 넷째 자리까지 계산한다.
     */
    private BigDecimal ratio(int numerator, int denominator) {
        if (denominator == 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    /**
     * 개별 시(hour)를 사용자 해석용 넓은 시간대 window 문자열로 변환한다.
     */
    private String toFailureWindow(int localHour) {
        if (localHour < 6) {
            return "00-06";
        }
        if (localHour < 9) {
            return "06-09";
        }
        if (localHour < 12) {
            return "09-12";
        }
        if (localHour < 15) {
            return "12-15";
        }
        if (localHour < 18) {
            return "15-18";
        }
        if (localHour < 21) {
            return "18-21";
        }
        return "21-24";
    }
}
