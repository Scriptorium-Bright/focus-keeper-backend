package com.focuskeeper.reboot.recovery.friction.controller;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.friction.dto.FailureHourDistributionResponse;
import com.focuskeeper.reboot.recovery.friction.dto.GenerateFailureHourDistributionRequest;
import com.focuskeeper.reboot.recovery.friction.service.FailureHourAnalyticsService;
import com.focuskeeper.reboot.recovery.friction.service.FailureHourQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/recovery/analytics/failure-hours")
@Tag(name = "Analytics", description = "Recovery analytics and KPI APIs")
/**
 * 실패 이벤트를 시간대별 분포 리포트로 생성/조회하는 API 진입점이다.
 */
public class FailureHourAnalyticsController {

    private final FailureHourAnalyticsService failureHourAnalyticsService;
    private final FailureHourQueryService failureHourQueryService;

    public FailureHourAnalyticsController(
            FailureHourAnalyticsService failureHourAnalyticsService,
            FailureHourQueryService failureHourQueryService
    ) {
        this.failureHourAnalyticsService = failureHourAnalyticsService;
        this.failureHourQueryService = failureHourQueryService;
    }

    /**
     * 지정한 날짜의 실패 이벤트를 시간대별 분포와 peak hour 기준으로 집계한 뒤 결과를 반환한다.
     */
    @PostMapping
    @Operation(summary = "Generate failure hour distribution", description = "Aggregates failure events by local hour and stores the peak failure hour report.")
    public ApiResponse<FailureHourDistributionResponse> generateFailureHourDistribution(
            @Valid @RequestBody GenerateFailureHourDistributionRequest request
    ) {
        LocalDate metricDate = parseMetricDate(request.metricDate());
        failureHourAnalyticsService.generate(request.userId(), metricDate);
        FailureHourDistributionResponse response = failureHourQueryService.get(request.userId(), metricDate);
        return ApiResponse.success(response, "FAILURE_HOUR_DISTRIBUTION_GENERATED");
    }

    /**
     * 생성된 시간대별 실패 분포 리포트를 사용자와 날짜 기준으로 조회한다.
     */
    @GetMapping
    @Operation(summary = "Get failure hour distribution", description = "Returns the generated failure-by-hour report for the given user and date.")
    public ApiResponse<FailureHourDistributionResponse> getFailureHourDistribution(
            @RequestParam String userId,
            @RequestParam String metricDate
    ) {
        FailureHourDistributionResponse response = failureHourQueryService.get(userId, parseMetricDate(metricDate));
        return ApiResponse.success(response, "FAILURE_HOUR_DISTRIBUTION_FETCHED");
    }

    /**
     * 문자열 날짜를 failure-hour 분석 기준일로 파싱하고 형식 오류를 공통 예외로 변환한다.
     */
    private LocalDate parseMetricDate(String metricDate) {
        try {
            return LocalDate.parse(metricDate);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("metricDate", "yyyy-MM-dd 형식의 날짜여야 합니다.")
            );
        }
    }
}
