# PostgreSQL Runtime Upsert Case Note

> Updated: 2026-03-22  
> Scope: RebootFocus 로컬 런타임을 실제 PostgreSQL로 전환하고, `daily_kpi_metrics` / `daily_kpi_watermarks`에 PostgreSQL native upsert를 적용한 이유와 구현, 검증, 포트폴리오 번역 포인트를 정리한다.

## 1. 이 문서의 목적

- 이 문서는 "PostgreSQL로 바꿨다"를 넘어서, "왜 이 변경이 가치 있었는가"를 남기기 위한 케이스 노트다.
- 단순 DB 교체가 아니라 아래 3가지를 함께 설명한다.
  - 실제 PostgreSQL 런타임 검증
  - native upsert를 통한 재실행 안전성 강화
  - watermark 단조 증가 보장

## 2. 문제

변경 전 상태는 크게 두 가지 한계가 있었다.

1. 로컬 기본 프로필이 실제 PostgreSQL이 아니라 H2 PostgreSQL mode였다.
2. `daily_kpi_metrics`, `daily_kpi_watermarks` 저장 경로는 대부분 `find -> mutate -> save` 패턴이었다.

이 상태의 문제는 명확하다.

- 문서상으로는 PostgreSQL 기반 시스템이라고 설명하지만, 실제 로컬 검증은 대체 DB 흉내에 머물렀다.
- 같은 KPI를 다시 생성하거나, 더 이른 날짜를 나중에 재처리하는 경우에 대해 DB 차원의 재실행 안전성을 강하게 설명하기 어려웠다.
- 스마트태그 / Data Engineer 지원 문맥에서 `PostgreSQL 활용 경험`을 말할 때도 "설계상 PostgreSQL" 수준에 머물 위험이 있었다.

## 3. 무엇을 바꿨는가

### 3.1 로컬 기본 런타임을 실제 PostgreSQL로 전환

- 로컬 datasource를 `jdbc:postgresql://...` 기준으로 전환했다. [application-local.yml](/Users/jeonjeonghyeon/studyCollection/adhd/src/main/resources/application-local.yml#L1)
- 루트에 로컬 PostgreSQL 기동용 compose를 추가했다. [compose.yaml](/Users/jeonjeonghyeon/studyCollection/adhd/compose.yaml)
- Spring Batch 메타데이터 테이블도 PostgreSQL에서 자동 초기화되게 설정했다. [application-local.yml](/Users/jeonjeonghyeon/studyCollection/adhd/src/main/resources/application-local.yml#L10)

### 3.2 DB dialect를 감지해 PostgreSQL 전용 경로를 선택

- 런타임 DB가 PostgreSQL인지 감지하는 resolver를 추가했다. [DatabaseDialectResolver.java](/Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/focuskeeper/reboot/common/persistence/DatabaseDialectResolver.java#L10)
- 이 resolver를 기준으로 PostgreSQL일 때만 native upsert를 타고, 테스트/H2에서는 기존 JPA 경로를 유지하게 했다.

### 3.3 `daily_kpi_metrics`를 PostgreSQL `ON CONFLICT` upsert로 전환

- 자연키는 `user_id + metric_date`다.
- 같은 사용자의 같은 날짜 KPI를 다시 생성하면 새 row를 만들지 않고 기존 row를 갱신한다.
- `returning id`를 사용해 기존 row identity를 그대로 돌려받는다. [DailyKpiMetricUpsertJdbcRepository.java](/Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/focuskeeper/reboot/recovery/analytics/repository/DailyKpiMetricUpsertJdbcRepository.java#L13)
- 서비스에서는 PostgreSQL일 때 이 경로를 우선 사용한다. [DailyKpiPipelineService.java](/Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/focuskeeper/reboot/recovery/analytics/service/DailyKpiPipelineService.java#L204)

### 3.4 `daily_kpi_watermarks`를 monotonic upsert로 전환

- 자연키는 `pipeline_key + user_id`다.
- PostgreSQL `greatest(...)`를 사용해 더 이른 날짜가 나중에 들어와도 watermark가 뒤로 가지 않게 했다. [DailyKpiWatermarkUpsertJdbcRepository.java](/Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/focuskeeper/reboot/recovery/analytics/repository/DailyKpiWatermarkUpsertJdbcRepository.java#L12)
- JPA fallback 엔티티도 같은 규칙을 따르도록 `advance(...)`를 수정했다. [DailyKpiWatermark.java](/Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/focuskeeper/reboot/recovery/analytics/entity/DailyKpiWatermark.java#L73)
- 서비스 계층에서는 PostgreSQL일 때 monotonic upsert를 사용한다. [DailyKpiWatermarkService.java](/Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/focuskeeper/reboot/recovery/analytics/service/DailyKpiWatermarkService.java#L91)

## 4. 왜 이게 가치 있는가

이 변경의 가치는 "DB를 바꿨다"가 아니라 "운영 상태를 더 안전하게 저장한다"는 데 있다.

### 4.1 재실행 안전성

- KPI 생성은 같은 요청을 다시 돌릴 수 있어야 한다.
- `ON CONFLICT` upsert를 쓰면 같은 자연키에 대해 중복 row를 만들지 않고 결과를 덮어쓸 수 있다.
- 즉 `generate`, `backfill`, 운영 중 수동 재실행을 더 안전하게 설명할 수 있다.

### 4.2 상태 회귀 방지

- watermark는 "마지막 처리 지점"이다.
- 더 이른 날짜를 나중에 재생성한다고 watermark가 뒤로 가면 운영상 큰 문제가 된다.
- `greatest(existing, incoming)`는 이 상태 회귀를 DB 차원에서 막는다.

### 4.3 실제 PostgreSQL 검증 근거 확보

- 이제 로컬에서 실제 PostgreSQL로 앱을 올리고, KPI 생성과 watermark 조회를 직접 검증할 수 있다.
- 즉 "PostgreSQL을 대상으로 설계했다"가 아니라 "PostgreSQL에서 실제로 동작을 확인했다"로 말할 수 있다.

## 5. 검증한 시나리오

### 5.1 컴파일 및 기존 테스트

- `compileJava` 통과
- `DailyKpiControllerIntegrationTest` 통과
- `DailyKpiBackfillControllerIntegrationTest` 통과

특히 watermark 비회귀 테스트를 추가했다. [DailyKpiBackfillControllerIntegrationTest.java](/Users/jeonjeonghyeon/studyCollection/adhd/src/test/java/com/focuskeeper/reboot/recovery/analytics/controller/DailyKpiBackfillControllerIntegrationTest.java#L140)

### 5.2 실제 PostgreSQL 런타임 검증

검증 환경:

- PostgreSQL 컨테이너 기동
- 앱을 로컬 프로필로 부팅
- `/api/v1/health`와 KPI/watermark API 직접 호출

검증 결과:

1. `pg-upsert-user / 2026-03-22` 기준 KPI 생성 성공
2. 같은 사용자/날짜로 순차 재생성했을 때 `dailyKpiId`가 같은 값으로 유지됨
3. 이후 더 이른 날짜 `2026-03-21`를 생성해도 watermark는 `2026-03-22`로 유지됨

즉 아래 두 가지를 확인했다.

- 같은 자연키에 대한 KPI mart upsert
- monotonic watermark 유지

## 6. 현재 한계

- 이 변경은 `PostgreSQL native upsert`와 `실제 런타임 검증`의 1차 사례다.
- 아직 `EXPLAIN ANALYZE`, 인덱스 전/후 비교, query count 감소 같은 성능 수치는 남기지 않았다.
- 동일 KPI generate를 완전히 동시에 때렸을 때는 Spring Batch job 경로에서 별도 충돌 가능성이 남아 있다.

즉 지금 단계의 핵심은 `성능 최적화 완료`가 아니라 `정합성과 재실행 안전성 강화`다.

## 7. 포트폴리오/자소서로 번역하는 법

### 7.1 기술 설명 버전

- H2 호환 환경에 머물던 로컬 저장소를 실제 PostgreSQL로 전환하고, KPI mart와 watermark 저장 경로를 native upsert로 재구성했다.
- `daily_kpi_metrics`는 `user_id + metric_date` 기준 `ON CONFLICT` upsert로, `daily_kpi_watermarks`는 `GREATEST` 기반 monotonic update로 바꿔 재실행과 백필 시 상태 일관성을 강화했다.

### 7.2 운영 의미 버전

- 배치나 재처리를 다시 실행해도 중복 적재나 마지막 처리 지점 회귀가 생기지 않도록, 운영 상태를 저장하는 경로를 PostgreSQL 기준으로 강화했다.

### 7.3 스마트태그 직무 번역 버전

- Java/Spring 기반 운영 시스템에서 중요한 것은 DB 종류 자체보다 상태 일관성과 예외 상황 복구 가능성이라고 생각한다.
- RebootFocus에서는 PostgreSQL을 실제 런타임 저장소로 전환한 뒤, KPI mart와 처리 watermark를 native upsert로 설계해 중복 저장과 상태 회귀를 막고 재실행 안전성을 높였다.

## 8. 다음 1순위

이 변경 다음으로 가장 가치 있는 작업은 아래다.

1. `find -> save`가 남아 있는 다른 mart/report/signal 저장 경로까지 native upsert 확대
2. PostgreSQL 기준 query count / duration baseline 측정
3. Spring Batch 동일 job 동시 실행 제어 강화

즉 이 문서는 끝이 아니라, "실제 PostgreSQL을 가치 있게 쓰기 시작한 첫 사례" 기록이다.
