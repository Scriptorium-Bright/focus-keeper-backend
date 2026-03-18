# Feature & Process Spec

> Version: v0.3  
> Updated: 2026-03-16  
> Scope: 현재/목표 기능 목록과 동작 프로세스를 한 문서에서 확인하기 위한 기준

## 1. 목적

- "무슨 기능이 있는지"와 "어떻게 동작하는지"를 분리하지 않고 한 번에 본다.
- 구현/테스트/자소서 작성 시 동일한 기능 정의를 사용한다.
- 기능 누락, 프로세스 중복, KPI 미연결 상태를 빠르게 찾는다.

## 2. 기능 카탈로그 (Phase 기준)

| ID | 기능 | Phase | 핵심 입력 | 핵심 출력 | 상태 |
|---|---|---|---|---|---|
| F-001 | Brain Dump 등록 | 4~5 | inbox item 목록 | 저장된 inbox item | done |
| F-002 | Big3 선택 | 4~5 | item id 1~3개 | 오늘의 Big3 세트 | done |
| F-003 | 첫 복귀 블록 포함 Timebox 배정 | 4~5 | 시작/종료 블록 | 일일 복귀 계획 | done |
| F-004 | 복귀 세션 시작/완료/중단 | 4~5 | session 상태 이벤트 | 복귀 세션 기록 | done |
| F-005 | 실패 체크인 | 4~5 | failure reason | 실패 이벤트 기록 | done |
| F-006 | 10분 복귀 재시작 제안/실행 | 5 | failure event | restart 이벤트 | planned |
| F-007 | 주간 회고 집계 생성 | 6 | 7일 실행 데이터 | 주간 집계 결과 | planned |
| F-008 | 주간 회고 조회 | 6 | user/week 입력 | 회고 리포트 | planned |
| F-009 | anti-slip action 추천 | 6 | 회고 입력 | 다음 행동 1개 | planned |
| F-010 | 다음날 오전 첫 복귀 블록 리마인더 예약 | 7 | 이탈/실패 신호 | 예약된 알림 작업 | planned |
| F-011 | 미복귀 사용자 감지 | 7 | timebox/session 상태 | 미복귀 사용자 목록 | planned |
| F-012 | 복귀 메시지 발송 결과 기록 | 7 | 메시지 요청 | 발송 결과 | planned |
| F-013 | 구독 상품/권한 모델 정의 | 8 | plan/product 정의 | 권한 기준선 | planned |
| F-014 | 구매/복원 처리 | 8 | 결제/복원 요청 | 구매 결과 | planned |
| F-015 | Entitlement 체크 | 8 | user/feature 요청 | 권한 상태 | planned |
| F-016 | Trial 실험군 배정 | 9 | userId, experiment key | variant 배정 | planned |
| F-017 | Paywall 노출 타이밍 실험 | 9 | user state | paywall exposure log | planned |
| F-018 | 가격 실험 리포트 | 9 | conversion data | variant report | planned |
| F-019 | 단일 ICP 랜딩 유입 이벤트 수집 | 10 | landing/referrer | 유입 이벤트 | planned |
| F-020 | 실패 다음날 복귀 진단 결과 생성 | 10 | 진단 응답 | diagnosis result | planned |
| F-021 | 딥링크 온보딩 어트리뷰션 | 10 | deep link/open event | attribution result | planned |
| F-022 | KPI 일간 mart 적재 | 11 | raw/clean events | KPI daily mart | planned |
| F-023 | 코호트 리텐션 분석 | 11 | KPI mart | cohort report | planned |
| F-024 | 전환 퍼널 분석 | 11 | event funnel data | funnel report | planned |
| F-025 | 가벼운 실행 확인 초대 | 12 | invite target | invite record | planned |
| F-026 | 가벼운 실행 체크인/격려 | 12 | check-in event | accountability record | planned |
| F-027 | 복귀 실패 패턴 신호 계산 | 13 | execution events | friction signals | planned |
| F-028 | 복귀 마찰 세그먼트 리포트 | 13 | friction signals | segment report | planned |
| F-029 | API/복귀 루프 대시보드 | 14 | metrics source | dashboard | planned |
| F-030 | Batch/DQ 대시보드 및 알림 | 14 | batch/dq metrics | alerting + dashboard | planned |
| F-031 | 장애 대응 룬북 검증 | 14 | alert/drill result | runbook evidence | planned |
| F-032 | AI 회고 작업 enqueue | 15 | weekly aggregate | queued job | planned |
| F-033 | AI 회고 생성/저장 | 15 | queued job + LLM | AI 회고 결과 | planned |
| F-034 | AI timeout/cost/fallback 가드레일 | 15 | worker execution | guarded result | planned |

상태 규칙:
- `planned`: 설계만 완료
- `in_progress`: 구현/테스트 진행
- `done`: 구현+테스트+문서 동기화 완료

포지셔닝 규칙:
- `Timebox`, `세션`, `타이머`는 제품 정체성이 아니라 복귀 행동을 실행시키는 인터랙션으로 다룬다.

포트폴리오 규칙:
- 기능 설명보다 각 기능이 해결하는 문제와 검증 결과를 먼저 기록한다.
- Phase당 최소 1개 이상 `문제 -> 해결 -> 결과` 사례 후보를 남긴다.
- 실제 사례 백로그와 제출 우선순위는 `docs/PORTFOLIO_CASEBOARD.md`에서 관리한다.

### 2.1 현재 구현 API 매핑

- F-001 Brain Dump 등록: `POST /api/v1/recovery/inbox-items`
  - 입력: `userId`, `items[]`
  - 출력: `savedCount`, `savedItems[]`
- F-002 Big3 선택: `POST /api/v1/recovery/big3`
  - 입력: `userId`, `itemIds[]`
  - 출력: `selectedCount`, `selectedItems[]`, `selectedDate`, `selectedAt`
- F-003 첫 복귀 블록 포함 Timebox 배정: `POST /api/v1/recovery/timeboxes`
  - 입력: `userId`, `timeboxes[]`
  - 출력: `plannedDate`, `allocatedCount`, `timeboxes[]`
- F-004 복귀 세션 시작/완료/중단:
  - `POST /api/v1/recovery/sessions/start`
  - `POST /api/v1/recovery/sessions/complete`
  - `POST /api/v1/recovery/sessions/interrupt`
  - 입력: `userId`, `timeboxId | sessionId`
  - 출력: `sessionId`, `timeboxId`, `status`, `startedAt`, `endedAt`
- F-005 실패 체크인: `POST /api/v1/recovery/failures/check-in`
  - 입력: `userId`, `sessionId`, `reason`, `note`
  - 출력: `failureEventId`, `sessionId`, `timeboxId`, `reason`, `occurredAt`, `sessionStatus`

### 2.2 포트폴리오 사례 후보 매핑

| 사례 ID | 연결 기능 | 핵심 문제 | 권장 시각 자료 |
|---|---|---|---|
| C-01 | F-003 ~ F-006 | 전날 실패 후 다음날 복귀 루프가 끊기는 문제 | 시퀀스 다이어그램 |
| C-02 | F-022 ~ F-024 | 복귀 KPI를 신뢰성 있게 계산해야 하는 문제 | 데이터 플로우 |
| C-03 | F-027 ~ F-030 | 반복 실패/운영 이상을 조기 감지해야 하는 문제 | 아키텍처 다이어그램 |
| C-04 | F-032 ~ F-034 | AI 회고를 넣되 코어 경로를 오염시키지 않아야 하는 문제 | 비동기 흐름도 |

### 2.3 현재 P0 기능의 문제 해결 프레임

| ID | 해결하려는 문제 | 핵심 설계 결정 | 검증/증빙 | 추천 시각 자료 |
|---|---|---|---|---|
| F-003 | 사용자가 오늘 다시 붙잡아야 할 첫 복귀 블록이 없으면 계획이 실행으로 이어지지 않는다. | 일반 일정 배정보다 먼저 "첫 복귀 블록"을 고정하고, 충돌은 `409`로 즉시 거절한다. | Timebox 충돌 테스트, 계획 API 통합 테스트 | 시퀀스 다이어그램 |
| F-004 | 세션 상태가 불명확하면 실패와 복귀를 지표로 추적할 수 없다. | `started/completed/interrupted` 상태 전이를 명시적으로 모델링한다. | 상태 전이 테스트, 세션 이벤트 로그 | 상태 전이 도식 |
| F-005 | 실패가 기록되지 않으면 사용자의 이탈 원인을 제품/데이터에서 모두 놓친다. | 실패 사유를 세션과 연결된 구조화 이벤트로 저장한다. | enum/입력 검증 테스트, failure event 적재 확인 | 이벤트 흐름도 |
| F-006 | 실패 직후 사용자가 그대로 멈추면 다음날까지 끌려갈 가능성이 커진다. | 실패 직후 `10분 복귀 재시작` 또는 더 작은 다음 행동을 제안하고 실행 이벤트를 남긴다. | restart 이벤트 테스트, Recovery24 입력 완전성 | 시퀀스 다이어그램 |

### 2.4 Phase 4~5 세부 작업 분해 (`T-00x`)

#### F-003 첫 복귀 블록 포함 Timebox 배정

| Task ID | 작업 | 완료 기준 |
|---|---|---|
| T-003-1 | Timebox 요청/응답 스키마 정의 | 시작/종료 시각, 첫 복귀 블록 여부, 충돌 응답 형식 확정 |
| T-003-2 | Timebox 충돌 검증 구현 | 겹치는 블록 요청 시 `409` 반환 |
| T-003-3 | 첫 복귀 블록 우선 배정 규칙 구현 | 일반 블록보다 첫 복귀 블록이 먼저 저장됨 |
| T-003-4 | 계획 루프 통합 테스트 추가 | Brain Dump -> Big3 -> Timebox 플로우 통과 |

#### F-004 복귀 세션 시작/완료/중단

| Task ID | 작업 | 완료 기준 |
|---|---|---|
| T-004-1 | 세션 상태 모델 정의 | `started/completed/interrupted` 전이 규칙 확정 |
| T-004-2 | 세션 시작 API 구현 | 유효한 timebox/session 시작 요청 저장 |
| T-004-3 | 세션 완료/중단 API 구현 | 종료 상태 전이와 타임스탬프 저장 |
| T-004-4 | 상태 전이 예외 테스트 추가 | 불가능한 전이가 `400/409`로 거절됨 |

#### F-005 실패 체크인

| Task ID | 작업 | 완료 기준 |
|---|---|---|
| T-005-1 | failure reason taxonomy 정의 | 허용 reason enum 및 검증 규칙 확정 |
| T-005-2 | 실패 체크인 API 구현 | 세션과 연결된 failure event 저장 |
| T-005-3 | 실패 입력 검증 테스트 추가 | 잘못된 reason/상태 입력이 거절됨 |
| T-005-4 | 재시작 제안 연결 필드 설계 | 다음 단계(`F-006`)로 넘길 payload 확정 |

#### F-006 10분 복귀 재시작

| Task ID | 작업 | 완료 기준 |
|---|---|---|
| T-006-1 | 10분 복귀 재시작 제안 규칙 정의 | failure event 기반 추천 규칙 확정 |
| T-006-2 | 재시작 실행 API 구현 | restart event 저장 및 새 복귀 시도 시작 |
| T-006-3 | 실패 -> 재시작 통합 테스트 추가 | 체크인 직후 재시작 플로우 통과 |
| T-006-4 | Recovery24 입력 이벤트 검증 | 배치 입력에 필요한 필드 누락 없음 확인 |

## 3. 핵심 사용자 프로세스

### P-01 Daily Recovery Planning

1. 사용자가 전날 밀린 일까지 포함해 Brain Dump를 등록한다.
2. 오늘 다시 붙잡아야 할 Big3를 선택한다.
3. Big3 기반으로 첫 복귀 블록을 포함한 Timebox를 배정한다.
4. 충돌/검증 오류가 없으면 계획 확정 응답을 반환한다.

예외:
- Big3 3개 초과 선택 -> `400`
- Timebox 겹침 -> `409`

### P-02 Recovery Loop

1. 사용자가 첫 복귀 블록 또는 복귀 세션을 시작한다.
2. 완료 또는 실패 체크인을 남긴다.
3. 실패 시 10분 복귀 재시작 또는 더 작은 다음 행동을 제안한다.
4. 같은 날 재시작하지 못하면 다음날 오전 첫 복귀 블록 후보를 기록한다.
5. 재시작 이벤트를 기록하고 루프를 재진입한다.

핵심 KPI 연결:
- Recovery24 / Recovery48
- RestartCount24/48
- TTR
- CycleCompletionRate

### P-03 Weekly Feedback

1. 배치가 7일 데이터를 집계한다.
2. 규칙 기반 회고 리포트를 생성한다.
3. 다음 주 anti-slip action 1개를 제안한다.

확장(Phase 15):
- 비동기 AI 회고 생성으로 대체/보강

## 4. 운영 프로세스

### O-01 KPI 배치 파이프라인

1. raw 이벤트 추출
2. 정제/클렌징
3. Recovery Metric Pack 계산
4. mart upsert
5. DQ 체크 및 알림

도구:
- Spring Batch(계산)
- Airflow(오케스트레이션)

### O-02 재처리(Backfill)

1. 기간 파라미터 입력
2. 동일 윈도우 재실행
3. 품질 검증
4. 워터마크 갱신

## 5. API 관점 기능 경계

| 경계 | 동기/비동기 | 담당 계층 | 비고 |
|---|---|---|---|
| 계획/복귀 사용자 요청 | 동기 | API + Service | 사용자 체감 지연 최소화 |
| KPI 집계/회고 생성 | 비동기 | Batch + Airflow | 재시도/재처리 가능 |
| AI 회고 | 비동기 | Worker | timeout/retry/fallback 필수 |

## 6. 데이터 관점 기능 경계

| 데이터 계층 | 역할 | 예시 |
|---|---|---|
| raw | 원천 이벤트 보관 | failure/restart/cycle 이벤트 |
| clean | 품질 정제 완료 | 유효 이벤트, 표준화된 타임존 |
| mart | 제품/리포트 제공 | recovery24_ratio, cycle_completion_rate |

## 7. DoD (기능 단위)

기능 하나가 완료(`done`)로 이동하려면:
1. API/도메인 동작 구현
2. 예외 시나리오 테스트
3. KPI/로그/관측 포인트 연결
4. 해당 기능이 "실패 다음날 복귀" 문제에 직접 연결되는지 검토
5. 관련 문서(`newPlan`, `KEY_FLOWS`, `RECOVERY_METRICS`) 동기화

## 8. 연계 문서

- `docs/newPlan.md`
- `docs/PORTFOLIO_CASEBOARD.md`
- `docs/spec/KEY_FLOWS.md`
- `docs/spec/RECOVERY_METRICS.md`
- `docs/spec/DATA_PIPELINE_ETL_BLUEPRINT.md`
- `docs/spec/WEEKLY_EXECUTION_CHECKLIST.md`

## 9. 실행 운영

- 주간 실행은 `docs/spec/WEEKLY_EXECUTION_CHECKLIST.md`를 기준으로 진행한다.
- 기능 상태(`planned`, `in_progress`, `done`)는 주차 종료 시 반드시 업데이트한다.
