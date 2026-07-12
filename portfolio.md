# FocusLoop Backend Engineering Portfolio

## 1. 프로젝트 정의

FocusLoop는 단순 Todo API가 아니라 다음 세 단계의 데이터 grain과 상태 전이를 보존하는 백엔드 프로젝트다.

```text
Inbox: 실행 전 후보
Planning: 무엇을 언제 실행할지 결정한 구조
Execution: 실제로 수행한 시도와 실패·재시작 이력
```

핵심 질문은 기능 수가 아니라 다음 불변식을 동시 요청과 대량 데이터에서도 지킬 수 있는가였다.

- 현재 활성 상태는 하나인가?
- 과거 이력은 지우지 않고 현재 상태만 유일하게 만들 수 있는가?
- 시간 구간과 parent-child 개수처럼 equality unique로 표현하기 어려운 규칙을 어떻게 지킬 것인가?
- 대량 상태 전이에서 ORM 메모리와 DB lock/WAL 부담을 어떻게 제한할 것인가?
- 성능 주장을 실제 재현 가능한 수치로 방어할 수 있는가?

## 2. Architecture

### 도메인 경계

`inbox`, `planning`, `execution`을 비즈니스 도메인으로 두고 `common`은 기술 지원 계층으로 제한했다.

기존에는 planning이 execution repository를 직접 참조하고 execution도 planning service를 참조하는 package cycle이 있었다. Planning에 `ActiveSessionTerminator` port를 두고 execution이 구현하도록 바꿔 다음 방향으로 정리했다.

```text
inbox -> planning <- execution
             ↑
          port contract

common -> response/error/trace/config/metrics only
```

### 데이터 모델

```text
InboxItem
  ↓
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

날짜별 배치, 주간 작업 identity, 실행 가능한 child, 계획 시간, 실제 시도를 분리했다. 이 분리는 기능명보다 데이터 생명주기가 다른 객체를 서로 다른 grain으로 저장한다는 데 의미가 있다.

## 3. Case 1 — 현재 상태 유일성과 이력 보존

### 문제 정의

한 사용자가 동시에 여러 STARTED session을 갖거나, 하루 보드의 같은 slot에 여러 item이 활성 상태로 존재하면 실행 시간과 완료 판단 기준이 중복된다. 반면 과거 session과 교체 전 board entry는 삭제하지 않고 남겨야 한다.

### 기술적 원인

서비스의 `exists -> insert`는 두 트랜잭션이 모두 false를 읽을 수 있다. 일반 unique는 과거 terminal/removed row까지 막아 정상 이력 생성을 방해한다.

### 해결 전략

PostgreSQL partial unique index로 현재 상태만 unique 범위에 포함했다.

```sql
CREATE UNIQUE INDEX uq_recovery_session_active
ON recovery_session (user_id)
WHERE status = 'STARTED';

CREATE UNIQUE INDEX uq_daily_big3_entry_order
ON daily_big3_entries (daily_big3_board_id, slot_order)
WHERE removed_at IS NULL;
```

constraint 이름을 `GlobalExceptionHandler`의 known conflict map에 연결해 DB 예외를 HTTP 409와 안정적인 reason code로 변환했다.

### 의사결정

- 애플리케이션 lock만 사용하지 않은 이유: 다른 write path가 lock 규약을 빠뜨려도 DB가 최종 방어해야 한다.
- hard delete를 쓰지 않은 이유: 교체 전 배치와 종료 session은 실행 이력의 근거다.
- 일반 unique를 쓰지 않은 이유: terminal/history row와 current row의 유일성 범위가 다르다.

### 결과

동시 요청에서 성공 1건, conflict 1건, 최종 active row 1건을 검증했다. 이력 row는 그대로 보존한다.

### 남은 한계

timeout 재시도와 실제 별도 명령을 구분하는 request idempotency key는 없다.

### 면접 방어 질문

- partial index predicate와 조회 predicate가 달라지면 어떻게 되는가?
- 기존 중복 데이터가 있는 운영 DB에 unique index를 어떻게 배포할 것인가?
- constraint conflict를 409로 줄지 멱등 성공으로 반환할지 기준은 무엇인가?

## 4. Case 2 — Carryover lineage write skew

### 문제 정의

한 이전 작업에서 다음 주 후속 작업은 하나만 만들어져야 한다. 여러 후속 identity가 생기면 ExecutionUnit, Timebox, Session 이력이 서로 다른 root로 분산된다.

### 기술적 원인

두 트랜잭션이 `existsByDerivedFromItem_Id=false`를 동시에 읽은 뒤 각각 insert할 수 있었다. 부정 조건을 조회한 것은 잠글 row가 없다는 뜻이기도 하다.

### 해결 전략

nullable FK에 partial unique를 적용했다.

```sql
CREATE UNIQUE INDEX uq_big3_items_derived_from_item
ON big3_items (derived_from_item_id)
WHERE derived_from_item_id IS NOT NULL;
```

기존 active entry가 있을 때 신규 entry만 검증·응답하던 문제도 최종 board 전체를 기준으로 수정했다.

### 의사결정

source parent pessimistic lock도 현재 service path는 직렬화할 수 있다. 하지만 lineage 1:1은 데이터 자체의 불변식이므로 모든 경로를 보호하는 unique가 더 적합하다.

### 결과

두 트랜잭션이 모두 `exists=false`를 읽도록 barrier를 둔 테스트에서도 성공 1건, unique conflict 1건, derived item 1건으로 수렴했다.

### 남은 한계

runtime DDL initializer를 versioned migration으로 옮기고 중복 preflight/cleanup runbook을 추가해야 한다.

### 면접 방어 질문

- PostgreSQL unique에서 NULL은 어떻게 처리되는가?
- `NULLS NOT DISTINCT`와 partial unique 중 무엇을 선택할 것인가?
- index 생성 중 write traffic과 lock은 어떻게 관리할 것인가?

## 5. Case 3 — Aggregate 최대 child 수 동시성 제어

### 문제 정의

Big3Item당 ExecutionUnit은 최대 5개다. 4개가 있는 상태에서 동시 생성 두 건이 같은 count를 보면 최종 6개가 될 수 있다.

### 기술적 원인

부모별 `count <= N`은 단일 child unique로 표현하기 어렵다. count read와 insert 사이에 직렬화 지점이 없어서 write skew가 발생한다.

### 해결 전략

생성 transaction이 parent `Big3Item`을 `PESSIMISTIC_WRITE`로 잠근 후 child count를 검증한다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("select b from Big3Item b where b.id = :id and b.userId = :userId")
Optional<Big3Item> findByIdAndUserIdForUpdate(String id, String userId);
```

같은 parent의 생성만 직렬화되고 다른 parent의 생성은 병렬로 진행된다.

### 의사결정

- optimistic retry: 충돌 후 command 전체 재실행 정책과 retry budget이 필요하다.
- counter row: child count와 별도 상태를 원자적으로 유지해야 한다.
- DB trigger: 규칙이 schema에 숨고 application error taxonomy가 복잡해진다.
- parent lock: 생성 빈도가 낮고 critical section이 짧은 현재 조건에서 가장 작은 변경이다.

### 결과

4개 상태의 동시 생성 두 건에서 성공 1건, conflict 1건, 최종 5개를 검증했다.

### 남은 한계

동일 parent가 hot key가 되면 lock wait가 증가한다. lock timeout과 deadlock 오류를 409/503 중 무엇으로 변환할지 정해야 한다.

### 면접 방어 질문

- lock order는 어떻게 통일하는가?
- child insert 자체는 parent row lock을 자동 획득하는가?
- 처리량이 증가하면 counter/optimistic 방식으로 언제 전환할 것인가?

## 6. Case 4 — 시간 범위 invariant

### 문제 정의

한 사용자의 PLANNED timebox는 겹치면 안 된다. 기존 겹침 row를 `SELECT FOR UPDATE`해도 최초 동시 INSERT에서는 조회 결과가 0건이라 잠글 대상이 없다.

### 기술적 원인

시간 구간 overlap은 equality unique가 아니다. `READ COMMITTED`에서 두 transaction은 상대의 미커밋 phantom을 보지 못한다.

### 해결 전략

유효 구간 check와 PostgreSQL range exclusion constraint를 적용했다.

```sql
CHECK (start_at < end_at)

EXCLUDE USING gist (
  user_id WITH =,
  tstzrange(start_at, end_at, '[)') WITH &&
)
WHERE (timebox_status = 'PLANNED');
```

### 의사결정

사용자별 guard row도 가능하지만 모든 timebox write를 직렬화한다. Exclusion constraint는 실제 충돌 구간을 DB가 판정하며 사용자 간 병렬성을 유지한다.

### 결과

동시 최초 INSERT에서도 한 건만 성공하며, 인접한 `[09:00, 09:30)`, `[09:30, 10:00)` 구간은 허용한다.

### 남은 한계

GiST index의 크기, write amplification, vacuum 비용은 운영 규모에서 다시 측정해야 한다.

### 면접 방어 질문

- 왜 `[)` 경계를 선택했는가?
- cancellation status가 predicate와 어긋나면 어떻게 되는가?
- exclusion violation의 constraint name을 API 오류로 어떻게 변환하는가?

## 7. Case 5 — 대량 상태 전이의 ORM 메모리 제거

### 문제 정의

과거 OPEN Big3Item을 entity로 전부 조회해 순회하면 대상 수만큼 persistence context가 커지고 dirty checking/flush 비용이 발생한다.

### 기술적 원인

entity materialization은 DB에서 끝낼 수 있는 동일 상태 전이를 JVM heap 문제로 바꾼다. 반대로 무제한 단일 bulk update는 lock 유지 시간과 WAL burst를 키운다.

### 해결 전략

최대 100,000건 target CTE, `FOR UPDATE SKIP LOCKED`, set-based update를 짧은 transaction으로 반복했다.

```sql
WITH targets AS (
  SELECT id
  FROM big3_items
  WHERE status = 'OPEN' AND week_start < :currentWeekStart
  ORDER BY week_start, id
  LIMIT :batchSize
  FOR UPDATE SKIP LOCKED
)
UPDATE big3_items item
SET status = 'EXPIRED', expired_at = :now, version = version + 1
FROM targets
WHERE item.id = targets.id AND item.status = 'OPEN';
```

### 실측 결과

2026-07-12 로컬 PostgreSQL 14.21, JVM max heap 512 MiB:

- 300,000 rows / 4,417 ms
- 67,919 rows/s
- peak heap +3.00 MiB
- GC 0회 / 0 ms
- 최종 EXPIRED 300,000, 과거 OPEN 0

### 의사결정

- entity iteration: domain callback이 필요할 때는 유효하지만 동일 전이 대량 처리에는 비용이 크다.
- single unbounded update: 단순하지만 transaction/lock/WAL burst를 제어하기 어렵다.
- bounded set-based update: heap 사용을 제거하면서 transaction 크기를 제한한다.

### 남은 한계

현재 100,000 chunk는 단일 로컬 결과다. 운영에서는 WAL bytes, replication lag, lock wait, I/O를 기준으로 재조정해야 한다.

### 면접 방어 질문

- SKIP LOCKED가 누락이나 starvation을 만들 수 있는가?
- 장애 후 재실행해도 안전한 이유는 무엇인가?
- bulk update 후 persistence context stale 문제를 어떻게 방지했는가?

## 8. End-to-end throughput evidence

### 시나리오

한 flow는 5개 HTTP write와 13개 row 생성을 포함한다.

```text
InboxItem 3
DailyBig3Board 1
Big3Item 3
DailyBig3Entry 3
ExecutionUnit 6
```

### 결과

| offered | completed | success | flow p95 | flow p99 | HTTP req/s |
|---:|---:|---:|---:|---:|---:|
| 40 flow/s | 40.01/s | 100% | 68 ms | 375 ms | 200.07 |
| 100 flow/s | 99.89/s | 100% | 535 ms | 765 ms | 499.46 |
| 150 flow/s | 130.37/s | 100% | 2.73 s | 3.23 s | 651.86 |

100 flow/s에서는 3,001 flow가 유실 없이 완료됐다. 150 flow/s에서는 성공 응답률은 100%였지만 384 iteration drop과 max 300 VU 포화가 발생했다. 따라서 성공률만 보면 병목을 놓치며 offered load, completed throughput, dropped work, tail latency를 함께 봐야 한다.

최종 DB 검증에서도 모든 완료 flow가 `board 1 : inbox 3 : Big3Item 3 : ExecutionUnit 6` 비율을 유지했다.

### 해석

현재 로컬 단일 instance의 검증된 안정 구간은 100 flow/s, p95 535 ms다. 150 flow/s에서는 완료 처리량이 약 130 flow/s에서 포화되고 queueing으로 p95가 2.73초까지 증가했다.

### 남은 한계

- 부하 발생기와 server/DB가 같은 머신이다.
- 30초 측정이라 장기 GC/vacuum 영향이 없다.
- multi-instance와 실제 network latency가 없다.
- 수치는 운영 SLA가 아니라 재현 가능한 local evidence다.

## 9. 프로젝트 범위 축소 의사결정

analytics, friction, retrospective, ops dashboard, frontend, Airflow를 제거했다. 기능이 부족해서가 아니라 core command model의 불변식과 처리량을 더 명확하게 설명하기 위한 결정이다.

파생 분석은 core write model과 다른 변경 주기와 부하 특성을 가진다. 다시 필요해지면 core repository를 common에서 직접 참조하지 않고 별도 read model/consumer 프로젝트로 분리한다.

## 10. 이력서 요약 문장

- 다중 트랜잭션의 check-then-act 한계를 PostgreSQL partial unique와 constraint-aware HTTP 409로 방어해 활성 상태 및 carryover lineage 유일성을 보장했습니다.
- 부모별 최대 child 수 write skew를 parent row pessimistic lock으로 직렬화하고, 동시 생성 테스트에서 최종 개수 불변식을 검증했습니다.
- 시간 범위 겹침을 PostgreSQL GiST exclusion constraint로 옮겨 미커밋 phantom INSERT 상황에서도 schema 수준으로 차단했습니다.
- 대량 상태 전이를 ORM entity 순회에서 bounded set-based update로 전환해 30만 건을 4.417초, 67,919 rows/s, peak heap +3 MiB로 처리했습니다.
- Inbox→Planning→ExecutionUnit 5-request flow를 부하 테스트해 100 flow/s에서 성공률 100%, p95 535ms를 확인하고 150 flow/s에서 130.37 flow/s 포화와 tail latency 급증을 식별했습니다.
