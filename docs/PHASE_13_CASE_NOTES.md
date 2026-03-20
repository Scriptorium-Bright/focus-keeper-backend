# Phase 13 Case Notes

## Scope

- `13.1` 시간대별 실패 분포와 `PeakFailureHour`
- `13.2` 반복 실패 / 지연 재시작 signal table
- `13.3` friction segment 조회 API

## Case 1. 실패 시간대 분포를 별도 분석 테이블로 분리

### Problem

- 일간 KPI만으로는 사용자가 언제 가장 자주 무너지는지 설명할 수 없었다.
- 원천 failure event를 직접 조회하면 시간대 분포를 재사용하기 어렵고, 이후 세그먼트 계산의 기준선도 흔들린다.

### Change

- `failure_hour_reports`, `failure_hour_metrics`를 추가했다.
- 사용자 로컬 시각 기준으로 `FailureCountByHour`, `FailureRatioByHour`, `PeakFailureHour`, `PeakFailureWindow`를 계산했다.
- `POST/GET /api/v1/recovery/analytics/failure-hours`로 생성과 조회를 분리했다.

### Result

- 시간대별 실패 패턴을 일간 집계 테이블로 재사용할 수 있게 됐다.
- 이후 friction segment에서 `morning slip`을 판단하는 기준선을 같은 소스에서 읽을 수 있게 됐다.

### Portfolio Fit

- 가능
- 점수: `8/10`
- 이유: raw 이벤트를 파생 집계 테이블로 승격하고, 그 결과를 후속 해석 계층에서 재사용하는 구조가 DE 포트폴리오 서사에 잘 맞는다.

## Case 2. 반복 실패와 지연 재시작을 signal table로 승격

### Problem

- `Recovery24`, `RestartCount24/48`만으로는 실패 이유가 반복되는지, 재시작이 너무 늦는지 바로 읽기 어려웠다.
- 제품/운영 문맥에서 바로 사용할 수 있는 최소 신호 테이블이 필요했다.

### Change

- `recovery_friction_signals`를 추가했다.
- `TOO_BIG_REPEAT`, `LATE_RESTART` 두 신호를 계산하도록 `FrictionSignalAnalyticsService`를 구현했다.
- 사용자/날짜/신호 타입 기준 멱등 upsert 구조로 저장했다.

### Result

- KPI를 넘어서 반복 실패 패턴을 신호 레벨로 조회할 수 있게 됐다.
- 이후 세그먼트 API가 signal table을 그대로 재사용할 수 있게 됐다.

### Portfolio Fit

- 가능
- 점수: `8/10`
- 이유: 단순 배치 집계가 아니라 후속 소비 계층을 염두에 둔 signal table 설계 사례로 설명할 수 있다.

## Case 3. 기존 분석 자산을 조합한 friction segment API

### Problem

- 시간대 분포와 signal table이 각각 있어도, 실제 사용/설명 단계에서는 사람이 읽을 수 있는 최소 세그먼트로 묶여야 했다.
- 별도 저장소를 또 만들면 지금 단계의 범위를 넘길 위험이 있었다.

### Change

- `failure_hour_reports`와 `recovery_friction_signals`를 조합하는 `FrictionSegmentQueryService`를 추가했다.
- `morning slip`, `oversized task`, `late restart` 세그먼트를 조회 API에서 동적으로 생성했다.
- 새 원천 테이블이나 배치를 추가하지 않고 기존 분석 결과를 재사용했다.

### Result

- 사용자/날짜 기준으로 `어떤 마찰 패턴이 보이는가`를 바로 읽을 수 있게 됐다.
- `Phase 11 KPI -> Phase 13 signal -> segment API`로 이어지는 분석 계층 서사가 생겼다.

### Portfolio Fit

- 가능
- 점수: `7/10`
- 이유: 새로운 플랫폼 기술을 붙인 건 아니지만, 기존 분석 자산을 조합해 해석 계층을 만드는 설계 판단을 설명하기 좋다.
