# Phase 14 Rough Execution Guide

> Updated: 2026-03-22  
> Scope: Phase 14 Watchtower를 rough하게 진행할 때, `14.1 ~ 14.5` 단위의 상세 설명, 현재 재사용 가능한 메소드, 필요한 용어를 정리한다.

## 1. 목적

- 이 문서는 Phase 14를 `운영 가능한 데이터 시스템`으로 끌어올리기 위한 rough 실행 가이드다.
- 구현 완료 문서가 아니라, 어떤 순서로 어디를 건드리고 무엇을 남겨야 하는지 정리하는 작업 문서다.
- 특히 `14.1 ~ 14.5`마다
  - 왜 필요한지
  - 현재 코드에서 어떤 메소드/서비스를 재사용하는지
  - rough 단계에서 어디까지 만들면 되는지
  - 어떤 용어를 이해해야 하는지
  를 한 번에 볼 수 있게 한다.

## 2. Phase 14 한 줄 정의

Phase 14는 `Phase 11/13에서 만든 KPI/신호/세그먼트`를 실제 운영 가능한 시스템처럼 보이게 만드는 단계다.

즉 핵심은 이 4가지다.

- 메트릭이 보인다.
- 배치가 오케스트레이션된다.
- 실패/지연/DQ 이상이 알림으로 간다.
- 장애 대응 절차와 drill 증거가 남는다.

## 3. 공통 용어

- `Actuator`
  - Spring Boot 운영 엔드포인트다.
  - 현재 이 프로젝트는 `health`, `info`, `prometheus`를 노출할 준비가 돼 있다.
- `Prometheus`
  - 애플리케이션 메트릭을 scrape해 수집하는 시스템이다.
- `Grafana`
  - Prometheus 같은 메트릭 소스를 시각화하는 대시보드 도구다.
- `Sentry`
  - 예외/에러 이벤트를 수집해 장애를 추적하는 도구다.
- `Metric`
  - 시스템 상태를 숫자로 남기는 관측 값이다.
  - 예: `batch_duration_seconds`, API `p95`
- `Dashboard`
  - 메트릭을 패널 단위로 묶어 보는 화면이다.
- `Alert Rule`
  - 특정 임계치 초과/하락 시 알림을 보내는 규칙이다.
- `Watermark lag`
  - 파이프라인이 최신 데이터에 얼마나 뒤처져 있는지 나타내는 지표다.
- `DAG`
  - Airflow에서 task 의존성을 가진 실행 그래프다.
- `Runbook`
  - 장애가 났을 때 어떤 순서로 확인/조치/재실행할지 적은 절차 문서다.
- `Drill`
  - 장애 상황을 의도적으로 재현해 runbook과 알림이 실제로 작동하는지 확인하는 훈련이다.
- `SLO`
  - 운영 목표 수치다.
  - 예: 배치 완료 시간 `p95 < 30분`
- `MTTR`
  - Mean Time To Recovery. 장애가 난 뒤 정상 상태로 복구되기까지 걸린 시간이다.
- `Idempotent reprocess`
  - 같은 기간을 다시 계산해도 결과가 안전하게 덮어써지는 재처리 방식이다.

## 4. rough 완료 기준

rough 단계에서 Phase 14를 닫는 기준은 아래 정도가 적절하다.

- 메트릭 이름과 태그가 정리돼 있다.
- 최소 1개 이상의 핵심 API/배치에 계측이 붙어 있다.
- 대시보드 초안이 있다.
- 최소 1개 Airflow DAG가 실제 경로를 오케스트레이션한다.
- 배치 실패 또는 lag 또는 DQ 이상 중 1개 이상에 대한 알림이 실제로 간다.
- runbook이 있고, 최소 1회 drill 기록이 남아 있다.

즉 rough 단계에서도 `계획만 있음` 상태로 두지 않고, `작동하는 최소 증거`까지는 남겨야 한다.

## 4.1 현재 rough 구현 자산

- 운영 overview API
  - `GET /api/v1/ops/overview/recovery-loop`
  - `GET /api/v1/ops/overview/batch`
- alert/runbook API
  - `GET /api/v1/ops/alerts`
  - `GET /api/v1/ops/runbooks`
- 운영 메트릭
  - `/actuator/prometheus`
  - `reboot_recovery_loop_actions_total`
  - `reboot_recovery_loop_action_duration_seconds`
  - `reboot_batch_duration_seconds`
  - `reboot_batch_failed_runs_total`
  - `reboot_dq_issue_count`
  - `reboot_batch_watermark_lag_seconds`
  - `reboot_backfill_processed_days`
- rough orchestration asset
  - `lab/airflow-orchestration/dags/daily_kpi_pipeline.py`
  - `lab/airflow-orchestration/dags/backfill_reprocess.py`
  - `lab/airflow-orchestration/dags/weekly_retrospective_input.py`
- rough operations runbook
  - `docs/spec/PHASE_14_OPERATIONS_RUNBOOK.md`

## 5. 14.1 API/복귀 루프 메트릭 계측

### 무엇을 하는 단계인가

- 사용자가 실제로 체감하는 recovery loop API의 지연/오류/성공 흐름을 숫자로 남기는 단계다.
- 단순 health check가 아니라, `복귀 루프가 실제로 얼마나 잘 굴러가는가`를 관측 가능하게 만드는 시작점이다.

### 현재 재사용 가능한 메소드

- `RecoverySessionController.startSession()`
  - 복귀 세션 시작 API다.
- `RecoverySessionController.completeSession()`
  - 복귀 세션 완료 API다.
- `RecoverySessionController.interruptSession()`
  - 복귀 세션 중단 API다.
- `FailureCheckInController.checkIn()`
  - 실패 체크인 API다.
- `RestartController.restart()`
  - 실패 후 10분 복귀 재시작 API다.
- `DailyKpiController.generateDailyKpi()`
  - 일간 KPI 생성 API다.
- `HealthCheckController.health()`
  - 앱 상태 확인용 기본 health endpoint다.

### 메소드 설명 관점에서 뭘 보면 되나

- `startSession / completeSession / interruptSession`
  - recovery loop의 정상 상태 전이가 얼마나 자주, 얼마나 빠르게 일어나는지 볼 수 있다.
- `checkIn`
  - 실패 이벤트가 얼마나 자주 발생하는지, 에러와 실패 체크인이 어떻게 섞이는지 볼 수 있다.
- `restart`
  - 실패 후 실제 복귀 시도가 얼마나 발생하는지 볼 수 있다.
- `generateDailyKpi`
  - 동기 요청 기준으로는 가장 무거운 analytics 생성 경로 중 하나다.
- `health`
  - 서비스 생존 확인의 최소 기준이다.

### rough 단계에서 계측할 항목

- API request count
- API success/error count
- API duration (`p50`, `p95`)
- recovery loop별 성공/실패 비율
- 4xx/5xx 비율

### rough 완료 기준

- recovery loop 핵심 API 4~6개에 timer/counter가 붙어 있다.
- Prometheus scrape로 메트릭이 노출된다.
- 최소한 `API p95`, `에러율`, `요청 수`를 한 화면에서 볼 수 있다.
- rough overview API로 `dailyKpi`, `failureHour`, `friction signal`, `alert`를 한 번에 조회할 수 있다.

### 포트폴리오 포인트

- "기능이 있다"를 넘어서 "사용자 행동 경로를 운영 메트릭으로 본다"는 설명이 가능해진다.
- 백엔드/데이터 경계에서 `도메인 API를 관측 가능하게 만든 경험`으로 말할 수 있다.

## 6. 14.2 API/복귀 루프 대시보드

### 무엇을 하는 단계인가

- `14.1`에서 붙인 메트릭을 사람이 한 번에 읽을 수 있는 대시보드로 묶는 단계다.
- 목적은 멋진 UI가 아니라, `문제가 어디 있는지 1분 안에 보이게 만드는 것`이다.

### 현재 재사용 가능한 자산

- `management.endpoints.web.exposure.include=health,info,prometheus`
  - 현재 애플리케이션은 Prometheus endpoint를 노출할 준비가 돼 있다.
- `GET /actuator/health`
  - 운영 기본 체크 포인트다.
- `GET /api/v1/health`
  - 앱 레벨 health 응답이다.

### 메소드/엔드포인트 설명

- `/actuator/prometheus`
  - rough 단계 대시보드의 기본 metric source가 된다.
- `/actuator/health`
  - 인프라/앱 생존 여부 확인의 기준점이다.
- `/api/v1/health`
  - 앱 자체 응답 표준을 유지하는 사용자 정의 health endpoint다.

### rough 단계에서 만들 패널

- API request volume
- API p95 latency
- API error ratio
- Recovery24 / Recovery48
- RestartCount24 / RestartCount48
- TTR
- CycleCompletionRate

### rough 완료 기준

- 운영자가 봐야 할 패널 5~7개가 정리돼 있다.
- 정상/예외 호출 후 대시보드 값이 기대대로 변하는지 확인했다.
- 패널 이름, 축, 태그 기준이 문서화돼 있다.
- rough 단계에서는 Grafana 대신 `/api/v1/ops/overview/*` 응답을 대시보드 snapshot으로 활용할 수 있다.

### 포트폴리오 포인트

- "메트릭을 찍었다"보다 한 단계 올라가, "운영 관점에서 읽을 수 있게 구조화했다"는 설명이 가능해진다.

## 7. 14.3 Batch/DQ 메트릭 표준화와 대시보드

### 무엇을 하는 단계인가

- Phase 11/13에서 만든 배치와 진단 계층을 운영 메트릭으로 승격하는 단계다.
- 이 단계가 있어야 성능 개선도 숫자로 말할 수 있다.

### 현재 재사용 가능한 메소드

- `DailyKpiBatchLauncher.launch()`
  - 일간 KPI 배치 실행 진입점이다.
- `DailyKpiPipelineService.generate()`
  - KPI mart 생성 핵심 경로다.
- `DailyKpiQualityService.generate()`
  - KPI 품질 리포트 생성 경로다.
- `DailyKpiBackfillService.backfill()`
  - 기간 재처리 경로다.
- `DailyKpiWatermarkService.advance()`
  - 마지막 처리 지점 갱신 경로다.
- `WeeklyRetrospectiveService.generate()`
  - 주간 회고 집계 생성 경로다.
- `FailureHourAnalyticsService.generate()`
  - 시간대별 실패 분포 생성 경로다.
- `FrictionSignalAnalyticsService.generate()`
  - 반복 실패/지연 재시작 signal 생성 경로다.

### 메소드 설명 관점에서 뭘 보면 되나

- `DailyKpiBatchLauncher.launch`
  - 배치 시작/종료/실패를 측정하기 가장 쉬운 엔트리 포인트다.
- `DailyKpiPipelineService.generate`
  - raw 조회, KPI 계산, mart 저장, quality, watermark까지 이어지는 핵심 비용 구간이다.
- `DailyKpiQualityService.generate`
  - DQ 검사 시간이 얼마나 드는지 확인할 수 있다.
- `DailyKpiBackfillService.backfill`
  - 기간이 길어질수록 비용이 얼마나 커지는지 보기 좋은 경로다.
- `DailyKpiWatermarkService.advance`
  - 재처리 후 진행 상태가 언제 갱신되는지 확인할 수 있다.
- `WeeklyRetrospectiveService.generate`
  - Phase 14에서 Airflow 오케스트레이션 대상으로 편입하기 좋은 보조 배치다.
- `FailureHourAnalyticsService.generate`
  - Phase 13 분석 계층의 배치성 생성 경로다.
- `FrictionSignalAnalyticsService.generate`
  - signal 계산의 batch/DQ 메트릭 연결 대상이다.

### rough 단계에서 표준화할 메트릭

- `batch_duration_seconds`
- `batch_processed_rows`
- `batch_failed_runs_total`
- `batch_watermark_lag_seconds`
- `batch_reprocess_runs_total`
- `dq_total_issue_count`
- duplicate/orphan/timezone/late-arrival 등 세부 DQ 카운트

### rough 완료 기준

- 최소 `daily_kpi_pipeline`에 배치 메트릭이 붙어 있다.
- DQ 총 이슈 수와 핵심 세부 카운트가 보인다.
- backfill 실행 후 duration과 watermark lag를 확인할 수 있다.
- `GET /api/v1/ops/overview/batch`에서 quality/watermark/alert를 같이 볼 수 있다.

### 포트폴리오 포인트

- `운영 가능한 데이터 시스템` 증거가 여기서 가장 강하게 나온다.
- Phase 11/13을 단순 집계가 아니라 `관측 가능한 배치 시스템`으로 승격했다고 설명할 수 있다.

## 8. 14.4 Airflow 오케스트레이션 rough 도입

### 무엇을 하는 단계인가

- 현재 `Spring Batch + 애플리케이션 내부 실행`인 경로 위에, 스케줄/재시도/의존성/재처리를 제어하는 오케스트레이션 계층을 얹는 단계다.
- 배치 엔진 교체가 아니라 실행 방식의 승격이다.

### 현재 재사용 가능한 메소드/서비스

- `DailyKpiBatchLauncher.launch()`
  - `daily_kpi_pipeline`의 rough 오케스트레이션 대상이다.
- `DailyKpiBackfillService.backfill()`
  - `backfill_reprocess`의 대상이다.
- `WeeklyRetrospectiveService.generate()`
  - `weekly_retrospective_input`의 rough 대상이다.

### rough 단계 메소드 설명

- `launch(userId, metricDate)`
  - 특정 사용자/날짜의 KPI 배치를 돌리는 단일 실행 단위다.
- `backfill(userId, startDate, endDate)`
  - 기간 재처리 단위다.
- `generate(userId, weekStart)`
  - 주간 회고 입력/생성의 기본 단위다.

### rough 도입 시 핵심 용어

- `DAG`
  - task 의존성을 표현한 실행 그래프다.
- `retry`
  - 실패 task를 자동으로 다시 시도하는 설정이다.
- `timeout`
  - 일정 시간 안에 끝나지 않으면 실패로 보는 제한 시간이다.
- `SLA`
  - "이 시간 안에 끝나야 한다"는 운영 약속이다.
- `catchup/backfill`
  - 과거 스케줄이나 기간을 다시 실행하는 기능이다.

### rough 단계에서 할 일

- `daily_kpi_pipeline` DAG 정의
- `weekly_retrospective_input` DAG 정의
- `backfill_reprocess` DAG 정의
- retry/timeout/SLA 기본값 정의
- 성공/실패/재시도 상태를 대시보드에서 확인 가능하게 연결

### rough 완료 기준

- 최소 1개 DAG가 실제 경로를 돌린다.
- 실패 시 retry 또는 failure 상태를 확인할 수 있다.
- backfill DAG가 기간 파라미터를 받아 재처리 가능하다.

### 포트폴리오 포인트

- `Spring Batch를 썼다` 수준에서 `Spring Batch를 Airflow로 운영했다` 수준으로 설명이 올라간다.
- 특히 데이터 엔지니어 JD에서 스케줄/재처리/오케스트레이션 경험으로 연결하기 좋다.

## 9. 14.5 알림 룰, runbook, drill

### 무엇을 하는 단계인가

- 대시보드만으로는 부족하므로, 실제 이상 상황을 감지하고 대응 절차를 검증하는 단계다.
- Phase 14를 진짜로 닫는 건 보통 여기다.

### 현재 재사용 가능한 메소드/엔드포인트

- `DailyKpiBackfillService.backfill()`
  - 장애 후 기간 재처리의 핵심 조치 경로다.
- `DailyKpiWatermarkService.get()`
  - 복구 후 어디까지 처리됐는지 확인하는 조회 경로다.
- `DailyKpiQualityQueryService.get()`
  - DQ 이상이 해소됐는지 확인하는 조회 경로다.
- `HealthCheckController.health()`
  - 앱 생존 여부 확인의 가장 빠른 엔드포인트다.
- `GET /actuator/health`
  - 런타임 health 상태 확인용이다.

### 메소드 설명 관점에서 뭘 보면 되나

- `backfill`
  - 장애/지각 데이터/논리 버그 후 가장 실제적인 복구 수단이다.
- `watermark get`
  - 복구가 끝났는지, 아직 밀려 있는지 확인하는 기준이다.
- `quality get`
  - 재처리 후 결과를 믿어도 되는지 확인하는 기준이다.
- `health`
  - 애플리케이션이 최소한 살아 있는지 보는 기준이다.

### rough 단계에서 만들 알림

- 배치 실패 알림
- watermark lag 초과 알림
- DQ issue count 임계치 초과 알림

### rough 단계에서 만들 runbook

- 단일 실행 실패
- 지각 데이터 반영 필요
- 계산식 버그 수정 후 기간 재처리

### rough 단계에서 할 drill 예시

- `daily_kpi_pipeline` 실패를 의도적으로 발생시킨다.
- 실패 알림이 실제로 오는지 확인한다.
- 원인을 분류한다.
- `backfill` 또는 재실행으로 복구한다.
- `watermark`, `quality`, 대시보드 값이 정상으로 돌아왔는지 확인한다.
- 이 과정을 캡처/기록으로 남긴다.

### rough 완료 기준

- 최소 1개 이상 알림이 실제 발송된다.
- 최소 1개 이상 runbook이 완결된 절차로 남아 있다.
- 최소 1회 drill 증거가 있다.

### 포트폴리오 포인트

- 여기까지 가야 `운영은 기능이 아니라 시스템 신뢰의 문제였다`는 메시지가 선다.
- 대시보드만 만든 사람과, 장애 대응 절차까지 검증한 사람의 차이가 여기서 난다.

## 10. Phase 14에서 성능 개선이 보이기 시작하는 지점

- `14.1`
  - API `p95`, 에러율이 수치로 보인다.
- `14.3`
  - `batch_duration_seconds`, `watermark lag`, DQ 처리 시간이 보인다.
- `14.4`
  - DAG duration, retry count, timeout 기반 병목이 보인다.
- `14.5`
  - 장애 복구 시간과 재처리 시간이 보인다.

즉 Phase 14는 성능개선 자체라기보다, 성능개선을 `운영 수치`로 말할 수 있게 만드는 단계다.

## 11. 추천 진행 순서

1. `14.3`
   - 배치/DQ 메트릭부터 잡아 숫자가 보이게 한다.
2. `14.4`
   - Airflow rough 도입으로 운영 경로를 만든다.
3. `14.5`
   - 알림/runbook/drill로 신뢰성을 닫는다.
4. `14.1`
   - recovery loop API 메트릭을 붙인다.
5. `14.2`
   - API/복귀 루프 대시보드를 다듬는다.

포트폴리오 관점에서는 이 순서가 가장 좋다.

- `대시보드 예쁘게 만들기`보다
- `배치 운영 근거 -> 오케스트레이션 -> 알림/복구 -> API 관측`
순서가 훨씬 설명력이 높다.

## 12. 문서 간 관계

- Phase 14 기능/작업 분해: `docs/spec/FEATURE_PROCESS_SPEC.md`
- Phase 14 상위 시작 순서: `docs/newPlan.md`
- 배치 재처리 기준선: `docs/spec/BATCH_RUNBOOK.md`
- 성능 후보: `docs/PHASE_11_13_PERFORMANCE_CANDIDATES.md`
- 실측 방법: `docs/PHASE_11_13_PERFORMANCE_MEASUREMENT_PLAYBOOK.md`

이 문서는 위 문서들 사이에서 `Phase 14를 rough하게 실제로 어떻게 밀 것인가`를 연결하는 설명 문서다.
