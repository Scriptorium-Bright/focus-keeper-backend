package com.focuskeeper.reboot.recovery.execution;

import com.focuskeeper.reboot.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/recovery/sessions")
public class RecoverySessionController {

    private final RecoverySessionService recoverySessionService;

    public RecoverySessionController(RecoverySessionService recoverySessionService) {
        this.recoverySessionService = recoverySessionService;
    }

    @PostMapping("/start")
    public ApiResponse<RecoverySessionResponse> startSession(
            @Valid @RequestBody StartRecoverySessionRequest request
    ) {
        RecoverySession session = recoverySessionService.startSession(request.userId(), request.timeboxId());
        return ApiResponse.success(toResponse(session), "RECOVERY_SESSION_STARTED");
    }

    @PostMapping("/complete")
    public ApiResponse<RecoverySessionResponse> completeSession(
            @Valid @RequestBody UpdateRecoverySessionRequest request
    ) {
        RecoverySession session = recoverySessionService.completeSession(request.userId(), request.sessionId());
        return ApiResponse.success(toResponse(session), "RECOVERY_SESSION_COMPLETED");
    }

    @PostMapping("/interrupt")
    public ApiResponse<RecoverySessionResponse> interruptSession(
            @Valid @RequestBody UpdateRecoverySessionRequest request
    ) {
        RecoverySession session = recoverySessionService.interruptSession(request.userId(), request.sessionId());
        return ApiResponse.success(toResponse(session), "RECOVERY_SESSION_INTERRUPTED");
    }

    private RecoverySessionResponse toResponse(RecoverySession session) {
        return new RecoverySessionResponse(
                session.id(),
                session.timeboxId(),
                session.status().name(),
                session.startedAt().toString(),
                session.endedAt() == null ? null : session.endedAt().toString(),
                session.createdAt().toString()
        );
    }

    public record StartRecoverySessionRequest(
            @NotBlank(message = "userId는 필수입니다.")
            String userId,
            @NotBlank(message = "timeboxId는 필수입니다.")
            String timeboxId
    ) {
    }

    public record UpdateRecoverySessionRequest(
            @NotBlank(message = "userId는 필수입니다.")
            String userId,
            @NotBlank(message = "sessionId는 필수입니다.")
            String sessionId
    ) {
    }

    public record RecoverySessionResponse(
            String sessionId,
            String timeboxId,
            String status,
            String startedAt,
            String endedAt,
            String createdAt
    ) {
    }
}
