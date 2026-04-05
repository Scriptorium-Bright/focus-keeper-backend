package com.focuskeeper.reboot.recovery.friction.controller;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.friction.dto.FrictionSegmentReportResponse;
import com.focuskeeper.reboot.recovery.friction.service.FrictionSegmentQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/recovery/analytics/friction-segments")
@Tag(name = "Analytics", description = "Recovery analytics and KPI APIs")
/**
 * failure-hour report와 friction signal을 사용자 해석용 세그먼트로 조합해 반환하는 API다.
 */
public class FrictionSegmentController {

    private final FrictionSegmentQueryService frictionSegmentQueryService;

    public FrictionSegmentController(FrictionSegmentQueryService frictionSegmentQueryService) {
        this.frictionSegmentQueryService = frictionSegmentQueryService;
    }

    /**
     * 이미 계산된 friction 신호와 시간대별 실패 리포트를 읽어 최소 세그먼트 리포트를 반환한다.
     */
    @GetMapping
    @Operation(summary = "Get friction segments", description = "Builds a minimal friction segment report from the generated failure-hour and friction-signal data.")
    public ApiResponse<FrictionSegmentReportResponse> getFrictionSegments(
            @RequestParam String userId,
            @RequestParam String metricDate
    ) {
        FrictionSegmentReportResponse response = frictionSegmentQueryService.get(userId, parseMetricDate(metricDate));
        return ApiResponse.success(response, "FRICTION_SEGMENTS_FETCHED");
    }

    /**
     * 문자열 날짜를 segment 조회 기준일로 파싱하고 형식 오류를 공통 예외로 변환한다.
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
