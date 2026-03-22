package com.focuskeeper.reboot.common.observability;

import com.focuskeeper.reboot.common.observability.dto.RunbookScenarioResponse;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OperationsRunbookCatalogService {

    public List<RunbookScenarioResponse> getScenarios() {
        return List.of(
                new RunbookScenarioResponse(
                        "daily_kpi_pipeline_failure",
                        "Daily KPI pipeline failure",
                        "reboot_batch_failed_runs_total 증가 또는 batch failure alert 활성화",
                        List.of(
                                "실패한 metricDate와 userId를 확인한다.",
                                "actuator/prometheus와 /api/v1/ops/alerts에서 실패 stage를 확인한다.",
                                "원천 이벤트와 DQ 리포트를 확인한 뒤 /api/v1/recovery/analytics/kpis/daily 또는 backfill을 다시 실행한다.",
                                "워터마크와 품질 리포트가 갱신됐는지 확인한다."
                        ),
                        List.of(
                                "reboot_batch_duration_seconds",
                                "reboot_batch_failed_runs_total",
                                "reboot_batch_watermark_lag_seconds"
                        )
                ),
                new RunbookScenarioResponse(
                        "daily_kpi_quality_alert",
                        "Daily KPI DQ alert",
                        "reboot_dq_issue_count > 0 또는 DQ alert 활성화",
                        List.of(
                                "quality report에서 어떤 issue count가 증가했는지 확인한다.",
                                "restart/failure/timebox 참조 이상 여부를 원천 테이블 기준으로 확인한다.",
                                "필요하면 원천 수정 후 backfill_reprocess를 실행한다.",
                                "issue count가 0으로 내려갔는지 다시 확인한다."
                        ),
                        List.of(
                                "reboot_dq_issue_count",
                                "daily_kpi_quality.totalIssueCount",
                                "ops alerts"
                        )
                ),
                new RunbookScenarioResponse(
                        "daily_kpi_watermark_lag",
                        "Daily KPI watermark lag",
                        "reboot_batch_watermark_lag_seconds 임계치 초과",
                        List.of(
                                "마지막 처리 날짜와 현재 날짜 차이를 확인한다.",
                                "미처리 구간을 backfill_reprocess로 재계산한다.",
                                "재처리 후 watermark가 최신 날짜로 전진했는지 확인한다.",
                                "lag alert가 resolve 상태로 바뀌었는지 확인한다."
                        ),
                        List.of(
                                "reboot_batch_watermark_lag_seconds",
                                "daily_kpi_watermark.lastProcessedDate",
                                "ops alerts"
                        )
                )
        );
    }
}
