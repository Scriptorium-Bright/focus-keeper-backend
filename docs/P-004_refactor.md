# P-004 Refactor

> Updated: 2026-03-31  
> Scope: quality 생성 경로의 다중 pass 스캔 축소 작업 메모

## 한눈에 보는 흐름

### 1. 문제

- [DailyKpiQualityService.java](/Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/focuskeeper/reboot/recovery/analytics/service/DailyKpiQualityService.java) 의 `generateFromSlices(...)`는 같은 raw 리스트를 여러 번 독립적으로 스캔한다.
- 특히 `restarts`는 현재 다음 항목을 각각 별도 stream으로 센다.
  - `duplicateRestartLinkCount`
  - `orphanRestartCount`
  - `restartBeforeFailureCount`
  - `lateRestartLinkCount`
- `sessions`도 별도 stream으로 다시 돈다.
  - `breakSessionReferenceCount`
  - `missingTimeboxReferenceCount`
- 마지막에 `countTimezoneMismatch(...)`가 다시 `sessions / failures / restarts / timeboxes`를 한 번 더 돈다.
- 즉 quality 생성은 raw 크기가 커질수록 `계산 자체보다 스캔 횟수`가 먼저 늘어나는 구조다.

### 2. 확인

- 먼저 결과 동등성을 유지해야 한다.
  - quality report issue count
  - `healthy`
  - 세부 issue 항목별 count
- P-004는 API보다 service-level 측정이 더 적절하다.
  - 대상: `DailyKpiQualityService.generateFromSlices(...)`
  - 지표: `quality duration`, 필요하면 `prepared statements`
- 그래서 전용 harness를 먼저 둔다.
  - [DailyKpiQualityPerformanceHarnessTest.java](/Users/jeonjeonghyeon/studyCollection/adhd/src/test/java/com/focuskeeper/reboot/recovery/analytics/service/DailyKpiQualityPerformanceHarnessTest.java)
  - baseline 기준: `PERF_DAILY_KPI_QUALITY_BLOCKS=600`
  - seed에는 duplicate restart, restart-before-failure, break session, missing timebox를 일부 섞어 `totalIssueCount`가 0이 아닌 상태를 만든다.

### 3. 수정

- `generateFromSlices(...)`에서 같은 리스트를 독립적으로 여러 번 돌던 부분을 `리스트당 한 번` 수준으로 줄였다.
  - `restarts`
    - duplicate/orphan/restartBeforeFailure/lateRestart/timezoneMismatch를 한 루프에서 같이 집계
  - `sessions`
    - breakSessionReference/missingTimeboxReference/timezoneMismatch를 한 루프에서 같이 집계
  - `failures`, `timeboxes`
    - timezoneMismatch만 따로 한 번씩 집계
- 즉 P-004는 DB를 덜 읽는 작업이 아니라, quality 생성 내부의 메모리 내 다중 pass를 줄인 작업이다.

### 4. 결과

- baseline:
  - 기준: local PostgreSQL, `PERF_DAILY_KPI_QUALITY_BLOCKS=600`
  - `durationMs=10`
  - `preparedStatements=3`
  - `totalIssues=1706`
- after:
  - 같은 조건에서 `durationMs=7`
  - `preparedStatements=3`
  - `totalIssues=1706`
- 해석:
  - quality duration은 `10ms -> 7ms`로 약 `30%` 감소
  - SQL 수는 변하지 않았다.
  - 즉 이번 개선은 DB round trip이 아니라, quality 계산 자체의 반복 순회를 줄여 얻은 결과다.
- 결과 동등성:
  - `DailyKpiQualityControllerIntegrationTest` 통과

### 5. 의미

- P-004는 generate 전체보다 `quality 생성 단독 비용`을 다듬는 작업이었다.
- SQL 수가 같고 duration만 줄었다는 점이, 이번 개선이 애플리케이션 레벨 계산 비용 절감이라는 걸 분명히 보여 준다.
- 따라서 포트폴리오나 면접에서는
  - `quality 규칙은 유지하면서`
  - `다중 pass를 줄여`
  - `계산 비용을 낮췄다`
  로 설명하는 게 맞다.

## 이번 라운드 할 일

1. 포트폴리오용 한 줄 문장 압축
2. 필요하면 `PERF_DAILY_KPI_QUALITY_BLOCKS`를 더 키워 재현성 한 번 더 확인
3. 다음 후보 검토

## 어떻게 써먹을 수 있나

### 포트폴리오

- `일간 KPI quality 생성에서 같은 raw 리스트를 여러 번 독립적으로 스캔하던 구조를 정리해, 품질 리포트 계산 시간을 약 30% 줄였습니다.`

### 면접

- `P-004는 SQL을 줄인 작업이 아니라 quality 규칙 계산을 더 적은 pass로 묶은 작업입니다. duplicate restart, orphan, restart-before-failure 같은 규칙은 유지하면서 restart와 session 리스트를 각각 한 번씩만 돌게 바꿨고, 그 결과 quality 생성 단독 시간은 10ms에서 7ms로 줄었습니다.`
