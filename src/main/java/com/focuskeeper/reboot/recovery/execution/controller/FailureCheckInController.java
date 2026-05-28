package com.focuskeeper.reboot.recovery.execution.controller;

import com.focuskeeper.reboot.common.observability.OperationsMetricRecorder;
import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.execution.dto.FailureCheckInRequest;
import com.focuskeeper.reboot.recovery.execution.dto.FailureCheckInResponse;
import com.focuskeeper.reboot.recovery.execution.service.FailureEventService;
import com.focuskeeper.reboot.recovery.execution.service.FailureEventService.FailureCheckInResult;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
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
/**
 * 실패 체크인 API 진입점이다.
 *
 * 요청을 받아 failure check-in 유스케이스를 호출하고,
 * observability용 action metric까지 함께 기록한다.
 */
public class FailureCheckInController {

    private final FailureEventService failureEventService;
    private final OperationsMetricRecorder operationsMetricRecorder;

    public FailureCheckInController(
            FailureEventService failureEventService,
            OperationsMetricRecorder operationsMetricRecorder
    ) {
        this.failureEventService = failureEventService;
        this.operationsMetricRecorder = operationsMetricRecorder;
    }

    /**
     * 진행 중 세션의 실패를 기록하고 세션 상태와 재시작 제안을 함께 반환한다.
     */
    @PostMapping("/check-in")
    @Operation(summary = "Check in a recovery failure", description = "Records a failure reason for the current session and interrupts the active recovery session.")
    public ApiResponse<FailureCheckInResponse> checkIn(
            @Valid @RequestBody FailureCheckInRequest request
    ) {
        Sample sample = operationsMetricRecorder.startSample();
        try {
            FailureCheckInResult result = failureEventService.checkIn(
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
                    result.recoverySession().status(),
                    result.restartSuggestion()
            );
            operationsMetricRecorder.recordRecoveryLoopAction(sample, "failure_check_in", "success");
            return ApiResponse.success(response, "FAILURE_CHECKED_IN");
        } catch (RuntimeException exception) {
            operationsMetricRecorder.recordRecoveryLoopAction(sample, "failure_check_in", "failure");
            throw exception;
        }
    }
}
