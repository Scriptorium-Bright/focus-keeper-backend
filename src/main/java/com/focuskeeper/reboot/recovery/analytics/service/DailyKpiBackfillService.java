package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.common.observability.OperationsMetricRecorder;
import com.focuskeeper.reboot.common.observability.OperationsPipelineKeys;
import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.recovery.analytics.dto.BackfillDailyKpiResponse;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository.FailureReference;
import com.focuskeeper.reboot.recovery.execution.repository.FailureEventRepository.FailureSlice;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RecoverySessionRepository.SessionSlice;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository;
import com.focuskeeper.reboot.recovery.execution.repository.RestartEventRepository.RestartSlice;
import com.focuskeeper.reboot.recovery.planning.TimeboxType;
import com.focuskeeper.reboot.recovery.planning.entity.Timebox;
import com.focuskeeper.reboot.recovery.planning.repository.TimeboxRepository;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DailyKpiBackfillService {

    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(9);

    private final DailyKpiPipelineService dailyKpiPipelineService;
    private final DailyKpiQualityService dailyKpiQualityService;
    private final DailyKpiLastProcessedDateService dailyKpiLastProcessedDateService;
    private final RecoverySessionRepository recoverySessionRepository;
    private final FailureEventRepository failureEventRepository;
    private final RestartEventRepository restartEventRepository;
    private final TimeboxRepository timeboxRepository;
    private final OperationsMetricRecorder operationsMetricRecorder;

    public DailyKpiBackfillService(
            DailyKpiPipelineService dailyKpiPipelineService,
            DailyKpiQualityService dailyKpiQualityService,
            DailyKpiLastProcessedDateService dailyKpiLastProcessedDateService,
            RecoverySessionRepository recoverySessionRepository,
            FailureEventRepository failureEventRepository,
            RestartEventRepository restartEventRepository,
            TimeboxRepository timeboxRepository,
            OperationsMetricRecorder operationsMetricRecorder
    ) {
        this.dailyKpiPipelineService = dailyKpiPipelineService;
        this.dailyKpiQualityService = dailyKpiQualityService;
        this.dailyKpiLastProcessedDateService = dailyKpiLastProcessedDateService;
        this.recoverySessionRepository = recoverySessionRepository;
        this.failureEventRepository = failureEventRepository;
        this.restartEventRepository = restartEventRepository;
        this.timeboxRepository = timeboxRepository;
        this.operationsMetricRecorder = operationsMetricRecorder;
    }

    /**
     * 지정한 날짜 구간을 하루씩 다시 계산해 KPI mart를 재생성하고, 처리 결과와 최신 lastProcessedDate를 반환한다.
     */
    public BackfillDailyKpiResponse backfill(String userId, LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("dateRange", "endDate는 startDate보다 빠를 수 없습니다.")
            );
        }

        Timer.Sample sample = operationsMetricRecorder.startSample();
        try {
            List<String> processedMetricDates = new ArrayList<>();
            OffsetDateTime generatedAt = OffsetDateTime.now();
            OffsetDateTime periodStart = startDate.atStartOfDay().atOffset(DEFAULT_OFFSET);
            OffsetDateTime periodEndExclusive = endDate.plusDays(1).atStartOfDay().atOffset(DEFAULT_OFFSET);
            OffsetDateTime restartEndExclusive = periodEndExclusive.plusHours(48);

            List<SessionSlice> sessions = recoverySessionRepository.findSlicesByUserIdAndStartedAtBetween(
                    userId,
                    periodStart,
                    periodEndExclusive
            );
            List<FailureSlice> failures = failureEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                    userId,
                    periodStart,
                    periodEndExclusive
            );
            List<RestartSlice> restarts = restartEventRepository.findSlicesByUserIdAndOccurredAtBetween(
                    userId,
                    periodStart,
                    restartEndExclusive
            );
            List<Timebox> timeboxes = timeboxRepository.findAllByUserIdAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                    userId,
                    periodStart,
                    periodEndExclusive
            );

            Map<LocalDate, List<SessionSlice>> sessionsByDate = sessions.stream()
                    .collect(Collectors.groupingBy(session -> normalizeMetricDate(session.getStartedAt())));
            Map<LocalDate, List<FailureSlice>> failuresByDate = failures.stream()
                    .collect(Collectors.groupingBy(failure -> normalizeMetricDate(failure.getOccurredAt())));
            Map<LocalDate, List<RestartSlice>> restartsByDate = restarts.stream()
                    .collect(Collectors.groupingBy(restart -> normalizeMetricDate(restart.getOccurredAt())));
            Map<String, List<RestartSlice>> restartByFailureEventId = restarts.stream()
                    .collect(Collectors.groupingBy(RestartSlice::getFailureEventId));
            Map<LocalDate, List<Timebox>> workTimeboxesByDate = timeboxes.stream()
                    .filter(timebox -> timebox.getType() == TimeboxType.WORK)
                    .collect(Collectors.groupingBy(timebox -> normalizeMetricDate(timebox.getStartAt())));
            Map<String, Timebox> timeboxesById = timeboxes.stream()
                    .collect(Collectors.toMap(Timebox::getId, Function.identity()));
            Map<String, OffsetDateTime> failureOccurredAtById = loadFailureOccurredAtById(userId, restarts);

            LocalDate current = startDate;
            while (!current.isAfter(endDate)) {
                List<SessionSlice> dailySessions = sessionsByDate.getOrDefault(current, List.of());
                List<FailureSlice> dailyFailures = failuresByDate.getOrDefault(current, List.of());
                List<RestartSlice> dailyRestarts = restartsByDate.getOrDefault(current, List.of());

                dailyKpiPipelineService.generateMetric(
                        userId,
                        current,
                        dailySessions,
                        dailyFailures,
                        restartByFailureEventId,
                        workTimeboxesByDate.getOrDefault(current, List.of()),
                        generatedAt
                );
                dailyKpiQualityService.generateFromSlices(
                        userId,
                        current,
                        generatedAt,
                        dailySessions,
                        dailyFailures,
                        dailyRestarts,
                        loadDailyTimeboxes(dailySessions, timeboxesById),
                        failureOccurredAtById
                );
                processedMetricDates.add(current.toString());
                current = current.plusDays(1);
            }

            // backfill은 날짜 구간 전체를 한 번의 재처리 작업으로 보므로, lastProcessedDate는 마지막 날짜 기준으로 한 번만 전진시킨다.
            dailyKpiLastProcessedDateService.advance(userId, endDate, generatedAt);

            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.BACKFILL_REPROCESS,
                    "backfill",
                    "success"
            );
            operationsMetricRecorder.recordBackfillProcessedDays(
                    OperationsPipelineKeys.BACKFILL_REPROCESS,
                    processedMetricDates.size()
            );

            return new BackfillDailyKpiResponse(
                    userId,
                    startDate.toString(),
                    endDate.toString(),
                    processedMetricDates.size(),
                    processedMetricDates,
                    dailyKpiLastProcessedDateService.get(userId)
            );
        } catch (RuntimeException exception) {
            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.BACKFILL_REPROCESS,
                    "backfill",
                    "failure"
            );
            throw exception;
        }
    }

    private Map<String, OffsetDateTime> loadFailureOccurredAtById(String userId, List<RestartSlice> restarts) {
        Set<String> failureEventIds = restarts.stream()
                .map(RestartSlice::getFailureEventId)
                .collect(Collectors.toSet());
        if (failureEventIds.isEmpty()) {
            return Map.of();
        }

        return failureEventRepository.findReferencesByUserIdAndIdIn(userId, failureEventIds).stream()
                .collect(Collectors.toMap(
                        FailureReference::getFailureEventId,
                        FailureReference::getOccurredAt
                ));
    }

    private Map<String, Timebox> loadDailyTimeboxes(
            List<SessionSlice> sessions,
            Map<String, Timebox> timeboxesById
    ) {
        if (sessions.isEmpty()) {
            return Map.of();
        }

        return sessions.stream()
                .map(SessionSlice::getTimeboxId)
                .distinct()
                .map(timeboxesById::get)
                .filter(timebox -> timebox != null)
                .collect(Collectors.toMap(Timebox::getId, Function.identity()));
    }

    private LocalDate normalizeMetricDate(OffsetDateTime timestamp) {
        return timestamp.withOffsetSameInstant(DEFAULT_OFFSET).toLocalDate();
    }
}
