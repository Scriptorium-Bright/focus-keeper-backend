# Phase 11 Case Notes

> Version: v0.1  
> Updated: 2026-03-19  
> Scope: Phase 11 Product Analytics 구현 기록, 포트폴리오 후보 정리, 후속 리팩터링 포인트

## 1. 목적

- 이 문서는 Phase 11에서 만든 분석 기능을 `문제 -> 해결 -> 결과` 기준으로 정리한다.
- 구현 이력보다 `왜 이 기능이 필요했고, 무엇이 데이터 엔지니어링 포트폴리오 포인트가 되는지`를 먼저 남긴다.
- 기존 `docs/refactor.md`가 작업 로그 중심이라면, 이 문서는 Phase 11 전용 사례 정리 문서다.

## 2. 11.1 일간 KPI mart 적재

### 처음 상태

- 실행/실패/재시작 이벤트는 쌓이기 시작했지만, 이를 일간 KPI로 신뢰성 있게 읽는 저장 계층은 없었다.
- 제품은 `Recovery24`, `TTR`, `PlanExecutionRate`를 핵심 지표로 말하고 있었지만, 실제로는 API 응답과 개별 이벤트만 확인 가능한 상태였다.

### 왜 바꿨나

- 기능 구현만으로는 복귀 제품의 효과를 설명할 수 없고, Phase 11부터는 `이벤트 -> 지표` 경로를 실제로 보여줘야 했다.
- 데이터 엔지니어링 포트폴리오 관점에서도 원천 이벤트를 mart에 적재하고 조회하는 기준선이 먼저 필요했다.

### 무엇을 바꿨나

- `daily_kpi_metrics` mart 엔티티와 repository를 추가했다.
- `Spring Batch` 기반 `dailyKpiPipelineJob`을 도입해 사용자/일자 기준 KPI를 계산하고 upsert하도록 했다.
- `Recovery24`, `Recovery48`, `RestartCount24/48`, `TTR`, `CycleCompletionRate`, `PlanExecutionRate`, `EstimationError`를 일간 응답으로 노출했다.
- `POST /api/v1/recovery/analytics/kpis/daily`, `GET /api/v1/recovery/analytics/kpis/daily`를 추가했다.

### 결과

- `일간 KPI mart 생성/조회` 경로가 생겨, 제품 지표를 이벤트가 아니라 보고서 수준으로 확인할 수 있게 됐다.
- 대상 통합 테스트에서 mart upsert와 조회 결과를 검증했다.
- `./gradlew test --no-daemon` 전체 테스트 통과로 회귀가 없음을 확인했다.

### 기대 효과

- 복귀 루프 효과를 사용자 행동 데이터 기준으로 설명할 수 있다.
- 이후 코호트/퍼널/마찰 신호의 기준 데이터로 재사용할 수 있다.

### 포트폴리오 평가

- 가능 여부: 가능
- 점수: `8/10`
- 이유: `이벤트 -> 배치 -> mart -> 조회` 흐름을 실제 코드와 테스트로 보여주지만, DQ/백필/운영 메트릭은 아직 후속 단계가 남아 있다.

## 3. 11.2 코호트 리텐션 분석

### 처음 상태

- 일간 KPI가 생겨도 사용자군별 유지 패턴을 볼 수 있는 계층이 없었다.
- 총합 지표만 보면 특정 날짜에 들어온 유저군이 잘 남는지, 빠르게 이탈하는지 설명할 수 없었다.

### 왜 바꿨나

- `D1/D7/D30`은 데이터 엔지니어링 포트폴리오와 제품 분석 모두에서 기본 지표다.
- 이 프로젝트는 단일 ICP 검증이 중요하므로, cohort 단위로 “남는가/사라지는가”를 설명할 수 있어야 했다.

### 무엇을 바꿨나

- `cohort_retention_reports` 엔티티와 repository를 추가했다.
- `daily_kpi_metrics`의 첫 활성화 날짜를 기준으로 cohort를 정의했다.
- cohort 날짜별 `cohortSize`, `retainedDay1Users`, `retainedDay7Users`, `retainedDay30Users`와 각 비율을 계산하도록 구현했다.
- `POST /api/v1/recovery/analytics/cohorts/retention`, `GET /api/v1/recovery/analytics/cohorts/retention`을 추가했다.

### 결과

- 특정 cohort 날짜를 기준으로 D1/D7/D30 리텐션을 바로 조회할 수 있게 됐다.
- 리포트는 재생성 시 upsert 되며, 동일 cohort에 대한 중복 row를 만들지 않는다.
- 대상 통합 테스트에서 cohort 계산과 upsert를 검증했다.

### 기대 효과

- 총합 KPI가 아니라 `유저군별 유지 패턴`을 설명할 수 있다.
- 실험군/유입군 비교의 기본 토대를 만들었다.

### 포트폴리오 평가

- 가능 여부: 가능
- 점수: `8/10`
- 이유: cohort 개념과 저장 구조가 명확하고 테스트도 있다. 다만 아직 세그먼트 확장, 여러 cohort 기준 비교, 시각화는 없다.

## 4. 11.3 전환 퍼널 분석

### 처음 상태

- 사용자가 어디서 끊기는지는 기능 단위로만 짐작할 수 있었고, 단계별 전환율은 계산되지 않았다.
- Brain Dump, Big3, Timebox, Session, Failure, Restart가 각각 존재했지만 “어느 단계에서 유저가 줄어드는지”는 보이지 않았다.

### 왜 바꿨나

- 이 프로젝트는 단순 TODO 앱이 아니라 복귀 루프가 핵심이라, 단계별 이탈을 보는 퍼널이 꼭 필요했다.
- 제품/포트폴리오 모두에서 `계획 -> 실행 -> 실패 -> 재시작`이 실제로 어떻게 좁아지는지 보여줄 필요가 있었다.

### 무엇을 바꿨나

- `daily_funnel_reports` 엔티티와 repository를 추가했다.
- 날짜별로 아래 단계의 distinct user 수를 계산하도록 구현했다.
  - `Brain Dump`
  - `Big3`
  - `WORK Timebox`
  - `Session Start`
  - `Failure`
  - `Restart`
- 각 단계 간 전환율을 계산했다.
  - `big3SelectionRate`
  - `timeboxPlanningRate`
  - `sessionStartRate`
  - `failureRate`
  - `restartRate`
- `POST /api/v1/recovery/analytics/funnels/daily`, `GET /api/v1/recovery/analytics/funnels/daily`을 추가했다.

### 결과

- 복귀 루프의 단계별 사용자 수와 전환율을 저장/조회할 수 있게 됐다.
- 대상 통합 테스트에서 `3 -> 2 -> 2 -> 2 -> 1 -> 1` 퍼널과 각 비율을 검증했다.
- 기능과 테스트가 Phase 11 안에서 분리된 커밋으로 정리됐다.

### 기대 효과

- “기능은 있는데 왜 실제 전환이 안 나오는가”를 단계별로 설명할 수 있다.
- 이후 ICP 실험, 리마인더, anti-slip 개선과 연결하기 쉬워진다.

### 포트폴리오 평가

- 가능 여부: 가능
- 점수: `9/10`
- 이유: 제품 퍼널과 데이터 집계가 명확히 연결되고, 사용자 행동 단계가 선명해서 발표/면접 설명력이 강하다.

## 5. Phase 11 High / Mid / Low

## 5. 11.4 워터마크와 백필

### 처음 상태

- 일간 KPI mart를 생성할 수는 있었지만, 특정 기간을 다시 계산하는 경로와 마지막 처리 지점을 추적하는 기준선이 없었다.
- 이 상태에서는 “배치는 있다”까지만 말할 수 있고, 운영 가능한 데이터 파이프라인이라고 설명하기는 어려웠다.

### 왜 바꿨나

- 데이터 엔지니어링 포트폴리오에서는 단순 집계보다 `재처리 가능성`과 `진행 상태 추적`이 중요하다.
- 특히 잘못 계산된 날짜를 다시 돌리거나, 어느 날짜까지 적재가 끝났는지 보여주는 구조가 필요했다.

### 무엇을 바꿨나

- `daily_kpi_watermarks` 엔티티와 repository를 추가해 사용자별 `lastProcessedDate`를 저장하도록 했다.
- `POST /api/v1/recovery/analytics/kpis/daily/backfill`로 기간 백필 API를 추가했다.
- `GET /api/v1/recovery/analytics/kpis/daily/watermark`로 현재 워터마크를 조회할 수 있게 했다.
- `dailyKpiPipelineService.generate()`가 mart 적재 후 워터마크를 전진시키도록 연결했다.

### 결과

- 특정 날짜 범위를 다시 계산하고, 그 결과가 워터마크에 반영되는 흐름을 실제로 검증할 수 있게 됐다.
- 대상 통합 테스트에서 `2일 백필 -> mart 2건 생성 -> 워터마크 최종 날짜 갱신` 경로를 검증했다.

### 기대 효과

- Phase 11 산출물이 “한 번 계산하는 분석 API”가 아니라 “다시 돌릴 수 있는 배치 파이프라인”이라는 설명이 가능해졌다.
- 이후 Airflow DAG나 운영 알림을 붙일 때 기준 상태를 제공한다.

### 포트폴리오 평가

- 가능 여부: 가능
- 점수: `9/10`
- 이유: `백필 + 워터마크`는 데이터 엔지니어링 포트폴리오에서 설명력이 높고, mart 생성 흐름을 운영 가능한 형태로 끌어올린다.

## 6. 11.5 데이터 품질 리포트

### 처음 상태

- mart/cohort/funnel은 생성되지만, 결과를 신뢰할 수 있는지 보여주는 품질 계층은 없었다.
- 특히 중복 재시작, 끊어진 참조, BREAK 세션, 시간대 불일치 같은 이상 징후는 로그를 직접 뒤져야만 알 수 있었다.

### 왜 바꿨나

- 데이터 엔지니어링 포트폴리오에서 `지표를 만든다`와 `지표를 믿을 수 있게 만든다`는 다른 단계다.
- Phase 11을 마감하려면 최소한의 DQ 검사를 넣어, mart 생성과 함께 품질 리포트가 남는 구조가 필요했다.

### 무엇을 바꿨나

- `daily_kpi_quality_reports` 엔티티와 repository를 추가했다.
- 일간 KPI 생성 시 아래 이슈를 함께 검사하도록 했다.
  - 동일 failure에 대한 중복 restart link
  - 존재하지 않는 failure를 가리키는 orphan restart
  - failure보다 먼저 기록된 restart
  - 48시간을 넘긴 late restart link
  - BREAK timebox에 연결된 세션
  - 존재하지 않는 timebox를 가리키는 세션
  - `+09:00` 기준과 다른 timezone offset
- `GET /api/v1/recovery/analytics/kpis/daily/quality` 조회 API를 추가했다.

### 결과

- 일간 KPI를 생성하면 품질 리포트도 함께 upsert되어, 지표와 품질 상태를 같은 날짜 기준으로 읽을 수 있게 됐다.
- 대상 통합 테스트에서 의도적으로 잘못된 데이터를 주입해 각 이슈 카운트와 총합을 검증했다.

### 기대 효과

- 복귀 지표가 왜곡될 수 있는 이상 징후를 보고서 수준에서 확인할 수 있다.
- 이후 alerting, dashboard, SLA와 연결할 수 있는 최소 DQ 기반이 생겼다.

### 포트폴리오 평가

- 가능 여부: 가능
- 점수: `8/10`
- 이유: DQ를 `mart 생성 흐름 안에 결합했다`는 점은 강하지만, 아직 배치 알림/임계치/운영 메트릭까지는 가지 않았다.

## 7. Phase 11 High / Mid / Low

### High

- `daily_kpi_metrics`는 여전히 API 트리거 중심이다. Airflow DAG, 스케줄링, 실패 재시도, 운영 경보까지는 아직 붙지 않았다.
- DQ는 현재 rule-based 리포트 수준이며, 임계치 초과 시 배치를 실패시키거나 알림으로 연결하는 운영 흐름은 남아 있다.

### Mid

- cohort 기준이 현재는 `첫 activation 날짜` 하나로 고정돼 있다. `첫 failure`, `첫 recovery` 기준 비교가 아직 없다.
- 퍼널은 날짜별 distinct user 기준이라, 세션 수/실패 이유/세그먼트 기준 drill-down은 후속 구현이 필요하다.
- 워터마크는 현재 사용자별 단일 `lastProcessedDate`만 저장한다. 부분 실패 지점이나 세부 스테이지별 watermark 분리는 아직 없다.

### Low

- analytics 응답이 아직 dashboard 전용 조회라기보다 API 응답 중심이다.
- timezone 기준은 현재 `+09:00` 고정이라, 이후 사용자별 timezone 모델이 들어오면 계산 기준을 다시 분리해야 한다.

## 8. Phase 11 한 줄 정리

Phase 11에서는 복귀 행동 이벤트를 `일간 KPI mart`, `cohort retention report`, `daily funnel report`, `watermark/backfill`, `data quality report`로 승격해, 기능 구현 단계를 넘어 제품 효과와 데이터 신뢰성을 함께 설명할 수 있는 상태를 만들었다.
