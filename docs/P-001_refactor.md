# P-001 Refactor

> Updated: 2026-03-31  
> Scope: generate와 quality 경로의 raw 중복 조회 축소 작업 메모

## 한눈에 보는 흐름

### 1. 문제

- 현재 `POST /api/v1/recovery/analytics/kpis/daily`는 한 번의 generate 요청 안에서 raw 범위를 사실상 두 번 읽는다.
- 첫 번째 조회는 [DailyKpiPipelineService.java](/Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/focuskeeper/reboot/recovery/analytics/service/DailyKpiPipelineService.java) 의 `generate()`에서 수행된다.
  - `session`
  - `failure`
  - `restart`
  - `timebox`
- 두 번째 조회는 [DailyKpiQualityService.java](/Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/focuskeeper/reboot/recovery/analytics/service/DailyKpiQualityService.java) 의 `generate()`에서 다시 수행된다.
  - `session`
  - `failure`
  - `restart`
  - 세션이 참조한 `timebox`
  - restart가 참조한 `failureOccurredAt`
- 즉 generate API 한 번이 `metric 계산 + quality 계산`을 위해 같은 날짜 범위 raw를 다시 읽는 구조다.

### 2. 확인

- 먼저 결과 동등성 기준을 유지해야 한다.
  - `DailyKpiControllerIntegrationTest`
  - `DailyKpiQualityControllerIntegrationTest`
- baseline은 두 층으로 나눠 잡는다.
  - service-level: query count / prepared statements / duration
  - API-level: `k6` 기준 `p50 / p95`
- service-level baseline은 [DailyKpiGeneratePerformanceHarnessTest.java](/Users/jeonjeonghyeon/studyCollection/adhd/src/test/java/com/focuskeeper/reboot/recovery/analytics/service/DailyKpiGeneratePerformanceHarnessTest.java) 로 잡는다.
- 대상 API: `POST /api/v1/recovery/analytics/kpis/daily`
  - 지표: `p50 / p95`, `prepared statements` 또는 query count
- API-level 측정은 전용 스크립트 [p001_daily_kpi_generate.js](/Users/jeonjeonghyeon/studyCollection/adhd/perf/k6/p001_daily_kpi_generate.js) 로 본다.
  - `setup()`에서 `inbox -> big3 -> timeboxes -> sessions -> failure -> restart` 순서로 raw를 만든다.
  - `default()`는 generate API만 호출해 `p50 / p95`를 본다.
  - execution API는 이벤트 시각을 직접 받지 않기 때문에, `METRIC_DATE`는 `k6`를 실행하는 날짜와 맞춰야 한다.
  - `VUS=4`로 바로 때리면 `DailyKpiBatchLauncher`가 내부 Spring Batch job launch 충돌을 먼저 일으켜 `CannotAcquireLockException`이 난다.
  - 그래서 P-001 비교는 `VUS=1` 순차 반복 기준으로 잡는 것이 맞다.

### 3. 수정

- P-001의 핵심은 `generate에서 이미 읽은 raw slice를 quality에 재사용`하게 만드는 것이다.
- 실제 수정은 이 방향으로 넣었다.
  - `DailyKpiPipelineService.generate()`
    - 같은 날 `timebox`를 한 번만 읽고, `dailyRestarts`와 `timeboxesById`를 만든 뒤 quality 경로에 같이 넘기게 했다.
  - `DailyKpiQualityService`
    - `generateFromLoadedRaw(...)`를 추가해, 일반 generate 경로에서도 이미 읽은 `sessions / failures / restarts`를 재사용하게 했다.
    - restart가 참조한 failure 시각도 `dailyFailures`에 있는 것은 재사용하고, 없는 것만 추가 조회하게 바꿨다.
- 즉 P-001은 `P-006`에서 먼저 분리해둔 helper를 일반 generate 경로에 연결하는 형태로 닫았다.

### 4. 결과

- service-level 전/후 비교는 확보했다.
  - 기준: local PostgreSQL, `PERF_DAILY_KPI_BLOCKS=12`
  - 변경 전: `durationMs=19`, `preparedStatements=12`
  - 변경 후: `durationMs=29`, `preparedStatements=7`
  - prepared statement는 `12 -> 7`로 약 `42%` 감소
  - duration은 작은 단일 측정이라 변동폭이 있어, API-level `k6`로 한 번 더 보는 게 맞다.
- API-level `k6` 전/후 비교도 확보했다.
  - 기준: local PostgreSQL, `VUS=1`, `ITERATIONS=24`, public API seed 포함
  - 변경 전:
    - `avg=56.42ms`
    - `p90=70.23ms`
    - `p95=76.16ms`
  - 변경 후:
    - `avg=54.10ms`
    - `p90=62.77ms`
    - `p95=64.44ms`
  - 해석:
    - `avg`는 약 `4.1%` 감소
    - `p90`은 약 `10.6%` 감소
    - `p95`는 약 `15.4%` 감소
- 관련 결과 동등성 테스트도 유지됐다.
  - `DailyKpiControllerIntegrationTest`
  - `DailyKpiQualityControllerIntegrationTest`
- 출력 위치
  - `build/test-results/test/TEST-com.focuskeeper.reboot.recovery.analytics.service.DailyKpiGeneratePerformanceHarnessTest.xml`
- baseline은 `P-001` 이전 worktree에서 재고, after는 현재 worktree에서 같은 스크립트로 다시 측정했다.
- baseline worktree는 `friction` 패키지 이동 중인 상태 때문에 단독 부팅이 되지 않아, 임시 측정용으로 observability import만 이전 패키지에 맞게 보정했다.

### 5. 의미

- 이 케이스는 backfill 같은 배치 경로가 아니라, 사용자가 직접 호출하는 generate API의 낭비를 줄이는 작업이다.
- 따라서 `P-006`보다 `k6`가 더 적절하고, 결과도 `API 응답 시간 개선`과 `DB round trip 감소`로 설명하는 게 맞다.
- 다만 이 generate API는 내부적으로 Spring Batch launcher를 호출하므로, 무작정 동시 부하를 올리면 P-001보다 job launch lock이 먼저 병목이 된다.
- 그래서 이번 라운드는 `service-level query count 감소`와 `sequential API 응답 시간 개선`을 함께 보는 쪽으로 닫았다.
- 결론적으로 P-001은
  - raw 중복 조회를 줄여 statement 수를 줄였고
  - public API 기준에서도 `p95`를 낮추는 방향으로 이어졌다고 정리할 수 있다.

## 현재 코드 기준 메모

- [DailyKpiPipelineService.java](/Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/focuskeeper/reboot/recovery/analytics/service/DailyKpiPipelineService.java)
  - `generate()`에서 raw를 조회한 뒤 `generateMetric(...)`을 호출한다.
  - 지금은 같은 raw를 `dailyKpiQualityService.generateFromLoadedRaw(...)`에도 넘겨 재사용한다.
- [DailyKpiQualityService.java](/Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/focuskeeper/reboot/recovery/analytics/service/DailyKpiQualityService.java)
  - `generateFromLoadedRaw(...)`가 일반 generate 경로의 재사용 진입점 역할을 한다.
- 즉 P-001은 완전히 새로운 구조를 도입하는 작업이 아니라, 이미 분리된 helper를 일반 generate 경로에 연결해 중복 조회를 줄인 작업으로 보는 게 맞다.

## 이번 라운드 할 일

1. 결과를 포트폴리오용 문장으로 압축
2. 필요하면 `ITERATIONS`를 더 늘려 재현성 한 번 더 확인
3. 다음 후보 `P-004`로 이동

## k6 실행 메모

- 스크립트: [p001_daily_kpi_generate.js](/Users/jeonjeonghyeon/studyCollection/adhd/perf/k6/p001_daily_kpi_generate.js)
- seed 방식
  - `setup()`에서 사용자 여러 명을 만들고, 각 사용자마다 완료 세션 2개 + 실패 후 재시작 1개를 만든다.
  - `default()`는 seed된 사용자 중 하나를 골라 `POST /api/v1/recovery/analytics/kpis/daily`만 호출한다.
- 실행 예시

```bash
K6_WEB_DASHBOARD=false \
BASE_URL=http://127.0.0.1:18084 \
METRIC_DATE=2026-03-31 \
VUS=1 \
ITERATIONS=24 \
k6 run perf/k6/p001_daily_kpi_generate.js
```

## 어떻게 써먹을 수 있나

### 포트폴리오

- `일간 KPI generate API에서 metric 계산과 quality 계산이 같은 raw 범위를 중복 조회하던 구조를 정리해, 응답 시간과 DB round trip을 함께 줄였습니다.`

### 면접

- `처음엔 generate와 quality를 각각 독립된 단계로 두는 쪽이 읽기 쉬웠지만, 실제로는 같은 날짜 범위를 다시 읽는 낭비가 있었습니다. 그래서 generate가 이미 읽은 slice를 quality 경로에서도 재사용하게 바꿔 API 요청 한 번당 중복 조회를 줄이려 했습니다.`

### 키워드

- `generate API`
- `raw 중복 조회`
- `quality 재사용`
- `query count 감소`
- `k6 baseline`
