# Phase 14 Operations Runbook

> Updated: 2026-03-22  
> Scope: Phase 14 Watchtower rough 운영 절차, 알림 해석, drill 시나리오를 정리한다.

## 1. 목적

- 이 문서는 Phase 14에서 추가한 `ops overview`, `alerts`, `Prometheus`, `Airflow rough DAG`를 실제 장애 대응 흐름으로 엮는 runbook이다.
- 목표는 멋진 운영 체계 문서가 아니라, `문제 감지 -> 확인 -> 재처리 -> 검증`을 일관되게 밟는 최소 절차를 만드는 것이다.

## 2. 현재 rough 운영 자산

- Prometheus endpoint
  - `GET /actuator/prometheus`
- Recovery loop overview
  - `GET /api/v1/ops/overview/recovery-loop?userId={userId}&metricDate={yyyy-MM-dd}`
- Batch overview
  - `GET /api/v1/ops/overview/batch?userId={userId}&metricDate={yyyy-MM-dd}`
- Active alerts
  - `GET /api/v1/ops/alerts?userId={userId}&activeOnly=true`
- Runbook catalog
  - `GET /api/v1/ops/runbooks`
- Rough Airflow DAG
  - `lab/airflow-orchestration/dags/daily_kpi_pipeline.py`
  - `lab/airflow-orchestration/dags/backfill_reprocess.py`
  - `lab/airflow-orchestration/dags/weekly_retrospective_input.py`

## 3. 핵심 메트릭

- `reboot_recovery_loop_actions_total`
  - recovery loop API action count
- `reboot_recovery_loop_action_duration_seconds`
  - recovery loop API latency
- `reboot_batch_duration_seconds`
  - batch stage duration
- `reboot_batch_failed_runs_total`
  - failed batch stage count
- `reboot_dq_issue_count`
  - current DQ issue count
- `reboot_batch_watermark_lag_seconds`
  - current watermark lag
- `reboot_backfill_processed_days`
  - processed days per backfill run

## 4. 알림 해석 기준

- `batch_failure:*`
  - 배치 stage 자체가 실패했다.
  - 우선 실패 stage, metricDate, 예외 종류를 확인한다.
- `dq:*`
  - DQ report에 issue가 1개 이상 발생했다.
  - 원천 이벤트와 참조 무결성을 먼저 본다.
- `watermark_lag:*`
  - 마지막 처리 날짜가 현재 날짜 기준 2일 이상 뒤처졌다.
  - 미처리 구간 backfill 여부를 우선 확인한다.

## 5. 시나리오별 절차

### 5.1 Daily KPI pipeline failure

1. `/api/v1/ops/alerts`에서 `batch_failure:daily_kpi_pipeline:*`가 활성화됐는지 확인한다.
2. `/actuator/prometheus`에서 `reboot_batch_failed_runs_total`와 `reboot_batch_duration_seconds`를 본다.
3. 실패한 `metricDate` 기준으로 `/api/v1/recovery/analytics/kpis/daily/quality`와 `/api/v1/ops/overview/batch`를 확인한다.
4. 원천 데이터가 정상이라면 `/api/v1/recovery/analytics/kpis/daily`를 다시 호출한다.
5. 재실행 후 watermark와 alert resolve 상태를 확인한다.

### 5.2 DQ alert

1. `/api/v1/ops/overview/batch`에서 `qualityReport.totalIssueCount`와 세부 카운트를 확인한다.
2. `orphan_restart_count`, `restart_before_failure_count`, `missing_timebox_reference_count` 중 무엇이 증가했는지 본다.
3. 원천 이벤트/참조 문제를 수정하거나 원인 문서를 남긴다.
4. `/api/v1/recovery/analytics/kpis/daily/backfill`로 해당 날짜를 재처리한다.
5. `reboot_dq_issue_count`가 0으로 내려갔는지 확인한다.

### 5.3 Watermark lag

1. `/api/v1/ops/overview/batch`에서 `watermark.lastProcessedDate`를 확인한다.
2. 현재 날짜와 차이를 계산해 미처리 구간을 정한다.
3. `backfill_reprocess` DAG 또는 `/api/v1/recovery/analytics/kpis/daily/backfill`을 실행한다.
4. `/api/v1/recovery/analytics/kpis/daily/watermark`로 전진 여부를 확인한다.
5. `watermark_lag` alert가 resolve 상태로 바뀌었는지 확인한다.

## 6. Drill 시나리오

### Drill A. Backfill recovery

- 목적
  - watermark lag와 backfill 경로가 실제로 연결되는지 확인
- 준비
  - 과거 날짜 데이터 2~3일치 준비
- 실행
  - `/api/v1/recovery/analytics/kpis/daily/backfill`
- 확인
  - `processedDays`
  - `watermark.lastProcessedDate`
  - `reboot_backfill_processed_days`

### Drill B. DQ issue visibility

- 목적
  - DQ 이슈가 report, gauge, alert로 동시에 보이는지 확인
- 준비
  - restart/failure 참조 이상 데이터 1건 준비
- 실행
  - daily KPI 생성 후 `/api/v1/ops/overview/batch` 조회
- 확인
  - `qualityReport.totalIssueCount`
  - `reboot_dq_issue_count`
  - `dq:*` alert

## 7. 증빙으로 남길 것

- 실행 시각
- 사용자/날짜 범위
- 어떤 alert/metric을 확인했는지
- 어떤 재처리를 했는지
- 전후 값이 어떻게 바뀌었는지

## 8. rough 단계 한계

- alert는 현재 in-memory 상태라 재시작 시 유지되지 않는다.
- Grafana/Alertmanager/Sentry 실제 연동은 아직 rough 범위 밖이다.
- Airflow DAG는 자산만 제공하며, 로컬 Airflow 배포 자체는 검증하지 않았다.
