# Data Model (Baseline)

> Version: v0.4  
> Updated: 2026-03-16  
> Scope: 복귀 코어 도메인 + 이벤트 전달 + 분석 결과 저장소

## 1. 모델링 원칙

- 복귀 루프 핵심 상태는 PostgreSQL을 기본 저장소로 사용한다.
- 이벤트 전달이 필요한 경우 `outbox_events`를 Stage 1 기준선으로 사용한다.
- 계획 상태와 복귀 이벤트는 분리 저장해 해석과 재처리를 단순하게 유지한다.

## 2. 핵심 엔티티

- `users`: 사용자 기본 정보
- `inbox_items`: Brain Dump 항목
- `daily_big3`: 일자별 Big3 선택 결과
- `timeboxes`: 일자별 계획 블록 및 첫 복귀 블록
- `recovery_sessions`: 복귀 세션 시작/완료/중단 기록
- `failure_events`: 실패 체크인 이벤트
- `restart_events`: 10분 복귀 재시작 이벤트
- `cycle_events`: 집중-휴식 사이클 시작/완료 이벤트
- `outbox_events`: 외부 전달 대기 이벤트(Stage 1 기준)
- `mart_failure_hourly`: 시간대별 실패 집계 결과
- `recovery_friction_signals`: 반복 실패/다음날 미복귀/과부하 신호 계산 결과
- `ai_retrospectives`: 주간 AI 회고 결과

## 3. 관계 (요약)

- `users 1 - N inbox_items`
- `users 1 - N daily_big3`
- `users 1 - N timeboxes`
- `users 1 - N recovery_sessions`
- `users 1 - N failure_events`
- `users 1 - N restart_events`
- `users 1 - N recovery_friction_signals`
- `users 1 - N ai_retrospectives`
- `recovery_sessions 1 - N failure_events`

## 4. 핵심 스키마 초안

```sql
create table users (
  id bigserial primary key,
  email varchar(255) not null unique,
  nickname varchar(50) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table inbox_items (
  id bigserial primary key,
  user_id bigint not null references users(id),
  content varchar(200) not null,
  created_at timestamptz not null default now()
);

create table daily_big3 (
  id bigserial primary key,
  user_id bigint not null references users(id),
  inbox_item_id bigint not null references inbox_items(id),
  selected_date date not null,
  selected_at timestamptz not null default now(),
  unique (user_id, selected_date, inbox_item_id)
);

create table timeboxes (
  id bigserial primary key,
  user_id bigint not null references users(id),
  source_type varchar(30) not null,
  source_id bigint null,
  start_at timestamptz not null,
  end_at timestamptz not null,
  is_first_recovery_block boolean not null default false,
  created_at timestamptz not null default now()
);

create table recovery_sessions (
  id bigserial primary key,
  user_id bigint not null references users(id),
  timebox_id bigint null references timeboxes(id),
  status varchar(30) not null,
  started_at timestamptz not null,
  ended_at timestamptz null,
  created_at timestamptz not null default now()
);

create table failure_events (
  id bigserial primary key,
  user_id bigint not null references users(id),
  session_id bigint not null references recovery_sessions(id),
  timebox_id bigint null references timeboxes(id),
  reason_code varchar(50) not null,
  note varchar(200) null,
  occurred_at timestamptz not null,
  timezone varchar(50) not null,
  created_at timestamptz not null default now()
);

create table restart_events (
  id bigserial primary key,
  user_id bigint not null references users(id),
  failure_event_id bigint not null references failure_events(id),
  restart_type varchar(30) not null,
  suggested_minutes int not null,
  occurred_at timestamptz not null,
  timezone varchar(50) not null,
  created_at timestamptz not null default now()
);

create table outbox_events (
  event_id uuid primary key,
  aggregate_type varchar(50) not null,
  aggregate_id varchar(100) not null,
  event_type varchar(100) not null,
  payload_json jsonb not null,
  status varchar(20) not null,
  retry_count int not null default 0,
  next_retry_at timestamptz null,
  created_at timestamptz not null default now(),
  processed_at timestamptz null
);

create table mart_failure_hourly (
  metric_date date not null,
  user_id bigint not null references users(id),
  timezone varchar(50) not null,
  local_hour smallint not null,
  failure_count int not null,
  failure_ratio numeric(8,4) not null,
  is_peak_hour boolean not null default false,
  primary key (metric_date, user_id, local_hour)
);
```

## 5. 인덱스 및 제약조건

- `inbox_items(user_id, created_at desc)` 인덱스
- `daily_big3(user_id, selected_date)` 인덱스
- `timeboxes(user_id, start_at)` 인덱스
- `recovery_sessions(user_id, started_at desc)` 인덱스
- `failure_events(user_id, occurred_at desc)` 인덱스
- `restart_events(user_id, occurred_at desc)` 인덱스
- `failure_events(user_id, timezone, occurred_at desc)` 인덱스
- `mart_failure_hourly(metric_date, user_id)` 인덱스
- `recovery_friction_signals(user_id, signal_date desc)` 인덱스
- `outbox_events(status, next_retry_at, created_at)` 복합 인덱스
- `outbox_events.event_id` 유니크(멱등성 핵심)

## 6. 정합성/일관성 정책

- Brain Dump / Big3 / Timebox 변경은 사용자별 계획 상태 기준으로 일관되게 반영한다.
- 실패 체크인과 다음 복귀 액션 스냅샷 기록은 동일 트랜잭션에서 처리한다.
- Relay는 outbox row lock 기반으로 중복 전송을 방지한다.
- 시간대별 실패 통계는 서버 시각이 아니라 `failure_events.occurred_at`의 오프셋과 `timezone` 기준 로컬 시각으로 계산한다.
- 원천 이벤트에는 로컬 시각을 복원할 수 있는 정보가 남아 있어야 하며, 시간대 정보가 없으면 hourly mart 계산에서 제외한다.

## 7. 저장소 선택 근거

- RDBMS: 복귀 루프 핵심 상태와 KPI 계산에 필요한 정합성 보장
- Redis: 리마인더/대시보드 캐시 같은 고속 조회 보조
- Data Lake/Spark: 데이터 규모 임계치 초과 시 선택 도입
