package com.focuskeeper.reboot.recovery.analytics.controller;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.GenerateDailyKpiRequest;
import com.focuskeeper.reboot.recovery.analytics.service.DailyKpiBatchLauncher;
import com.focuskeeper.reboot.recovery.analytics.service.DailyKpiQueryService;
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
@RequestMapping("/api/v1/recovery/analytics")
@Tag(name = "Analytics", description = "Recovery analytics and KPI APIs")
public class DailyKpiController {

    private final DailyKpiBatchLauncher dailyKpiBatchLauncher;
    private final DailyKpiQueryService dailyKpiQueryService;

    public DailyKpiController(
            DailyKpiBatchLauncher dailyKpiBatchLauncher,
            DailyKpiQueryService dailyKpiQueryService
    ) {
        this.dailyKpiBatchLauncher = dailyKpiBatchLauncher;
        this.dailyKpiQueryService = dailyKpiQueryService;
    }

    @PostMapping("/kpis/daily")
    @Operation(summary = "Generate daily KPI mart", description = "Runs the daily KPI pipeline and upserts the mart row for the given user and date.")
    public ApiResponse<DailyKpiResponse> generateDailyKpi(
            @Valid @RequestBody GenerateDailyKpiRequest request
    ) {
        LocalDate metricDate = parseMetricDate(request.metricDate());
        dailyKpiBatchLauncher.launch(request.userId(), metricDate);
        DailyKpiResponse response = dailyKpiQueryService.get(request.userId(), metricDate);
        return ApiResponse.success(response, "DAILY_KPI_GENERATED");
    }

    @GetMapping("/kpis/daily")
    @Operation(summary = "Get daily KPI mart", description = "Returns the generated daily KPI mart row for the given user and date.")
    public ApiResponse<DailyKpiResponse> getDailyKpi(
            @RequestParam String userId,
            @RequestParam String metricDate
    ) {
        DailyKpiResponse response = dailyKpiQueryService.get(userId, parseMetricDate(metricDate));
        return ApiResponse.success(response, "DAILY_KPI_FETCHED");
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
