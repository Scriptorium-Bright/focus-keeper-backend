package com.focuskeeper.reboot.recovery.retrospective.controller;

import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.retrospective.dto.GenerateWeeklyRetrospectiveRequest;
import com.focuskeeper.reboot.recovery.retrospective.dto.WeeklyRetrospectiveResponse;
import com.focuskeeper.reboot.recovery.retrospective.service.WeeklyRetrospectiveService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
                LocalDate.parse(request.weekStart())
        );
        return ApiResponse.success(response, "WEEKLY_RETROSPECTIVE_GENERATED");
    }
}
