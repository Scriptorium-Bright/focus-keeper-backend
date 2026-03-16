package com.focuskeeper.reboot.recovery.execution;

import com.focuskeeper.reboot.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/recovery/failures")
public class FailureCheckInController {

    private final FailureEventService failureEventService;

    public FailureCheckInController(FailureEventService failureEventService) {
        this.failureEventService = failureEventService;
    }

    @PostMapping("/check-in")
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
                result.recoverySession().status().name()
        );
        return ApiResponse.success(response, "FAILURE_CHECKED_IN");
    }

    public record FailureCheckInRequest(
            @NotBlank(message = "userId는 필수입니다.")
            String userId,
            @NotBlank(message = "sessionId는 필수입니다.")
            String sessionId,
            @NotBlank(message = "reason은 필수입니다.")
            String reason,
            @Size(max = 200, message = "note는 최대 200자까지 허용됩니다.")
            String note
    ) {
    }

    public record FailureCheckInResponse(
            String failureEventId,
            String sessionId,
            String timeboxId,
            String reason,
            String note,
            String occurredAt,
            String sessionStatus
    ) {
    }
}
