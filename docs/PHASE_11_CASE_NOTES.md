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

### High

- `daily_kpi_metrics`는 현재 배치 job을 API에서 직접 실행하는 구조라, 진짜 운영 환경의 스케줄/워터마크/백필 흐름과는 아직 분리되지 않았다.
- `Recovery24/48` 계산은 현재 일자 기준 생성 경로에 집중돼 있어, 기간 재처리와 지각 이벤트 처리까지는 아직 완성되지 않았다.

### Mid

- cohort 기준이 현재는 `첫 activation 날짜` 하나로 고정돼 있다. `첫 failure`, `첫 recovery` 기준 비교가 아직 없다.
- 퍼널은 날짜별 distinct user 기준이라, 세션 수/실패 이유/세그먼트 기준 drill-down은 후속 구현이 필요하다.

### Low

- analytics 응답이 아직 dashboard 전용 조회라기보다 API 응답 중심이다.
- batch metric, DQ 경고, airflow DAG 메타데이터는 현재 별도 구조로 확장할 여지가 있다.

## 6. Phase 11 한 줄 정리

Phase 11에서는 복귀 행동 이벤트를 `일간 KPI mart`, `cohort retention report`, `daily funnel report`로 승격해, 기능 구현 단계를 넘어 제품 효과를 데이터로 설명할 수 있는 상태를 만들었다.
