package com.focuskeeper.reboot.common.observability;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.common.observability.dto.BatchOverviewResponse;
import com.focuskeeper.reboot.common.observability.dto.OperationsAlertResponse;
import com.focuskeeper.reboot.common.observability.dto.RecoveryLoopOverviewResponse;
import com.focuskeeper.reboot.common.observability.dto.RunbookScenarioResponse;
import com.focuskeeper.reboot.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ops")
@Tag(name = "Ops", description = "Operational dashboards, alerts, and runbook surfaces")
public class OperationsController {

    private final OperationsOverviewService operationsOverviewService;
    private final OperationsAlertService operationsAlertService;
    private final OperationsRunbookCatalogService operationsRunbookCatalogService;

    public OperationsController(
            OperationsOverviewService operationsOverviewService,
            OperationsAlertService operationsAlertService,
            OperationsRunbookCatalogService operationsRunbookCatalogService
    ) {
        this.operationsOverviewService = operationsOverviewService;
        this.operationsAlertService = operationsAlertService;
        this.operationsRunbookCatalogService = operationsRunbookCatalogService;
    }

    @GetMapping("/overview/recovery-loop")
    @Operation(summary = "Get recovery loop overview", description = "Returns the rough recovery loop dashboard snapshot for Phase 14.")
    public ApiResponse<RecoveryLoopOverviewResponse> getRecoveryLoopOverview(
            @RequestParam String userId,
            @RequestParam String metricDate
    ) {
        RecoveryLoopOverviewResponse response = operationsOverviewService.getRecoveryLoopOverview(
                userId,
                parseDate("metricDate", metricDate)
        );
        return ApiResponse.success(response, "OPS_RECOVERY_LOOP_OVERVIEW_FETCHED");
    }

    @GetMapping("/overview/batch")
    @Operation(summary = "Get batch overview", description = "Returns the rough batch, DQ, and lastProcessedDate dashboard snapshot for Phase 14.")
    public ApiResponse<BatchOverviewResponse> getBatchOverview(
            @RequestParam String userId,
            @RequestParam String metricDate
    ) {
        BatchOverviewResponse response = operationsOverviewService.getBatchOverview(
                userId,
                parseDate("metricDate", metricDate)
        );
        return ApiResponse.success(response, "OPS_BATCH_OVERVIEW_FETCHED");
    }

    @GetMapping("/alerts")
    @Operation(summary = "Get operations alerts", description = "Returns rough in-memory alert states for Phase 14.")
    public ApiResponse<List<OperationsAlertResponse>> getAlerts(
            @RequestParam(required = false) String userId,
            @RequestParam(defaultValue = "true") boolean activeOnly
    ) {
        List<OperationsAlertResponse> response = operationsAlertService.getAlerts(activeOnly, userId);
        return ApiResponse.success(response, "OPS_ALERTS_FETCHED");
    }

    @GetMapping("/runbooks")
    @Operation(summary = "Get operations runbooks", description = "Returns the rough runbook catalog used in Phase 14 drill flows.")
    public ApiResponse<List<RunbookScenarioResponse>> getRunbooks() {
        return ApiResponse.success(operationsRunbookCatalogService.getScenarios(), "OPS_RUNBOOKS_FETCHED");
    }

    private LocalDate parseDate(String fieldName, String value) {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of(fieldName, "yyyy-MM-dd 형식의 날짜여야 합니다.")
            );
        }
    }
}
