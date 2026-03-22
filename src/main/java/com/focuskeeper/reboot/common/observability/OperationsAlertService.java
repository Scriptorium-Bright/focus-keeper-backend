package com.focuskeeper.reboot.common.observability;

import com.focuskeeper.reboot.common.observability.dto.OperationsAlertResponse;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OperationsAlertService {

    private static final Logger log = LoggerFactory.getLogger(OperationsAlertService.class);
    private static final ZoneOffset DEFAULT_OFFSET = ZoneOffset.ofHours(9);

    private final ConcurrentMap<String, OperationsAlertResponse> alerts = new ConcurrentHashMap<>();

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

    public void evaluateWatermarkLag(String pipelineKey, String userId, LocalDate lastProcessedDate) {
        LocalDate today = LocalDate.now(DEFAULT_OFFSET);
        long lagDays = Math.max(ChronoUnit.DAYS.between(lastProcessedDate, today), 0);
        String alertKey = "watermark_lag:" + pipelineKey + ":" + userId;

        if (lagDays > 1) {
            OperationsAlertSeverity severity = lagDays > 2
                    ? OperationsAlertSeverity.CRITICAL
                    : OperationsAlertSeverity.WARNING;
            upsert(
                    alertKey,
                    pipelineKey,
                    "watermark",
                    userId,
                    severity,
                    true,
                    "Watermark lag exceeded the rough Phase 14 threshold.",
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
                "watermark",
                userId,
                OperationsAlertSeverity.WARNING,
                false,
                "Watermark lag returned to the acceptable rough threshold.",
                Map.of(
                        "lastProcessedDate", lastProcessedDate.toString(),
                        "lagDays", Long.toString(lagDays)
                )
        );
    }

    public List<OperationsAlertResponse> getAlerts(boolean activeOnly, String userId) {
        return alerts.values().stream()
                .filter(alert -> !activeOnly || alert.active())
                .filter(alert -> userId == null || userId.isBlank() || userId.equals(alert.userId()))
                .sorted(Comparator.comparing(OperationsAlertResponse::lastChangedAt).reversed())
                .toList();
    }

    public void clearAll() {
        alerts.clear();
    }

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
        OperationsAlertResponse updated = new OperationsAlertResponse(
                alertKey,
                pipelineKey,
                stage,
                userId,
                severity.name(),
                active,
                summary,
                Map.copyOf(details),
                OffsetDateTime.now().toString()
        );
        alerts.put(alertKey, updated);

        if (active) {
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

    private String batchFailureKey(String pipelineKey, String stage, String userId) {
        return "batch_failure:" + pipelineKey + ":" + stage + ":" + userId;
    }
}
