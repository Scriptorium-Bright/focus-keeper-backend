package com.focuskeeper.reboot.recovery.execution.controller;

import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.execution.dto.RecoverySessionResponse;
import com.focuskeeper.reboot.recovery.execution.dto.StartRecoverySessionRequest;
import com.focuskeeper.reboot.recovery.execution.dto.UpdateRecoverySessionRequest;
import com.focuskeeper.reboot.recovery.execution.service.RecoverySessionService;
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

    public RecoverySessionController(RecoverySessionService recoverySessionService) {
        this.recoverySessionService = recoverySessionService;
    }

    @PostMapping("/start")
    @Operation(summary = "Start a recovery session", description = "Starts a recovery session for a selected timebox.")
    public ApiResponse<RecoverySessionResponse> startSession(
            @Valid @RequestBody StartRecoverySessionRequest request
    ) {
        RecoverySessionResponse session = recoverySessionService.startSession(request.userId(), request.timeboxId());
        return ApiResponse.success(session, "RECOVERY_SESSION_STARTED");
    }

    @PostMapping("/complete")
    @Operation(summary = "Complete a recovery session", description = "Marks an active recovery session as completed.")
    public ApiResponse<RecoverySessionResponse> completeSession(
            @Valid @RequestBody UpdateRecoverySessionRequest request
    ) {
        RecoverySessionResponse session = recoverySessionService.completeSession(request.userId(), request.sessionId());
        return ApiResponse.success(session, "RECOVERY_SESSION_COMPLETED");
    }

    @PostMapping("/interrupt")
    @Operation(summary = "Interrupt a recovery session", description = "Marks an active recovery session as interrupted.")
    public ApiResponse<RecoverySessionResponse> interruptSession(
            @Valid @RequestBody UpdateRecoverySessionRequest request
    ) {
        RecoverySessionResponse session = recoverySessionService.interruptSession(request.userId(), request.sessionId());
        return ApiResponse.success(session, "RECOVERY_SESSION_INTERRUPTED");
    }
}
