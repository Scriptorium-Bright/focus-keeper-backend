package com.focuskeeper.reboot.recovery.analytics.controller;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyFunnelResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.GenerateDailyFunnelRequest;
import com.focuskeeper.reboot.recovery.analytics.service.DailyFunnelService;
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
@RequestMapping("/api/v1/recovery/analytics/funnels")
@Tag(name = "Analytics", description = "Recovery analytics and KPI APIs")
public class DailyFunnelController {

    private final DailyFunnelService dailyFunnelService;

    public DailyFunnelController(DailyFunnelService dailyFunnelService) {
        this.dailyFunnelService = dailyFunnelService;
    }

    @PostMapping("/daily")
    @Operation(summary = "Generate daily recovery funnel report", description = "Calculates daily user funnel counts from Brain Dump to Restart for the given date.")
    public ApiResponse<DailyFunnelResponse> generateDailyFunnel(
            @Valid @RequestBody GenerateDailyFunnelRequest request
    ) {
        DailyFunnelResponse response = dailyFunnelService.generate(parseDate(request.metricDate()));
        return ApiResponse.success(response, "DAILY_FUNNEL_GENERATED");
    }

    @GetMapping("/daily")
    @Operation(summary = "Get daily recovery funnel report", description = "Returns the generated daily funnel report for the given date.")
    public ApiResponse<DailyFunnelResponse> getDailyFunnel(@RequestParam String metricDate) {
        DailyFunnelResponse response = dailyFunnelService.get(parseDate(metricDate));
        return ApiResponse.success(response, "DAILY_FUNNEL_FETCHED");
    }

    private LocalDate parseDate(String rawDate) {
        try {
            return LocalDate.parse(rawDate);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("metricDate", "yyyy-MM-dd 형식의 날짜여야 합니다.")
            );
        }
    }
}
