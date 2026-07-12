package com.focuskeeper.reboot.recovery.execution.controller;

import com.focuskeeper.reboot.common.metrics.CoreMetricRecorder;
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
/**
 * failure event를 기준으로 재시작 유스케이스를 실행하는 컨트롤러다.
 */
public class RestartController {

    private final RestartService restartService;
    private final CoreMetricRecorder coreMetricRecorder;

    public RestartController(
            RestartService restartService,
            CoreMetricRecorder coreMetricRecorder
    ) {
        this.restartService = restartService;
        this.coreMetricRecorder = coreMetricRecorder;
    }

    /**
     * 실패 이벤트 하나를 기준으로 10분 재시작을 실행한다.
     */
    @PostMapping
    @Operation(summary = "Execute a 10-minute restart", description = "Starts a new recovery session from a failure event and records the restart event.")
    public ApiResponse<RestartRecoveryResponse> restart(
            @Valid @RequestBody RestartRecoveryRequest request
    ) {
        Timer.Sample sample = coreMetricRecorder.startSample();
        try {
            RestartService.RestartRecoveryResult result = restartService.restart(request.userId(), request.failureEventId());
            RestartRecoveryResponse response = new RestartRecoveryResponse(
                    result.restartEvent(),
                    result.recoverySession(),
                    result.restartSuggestion()
            );
            coreMetricRecorder.recordExecutionAction(sample, "restart_recovery", "success");
            return ApiResponse.success(response, "RECOVERY_RESTARTED");
        } catch (RuntimeException exception) {
            coreMetricRecorder.recordExecutionAction(sample, "restart_recovery", "failure");
            throw exception;
        }
    }
}
