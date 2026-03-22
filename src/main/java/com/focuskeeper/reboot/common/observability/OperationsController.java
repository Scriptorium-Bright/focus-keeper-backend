package com.focuskeeper.reboot.common.observability;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.common.observability.dto.BatchOverviewResponse;
import com.focuskeeper.reboot.common.observability.dto.RecoveryLoopOverviewResponse;
import com.focuskeeper.reboot.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
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

    public OperationsController(OperationsOverviewService operationsOverviewService) {
        this.operationsOverviewService = operationsOverviewService;
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
    @Operation(summary = "Get batch overview", description = "Returns the rough batch, DQ, and watermark dashboard snapshot for Phase 14.")
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
