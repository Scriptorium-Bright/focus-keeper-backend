# Refactor Log

> 목적: Phase 진행 중 테스트/커밋 이력과 Phase 종료 시점의 실제 High/Mid/Low, 사례 초안을 기록한다.

## 운영 규칙

1. 각 `N.x` 작업이 끝날 때 테스트를 수행한다.
2. 각 `N.x` 작업이 끝날 때 커밋한다. (1 작업 = 1 논리 커밋)
3. `refactor.md`의 High/Mid/Low 정리는 `N` Phase 종료 시점에만 업데이트한다.
4. 커밋 메시지는 컨벤션(`feat`, `fix`, `refactor`, `docs`, `chore`)을 유지한다.
5. 각 Phase 종료 시에는 `docs/PHASE_EXIT_PROTOCOL.md` 기준으로 한 번 멈춰서 High/Mid/Low와 포트폴리오 사례를 정리한다.
6. 블로그로 남길 가치가 있는 의사결정이 있으면 각 Phase 종료 시점에 함께 기록한다.

주의:
- `docs/PHASE_EXIT_PROTOCOL.md`는 종료 기준/템플릿 문서다.
- 이 문서는 실제 Phase 종료 결과를 누적 기록하는 문서다.

## 심각도 기준

- `High`: 다음 Phase 시작 전 반드시 수정해야 하는 항목 (정합성/보안/장애 위험)
- `Mid`: 다음 1~2개 Phase 내 해결 권장 항목 (성능/구조 개선)
- `Low`: 기능 개발을 막지 않는 품질 개선 항목 (가독성/중복/네이밍)

## N.x 테스트/커밋 로그

| Date | Phase | N.x | Branch | Test Command | Result | Commit |
|---|---|---|---|---|---|---|
| 2026-03-03 | 1 | 1.0 | `feature/project-bootstrap` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `4c33390` |
| 2026-03-03 | 2 | 2.0 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `b630faf` |
| 2026-03-12 | 4 | 4.1 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `(local)` |
| 2026-03-12 | 4 | 4.2 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `(local)` |
| 2026-03-16 | 4 | 4.3 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `39c462b` |
| 2026-03-16 | 4 | 4.4 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `4ba4d74` |
| 2026-03-16 | 4 | 4.5 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `8e5da4c` |
| 2026-03-19 | 4 | 4.6 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `67a9e80` |
| 2026-03-19 | 5 | 5.0 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `b82f5ce` |
| 2026-03-21 | 11 | 11.1 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon --tests com.focuskeeper.reboot.recovery.analytics.controller.DailyKpiControllerIntegrationTest` | PASS | `fca1df2 / cb3eabc` |
| 2026-03-21 | 11 | 11.4 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon --tests com.focuskeeper.reboot.recovery.analytics.controller.DailyKpiBackfillControllerIntegrationTest` | PASS | `8a6dd72 / 5cfece3` |
| 2026-03-21 | 11 | 11.5 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon --tests com.focuskeeper.reboot.recovery.analytics.controller.DailyKpiQualityControllerIntegrationTest` | PASS | `48d1c5a / 8d1b9f7` |
| 2026-03-21 | 13 | 13.1 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon --tests com.focuskeeper.reboot.recovery.analytics.friction.controller.FailureHourAnalyticsControllerIntegrationTest` | PASS | `2156319 / e1ebdbb` |
| 2026-03-21 | 13 | 13.2 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon --tests com.focuskeeper.reboot.recovery.analytics.friction.controller.FrictionSignalControllerIntegrationTest` | PASS | `67647a2 / a1abb0d` |
| 2026-03-21 | 13 | 13.3 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon --tests com.focuskeeper.reboot.recovery.analytics.friction.controller.FrictionSegmentControllerIntegrationTest` | PASS | `37a360f / 894efc9` |

## Phase 종료 리팩토링 정리

작성 규칙:
- Phase 종료 시 High / Mid / Low를 반드시 채운다.
- 각 항목은 "무엇을 바꿔야 하는가"보다 "왜 다음 Phase에 영향을 주는가"가 먼저 보이게 적는다.
- 각 Phase별로 포트폴리오 적용 가능 항목도 함께 남긴다.
- 각 Phase별로 블로그로 확장 가능한 의사결정 주제도 함께 남긴다.

### Phase 1 종료

#### High

- 없음

#### Mid

- 없음

#### Low

- Health API 응답 구조를 Phase 2 공통 Envelope로 통일 필요

---

### Phase 2 종료

#### High

- 없음

#### Mid

- `api/openapi.yaml` 스키마를 공통 Envelope(`ApiResponse`/`ErrorResponse`) 기준으로 동기화 필요

#### Low

- 없음

#### Portfolio Candidates

- 공통 응답/예외/트레이스 표준화는 "기능보다 API 일관성을 먼저 고정한 이유" 사례로 설명 가능
- OpenAPI 동기화는 문서-코드 계약 관리 사례로 확장 가능

#### Blog Candidates

- 왜 기능 구현보다 공통 응답/예외/트레이스를 먼저 고정했는가
- OpenAPI를 코드와 같이 관리해야 하는 이유

---

### Phase 4 종료

#### High

- [x] 실패 체크인 이후 `10분 복귀 재시작`이 아직 없어 복귀 루프가 닫히지 않았다.

#### Mid

- [ ] 세션/실패 이벤트를 KPI 입력 스키마와 직접 연결하는 테스트가 아직 없다.

#### Low

- [ ] execution 패키지의 상태 전이 규칙을 service 단위 테스트로도 보강할 수 있다.
- [ ] 로컬 H2 기준 저장 모델을 향후 PostgreSQL 운영 스키마와 비교 검증해야 한다.

#### Portfolio Candidates

- `첫 복귀 블록 충돌 정책`은 계획을 실행으로 이어지게 만든 제약 설계 사례로 설명 가능
- `복귀 세션 상태 전이`는 도메인 상태 모델링 사례로 설명 가능
- `실패 체크인 reason taxonomy`는 행동 이벤트 구조화 사례로 설명 가능

#### Blog Candidates

- 복귀 앱의 핵심은 타이머가 아니라 상태 전이였다
- 왜 첫 복귀 블록을 강제하는가
- 실패 사유를 구조화 이벤트로 저장해야 하는 이유

#### Problem -> Solution -> Result Draft

- 문제:
  - 전날 실패한 사용자는 다음날 첫 복귀 블록이 없거나 세션 상태가 불명확하면 다시 시작할 계기를 잃는다.
- 해결:
  - Big3 기반 첫 복귀 블록을 고정하고, 세션과 실패를 명시적 상태 전이/구조화 이벤트로 모델링한 뒤 JPA 영속 저장소로 바꿨다.
- 결과:
  - 타임박스, 세션, 실패 체크인 API와 통합 테스트를 통해 핵심 복귀 루프를 영속 저장소 기준으로 검증했다.

---

### Phase 5 종료

#### High

- [ ] 같은 `failureEventId`로 재시작을 여러 번 실행할 수 있어 `RestartCount`와 `Recovery24`가 과대 집계될 수 있다. 중복 재시작 방지 규칙 또는 멱등 처리 기준을 다음 Phase 시작 전 정의해야 한다.

#### Mid

- [ ] `restart_events`가 `failureEventId`만 들고 있어 재시작이 어떤 새 세션으로 이어졌는지 직접 추적하기 어렵다. KPI mart/백필 기준을 위해 `restart -> recoverySession` 계보를 더 명시적으로 남길지 결정해야 한다.
- [ ] `failure_events`와 `restart_events`에 사용자 timezone/local 시각 기준선이 아직 없다. 시간대별 실패 패턴과 로컬 시간 진단 지표를 계산할 때 해석 오차가 생길 수 있다.

#### Low

- [ ] `RestartSuggestionPolicy`의 메시지/분 단위가 하드코딩되어 있어 정책 실험 시 코드 수정이 필요하다. 설정 또는 템플릿 분리 여지가 있다.
- [ ] execution 엔티티가 아직 직접 `toResponse()`를 가지고 있어 영속 모델과 API 응답 모델 결합도가 남아 있다. mapper 분리 여부를 추후 검토할 수 있다.

#### Portfolio Candidates

- `실패 체크인 -> 10분 재시작 -> 새 세션 시작`을 하나의 상태 전이 루프로 닫은 사례는 Phase 5 대표 문제 해결 사례로 설명 가능
- `RestartEvent`를 별도 원천 이벤트로 분리한 결정은 나중에 Recovery24/RestartCount 집계를 위해 이벤트 모델을 설계한 사례로 설명 가능

#### Blog Candidates

- 왜 실패 체크인만으로는 부족하고 별도 재시작 이벤트가 필요한가
- 10분 재시작은 UI 기능이 아니라 KPI 입력 이벤트라는 점

#### Problem -> Solution -> Result Draft

- 문제:
  - 실패 체크인만 저장하면 사용자가 실제로 다시 시작했는지 제품과 데이터에서 구분할 수 없다.
- 해결:
  - 실패 직후 재시작 제안 정책을 만들고, `restart_events`와 새 복귀 세션 시작을 하나의 API 흐름으로 묶었다.
- 결과:
  - 실패 체크인 응답에서 즉시 재시작 제안을 내려주고, 재시작 API/통합 테스트로 `failure -> restart -> new session` 루프를 검증했다.

---

### Phase 6 종료

#### High

- [ ] `weekly_retrospectives`가 현재 수동 생성 API 기반이라 주간 집계 누락 가능성이 있다. 실제 배치/스케줄 경로(`F-007`)를 다음 단계에서 붙여 자동 생성 기준을 고정해야 한다.

#### Mid

- [ ] 주간 회고 경계는 현재 `Asia/Seoul` 고정 오프셋으로 계산한다. 사용자 timezone 저장 전에는 다국가/원격 근무 사용자 주간 경계 해석에 오차가 생길 수 있다.
- [ ] `anti-slip action`은 dominant failure reason 기반 규칙만 사용한다. completion/failure/restart 조합을 더 세밀하게 반영하는 규칙 고도화 여지가 있다.

#### Low

- [ ] `TimeboxService.getTimebox()`가 현재 `WORK/BREAK` 실행 제약까지 함께 들고 있어, 이후 조회/실행 책임 분리가 한 번 더 필요할 수 있다.
- [ ] `BREAK` timebox도 현재는 `itemId`를 요구한다. 나중에 휴식이 task 비종속 개념으로 바뀌면 모델을 다시 분리해야 할 수 있다.

#### Portfolio Candidates

- `실패/재시작 원천 이벤트를 7일 단위 Rule-based retrospective로 집계한 사례`는 제품 이벤트를 의미 있는 리포트로 바꾸는 문제 해결 사례로 설명 가능
- `WORK / BREAK timebox 분리와 BREAK 세션 차단`은 계획 모델과 실행 모델의 경계를 명시적으로 나눈 사례로 설명 가능

#### Blog Candidates

- 왜 AI보다 Rule-based 주간 회고를 먼저 만들었는가
- 휴식을 실패가 아니라 BREAK timebox로 다뤄야 하는 이유

#### Problem -> Solution -> Result Draft

- 문제:
  - 복귀 이벤트가 쌓여도 사용자가 왜 다음날까지 끌렸는지 한 주 단위로 설명해주지 못하면 회고와 다음 행동 추천으로 이어지지 않는다.
- 해결:
  - `weekly_retrospectives` 집계 모델과 조회 API를 추가하고, 실패 사유 기반 `anti-slip action` 규칙을 분리했다. 동시에 `WORK / BREAK` timebox를 도입해 휴식을 실패 이벤트와 분리했다.
- 결과:
  - 주간 회고 생성/조회 API, anti-slip 규칙 테스트, BREAK 실행 제한 테스트까지 자동화해 Rule-based retrospective의 최소 경로를 검증했다.

---

### Phase 11 종료

#### High

- [ ] `daily_kpi_metrics` 생성이 아직 수동 API/배치 런처 중심이다. 정기 스케줄, 실패 재시도, 운영 알림을 포함한 오케스트레이션은 Phase 14에서 닫아야 한다.
- [ ] `DailyKpiPipelineService`가 원천 이벤트 로드, KPI 계산, mart upsert, 품질 리포트 생성, 워터마크 갱신을 한 메소드에서 함께 처리한다. 계산 규칙이 늘어나면 테스트성과 변경 영향도가 빠르게 커질 수 있다.

#### Mid

- [ ] 워터마크는 현재 사용자별 단일 `lastProcessedDate`만 관리한다. 부분 실패 지점 복구나 스테이지별 재처리가 필요해지면 granularity를 더 잘게 나눌 필요가 있다.
- [ ] DQ는 현재 리포트 생성까지는 되지만, 임계치 초과 시 배치 실패 전환이나 alert 연계는 아직 없다.

#### Low

- [ ] `DEFAULT_OFFSET = +09:00` 고정 계산이라 사용자별 timezone 모델이 들어오면 KPI 집계 기준을 분리해야 한다.
- [ ] mart 조회는 현재 API 응답 중심이라 dashboard/warehouse 소비 계층으로 확장할 때 조회 모델 분리가 한 번 더 필요할 수 있다.

#### Portfolio Candidates

- `실행/실패/재시작 이벤트를 일간 KPI mart로 적재한 사례`는 이벤트 기반 제품 백엔드를 데이터 파이프라인으로 승격한 사례로 설명 가능
- `watermark + backfill`은 재처리 가능한 배치 기준선을 만든 사례로 설명 가능
- `DQ 리포트`는 지표를 계산하는 것에서 끝나지 않고 신뢰성까지 함께 설계한 사례로 설명 가능
- `k6 스모크 테스트`는 analytics API와 배치 경계가 최소 부하에서 안정적으로 동작함을 보여주는 보조 근거로 사용 가능

#### Blog Candidates

- 왜 코호트/퍼널보다 KPI mart와 backfill/DQ를 먼저 만들었는가
- 사용자 행동 이벤트를 데이터 엔지니어링 포트폴리오로 바꾸는 방법
- 배치 파이프라인에서 워터마크를 먼저 설계해야 하는 이유

#### Problem -> Solution -> Result Draft

- 문제:
  - 복귀 기능은 동작하지만, 실행/실패/재시작 로그를 제품 지표로 읽거나 다시 계산하고 검증하는 계층이 없었다.
- 해결:
  - `11.1`에서 일간 KPI mart를 만들고, `11.4`에서 watermark/backfill, `11.5`에서 DQ 리포트를 추가해 이벤트 -> 배치 -> mart -> 품질 흐름을 닫았다.
- 결과:
  - `Recovery24`, `TTR`, `PlanExecutionRate`를 일간 집계로 조회할 수 있게 되었고, 기간 재처리와 품질 검사까지 포함한 운영 가능한 분석 파이프라인 기준선을 확보했다.

---

### Phase 13 종료

#### High

- [ ] `FailureHourAnalyticsService`와 `FrictionSignalAnalyticsService`가 모두 `+09:00` 고정 오프셋으로 날짜 경계를 계산한다. 사용자 timezone 모델 없이 phase 13 신호를 확장하면 `PeakFailureHour`와 세그먼트 해석 오차가 커질 수 있다.

#### Mid

- [ ] friction segment는 현재 `failure-hour report + signal table`을 읽어 동적으로 조합한다. 계산 결과 자체를 저장하지 않기 때문에, 이후 세그먼트 버전 관리나 배치 소비가 필요해지면 별도 report row 또는 materialized view 형태를 검토해야 한다.
- [ ] signal 종류가 `TOO_BIG_REPEAT`, `LATE_RESTART` 두 개뿐이라 `low_energy`, `next-day miss` 같은 반복 실패 패턴을 설명하기엔 아직 부족하다.

#### Low

- [ ] 날짜 파싱과 `RESOURCE_NOT_FOUND` 예외 번역이 controller/service마다 반복된다. analytics 공통 request parser나 query helper로 한 번 묶을 수 있다.
- [ ] friction segment 설명 문구는 코드에 하드코딩돼 있어 향후 카피 실험이나 다국어 대응 시 분리 여지가 있다.

#### Portfolio Candidates

- `실패 이벤트를 시간대별 분포와 peak window로 재가공한 사례`는 raw 이벤트를 파생 신호 테이블로 승격한 문제 해결 사례로 설명 가능
- `TOO_BIG_REPEAT / LATE_RESTART signal table`은 단순 KPI를 넘어서 반복 실패 패턴을 데이터 제품으로 해석한 사례로 설명 가능
- `signal table + failure-hour report를 조합한 friction segment API`는 새 원천 테이블을 늘리지 않고 기존 분석 자산을 재사용해 해석 계층을 만든 사례로 설명 가능

#### Blog Candidates

- KPI 이후 어떤 기준으로 signal table을 추가해야 하는가
- peak failure hour가 단순 통계가 아니라 제품 행동 신호가 되는 이유
- 새로운 저장소를 만들지 않고 기존 mart와 signal을 조합해 해석 API를 만드는 방법

#### Problem -> Solution -> Result Draft

- 문제:
  - 일간 KPI만으로는 사용자가 왜 반복해서 무너지는지 설명하기 어려웠고, 시간대별 실패 집중과 반복 실패 유형을 구분해 볼 계층이 없었다.
- 해결:
  - `13.1`에서 시간대별 실패 분포와 `PeakFailureHour`를 계산하고, `13.2`에서 `TOO_BIG_REPEAT`, `LATE_RESTART` signal table을 만들었다. 그 위에 `13.3`에서 기존 분석 결과를 조합한 friction segment API를 추가했다.
- 결과:
  - 원천 이벤트를 직접 읽지 않고도 `morning slip`, `oversized task`, `late restart` 같은 최소 해석 세그먼트를 사용자/날짜 기준으로 조회할 수 있게 됐다.

---

### 템플릿 (다음 Phase용)

#### Phase N 종료

##### High

- [ ] 항목

##### Mid

- [ ] 항목

##### Low

- [ ] 항목

##### Blog Candidates

- [ ] 항목

---

## 포트폴리오 사례 템플릿

각 Phase 종료 시 최소 1개 이상 작성한다.

### Case N

- 문제:
- 해결:
- 결과:
- 사용한 증거:
  - 테스트:
  - KPI/로그:
  - 다이어그램:

## Phase 종료 템플릿

```text
### Phase N 종료

#### High
- [ ] ...

#### Mid
- [ ] ...

#### Low
- [ ] ...

#### Portfolio Candidates
- ...

#### Problem -> Solution -> Result Draft
- 문제:
- 해결:
- 결과:
```
