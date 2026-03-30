# P-006 Refactor

> Updated: 2026-03-31  
> Scope: backfill 직렬 재조회 축소 작업 메모

## 무엇을 바꿨나

- `DailyKpiBackfillService`
  - 날짜를 하루씩 돌 때마다 raw 데이터를 다시 읽지 않도록, 구간 전체의 `session / failure / restart / timebox`를 한 번만 읽고 날짜별로 재사용하게 바꿨다.
- `DailyKpiPipelineService`
  - KPI metric 계산 로직을 `generateMetric(...)`으로 분리해, backfill이 prefetch한 slice를 그대로 넘길 수 있게 했다.
- `DailyKpiQualityService`
  - quality 계산도 `generateFromSlices(...)`로 분리해, 날짜마다 raw를 다시 읽지 않게 했다.
- `watermark`
  - backfill 중 날짜마다 전진시키지 않고, 마지막 날짜 기준으로 한 번만 갱신하게 바꿨다.

## 왜 바꿨나

- 기존 backfill은 날짜 범위가 길어질수록 같은 raw 범위를 반복 조회했다.
- 구조상 가장 먼저 줄일 수 있는 낭비가 `직렬 재조회`였고, `P-006`의 핵심도 여기에 있다.
- 목표는 "backfill이 된다"가 아니라, "긴 범위 재처리에서도 구조적으로 덜 비싸게 돈다"로 올리는 것이다.

## 지금 확인된 것

- 관련 영향 테스트 통과
  - `DailyKpiBackfillControllerIntegrationTest`
  - `DailyKpiControllerIntegrationTest`
  - `DailyKpiQualityControllerIntegrationTest`
- 즉 결과 동등성 기준은 유지한 상태다.
- 성능 harness로 7일 / 30일 backfill 전후 수치를 확인했다.

## 측정 결과

- 기준
  - local PostgreSQL
  - `DailyKpiBackfillPerformanceHarnessTest`
  - 전 상태는 `P-006` 변경 대상 3개 파일만 이전 버전으로 되돌린 비교 worktree에서 측정
- 7일 backfill
  - 변경 전: `125ms`, `preparedStatements=85`
  - 변경 후: `35ms`, `preparedStatements=27`
- 30일 backfill
  - 변경 전: `983ms`, `preparedStatements=361`
  - 변경 후: `179ms`, `preparedStatements=96`

## 아직 남은 것

- `90일 backfill`까지 한 번 더 재서 긴 구간 추세를 확인
- 필요하면 날짜당 평균 처리 시간도 같이 정리

## 어떻게 써먹을 수 있나

### 포트폴리오

- `날짜 범위 backfill 경로에서 같은 raw 데이터를 반복 조회하던 구조를 range prefetch 기반으로 바꿔 재처리 비용을 줄였습니다.`
- `30일 backfill 기준으로 실행 시간을 983ms에서 179ms로 줄였고, prepared statement 수도 361개에서 96개로 낮췄습니다.`

### 면접

- `기존에는 날짜를 하루씩 돌며 generate를 반복 호출해 같은 raw 범위를 여러 번 읽었습니다. 이를 backfill 시작 시 한 번만 읽고 날짜별로 재사용하는 구조로 바꿨고, 결과가 같다는 것은 integration test로 먼저 확인했습니다.`
- `그 뒤 performance harness로 7일과 30일 backfill을 비교해, 긴 구간일수록 개선폭이 더 커지는 것도 같이 확인했습니다.`

### 키워드

- `backfill`
- `range prefetch`
- `raw 중복 조회 축소`
- `결과 동등성 유지`
- `watermark 단일 advance`
