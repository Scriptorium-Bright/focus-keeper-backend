# Execution Grain Spec

> Version: v1.3
> Updated: 2026-06-06
> Status: Source of Truth
> Scope: Daily Big3, weekly item identity, ExecutionUnit, Timebox, Session

## 1. 결정

별도 `Big3Task` 엔티티를 추가하지 않는다.

명칭은 다음처럼 변경한다.

```text
Big3Selection     -> DailyBig3Board
Big3SelectionItem -> Big3Item
```

legacy 코드의 `Big3SelectionItem`은 목표 모델의 `Big3Item`이며 이미 다음 책임을 가진다.

- InboxItem에서 선택된 실제 Big3 작업
- ExecutionUnit 소유자
- 하위 작업 완료 상태의 roll-up 대상

부족한 것은 작업 엔티티가 아니라 날짜별 선택 기록이다. 따라서 `DailyBig3Entry`를 추가한다.

```text
DailyBig3Board
-> DailyBig3Entry
   -> Big3Item
      -> ExecutionUnit
         -> Timebox
            -> RecoverySession
```

관계 cardinality:

```text
DailyBig3Board 1 : N DailyBig3Entry
Big3Item        1 : N DailyBig3Entry
Big3Item        1 : N ExecutionUnit
ExecutionUnit   1 : N Timebox
Timebox         1 : N RecoverySession
```

따라서 `DailyBig3Board`와 `Big3Item`의 논리적 N:M 관계를 `DailyBig3Entry` 연결 엔티티로 풀어낸 구조다.

## 2. 객체별 Grain

| 객체 | Grain | 의미 | Lifecycle |
|---|---|---|---|
| `DailyBig3Board` | user/date | 하루 Big3 보드 | 없음 |
| `DailyBig3Entry` | daily placement | 그날 보드의 한 자리에 작업을 선택한 기록 | 선택 해제/교체 |
| `Big3Item` | weekly item | 같은 주 안에서 유지되는 실제 Big3 작업 | OPEN/COMPLETED/ABANDONED/EXPIRED |
| `ExecutionUnit` | unit | 실제로 완료해야 하는 하위 작업 | PLANNED/COMPLETED |
| `Timebox` | planned block | 언제 얼마나 실행할지 정한 계획 | PLANNED/CANCELLED |
| `RecoverySession` | attempt | Timebox를 실제 수행한 한 번의 시도 | STARTED/COMPLETED/INTERRUPTED |
| `FailureEvent` | failure | 사용자가 실패로 체크인한 사건 | immutable |
| `RestartEvent` | restart | 특정 실패 이후 다시 시작한 사건 | immutable |

## 3. 테이블과 자료형

### 3.1 자료형 기준

아래 표는 목표 운영 스키마 기준이다.
구현 착수 시점은 `docs/goal/IMPLEMENTATION_SCOPE.md`를 따른다.

| 의미 | PostgreSQL | Java/JPA |
|---|---|---|
| 현재 UUID 문자열 ID | `varchar(36)` | `String` |
| 날짜 | `date` | `LocalDate` |
| 시간대 포함 시각 | `timestamp with time zone` | `OffsetDateTime` |
| enum | `varchar(N)` | `enum` + `@Enumerated(EnumType.STRING)` |
| 참/거짓 | `boolean` | `boolean` |
| 순서·분 단위 정수 | `integer` | `int` / `Integer` |
| 분류 confidence | `numeric(5,4)` | `BigDecimal` |
| optimistic lock | `bigint` | `long` + `@Version` |

현재 ID가 `String` 기반이므로 명칭 변경과 관계 분리 단계에서는 `varchar(36)`을 유지한다.
추후 PostgreSQL native `uuid`로 바꾸는 작업은 별도 마이그레이션으로 다룬다.

`NULL 허용`은 최종 목표 기준이다.
additive migration 중에는 backfill을 위해 일부 컬럼을 일시적으로 nullable하게 추가할 수 있다.

### 3.2 `inbox_items`

`InboxItem`은 아직 실행 구조에 배치되지 않은 작업 후보이며, `Big3Item` 생성의 출처다.

| 컬럼 | PostgreSQL 타입 | Java 타입 | NULL 허용 | 키/제약 | 의미 |
|---|---|---|---|---|---|
| `id` | `varchar(36)` | `String` | 아니오 | PK | Inbox 후보 ID |
| `user_id` | `varchar(100)` | `String` | 아니오 | index 권장 | 소유 사용자 |
| `content` | `varchar(200)` | `String` | 아니오 |  | 원본 작업 내용 |
| `created_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 |  | Inbox에 입력된 시각 |

`InboxItem`은 `DailyBig3Board`나 `DailyBig3Entry`가 직접 참조하지 않는다.
`Big3Item.origin_inbox_item_id`가 원본 Inbox 후보를 참조한다.

### 3.3 `daily_big3_boards`

`DailyBig3Board`는 사용자와 날짜 단위의 Daily Big3 헤더다.

| 컬럼 | PostgreSQL 타입 | Java 타입 | NULL 허용 | 키/제약 | 의미 |
|---|---|---|---|---|---|
| `id` | `varchar(36)` | `String` | 아니오 | PK | Daily Big3 보드 ID |
| `user_id` | `varchar(100)` | `String` | 아니오 | `unique(user_id, selected_date)` | 소유 사용자 |
| `selected_date` | `date` | `LocalDate` | 아니오 | `unique(user_id, selected_date)` | 사용자 ZoneId 기준 보드 날짜 |
| `selected_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 |  | 최초 Big3 선택 시각 |
| `created_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 |  | row 생성 시각 |
| `updated_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 |  | 마지막 변경 시각 |

보드에는 작업 상태, slot, `ExecutionUnit` FK를 저장하지 않는다.
하루 최대 세 자리 규칙은 활성 `DailyBig3Entry` 수로 검증한다.

### 3.4 `daily_big3_entries`

`DailyBig3Entry`는 특정 날짜의 보드 한 자리에 어떤 `Big3Item`을 선택했는지 기록한다.

| 컬럼 | PostgreSQL 타입 | Java 타입 | NULL 허용 | 키/제약 | 의미 |
|---|---|---|---|---|---|
| `id` | `varchar(36)` | `String` | 아니오 | PK | 날짜별 선택 기록 ID |
| `daily_big3_board_id` | `varchar(36)` | `String` / `DailyBig3Board` | 아니오 | FK → `daily_big3_boards.id` | 어느 날짜 보드의 선택인지 |
| `big3_item_id` | `varchar(36)` | `String` / `Big3Item` | 아니오 | FK → `big3_items.id` | 선택된 실제 작업 |
| `slot_order` | `integer` | `int` | 아니오 | 활성 row 기준 1~3 | 그날 Big3 자리 |
| `selection_source` | `varchar(20)` | `SelectionSource` | 아니오 | `NEW`, `CARRYOVER` | 신규 선택인지 같은 주 이월인지 |
| `selected_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 |  | 해당 보드에 배치된 시각 |
| `removed_at` | `timestamp with time zone` | `OffsetDateTime` | 예 | partial index 조건 | 교체·선택 해제 시각, 활성 row는 `NULL` |

필수 index와 제약:

```sql
create index idx_daily_big3_entries_board
    on daily_big3_entries (daily_big3_board_id);

create index idx_daily_big3_entries_item_selected_at
    on daily_big3_entries (big3_item_id, selected_at);

create unique index uk_daily_big3_entries_active_slot
    on daily_big3_entries (daily_big3_board_id, slot_order)
    where removed_at is null;

create unique index uk_daily_big3_entries_active_item
    on daily_big3_entries (daily_big3_board_id, big3_item_id)
    where removed_at is null;
```

`removed_at`은 반드시 nullable이다.
`removed_at is null`이 현재 보드에 활성 배치된 entry를 뜻한다.

### 3.5 `big3_items`

`Big3Item`은 같은 주 안에서 유지되는 실제 작업 identity이며 `ExecutionUnit`의 소유자다.

| 컬럼 | PostgreSQL 타입 | Java 타입 | NULL 허용 | 키/제약 | 의미 |
|---|---|---|---|---|---|
| `id` | `varchar(36)` | `String` | 아니오 | PK | 실제 Big3 작업 ID |
| `user_id` | `varchar(100)` | `String` | 아니오 | index 권장 | 소유 사용자 |
| `week_start` | `date` | `LocalDate` | 아니오 | index 권장 | 사용자 ZoneId 기준 월요일 |
| `origin_inbox_item_id` | `varchar(36)` | `String` / `InboxItem` | 아니오 | FK → `inbox_items.id` | 이 작업을 만든 Inbox 후보 |
| `title_snapshot` | `varchar(200)` | `String` | 아니오 |  | 생성 당시 Inbox 내용 보존 |
| `goal_category` | `varchar(50)` | `GoalCategory` | 예 | NEXT | 목표·업무 도메인 분류 |
| `goal_category_confidence` | `numeric(5,4)` | `BigDecimal` | 예 | `0.0000~1.0000` | 분류 신뢰도 |
| `goal_category_source` | `varchar(30)` | `ClassificationSource` | 예 |  | RULE, USER_CORRECTED 등의 분류 출처 |
| `goal_classification_version` | `varchar(50)` | `String` | 예 |  | 분류 규칙·모델 버전 |
| `status` | `varchar(20)` | `Big3ItemStatus` | 아니오 | `OPEN`, `COMPLETED`, `ABANDONED`, `EXPIRED` | 주간 작업 lifecycle |
| `created_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 |  | item 생성 시각 |
| `completed_at` | `timestamp with time zone` | `OffsetDateTime` | 예 |  | 완료 시각 |
| `abandoned_at` | `timestamp with time zone` | `OffsetDateTime` | 예 |  | 사용자가 포기한 시각 |
| `expired_at` | `timestamp with time zone` | `OffsetDateTime` | 예 |  | 주간 reset으로 만료된 시각 |
| `derived_from_item_id` | `varchar(36)` | `String` / `Big3Item` | 예 | self FK → `big3_items.id` | 다음 주에 새로 만든 후속 item의 이전 item |
| `version` | `bigint` | `long` | 아니오 | optimistic lock, default 0 | 완료와 주간 sweep 경쟁 제어 |

권장 index:

```sql
create index idx_big3_items_user_week_status
    on big3_items (user_id, week_start, status);
```

`NOT_STARTED`, `IN_PROGRESS`는 `Big3Item.status`에 저장하지 않는다.
이는 하위 `ExecutionUnit` 상태로 계산하는 화면용 `completionStatus`다.

### 3.6 `execution_units`

`ExecutionUnit`은 `Big3Item`을 실제 실행하고 완료 여부를 판단할 수 있게 쪼갠 하위 작업이다.

| 컬럼 | PostgreSQL 타입 | Java 타입 | NULL 허용 | 키/제약 | 의미 |
|---|---|---|---|---|---|
| `id` | `varchar(36)` | `String` | 아니오 | PK | 실행 단위 ID |
| `big3_item_id` | `varchar(36)` | `String` / `Big3Item` | 아니오 | FK → `big3_items.id` | 상위 실제 작업 |
| `title` | `varchar(200)` | `String` | 아니오 |  | 바로 실행 가능한 하위 작업명 |
| `unit_category` | `varchar(50)` | `UnitCategory` | 예 | NEXT | 실제 실행 행위 분류 |
| `unit_category_confidence` | `numeric(5,4)` | `BigDecimal` | 예 | `0.0000~1.0000` | 분류 신뢰도 |
| `unit_category_source` | `varchar(30)` | `ClassificationSource` | 예 |  | 분류 출처 |
| `unit_classification_version` | `varchar(50)` | `String` | 예 |  | 분류 규칙·모델 버전 |
| `status` | `varchar(20)` | `ExecutionUnitStatus` | 아니오 | `PLANNED`, `COMPLETED` | 명시적 작업 완료 상태 |
| `completed_at` | `timestamp with time zone` | `OffsetDateTime` | 예 |  | 사용자가 실제 완료한 시각 |
| `created_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 |  | 실행 단위 생성 시각 |
| `updated_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 |  | 제목·상태 마지막 변경 시각 |

권장 index:

```sql
create index idx_execution_units_big3_item
    on execution_units (big3_item_id, created_at);
```

`ExecutionUnit`에는 `daily_big3_board_id`나 `daily_big3_entry_id`를 저장하지 않는다.
같은 item이 다른 날짜에 carryover되어도 동일한 실행 단위를 유지하기 위해서다.

### 3.7 `recovery_timeboxes`

`Timebox`는 작업 완료가 아니라 언제 실행할지 정한 계획 블록이다.

| 컬럼 | PostgreSQL 타입 | Java 타입 | NULL 허용 | 키/제약 | 의미 |
|---|---|---|---|---|---|
| `id` | `varchar(36)` | `String` | 아니오 | PK | 계획 블록 ID |
| `user_id` | `varchar(100)` | `String` | 아니오 | index 권장 | 소유 사용자 |
| `execution_unit_id` | `varchar(36)` | `String` / `ExecutionUnit` | 아니오 | FK → `execution_units.id` | 어떤 하위 작업의 계획인지 |
| `item_content` | `varchar(200)` | `String` | 아니오 |  | 생성 당시 ExecutionUnit 제목 snapshot |
| `timebox_type` | `varchar(20)` | `TimeboxType` | 아니오 | `WORK`, `BREAK` | 작업·휴식 블록 구분 |
| `timebox_status` | `varchar(20)` | `TimeboxStatus` | 아니오 | `PLANNED`, `CANCELLED_BY_TASK_COMPLETION`, `CANCELLED_BY_USER` | 계획 블록 상태 |
| `start_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 | `start_at < end_at` | 계획 시작 시각 |
| `end_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 | `start_at < end_at` | 계획 종료 시각 |
| `first_recovery_block` | `boolean` | `boolean` | 아니오 | 사용자·계획일별 하나 | 첫 복귀 블록 여부 |
| `cancelled_at` | `timestamp with time zone` | `OffsetDateTime` | 예 |  | 취소된 시각 |
| `created_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 |  | 계획 생성 시각 |

`initial_planned_minutes`, `added_planned_minutes`, `cancelled_planned_minutes`,
`actual_focus_minutes`, `estimate_error_minutes`는 단일 Timebox row의 컬럼이 아니다.
여러 Timebox와 Session을 `execution_unit_outcome_fact` grain으로 집계한 분석 값이다.

### 3.8 `recovery_sessions`

`RecoverySession`은 한 Timebox를 실제로 수행한 한 번의 시도다.

| 컬럼 | PostgreSQL 타입 | Java 타입 | NULL 허용 | 키/제약 | 의미 |
|---|---|---|---|---|---|
| `id` | `varchar(36)` | `String` | 아니오 | PK | 실행 시도 ID |
| `user_id` | `varchar(100)` | `String` | 아니오 | index 권장 | 소유 사용자 |
| `timebox_id` | `varchar(36)` | `String` | 아니오 | FK 권장 → `recovery_timeboxes.id` | 실행한 계획 블록 |
| `status` | `varchar(30)` | `RecoverySessionStatus` | 아니오 | `STARTED`, `COMPLETED`, `INTERRUPTED` | 세션 상태 |
| `recovery_end_reason` | `varchar(30)` | `RecoveryEndReason` | 예 | 종료 시 필수 | `TIMER_ELAPSED`, `TASK_COMPLETED`, `USER_STOPPED`, `FAILURE_CHECKED_IN` |
| `started_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 |  | 실제 시작 시각 |
| `ended_at` | `timestamp with time zone` | `OffsetDateTime` | 예 | STARTED면 `NULL` | 실제 종료 시각 |
| `created_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 |  | 세션 row 생성 시각 |

동일 사용자의 활성 Session 하나만 허용하려면 다음 partial unique index를 권장한다.

`STARTED` Session에서는 `recovery_end_reason`과 `ended_at`이 `NULL`이다.
`COMPLETED` 또는 `INTERRUPTED`로 전이할 때 종료 사유와 종료 시각을 함께 기록한다.
`Timebox`에는 역방향 `session_id`를 두지 않으며, Session 조회는
`recovery_sessions(timebox_id, user_id, status)` 기준으로 수행한다.

```sql
create unique index uk_recovery_sessions_active_user
    on recovery_sessions (user_id)
    where status = 'STARTED';
```

### 3.9 `failure_events`

`FailureEvent`는 사용자가 실패로 체크인한 원자 사건이며 수정하지 않는 append-only 데이터다.

| 컬럼 | PostgreSQL 타입 | Java 타입 | NULL 허용 | 키/제약 | 의미 |
|---|---|---|---|---|---|
| `id` | `varchar(36)` | `String` | 아니오 | PK | 실패 사건 ID |
| `user_id` | `varchar(100)` | `String` | 아니오 | index 권장 | 소유 사용자 |
| `session_id` | `varchar(36)` | `String` | 아니오 | FK 권장 → `recovery_sessions.id` | 실패가 발생한 실행 시도 |
| `timebox_id` | `varchar(36)` | `String` | 아니오 | FK 권장 → `recovery_timeboxes.id` | 실패가 발생한 계획 블록 |
| `reason` | `varchar(30)` | `FailureReason` | 아니오 | enum | 실패 이유 |
| `note` | `varchar(200)` | `String` | 예 |  | 선택 입력 메모 |
| `occurred_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 | index 권장 | 실패 체크인 시각 |

권장 index:

```sql
create index idx_failure_events_user_occurred_at
    on failure_events (user_id, occurred_at);
```

### 3.10 `restart_events`

`RestartEvent`는 특정 실패 이후 다시 시작한 사건이며 수정하지 않는 append-only 데이터다.

| 컬럼 | PostgreSQL 타입 | Java 타입 | NULL 허용 | 키/제약 | 의미 |
|---|---|---|---|---|---|
| `id` | `varchar(36)` | `String` | 아니오 | PK | 재시작 사건 ID |
| `user_id` | `varchar(100)` | `String` | 아니오 | index 권장 | 소유 사용자 |
| `failure_event_id` | `varchar(36)` | `String` | 아니오 | FK 권장 → `failure_events.id` | 어떤 실패 이후의 재시작인지 |
| `restart_type` | `varchar(30)` | `RestartType` | 아니오 | enum | 재시작 방식 |
| `suggested_minutes` | `integer` | `int` | 아니오 | `>= 0` | 제안된 재시작 시간 |
| `occurred_at` | `timestamp with time zone` | `OffsetDateTime` | 아니오 | index 권장 | 실제 재시작 시각 |

권장 index:

```sql
create index idx_restart_events_failure_occurred_at
    on restart_events (failure_event_id, occurred_at);
```

### 3.11 전체 FK 흐름

```text
inbox_items.id
<- big3_items.origin_inbox_item_id

daily_big3_boards.id
<- daily_big3_entries.daily_big3_board_id

big3_items.id
<- daily_big3_entries.big3_item_id
<- execution_units.big3_item_id

execution_units.id
<- recovery_timeboxes.execution_unit_id

recovery_timeboxes.id
<- recovery_sessions.timebox_id
<- failure_events.timebox_id

recovery_sessions.id
<- failure_events.session_id

failure_events.id
<- restart_events.failure_event_id
```

## 4. DailyBig3Board

`DailyBig3Board`는 Daily Big3 보드의 헤더다.

```text
daily_big3_board_id
user_id
selected_date
selected_at
created_at
updated_at
```

제약:

```text
unique(user_id, selected_date)
```

보드는 사용자와 날짜 경계만 관리한다.
slot 순서와 선택·교체 이력은 `DailyBig3Entry`가 관리하며, 보드는 작업 상태와 ExecutionUnit을 소유하지 않는다.

## 5. DailyBig3Entry

`DailyBig3Entry`는 작업 자체가 아니라 날짜별 선택 관계다.

```text
daily_big3_entry_id
daily_big3_board_id
big3_item_id
slot_order
selection_source
selected_at
removed_at
```

`selection_source`:

```text
NEW
CARRYOVER
```

정의:

- `NEW`: `Big3Item`을 만든 날짜의 최초 선택
- `CARRYOVER`: 같은 주의 기존 OPEN item을 이후 날짜에 다시 선택
- carryover는 item 상태가 아니라 날짜별 선택 사실

활성 entry 제약:

```text
한 보드의 활성 entry는 최대 3개
같은 보드에서 같은 item을 중복 선택할 수 없음
같은 slot_order에는 활성 entry가 하나만 존재
```

PostgreSQL에서는 `removed_at is null` 조건의 partial unique index를 사용한다.

```text
unique(daily_big3_board_id, slot_order) where removed_at is null
unique(daily_big3_board_id, big3_item_id) where removed_at is null
```

## 6. Big3Item

`Big3Item`은 같은 주 안에서 유지되는 실제 Big3 작업 identity다.

```text
big3_item_id
user_id
week_start
origin_inbox_item_id
title_snapshot
goal_category
goal_category_confidence
goal_category_source
goal_classification_version
status
created_at
completed_at
abandoned_at
expired_at
derived_from_item_id
version
```

상태:

```text
OPEN
COMPLETED
ABANDONED
EXPIRED
```

규칙:

- 처음 Daily Big3에 선택될 때 생성한다.
- 같은 주의 여러 `DailyBig3Entry`에서 참조할 수 있다.
- ExecutionUnit을 직접 소유한다.
- 다음 주 selection에서 재사용하지 않는다.
- 다음 주에도 계속할 경우 새 item을 만들고 필요하면 `derived_from_item_id`로 연결한다.

`IN_PROGRESS`는 lifecycle 상태가 아니라 ExecutionUnit 상태에서 계산하는 표시 상태다.

### 6.1 `Big3Item` 자기참조 관계

`derived_from_item_id`는 `big3_items.id`를 참조하는 단방향 자기참조 FK다.
다음 주에 생성된 새 `Big3Item`이 이전 주의 원본 `Big3Item`을 가리킨다.

```text
이전 주 Big3Item A
        ^
        | derived_from_item_id
다음 주 Big3Item B
```

JPA에서는 후속 item인 B가 연관관계의 소유자다.

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "derived_from_item_id")
private Big3Item derivedFromItem;
```

- `B.derivedFromItem = A`이며 반대 방향 컬렉션은 현재 두지 않는다.
- 이는 부모-자식 계층이 아니라 작업이 어느 item에서 이어졌는지 추적하는 계보 관계다.
- 이전 item 삭제가 후속 item 삭제로 전파되면 안 되므로 cascade remove를 사용하지 않는다.
- 같은 주의 재선택은 자기참조를 만들지 않고 동일한 `Big3Item`을 `DailyBig3Entry`에서 다시 참조한다.

## 7. 재선택과 교체

legacy 코드처럼 기존 `Big3SelectionItem` 행의 InboxItem만 바꾸면 안 된다.

금지:

```text
slot 0의 기존 Big3Item을 재사용
-> inboxItem만 A에서 B로 변경
-> A의 ExecutionUnit과 완료 상태가 B에 붙음
```

허용:

```text
기존 DailyBig3Entry.removed_at 기록
새 DailyBig3Entry 생성
Big3Item과 기존 ExecutionUnit은 변경하지 않음
```

이미 생성된 `Big3Item`과 실행 기록은 immutable identity로 취급한다.
보드에서 빠져도 같은 주의 OPEN item 보관함에는 남을 수 있다.

## 8. Category Grain

```text
Big3Item.goalCategory = 목표 또는 업무 도메인
ExecutionUnit.unitCategory = 실제 실행 행위
```

예:

```text
Big3Item: FocusLoop 백엔드 개선
goalCategory: CODING

ExecutionUnit:
- analytics 구조 문서화
  unitCategory: DOCUMENTATION
- repository 쿼리 검토
  unitCategory: REVIEW
- 테스트 작성
  unitCategory: TESTING
```

분류 metadata:

```text
goal_category_confidence
goal_category_source
goal_classification_version

unit_category_confidence
unit_category_source
unit_classification_version
```

분류는 Planning의 생성 시점에 수행한다. Analytics는 저장된 분류 결과와 버전을 사용한다.

## 9. ExecutionUnit

ExecutionUnit은 `Big3Item`에 속한다.

```text
big3_items.id
<- execution_units.big3_item_id
```

이 FK는 현재 구조와 목표 구조에서 동일하다. 별도 작업 테이블로 이동하지 않는다.

초기 상태:

```text
PLANNED
COMPLETED
```

완료 조건:

- 사용자가 실제 하위 작업이 끝났다고 선언한다.
- Timebox 개수나 소진 여부는 완료 조건이 아니다.
- 모든 Timebox가 끝나도 실제 작업이 미완료면 PLANNED를 유지한다.

## 10. ExecutionUnit 완료 트랜잭션

`completeUnit(executionUnitId)`는 다음을 하나의 트랜잭션으로 처리한다.

```text
1. ExecutionUnit과 사용자 소유권 검증
2. 이미 COMPLETED면 idempotent 응답
3. 해당 unit의 활성 Session 조회
4. 활성 Session이 있으면 TASK_COMPLETED 사유로 종료
5. ExecutionUnit -> COMPLETED
6. completed_at 저장
7. 해당 unit의 미래 WORK Timebox 취소
8. Big3Item 표시 상태 roll-up 재계산
```

활성 Session 조회에는 같은 `executionUnitId` 조건이 반드시 포함돼야 한다.
사용자의 다른 unit Session을 종료하면 안 된다.

## 11. Big3Item Roll-Up

표시 상태:

```text
NOT_STARTED
= ExecutionUnit이 없음

IN_PROGRESS
= ExecutionUnit이 하나 이상 있고 하나 이상 미완료

COMPLETED
= required ExecutionUnit이 하나 이상이고 모두 COMPLETED
```

item lifecycle의 `COMPLETED` 전이는 required unit roll-up과 같은 트랜잭션에서 확정한다.

optional unit이 필요해지기 전까지 모든 unit을 required로 본다.

## 12. Timebox

Timebox는 작업 완료가 아니라 계획이다.

필요 상태:

```text
PLANNED
CANCELLED_BY_TASK_COMPLETION
CANCELLED_BY_USER
```

완료된 unit에는 새 WORK Timebox를 생성할 수 없다.
취소된 Timebox를 물리 삭제하지 않는다.

다음 값은 `recovery_timeboxes`의 단일 row 컬럼이 아니라
여러 Timebox와 Session을 `ExecutionUnit` 기준으로 집계한 분석 값이다.

```text
initial_planned_minutes
added_planned_minutes
cancelled_planned_minutes
actual_focus_minutes
```

계획 오차는 방향을 보존한다.

```text
estimate_error_minutes
= actual_focus_minutes - effective_planned_minutes
```

절대값만 저장하지 않는다. 음수는 조기 완료, 양수는 초과 소요를 뜻한다.

## 13. RecoverySession

Session은 Timebox의 실제 실행 시도다.

```text
Session COMPLETED
!= ExecutionUnit COMPLETED
!= Big3Item COMPLETED
```

종료 이유:

```text
TIMER_ELAPSED
TASK_COMPLETED
USER_STOPPED
FAILURE_CHECKED_IN
```

중단과 실패를 분리한다.

```text
USER_STOPPED
-> Session INTERRUPTED
-> FailureEvent 없음

FAILURE_CHECKED_IN
-> Session INTERRUPTED
-> FailureEvent 생성
```

Session 중단 자체를 막지 않는다. 실제 중단을 금지하면 행동 데이터가 왜곡된다.

## 14. 주간 Closure

사용자 `ZoneId` 기준 다음 주 시작 시:

```text
OPEN Big3Item -> EXPIRED
열린 ExecutionUnit -> EXPIRED_BY_ITEM fact outcome
```

사용자가 명시적으로 포기할 때:

```text
OPEN Big3Item -> ABANDONED
열린 ExecutionUnit -> ABANDONED_BY_ITEM fact outcome
```

다음 주에도 같은 주제를 계속할 경우:

```text
새 Big3Item 생성
derived_from_item_id = 이전 item id
```

지난 주 item을 되살리거나 다음 주 entry에서 직접 참조하지 않는다.

## 15. API 계약

Daily Big3 응답:

```text
dailyBig3BoardId
dailyBig3EntryId
big3ItemId
originInboxItemId
content
slotOrder
selectionSource
itemStatus
completionStatus
```

ExecutionUnit API:

- 생성/조회는 `big3ItemId` 기준
- 완료는 `executionUnitId` 기준
- item이 같은 주의 OPEN 상태인지 검증

Carryover API:

- 기존 `big3ItemId`를 오늘의 보드에 선택
- 같은 사용자, 같은 주, OPEN 상태인지 검증
- 당일 중복 선택과 세 자리 초과를 차단

## 16. 동시성 및 제약

필수 보호 대상:

```text
같은 unit 완료 요청 중복
Session 종료와 unit 완료 동시 요청
주간 sweep과 item 완료 경쟁
같은 item의 당일 중복 선택
같은 slot의 동시 교체
```

권장:

- `Big3Item`, `ExecutionUnit`, `RecoverySession`에 optimistic locking (`@Version`) 추가
- 완료 및 상태 변경 시 조건부 업데이트(Native UPDATE) 대신 순수 엔티티 변경 후 `save()` 호출 (버전 충돌 활용)
- 완료 API idempotency
- partial unique index로 활성 entry 중복 방지
- 사용자의 활성 Session 하나 제약을 DB 수준에서도 보강

## 17. 명칭 변경 이후 구현 체크리스트

명칭 변경만으로 목표 관계가 완성되지는 않는다.
다음 구조 변경은 별도 구현과 테스트가 필요하다.

- `DailyBig3Board`가 `Big3Item`을 직접 소유하지 않고 `DailyBig3Entry`만 소유하도록 변경
- `Big3Item`의 보드 FK와 `sortOrder`를 제거하고 날짜별 배치 정보를 `DailyBig3Entry`로 이동
- `DailyBig3Entry.dailyBig3Board` FK를 올바르게 연결하고 `removedAt`을 nullable로 설정
- `Big3Item`에 user/week/lifecycle/category 컬럼 추가
- `replaceItems()`의 index 기반 entity 재사용 제거
- Timebox 취소 상태 추가
- Session 종료 이유 추가
- 완료 API를 idempotent하고 원자적으로 변경
- carryover와 weekly sweep 추가

## 18. 해석 금지

```text
Session 완료 => ExecutionUnit 완료
Timebox 소진 => ExecutionUnit 완료
보드에서 제거 => Big3Item 삭제
다음 날 같은 내용 입력 => 자동으로 같은 item
모든 Session 중단 => FailureEvent
DailyBig3Entry => 실제 작업 identity
```

올바른 해석:

```text
DailyBig3Board = 하루 보드
DailyBig3Entry = 날짜별 선택 기록
Big3Item = 주간 내 실제 Big3 작업
ExecutionUnit = 실제 하위 작업
Timebox = 계획
RecoverySession = 실행 시도
Carryover = 같은 item을 다른 날짜에 다시 선택한 사실
```
