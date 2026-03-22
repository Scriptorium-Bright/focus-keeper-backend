package com.focuskeeper.reboot.recovery.execution.controller;

import com.focuskeeper.reboot.common.observability.OperationsMetricRecorder;
import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RecoverySessionResponse;
import com.focuskeeper.reboot.recovery.execution.dto.StartRecoverySessionRequest;
import com.focuskeeper.reboot.recovery.execution.dto.UpdateRecoverySessionRequest;
import com.focuskeeper.reboot.recovery.execution.service.RecoverySessionService;
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
@RequestMapping("/api/v1/recovery/sessions")
@Tag(name = "Recovery", description = "Recovery loop planning and execution APIs")
public class RecoverySessionController {

    private final RecoverySessionService recoverySessionService;
    private final OperationsMetricRecorder operationsMetricRecorder;

    public RecoverySessionController(
            RecoverySessionService recoverySessionService,
            OperationsMetricRecorder operationsMetricRecorder
    ) {
        this.recoverySessionService = recoverySessionService;
        this.operationsMetricRecorder = operationsMetricRecorder;
    }

    @PostMapping("/start")
    @Operation(summary = "Start a recovery session", description = "Starts a recovery session for a selected timebox.")
    public ApiResponse<RecoverySessionResponse> startSession(
            @Valid @RequestBody StartRecoverySessionRequest request
    ) {
        Timer.Sample sample = operationsMetricRecorder.startSample();
        try {
            RecoverySessionResponse session = recoverySessionService.startSession(request.userId(), request.timeboxId());
            operationsMetricRecorder.recordRecoveryLoopAction(sample, "start_session", "success");
            return ApiResponse.success(session, "RECOVERY_SESSION_STARTED");
        } catch (RuntimeException exception) {
            operationsMetricRecorder.recordRecoveryLoopAction(sample, "start_session", "failure");
            throw exception;
        }
    }

    @PostMapping("/complete")
    @Operation(summary = "Complete a recovery session", description = "Marks an active recovery session as completed.")
    public ApiResponse<RecoverySessionResponse> completeSession(
            @Valid @RequestBody UpdateRecoverySessionRequest request
    ) {
        Timer.Sample sample = operationsMetricRecorder.startSample();
        try {
            RecoverySessionResponse session = recoverySessionService.completeSession(request.userId(), request.sessionId());
            operationsMetricRecorder.recordRecoveryLoopAction(sample, "complete_session", "success");
            return ApiResponse.success(session, "RECOVERY_SESSION_COMPLETED");
        } catch (RuntimeException exception) {
            operationsMetricRecorder.recordRecoveryLoopAction(sample, "complete_session", "failure");
            throw exception;
        }
    }

    @PostMapping("/interrupt")
    @Operation(summary = "Interrupt a recovery session", description = "Marks an active recovery session as interrupted.")
    public ApiResponse<RecoverySessionResponse> interruptSession(
            @Valid @RequestBody UpdateRecoverySessionRequest request
    ) {
        Timer.Sample sample = operationsMetricRecorder.startSample();
        try {
            RecoverySessionResponse session = recoverySessionService.interruptSession(request.userId(), request.sessionId());
            operationsMetricRecorder.recordRecoveryLoopAction(sample, "interrupt_session", "success");
            return ApiResponse.success(session, "RECOVERY_SESSION_INTERRUPTED");
        } catch (RuntimeException exception) {
            operationsMetricRecorder.recordRecoveryLoopAction(sample, "interrupt_session", "failure");
            throw exception;
        }
    }
}
