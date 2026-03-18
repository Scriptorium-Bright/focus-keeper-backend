package com.focuskeeper.reboot.recovery.execution.controller;

import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.execution.dto.FailureCheckInRequest;
import com.focuskeeper.reboot.recovery.execution.dto.FailureCheckInResponse;
import com.focuskeeper.reboot.recovery.execution.service.FailureEventService;
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
@RequestMapping("/api/v1/recovery/failures")
@Tag(name = "Recovery", description = "Recovery loop planning and execution APIs")
public class FailureCheckInController {

    private final FailureEventService failureEventService;

    public FailureCheckInController(FailureEventService failureEventService) {
        this.failureEventService = failureEventService;
    }

    @PostMapping("/check-in")
    @Operation(summary = "Check in a recovery failure", description = "Records a failure reason for the current session and interrupts the active recovery session.")
    public ApiResponse<FailureCheckInResponse> checkIn(
            @Valid @RequestBody FailureCheckInRequest request
    ) {
        FailureEventService.FailureCheckInResult result = failureEventService.checkIn(
                request.userId(),
                request.sessionId(),
                request.reason(),
                request.note()
        );

        FailureCheckInResponse response = new FailureCheckInResponse(
                result.failureEvent().id(),
                result.failureEvent().sessionId(),
                result.failureEvent().timeboxId(),
                result.failureEvent().reason().name(),
                result.failureEvent().note(),
                result.failureEvent().occurredAt().toString(),
                result.recoverySession().status()
        );
        return ApiResponse.success(response, "FAILURE_CHECKED_IN");
    }
}
