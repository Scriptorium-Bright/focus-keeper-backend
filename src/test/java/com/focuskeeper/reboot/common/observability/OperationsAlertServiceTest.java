package com.focuskeeper.reboot.common.observability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OperationsAlertServiceTest {

    private OperationsAlertService operationsAlertService;

    @BeforeEach
    void setUp() {
        operationsAlertService = new OperationsAlertService();
    }

    @Test
    void resolveWithoutActiveAlertDoesNothing() {
        operationsAlertService.resolveBatchFailure(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                "launch",
                "phase1-user",
                "resolved without prior active",
                Map.of("metricDate", "2026-05-08")
        );

        assertThat(operationsAlertService.getAlerts(false, null)).isEmpty();
        assertThat(operationsAlertService.getAlerts(true, null)).isEmpty();
    }

    @Test
    void repeatedActiveRefreshDoesNotIncreaseAlertRecordCount() {
        operationsAlertService.reportBatchFailure(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                "launch",
                "phase1-user",
                "first failure",
                Map.of("metricDate", "2026-05-08")
        );
        operationsAlertService.reportBatchFailure(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                "launch",
                "phase1-user",
                "same failure refreshed",
                Map.of("metricDate", "2026-05-08", "retry", "1")
        );

        assertThat(operationsAlertService.getAlerts(false, null)).hasSize(1);
        assertThat(operationsAlertService.getAlerts(true, null)).hasSize(1);
        assertThat(operationsAlertService.getAlerts(true, null).getFirst().summary())
                .isEqualTo("same failure refreshed");
    }

    @Test
    void activeAlertCanResolveAndBeReopenedWithSameIdentity() {
        operationsAlertService.reportBatchFailure(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                "launch",
                "phase1-user",
                "opened",
                Map.of("metricDate", "2026-05-08")
        );

        String alertKey = operationsAlertService.getAlerts(true, null).getFirst().alertKey();

        operationsAlertService.resolveBatchFailure(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                "launch",
                "phase1-user",
                "resolved",
                Map.of("metricDate", "2026-05-08")
        );

        assertThat(operationsAlertService.getAlerts(true, null)).isEmpty();
        assertThat(operationsAlertService.getAlerts(false, null)).hasSize(1);
        assertThat(operationsAlertService.getAlerts(false, null).getFirst().active()).isFalse();

        operationsAlertService.reportBatchFailure(
                OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                "launch",
                "phase1-user",
                "reopened",
                Map.of("metricDate", "2026-05-09")
        );

        assertThat(operationsAlertService.getAlerts(true, null)).hasSize(1);
        assertThat(operationsAlertService.getAlerts(true, null).getFirst().alertKey()).isEqualTo(alertKey);
        assertThat(operationsAlertService.getAlerts(true, null).getFirst().summary()).isEqualTo("reopened");
        assertThat(operationsAlertService.getAlerts(false, null)).hasSize(1);
    }
}
