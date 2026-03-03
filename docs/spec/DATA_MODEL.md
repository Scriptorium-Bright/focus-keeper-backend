# Data Model (Baseline)

> Version: v0.1  
> Updated: 2026-03-03  
> Scope: 핵심 도메인 + Outbox + 분석 결과 저장소

## 1. 모델링 원칙

- 트랜잭션 무결성이 필요한 영역은 PostgreSQL을 기본으로 한다.
- 이벤트 전달은 `outbox_events`를 기준으로 한다.
- 조회 성능이 중요한 뷰는 캐시(Redis) 또는 파생 테이블로 분리한다.

## 2. 핵심 엔티티

- `users`: 사용자 기본 정보
- `wallets`: 사용자 잔액
- `wallet_transactions`: 충전/차감/환급/몰수 내역
- `challenges`: 챌린지 상태/기한/보상/패널티
- `challenge_attempts`: 검증 이력
- `outbox_events`: 외부 전달 대기 이벤트
- `burnout_scores`: 배치 계산 결과
- `ai_retrospectives`: 주간 AI 회고 결과

## 3. 관계 (요약)

- `users 1 - 1 wallets`
- `users 1 - N challenges`
- `users 1 - N wallet_transactions`
- `challenges 1 - N challenge_attempts`
- `users 1 - N burnout_scores`
- `users 1 - N ai_retrospectives`

## 4. 핵심 스키마 초안

```sql
create table users (
  id bigserial primary key,
  email varchar(255) not null unique,
  nickname varchar(50) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table wallets (
  id bigserial primary key,
  user_id bigint not null unique references users(id),
  balance bigint not null check (balance >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create table challenges (
  id bigserial primary key,
  user_id bigint not null references users(id),
  title varchar(200) not null,
  status varchar(30) not null,
  stake_amount bigint not null check (stake_amount >= 0),
  reward_points bigint not null check (reward_points >= 0),
  due_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
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
```

## 5. 인덱스 및 제약조건

- `outbox_events(status, next_retry_at, created_at)` 복합 인덱스
- `challenges(user_id, status, due_at)` 인덱스
- `wallet_transactions(user_id, created_at)` 인덱스
- `burnout_scores(user_id, computed_at desc)` 인덱스
- `outbox_events.event_id` 유니크(멱등성 핵심)

## 6. 정합성/일관성 정책

- 지갑 변경과 거래내역 기록은 동일 트랜잭션에서 처리
- 챌린지 상태 변경과 outbox insert는 동일 트랜잭션에서 처리
- Relay는 outbox row lock 기반으로 중복 전송 방지

## 7. 저장소 선택 근거

- RDBMS: 강한 정합성이 필요한 핵심 트랜잭션
- Redis: 랭킹/피드/캐시 같은 고속 조회 보조
- Data Lake/Spark: 데이터 규모 임계치 초과 시 선택 도입
