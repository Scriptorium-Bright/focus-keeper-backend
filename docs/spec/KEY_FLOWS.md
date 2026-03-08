# Key Logic Flows

> Version: v0.1  
> Updated: 2026-03-03  
> Scope: 핵심 비즈니스/이벤트/배치/AI 흐름

## 1. 챌린지 실패 처리 + Outbox 기록

```mermaid
sequenceDiagram
  participant C as Client
  participant API as Challenge API
  participant S as Challenge Service
  participant DB as PostgreSQL
  participant PUB as ApplicationEventPublisher
  participant L as Outbox Listener

  C->>API: 챌린지 실패 처리 요청
  API->>S: handleFailure(challengeId)
  S->>DB: challenge 상태 변경 + wallet 차감
  S->>PUB: DomainEvent 발행
  PUB->>L: Event 전달 (동기)
  L->>DB: outbox_events insert(status=PENDING)
  S-->>API: 트랜잭션 커밋 후 성공 응답
  API-->>C: 200 OK
```

트랜잭션 경계:

- `challenge update + wallet transaction + outbox insert`는 하나의 트랜잭션으로 묶는다.
- Outbox 저장 실패 시 전체 트랜잭션은 롤백되어야 한다.

## 1A. 일일 계획 루프 (Brain Dump -> Big3 -> Timeboxing)

```mermaid
sequenceDiagram
  participant U as User
  participant API as Planning API
  participant S as Planning Service
  participant DB as PostgreSQL

  U->>API: Brain Dump 항목 등록
  API->>S: saveInboxItems(userId, items)
  S->>DB: inbox_items insert
  U->>API: 오늘 Big3 선택
  API->>S: selectBig3(userId, itemIds)
  S->>DB: daily_big3 upsert
  U->>API: 타임박스 배정
  API->>S: allocateTimeboxes(userId, blocks)
  S->>DB: timeboxes upsert
  API-->>U: 계획 완료 응답
```

예외 시나리오:

- Big3가 3개 초과 선택되면 검증 에러 반환
- 타임박스 겹침 시 충돌 에러 반환
- 실패 체크인 시 10분 재타임박스 자동 제안

## 2. Relay 워커 처리 (멱등)

```mermaid
sequenceDiagram
  participant W as Relay Worker
  participant DB as PostgreSQL
  participant EXT as External Target

  W->>DB: PENDING row 조회 (FOR UPDATE SKIP LOCKED)
  W->>DB: 상태 PENDING -> PROCESSING
  W->>EXT: 이벤트 전송
  alt 전송 성공
    W->>DB: 상태 SENT, processed_at 저장
  else 전송 실패
    W->>DB: retry_count 증가, next_retry_at 계산
    W->>DB: 상태 FAILED 또는 PENDING 복귀
  end
```

멱등 기준:

- `event_id`를 키로 중복 전송/중복 반영을 방지한다.
- 동일 이벤트 재시도 시 시스템 상태가 추가로 변하지 않아야 한다.

## 3. 배치 번아웃 계산 (Track A)

```mermaid
sequenceDiagram
  participant SCH as Scheduler
  participant B as Spring Batch Job
  participant DB as PostgreSQL

  SCH->>B: 일 배치 실행
  B->>DB: 챌린지/이벤트 데이터 조회
  B->>B: I_bo 계산
  B->>DB: burnout_scores upsert
  B-->>SCH: 실행 결과(성공/실패/소요시간)
```

## 4. AI 주간 회고 (비동기)

```mermaid
sequenceDiagram
  participant SCH as Weekly Scheduler
  participant AGG as Aggregate Job
  participant Q as Async Queue
  participant W as AI Worker
  participant LLM as LLM API
  participant DB as PostgreSQL

  SCH->>AGG: 유저별 주간 집계 시작
  AGG->>DB: 7일 데이터 집계
  AGG->>Q: 회고 작업 enqueue
  W->>Q: 작업 consume
  W->>LLM: 프롬프트 요청
  LLM-->>W: 코칭 텍스트 응답
  W->>DB: ai_retrospectives 저장
```

예외 시나리오:

- LLM timeout: 재시도 정책 적용 후 fallback 메시지 저장
- 작업 중복 소비: 유저/주차 유니크 키로 중복 저장 방지
