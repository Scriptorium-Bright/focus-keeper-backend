package com.focuskeeper.reboot.recovery.analytics.controller;

import com.focuskeeper.reboot.common.error.BusinessException;
import com.focuskeeper.reboot.common.error.ErrorCode;
import com.focuskeeper.reboot.common.response.ApiResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.BackfillDailyKpiRequest;
import com.focuskeeper.reboot.recovery.analytics.dto.BackfillDailyKpiResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiQualityResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.DailyKpiLastProcessedDateResponse;
import com.focuskeeper.reboot.recovery.analytics.dto.GenerateDailyKpiRequest;
import com.focuskeeper.reboot.recovery.analytics.service.DailyKpiBatchLauncher;
import com.focuskeeper.reboot.recovery.analytics.service.DailyKpiBackfillService;
import com.focuskeeper.reboot.recovery.analytics.service.DailyKpiQualityQueryService;
import com.focuskeeper.reboot.recovery.analytics.service.DailyKpiQueryService;
import com.focuskeeper.reboot.recovery.analytics.service.DailyKpiLastProcessedDateService;
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
@RequestMapping("/api/v1/recovery/analytics/kpis")
@Tag(name = "Analytics", description = "Recovery analytics and KPI APIs")
/**
 * recovery analytics 하위 기능을 외부 API로 노출하는 진입점이다.
 *
 * 실제 KPI 계산, 품질 검사, 백필, lastProcessedDate 관리는 각각의 서비스가 담당하고,
 * 이 컨트롤러는 요청 검증, 날짜 파싱, 응답 포맷 통일에 집중한다.
 */
public class DailyKpiController {

    private final DailyKpiBatchLauncher dailyKpiBatchLauncher;
    private final DailyKpiQueryService dailyKpiQueryService;
    private final DailyKpiQualityQueryService dailyKpiQualityQueryService;
    private final DailyKpiBackfillService dailyKpiBackfillService;
    private final DailyKpiLastProcessedDateService dailyKpiLastProcessedDateService;

    public DailyKpiController(
            DailyKpiBatchLauncher dailyKpiBatchLauncher,
            DailyKpiQueryService dailyKpiQueryService,
            DailyKpiQualityQueryService dailyKpiQualityQueryService,
            DailyKpiBackfillService dailyKpiBackfillService,
            DailyKpiLastProcessedDateService dailyKpiLastProcessedDateService
    ) {
        this.dailyKpiBatchLauncher = dailyKpiBatchLauncher;
        this.dailyKpiQueryService = dailyKpiQueryService;
        this.dailyKpiQualityQueryService = dailyKpiQualityQueryService;
        this.dailyKpiBackfillService = dailyKpiBackfillService;
        this.dailyKpiLastProcessedDateService = dailyKpiLastProcessedDateService;
    }

    /**
     * 지정한 사용자와 날짜에 대해 일간 KPI 배치를 실행하고, 생성된 mart 결과를 즉시 조회해 반환한다.
     */
    @PostMapping("/daily")
    @Operation(summary = "Generate daily KPI mart", description = "Runs the daily KPI pipeline and upserts the mart row for the given user and date.")
    public ApiResponse<DailyKpiResponse> generateDailyKpi(
            @Valid @RequestBody GenerateDailyKpiRequest request
    ) {
        LocalDate metricDate = parseMetricDate(request.metricDate());
        dailyKpiBatchLauncher.launch(request.userId(), metricDate);
        DailyKpiResponse response = dailyKpiQueryService.get(request.userId(), metricDate);
        return ApiResponse.success(response, "DAILY_KPI_GENERATED");
    }

    /**
     * 이미 생성된 일간 KPI mart 행을 사용자와 날짜 기준으로 조회한다.
     */
    @GetMapping("/daily")
    @Operation(summary = "Get daily KPI mart", description = "Returns the generated daily KPI mart row for the given user and date.")
    public ApiResponse<DailyKpiResponse> getDailyKpi(
            @RequestParam String userId,
            @RequestParam String metricDate
    ) {
        DailyKpiResponse response = dailyKpiQueryService.get(userId, parseMetricDate(metricDate));
        return ApiResponse.success(response, "DAILY_KPI_FETCHED");
    }

    /**
     * 일간 KPI 계산 결과에 연결된 데이터 품질 리포트를 조회한다.
     */
    @GetMapping("/daily/quality")
    @Operation(summary = "Get daily KPI data quality report", description = "Returns the latest data quality report for the generated daily KPI mart row.")
    public ApiResponse<DailyKpiQualityResponse> getDailyKpiQuality(
            @RequestParam String userId,
            @RequestParam String metricDate
    ) {
        DailyKpiQualityResponse response = dailyKpiQualityQueryService.get(userId, parseMetricDate(metricDate));
        return ApiResponse.success(response, "DAILY_KPI_QUALITY_FETCHED");
    }

    /**
     * 지정한 기간의 KPI mart를 다시 계산하고, 백필 결과와 최신 lastProcessedDate를 함께 반환한다.
     */
    @PostMapping("/daily/backfill")
    @Operation(summary = "Backfill daily KPI mart", description = "Recomputes daily KPI mart rows for the given user and date range, and advances the pipeline lastProcessedDate.")
    public ApiResponse<BackfillDailyKpiResponse> backfillDailyKpi(
            @Valid @RequestBody BackfillDailyKpiRequest request
    ) {
        BackfillDailyKpiResponse response = dailyKpiBackfillService.backfill(
                request.userId(),
                parseMetricDate(request.startDate()),
                parseMetricDate(request.endDate())
        );
        return ApiResponse.success(response, "DAILY_KPI_BACKFILL_COMPLETED");
    }

    /**
     * 일간 KPI 파이프라인이 마지막으로 어디까지 처리했는지 lastProcessedDate를 조회한다.
     */
    @GetMapping("/daily/last-processed-date")
    @Operation(summary = "Get daily KPI pipeline last processed date", description = "Returns the latest processed date and update timestamp for the daily KPI pipeline.")
    public ApiResponse<DailyKpiLastProcessedDateResponse> getDailyKpiLastProcessedDate(@RequestParam String userId) {
        DailyKpiLastProcessedDateResponse response = dailyKpiLastProcessedDateService.get(userId);
        return ApiResponse.success(response, "DAILY_KPI_LAST_PROCESSED_DATE_FETCHED");
    }

    /**
     * 문자열 날짜를 KPI 계산 기준일로 변환하고, 형식이 다르면 공통 요청 오류로 바꿔 던진다.
     *
     * analytics/friction API 전반이 yyyy-MM-dd 형식을 공통 계약으로 쓰기 때문에,
     * 컨트롤러 단계에서 형식 오류를 먼저 차단해 서비스 내부에는 LocalDate만 전달한다.
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
