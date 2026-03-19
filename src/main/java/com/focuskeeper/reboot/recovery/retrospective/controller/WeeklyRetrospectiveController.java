package com.focuskeeper.reboot.recovery.retrospective.controller;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.retrospective.dto.GenerateWeeklyRetrospectiveRequest;
import com.focuskeeper.reboot.recovery.retrospective.dto.WeeklyRetrospectiveResponse;
import com.focuskeeper.reboot.recovery.retrospective.service.WeeklyRetrospectiveService;
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
@RequestMapping("/api/v1/recovery/retrospectives")
@Tag(name = "Recovery", description = "Recovery loop planning and execution APIs")
public class WeeklyRetrospectiveController {

    private final WeeklyRetrospectiveService weeklyRetrospectiveService;

    public WeeklyRetrospectiveController(WeeklyRetrospectiveService weeklyRetrospectiveService) {
        this.weeklyRetrospectiveService = weeklyRetrospectiveService;
    }

    @PostMapping("/weekly")
    @Operation(summary = "Generate weekly retrospective", description = "Aggregates the last 7-day recovery activity into a rule-based weekly retrospective.")
    public ApiResponse<WeeklyRetrospectiveResponse> generateWeeklyRetrospective(
            @Valid @RequestBody GenerateWeeklyRetrospectiveRequest request
    ) {
        WeeklyRetrospectiveResponse response = weeklyRetrospectiveService.generate(
                request.userId(),
                parseWeekStart(request.weekStart())
        );
        return ApiResponse.success(response, "WEEKLY_RETROSPECTIVE_GENERATED");
    }

    @GetMapping("/weekly")
    @Operation(summary = "Get weekly retrospective", description = "Returns the generated rule-based weekly retrospective for a user and week start date.")
    public ApiResponse<WeeklyRetrospectiveResponse> getWeeklyRetrospective(
            @RequestParam String userId,
            @RequestParam String weekStart
    ) {
        WeeklyRetrospectiveResponse response = weeklyRetrospectiveService.get(
                userId,
                parseWeekStart(weekStart)
        );
        return ApiResponse.success(response, "WEEKLY_RETROSPECTIVE_FETCHED");
    }

    private LocalDate parseWeekStart(String weekStart) {
        try {
            return LocalDate.parse(weekStart);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("weekStart", "yyyy-MM-dd 형식의 날짜여야 합니다.")
            );
        }
    }
}
