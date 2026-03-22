package com.focuskeeper.reboot.recovery.execution.controller;

import com.focuskeeper.reboot.common.observability.OperationsMetricRecorder;
import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RestartRecoveryRequest;
import com.focuskeeper.reboot.recovery.execution.dto.RestartRecoveryResponse;
import com.focuskeeper.reboot.recovery.execution.service.RestartService;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/recovery/restarts")
@Tag(name = "Recovery", description = "Recovery loop planning and execution APIs")
public class RestartController {

    private final RestartService restartService;
    private final OperationsMetricRecorder operationsMetricRecorder;

    public RestartController(
            RestartService restartService,
            OperationsMetricRecorder operationsMetricRecorder
    ) {
        this.restartService = restartService;
        this.operationsMetricRecorder = operationsMetricRecorder;
    }

    @PostMapping
    @Operation(summary = "Execute a 10-minute restart", description = "Starts a new recovery session from a failure event and records the restart event.")
    public ApiResponse<RestartRecoveryResponse> restart(
            @Valid @RequestBody RestartRecoveryRequest request
    ) {
        Timer.Sample sample = operationsMetricRecorder.startSample();
        try {
            RestartService.RestartRecoveryResult result = restartService.restart(request.userId(), request.failureEventId());
            RestartRecoveryResponse response = new RestartRecoveryResponse(
                    result.restartEvent(),
                    result.recoverySession(),
                    result.restartSuggestion()
            );
            operationsMetricRecorder.recordRecoveryLoopAction(sample, "restart_recovery", "success");
            return ApiResponse.success(response, "RECOVERY_RESTARTED");
        } catch (RuntimeException exception) {
            operationsMetricRecorder.recordRecoveryLoopAction(sample, "restart_recovery", "failure");
            throw exception;
        }
    }
}
