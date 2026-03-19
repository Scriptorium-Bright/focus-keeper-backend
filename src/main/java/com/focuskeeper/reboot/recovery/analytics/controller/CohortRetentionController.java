package com.focuskeeper.reboot.recovery.analytics.controller;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.CohortRetentionResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.GenerateCohortRetentionRequest;
import com.focuskeeper.reboot.recovery.analytics.service.CohortRetentionService;
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
@RequestMapping("/api/v1/recovery/analytics/cohorts")
@Tag(name = "Analytics", description = "Recovery analytics and KPI APIs")
public class CohortRetentionController {

    private final CohortRetentionService cohortRetentionService;

    public CohortRetentionController(CohortRetentionService cohortRetentionService) {
        this.cohortRetentionService = cohortRetentionService;
    }

    @PostMapping("/retention")
    @Operation(summary = "Generate cohort retention report", description = "Calculates D1/D7/D30 retention for users whose first activation happened on the cohort date.")
    public ApiResponse<CohortRetentionResponse> generateCohortRetention(
            @Valid @RequestBody GenerateCohortRetentionRequest request
    ) {
        CohortRetentionResponse response = cohortRetentionService.generate(parseDate(request.cohortDate()));
        return ApiResponse.success(response, "COHORT_RETENTION_GENERATED");
    }

    @GetMapping("/retention")
    @Operation(summary = "Get cohort retention report", description = "Returns the generated D1/D7/D30 retention report for the cohort date.")
    public ApiResponse<CohortRetentionResponse> getCohortRetention(
            @RequestParam String cohortDate
    ) {
        CohortRetentionResponse response = cohortRetentionService.get(parseDate(cohortDate));
        return ApiResponse.success(response, "COHORT_RETENTION_FETCHED");
    }

    private LocalDate parseDate(String rawDate) {
        try {
            return LocalDate.parse(rawDate);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(
                    ErrorCode.COMMON_BAD_REQUEST,
                    Map.of("cohortDate", "yyyy-MM-dd 형식의 날짜여야 합니다.")
            );
        }
    }
}
