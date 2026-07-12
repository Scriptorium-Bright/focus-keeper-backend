# Focus-Loop

계획 후보를 수집하고, 오늘 실행할 작업으로 구조화한 뒤, 실제 실행·실패·재시작 이력을 보존하는 Spring Boot/PostgreSQL 백엔드입니다.

프로젝트 범위는 다음 네 계층으로 고정합니다.

```text
inbox      작업 후보 수집 도메인
planning   작업 선택·분해·시간 배정 도메인
execution  실제 실행 시도·실패·재시작 도메인
common     세 도메인을 지원하는 기술 계층
```

analytics, friction, retrospective, 별도 ops API, 프론트엔드, Airflow orchestration은 제거했습니다.

## Domain Model

```text
InboxItem
  ↓ origin
Big3Item ← DailyBig3Entry → DailyBig3Board
  ↓
ExecutionUnit
  ↓
Timebox
  ↓
RecoverySession
  ├─ FailureEvent
  └─ RestartEvent
```

### Inbox

실행 구조에 넣기 전의 작업 후보를 append 중심으로 저장합니다. planning이나 execution 상태를 소유하지 않습니다.

- aggregate: `InboxItem`
- 핵심 명령: 여러 후보 저장
- 경계: 후보 내용과 생성 시각까지만 책임

### Planning

사용자의 실행 의도를 구조화합니다. 날짜별 배치, 주간 작업 identity, 실행 단위, 시간 범위를 서로 다른 grain으로 관리합니다.

- aggregate/root: `DailyBig3Board`, `Big3Item`, `ExecutionUnit`, `Timebox`
- history: `DailyBig3Entry.removedAt`
- 핵심 불변식
  - 사용자·날짜별 보드 하나
  - 활성 slot/item 중복 금지
  - carryover lineage 1:1
  - Big3Item별 ExecutionUnit 최대 5개
  - PLANNED timebox 시간 범위 겹침 금지

### Execution

계획이 실제로 수행된 한 번의 시도와 그 결과를 기록합니다. 계획 자체를 변경하지 않고 실행 lifecycle을 소유합니다.

- aggregate/root: `RecoverySession`
- event: `FailureEvent`, `RestartEvent`
- 핵심 불변식
  - 사용자별 활성 session 하나
  - terminal session 재전이 금지
  - failure별 restart event 하나

### Common

도메인 개념을 소유하지 않고 공통 기술만 제공합니다.

- 표준 API 응답과 오류 taxonomy
- constraint 이름 기반 HTTP 409 변환
- trace id
- OpenAPI 설정
- PostgreSQL invariant 초기화
- core write metric 기록

planning은 execution repository를 직접 참조하지 않습니다. `ActiveSessionTerminator` port를 execution이 구현해 도메인 의존 방향을 단방향으로 유지합니다.

## Consistency Strategy

- active session: PostgreSQL partial unique index
- active board entry: slot/item partial unique index
- carryover lineage: nullable partial unique index
- timebox period: check + GiST exclusion constraint
- ExecutionUnit 최대 개수: parent row `PESSIMISTIC_WRITE`
- lifecycle 경쟁: JPA `@Version`
- 대량 만료: `FOR UPDATE SKIP LOCKED` + bounded set-based update

## Measured Throughput

2026-07-12, 로컬 단일 인스턴스, PostgreSQL 14.21, Hikari max 17 환경의 실측입니다.

### Core write flow

한 flow는 Inbox 3건 저장 → Daily Big3 3건 선택 → 각 Big3Item에 ExecutionUnit 2건 생성으로 구성됩니다.

| 요청 부하 | 완료 처리량 | 성공률 | flow p95 | flow p99 | HTTP 처리량 |
|---:|---:|---:|---:|---:|---:|
| 40 flow/s | 40.01 flow/s | 100% | 68 ms | 375 ms | 200.07 req/s |
| 100 flow/s | 99.89 flow/s | 100% | 535 ms | 765 ms | 499.46 req/s |
| 150 flow/s | 130.37 flow/s | 100% | 2.73 s | 3.23 s | 651.86 req/s |

150 flow/s에서는 384 iteration이 유실되고 300 VU 상한에 도달했습니다. 이 환경의 안정 운용 기준은 `100 flow/s, p95 < 1초`이며, 150 flow/s는 포화 구간입니다.

### Weekly expiration

- 대상: 과거 OPEN Big3Item 300,000건
- 결과: 300,000건 EXPIRED, 남은 과거 OPEN 0건
- 처리 시간: 4,417 ms
- 처리량: 67,919 rows/s
- peak heap 증가: 3.00 MiB
- GC: 0회 / 0 ms

상세 조건과 재현 명령은 `portfolio.md`와 `perf/results/core-throughput/README.md`에 기록합니다.

## API

- Inbox
  - `POST /api/v1/recovery/inbox-items`
- Planning
  - `POST /api/v1/recovery/big3`
  - `GET /api/v1/recovery/big3/today`
  - `POST /api/v1/recovery/execution-units`
  - `POST /api/v1/recovery/execution-units/multiple`
  - `POST /api/v1/recovery/timeboxes`
- Execution
  - `POST /api/v1/recovery/sessions/start`
  - `POST /api/v1/recovery/sessions/complete`
  - `POST /api/v1/recovery/failures/check-in`
  - `POST /api/v1/recovery/restarts`

Swagger UI: `http://localhost:10080/swagger-ui.html`  
OpenAPI JSON: `http://localhost:10080/api-docs`

## Run

```bash
docker compose up -d postgres
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootRun
```

기본값:

- `DB_HOST=localhost`
- `DB_PORT=5432`
- `DB_NAME=rebootfocus_oom`
- `DB_USERNAME=rebootfocus`
- `DB_PASSWORD=rebootfocus`

## Test

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon
```

## Reproduce Throughput

```bash
FLOW_RATE=100 DURATION=30s RUN_ID=local \
  k6 run perf/k6/load-test.js

PERF_EXPIRATION_ROWS=300000 \
PERF_EXPIRATION_MAX_HEAP=512m \
PERF_EXPIRATION_CONFIRM_DEDICATED_DB=true \
  ./gradlew expirationMemoryHarness --no-daemon --rerun-tasks
```

수치는 로컬 단일 실행 결과이며 운영 SLA로 일반화하지 않습니다.
