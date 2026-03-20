package com.focuskeeper.reboot.recovery.analytics.friction.controller;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.analytics.friction.dto.FrictionSignalReportResponse;
import com.focuskeeper.reboot.recovery.analytics.friction.dto.GenerateFrictionSignalsRequest;
import com.focuskeeper.reboot.recovery.analytics.friction.service.FrictionSignalAnalyticsService;
import com.focuskeeper.reboot.recovery.analytics.friction.service.FrictionSignalQueryService;
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
@RequestMapping("/api/v1/recovery/analytics/friction-signals")
@Tag(name = "Analytics", description = "Recovery analytics and KPI APIs")
public class FrictionSignalController {

    private final FrictionSignalAnalyticsService frictionSignalAnalyticsService;
    private final FrictionSignalQueryService frictionSignalQueryService;

    public FrictionSignalController(
            FrictionSignalAnalyticsService frictionSignalAnalyticsService,
            FrictionSignalQueryService frictionSignalQueryService
    ) {
        this.frictionSignalAnalyticsService = frictionSignalAnalyticsService;
        this.frictionSignalQueryService = frictionSignalQueryService;
    }

    /**
     * 지정한 날짜의 반복 실패 및 지연 재시작 신호를 계산한 뒤 signal report를 반환한다.
     */
    @PostMapping
    @Operation(summary = "Generate friction signals", description = "Calculates repeated failure and late restart signals for the given user and date.")
    public ApiResponse<FrictionSignalReportResponse> generateFrictionSignals(
            @Valid @RequestBody GenerateFrictionSignalsRequest request
    ) {
        LocalDate metricDate = parseMetricDate(request.metricDate());
        frictionSignalAnalyticsService.generate(request.userId(), metricDate);
        FrictionSignalReportResponse response = frictionSignalQueryService.get(request.userId(), metricDate);
        return ApiResponse.success(response, "FRICTION_SIGNALS_GENERATED");
    }

    /**
     * 생성된 friction signal report를 사용자와 날짜 기준으로 조회한다.
     */
    @GetMapping
    @Operation(summary = "Get friction signals", description = "Returns the generated friction signals for the given user and date.")
    public ApiResponse<FrictionSignalReportResponse> getFrictionSignals(
            @RequestParam String userId,
            @RequestParam String metricDate
    ) {
        FrictionSignalReportResponse response = frictionSignalQueryService.get(userId, parseMetricDate(metricDate));
        return ApiResponse.success(response, "FRICTION_SIGNALS_FETCHED");
    }

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
