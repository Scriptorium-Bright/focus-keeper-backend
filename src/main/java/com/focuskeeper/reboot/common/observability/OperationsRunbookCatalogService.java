package com.focuskeeper.reboot.common.observability;

import com.focuskeeper.reboot.common.observability.dto.RunbookScenarioResponse;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Observability 계층의 "대응 절차 카탈로그" 역할을 한다.
 *
 * 메트릭을 수집하거나 alert를 계산하는 서비스가 아니라, 특정 alert가 떴을 때
 * 운영자가 어떤 순서로 확인하고 복구할지에 대한 runbook 시나리오를 제공한다.
 * 즉 "문제를 감지하는 계층"이 아니라 "감지된 문제에 어떻게 대응할지 안내하는 계층"이다.
 */
@Service
public class OperationsRunbookCatalogService {

    /**
     * 현재 프로젝트에서 자주 볼 가능성이 높은 운영 시나리오를 카탈로그 형태로 반환한다.
     *
     * 이 목록은 "시스템에서 가능한 모든 장애"를 완전하게 나열한 것이 아니라,
     * Phase 14 rough observability 자산으로 설명 가능한 대표 시나리오를 정리한 것이다.
     * 대시보드에서 alert를 본 뒤 바로 후속 점검 절차를 확인할 수 있게 하려는 목적이다.
     */
    public List<RunbookScenarioResponse> getScenarios() {
        return List.of(
                dailyKpiPipelineFailureScenario(),
                dailyKpiQualityAlertScenario(),
                dailyKpiProcessingLagScenario(),
                backfillReprocessIncompleteScenario(),
                recoveryLoopLatencySpikeScenario(),
                weeklyRetrospectiveMissingScenario()
        );
    }

    /**
     * Daily KPI 파이프라인 stage가 실제로 실패했을 때의 기본 대응 절차다.
     *
     * 가장 먼저 봐야 할 것은 어느 metricDate/userId 조합이 깨졌는지와
     * 어느 stage에서 실패했는지다. 이후 원천 데이터와 재실행 가능 여부를 확인한다.
     */
    private RunbookScenarioResponse dailyKpiPipelineFailureScenario() {
        return new RunbookScenarioResponse(
                "daily_kpi_pipeline_failure",
                "Daily KPI pipeline failure",
                "reboot_batch_failed_runs_total 증가 또는 batch failure alert 활성화",
                List.of(
                        "실패한 metricDate와 userId를 확인한다.",
                        "actuator/prometheus와 /api/v1/ops/alerts에서 어떤 pipeline stage가 실패했는지 확인한다.",
                        "원천 failure/restart/session 데이터와 DQ 리포트를 먼저 확인한다.",
                        "원천 데이터가 정상이면 /api/v1/recovery/analytics/kpis/daily 또는 backfill을 다시 실행한다.",
                        "재실행 후 lastProcessedDate, 품질 리포트, alert resolve 상태를 함께 확인한다."
                ),
                List.of(
                        "reboot_batch_duration_seconds",
                        "reboot_batch_failed_runs_total",
                        "reboot_batch_processing_lag_seconds",
                        "ops alerts"
                )
        );
    }

    /**
     * DQ issue가 발생했을 때 원천 참조 무결성과 재처리 순서를 확인하는 절차다.
     *
     * 이 시나리오의 핵심은 "배치가 돌았는가"보다 "결과를 믿을 수 있는가"에 있다.
     * 따라서 issue 개수만 보는 것이 아니라 어떤 종류의 issue가 늘었는지까지 확인해야 한다.
     */
    private RunbookScenarioResponse dailyKpiQualityAlertScenario() {
        return new RunbookScenarioResponse(
                "daily_kpi_quality_alert",
                "Daily KPI DQ alert",
                "reboot_dq_issue_count > 0 또는 DQ alert 활성화",
                List.of(
                        "batch overview와 quality report에서 totalIssueCount와 세부 issue 항목을 확인한다.",
                        "restart/failure/timebox 참조 이상 여부를 원천 테이블 기준으로 확인한다.",
                        "원천 데이터 수정 또는 원인 분석 메모를 남긴 뒤 해당 날짜를 backfill 재처리한다.",
                        "재처리 후 quality report.totalIssueCount와 reboot_dq_issue_count가 0으로 내려갔는지 확인한다."
                ),
                List.of(
                        "reboot_dq_issue_count",
                        "daily_kpi_quality.totalIssueCount",
                        "ops alerts"
                )
        );
    }

    /**
     * 파이프라인이 최신 날짜를 따라가지 못할 때의 freshness 대응 절차다.
     *
     * processing lag는 단순 실패보다 더 교묘한 문제다. 배치는 성공처럼 보이지만
     * 실제 지표는 stale할 수 있으므로, 마지막 처리 날짜와 미처리 구간을 우선 확인해야 한다.
     */
    private RunbookScenarioResponse dailyKpiProcessingLagScenario() {
        return new RunbookScenarioResponse(
                "daily_kpi_processing_lag",
                "Daily KPI processing lag",
                "reboot_batch_processing_lag_seconds 임계치 초과",
                List.of(
                        "batch overview에서 마지막 처리 날짜와 현재 날짜 차이를 확인한다.",
                        "미처리 구간을 정하고 /api/v1/recovery/analytics/kpis/daily/backfill 또는 backfill_reprocess 흐름을 실행한다.",
                        "재처리 후 /api/v1/recovery/analytics/kpis/daily/last-processed-date로 전진 여부를 확인한다.",
                        "lag alert가 resolve 상태로 바뀌었는지와 quality report가 정상인지 함께 확인한다."
                ),
                List.of(
                        "reboot_batch_processing_lag_seconds",
                        "batch_overview.lastProcessedDate.lastProcessedDate",
                        "ops alerts"
                )
        );
    }

    /**
     * backfill을 실행했지만 기대한 날짜 범위가 모두 반영되지 않은 경우를 다룬다.
     *
     * 이 경우는 outright failure보다 놓치기 쉽다. 요청은 성공했지만 processedDays가
     * 기대보다 작거나 lastProcessedDate가 충분히 전진하지 않으면 부분 처리 실패 가능성을 의심해야 한다.
     */
    private RunbookScenarioResponse backfillReprocessIncompleteScenario() {
        return new RunbookScenarioResponse(
                "backfill_reprocess_incomplete",
                "Backfill reprocess incomplete",
                "backfill 요청 이후 processedDays가 기대보다 작거나 lastProcessedDate가 충분히 전진하지 않음",
                List.of(
                        "요청한 시작일/종료일 범위와 실제 processedDays를 먼저 비교한다.",
                        "/actuator/prometheus에서 reboot_backfill_processed_days와 reboot_batch_duration_seconds를 확인한다.",
                        "해당 날짜 구간의 원천 이벤트 유무와 DQ 이슈 존재 여부를 확인한다.",
                        "부분 반영이 의심되면 같은 범위를 다시 backfill하고, 필요하면 더 작은 날짜 구간으로 쪼개 재처리한다.",
                        "최종적으로 lastProcessedDate와 quality report가 요청 범위 기준으로 정상인지 검증한다."
                ),
                List.of(
                        "reboot_backfill_processed_days",
                        "reboot_batch_duration_seconds",
                        "reboot_batch_processing_lag_seconds",
                        "batch_overview.lastProcessedDate.lastProcessedDate"
                )
        );
    }

    /**
     * recovery loop API가 느려지거나 특정 행동의 지연이 증가할 때의 조사 절차다.
     *
     * 이 프로젝트는 운영 대시보드가 배치만 보는 것이 아니라, 사용자가 실제 누르는
     * start/restart/failure_check_in 같은 행동도 함께 본다는 점을 드러내기 위한 시나리오다.
     */
    private RunbookScenarioResponse recoveryLoopLatencySpikeScenario() {
        return new RunbookScenarioResponse(
                "recovery_loop_latency_spike",
                "Recovery loop latency spike",
                "reboot_recovery_loop_action_duration_seconds 또는 http.server.requests 지연 증가",
                List.of(
                        "어떤 action(start_session, restart, failure_check_in) 태그에서 지연이 늘었는지 먼저 확인한다.",
                        "같은 시간대에 failure status action이 함께 증가했는지 reboot_recovery_loop_actions_total을 본다.",
                        "동시간대 batch failure나 processing lag 같은 배경 장애가 있었는지 ops alerts를 함께 확인한다.",
                        "해당 API를 재현 호출해 지연이 지속되는지 확인하고, 최근 쿼리/배치 영향 여부를 점검한다.",
                        "지연이 정상 수준으로 돌아왔는지 action별 duration 추세를 다시 확인한다."
                ),
                List.of(
                        "reboot_recovery_loop_action_duration_seconds",
                        "reboot_recovery_loop_actions_total",
                        "http.server.requests",
                        "ops alerts"
                )
        );
    }

    /**
     * 주간 회고가 비어 있을 때 입력 데이터 부재인지 생성 누락인지 확인하는 절차다.
     *
     * batch overview는 정상인데 weekly retrospective만 비어 있으면,
     * 회고 입력 생성 경로가 빠졌는지 또는 해당 주 기준 데이터가 충분한지 따져봐야 한다.
     */
    private RunbookScenarioResponse weeklyRetrospectiveMissingScenario() {
        return new RunbookScenarioResponse(
                "weekly_retrospective_missing",
                "Weekly retrospective missing",
                "batch overview에서 weeklyRetrospective가 null이거나 기대한 주차 회고가 조회되지 않음",
                List.of(
                        "metricDate 기준 주 시작일을 다시 계산하고 같은 weekStart로 회고 조회를 확인한다.",
                        "해당 주의 daily KPI, friction signal, segment 데이터가 먼저 생성되어 있는지 확인한다.",
                        "회고만 빠졌다면 /api/v1/recovery/retrospectives/weekly 생성 경로를 다시 실행한다.",
                        "생성 후 batch overview와 회고 조회 API에서 같은 주차 데이터가 보이는지 확인한다.",
                        "반복 발생 시 회고 입력 생성 선행 조건과 스케줄 순서를 점검한다."
                ),
                List.of(
                        "weekly_retrospective.weekStart",
                        "daily_kpi.recovery24",
                        "friction signals",
                        "ops batch overview"
                )
        );
    }
}
