package com.focuskeeper.reboot.common.observability;

import com.focuskeeper.reboot.common.observability.dto.OperationsAlertResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Observability 계층의 "상태 판단기" 역할을 한다.
 *
 * 메트릭을 직접 기록하는 대신, 배치 실패/DQ/lastProcessedDate 상태를 alert 단위로 해석해
 * 현재 활성 경보 목록을 유지한다. 즉 숫자 신호를 운영자가 바로 볼 수 있는
 * "위험 상태"로 승격하는 책임을 가진다.
 */
@Service
public class OperationsAlertService {

    private static final Logger log = LoggerFactory.getLogger(OperationsAlertService.class);
    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(9);

    private final ConcurrentMap<String, OperationsAlertState> alerts = new ConcurrentHashMap<>();

    /**
     * 특정 배치 stage 실패를 활성 alert로 등록한다.
     *
     * 같은 pipeline/stage/user 조합은 동일한 논리 문제로 보고 같은 key에 덮어쓴다.
     * 따라서 이 메서드는 "새 레코드를 누적"하기보다 "현재 운영 상태를 최신값으로 반영"하는 역할을 한다.
     */
    public void reportBatchFailure(
            String pipelineKey,
            String stage,
            String userId,
            String summary,
            Map<String, String> details
    ) {
        upsert(
                batchFailureKey(pipelineKey, stage, userId),
                pipelineKey,
                stage,
                userId,
                OperationsAlertSeverity.CRITICAL,
                true,
                summary,
                details
        );
    }

    /**
     * 배치 실패가 해소됐음을 같은 alert key에 resolve 상태로 반영한다.
     *
     * 별도 삭제 대신 active=false 상태로 업데이트해서, 장애가 있었다가 해소된 흐름을
     * 같은 alert identity 안에서 볼 수 있게 한다.
     */
    public void resolveBatchFailure(
            String pipelineKey,
            String stage,
            String userId,
            String summary,
            Map<String, String> details
    ) {
        upsert(
                batchFailureKey(pipelineKey, stage, userId),
                pipelineKey,
                stage,
                userId,
                OperationsAlertSeverity.CRITICAL,
                false,
                summary,
                details
        );
    }

    /**
     * DQ 결과를 해석해 품질 alert를 활성 또는 해제한다.
     *
     * totalIssueCount가 0보다 크면 현재 품질 이상이 남아 있다고 보고 warning을 올리고,
     * 0이면 재처리나 원천 수정 후 문제가 해소됐다고 보고 resolve 처리한다.
     */
    public void evaluateQuality(String pipelineKey, String userId, LocalDate metricDate, int totalIssueCount) {
        String alertKey = "dq:" + pipelineKey + ":" + userId;
        if (totalIssueCount > 0) {
            upsert(
                    alertKey,
                    pipelineKey,
                    "quality",
                    userId,
                    OperationsAlertSeverity.WARNING,
                    true,
                    "DQ issue detected for daily KPI quality report.",
                    Map.of(
                            "metricDate", metricDate.toString(),
                            "totalIssueCount", Integer.toString(totalIssueCount)
                    )
            );
            return;
        }

        upsert(
                alertKey,
                pipelineKey,
                "quality",
                userId,
                OperationsAlertSeverity.WARNING,
                false,
                "DQ issue resolved for daily KPI quality report.",
                Map.of("metricDate", metricDate.toString())
        );
    }

    /**
     * 마지막 처리 날짜와 현재 날짜 차이를 기준으로 freshness 경보를 판단한다.
     *
     * 이 프로젝트에서 processing lag는 "지표가 얼마나 최신 상태를 따라가고 있는지"를 뜻한다.
     * 값이 커지면 batch는 돌아도 실제 운영 데이터는 stale할 수 있으므로 별도 경보가 필요하다.
     */
    public void evaluateProcessingLag(String pipelineKey, String userId, LocalDate lastProcessedDate) {
        LocalDate today = LocalDate.now(DEFAULT_OFFSET);
        long lagDays = Math.max(ChronoUnit.DAYS.between(lastProcessedDate, today), 0);
        String alertKey = "processing_lag:" + pipelineKey + ":" + userId;

        if (lagDays > 1) {
            OperationsAlertSeverity severity = lagDays > 2
                    ? OperationsAlertSeverity.CRITICAL
                    : OperationsAlertSeverity.WARNING;
            upsert(
                    alertKey,
                    pipelineKey,
                    "processing_lag",
                    userId,
                    severity,
                    true,
                    "Processing lag exceeded the rough Phase 14 threshold.",
                    Map.of(
                            "lastProcessedDate", lastProcessedDate.toString(),
                            "lagDays", Long.toString(lagDays)
                    )
            );
            return;
        }

        upsert(
                alertKey,
                pipelineKey,
                "processing_lag",
                userId,
                OperationsAlertSeverity.WARNING,
                false,
                "Processing lag returned to the acceptable rough threshold.",
                Map.of(
                        "lastProcessedDate", lastProcessedDate.toString(),
                        "lagDays", Long.toString(lagDays)
                )
        );
    }

    /**
     * 현재 보관 중인 alert를 조건에 맞게 조회한다.
     *
     * activeOnly=true면 현재 살아 있는 경보만 반환하고, userId가 있으면 특정 사용자 범위로 좁힌다.
     * overview API와 drill 검증이 모두 이 메서드를 통해 현재 운영 상태를 본다.
     */
    public List<OperationsAlertResponse> getAlerts(boolean activeOnly, String userId) {
        return alerts.values().stream()
                .filter(alert -> !activeOnly || alert.isActive())
                .filter(alert -> userId == null || userId.isBlank() || userId.equals(alert.userId()))
                .sorted(Comparator.comparing(OperationsAlertState::lastChangedAt).reversed())
                .map(OperationsAlertState::toResponse)
                .toList();
    }

    /**
     * in-memory alert 상태를 전부 초기화한다.
     *
     * 현재 alert 저장소는 영속 저장이 아니라 메모리 기반이므로, 이 메서드는 주로
     * 테스트/드릴/로컬 검증 전 상태 정리에 쓰인다.
     */
    public void clearAll() {
        alerts.clear();
    }

    /**
     * alert key 기준으로 현재 경보 상태를 저장하고 로그로도 남긴다.
     *
     * active 상태는 warn, resolve 상태는 info 레벨로 남겨서
     * 대시보드 외에도 애플리케이션 로그에서 상태 변화를 추적할 수 있게 한다.
     */
    private void upsert(
            String alertKey,
            String pipelineKey,
            String stage,
            String userId,
            OperationsAlertSeverity severity,
            boolean active,
            String summary,
            Map<String, String> details
    ) {
        OffsetDateTime now = OffsetDateTime.now();
        AlertMutation mutation = mutateAlert(
                alertKey,
                pipelineKey,
                stage,
                userId,
                severity,
                active,
                summary,
                details,
                now
        );
        if (!mutation.changed()) {
            return;
        }

        if (mutation.state().isActive()) {
            log.warn(
                    "ops alert active pipeline={} stage={} userId={} severity={} summary={} details={}",
                    pipelineKey,
                    stage,
                    userId,
                    severity.name(),
                    summary,
                    details
            );
        } else {
            log.info(
                    "ops alert resolved pipeline={} stage={} userId={} severity={} summary={} details={}",
                    pipelineKey,
                    stage,
                    userId,
                    severity.name(),
                    summary,
                    details
            );
        }
    }

    private AlertMutation mutateAlert(
            String alertKey,
            String pipelineKey,
            String stage,
            String userId,
            OperationsAlertSeverity severity,
            boolean active,
            String summary,
            Map<String, String> details,
            OffsetDateTime now
    ) {
        List<AlertMutation> holder = new ArrayList<>(1);
        alerts.compute(alertKey, (key, existing) -> {
            if (existing == null) {
                if (!active) {
                    holder.add(AlertMutation.noop());
                    return null;
                }

                OperationsAlertState opened = OperationsAlertState.opened(
                        alertKey,
                        pipelineKey,
                        stage,
                        userId,
                        severity,
                        summary,
                        details,
                        now
                );
                holder.add(AlertMutation.changed(opened));
                return opened;
            }

            if (active) {
                OperationsAlertState next = existing.isActive()
                        ? existing.refreshActive(severity, summary, details, now)
                        : existing.reopen(severity, summary, details, now);
                holder.add(AlertMutation.changed(next));
                return next;
            }

            if (!existing.isActive()) {
                holder.add(AlertMutation.noop());
                return existing;
            }

            OperationsAlertState resolved = existing.resolve(severity, summary, details, now);
            holder.add(AlertMutation.changed(resolved));
            return resolved;
        });

        return holder.isEmpty() ? AlertMutation.noop() : holder.getFirst();
    }

    /**
     * batch failure alert의 논리 키를 만든다.
     *
     * pipeline/stage/user 조합이 같으면 같은 종류의 운영 문제로 보고 하나의 alert identity로 묶는다.
     */
    private String batchFailureKey(String pipelineKey, String stage, String userId) {
        return "batch_failure:" + pipelineKey + ":" + stage + ":" + userId;
    }

    private enum OperationsAlertStatus {
        ACTIVE,
        RESOLVED
    }

    private record AlertMutation(boolean changed, OperationsAlertState state) {

        private static AlertMutation changed(OperationsAlertState state) {
            return new AlertMutation(true, state);
        }

        private static AlertMutation noop() {
            return new AlertMutation(false, null);
        }
    }

    private record OperationsAlertState(
            String alertKey,
            String pipelineKey,
            String stage,
            String userId,
            OperationsAlertSeverity severity,
            OperationsAlertStatus status,
            String summary,
            Map<String, String> details,
            OffsetDateTime firstSeenAt,
            OffsetDateTime lastSeenAt,
            OffsetDateTime resolvedAt,
            int occurrenceCount,
            int reopenCount,
            OffsetDateTime lastChangedAt
    ) {

        private static OperationsAlertState opened(
                String alertKey,
                String pipelineKey,
                String stage,
                String userId,
                OperationsAlertSeverity severity,
                String summary,
                Map<String, String> details,
                OffsetDateTime now
        ) {
            return new OperationsAlertState(
                    alertKey,
                    pipelineKey,
                    stage,
                    userId,
                    severity,
                    OperationsAlertStatus.ACTIVE,
                    summary,
                    Map.copyOf(details),
                    now,
                    now,
                    null,
                    1,
                    0,
                    now
            );
        }

        private boolean isActive() {
            return status == OperationsAlertStatus.ACTIVE;
        }

        private OperationsAlertState refreshActive(
                OperationsAlertSeverity severity,
                String summary,
                Map<String, String> details,
                OffsetDateTime now
        ) {
            return new OperationsAlertState(
                    alertKey,
                    pipelineKey,
                    stage,
                    userId,
                    severity,
                    OperationsAlertStatus.ACTIVE,
                    summary,
                    Map.copyOf(details),
                    firstSeenAt,
                    now,
                    null,
                    occurrenceCount + 1,
                    reopenCount,
                    now
            );
        }

        private OperationsAlertState resolve(
                OperationsAlertSeverity severity,
                String summary,
                Map<String, String> details,
                OffsetDateTime now
        ) {
            return new OperationsAlertState(
                    alertKey,
                    pipelineKey,
                    stage,
                    userId,
                    severity,
                    OperationsAlertStatus.RESOLVED,
                    summary,
                    Map.copyOf(details),
                    firstSeenAt,
                    now,
                    now,
                    occurrenceCount,
                    reopenCount,
                    now
            );
        }

        private OperationsAlertState reopen(
                OperationsAlertSeverity severity,
                String summary,
                Map<String, String> details,
                OffsetDateTime now
        ) {
            return new OperationsAlertState(
                    alertKey,
                    pipelineKey,
                    stage,
                    userId,
                    severity,
                    OperationsAlertStatus.ACTIVE,
                    summary,
                    Map.copyOf(details),
                    firstSeenAt,
                    now,
                    null,
                    occurrenceCount + 1,
                    reopenCount + 1,
                    now
            );
        }

        private OperationsAlertResponse toResponse() {
            return new OperationsAlertResponse(
                    alertKey,
                    pipelineKey,
                    stage,
                    userId,
                    severity.name(),
                    isActive(),
                    summary,
                    details,
                    lastChangedAt.toString()
            );
        }
    }
}
