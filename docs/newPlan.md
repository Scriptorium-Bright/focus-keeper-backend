# RebootFocus Product Plan (Phase 1 ~ 15)

## Goal
- 코드베이스를 Phase 1부터 재구축한다.
- 출시 기준은 Phase 15 완료로 유지한다.
- 제품 핵심은 "집중 앱"이 아니라 "전날 계획이 무너지면 다음날까지 다시 못 붙잡는 직장인을 위해, 놓친 일을 다시 시작하게 만드는 복귀 코치"로 고정한다.

## Execution Mode (Career-First)
- 비중: 취업 80% / 창업 검증 20%
- 1순위: 채용 시장에서 바로 설명 가능한 엔지니어링 완성도 확보
- 2순위: 최소 비용으로 시장성 신호만 검증
- 원칙: "채용 경쟁력 향상" 또는 "핵심 시장성 검증"에 기여하지 않는 기능은 보류

## Execution Algorithm (5-Step)
1. 요구사항에 의문 제기: 지표/요구사항을 그대로 수용하지 않고 목적을 다시 확인한다.
2. 불필요한 과정 삭제: 효과가 약한 단계/지표는 제거한다.
3. 단순화/최적화: 남은 흐름과 지표를 최소 집합으로 정리한다.
4. 사이클 타임 가속: 주간 단위로 구현-측정-수정 루프를 돌린다.
5. 자동화는 마지막: 정의/운영 안정화 이후 Airflow/Kafka 자동화를 확장한다.

## Execution Sprint (2026-03-09 ~ 2026-03-23)
- 목표: Phase 15 전체 방향은 유지하되, 단기적으로는 P0 Phase를 우선 구현/검증한다.
- 원칙: `구현 -> 테스트/관측 -> 트리거 판정 -> 기술 도입` 순서로 진행한다.
- Week 1 (2026-03-09 ~ 2026-03-15):
  - P0 구현 우선: Phase 4, Phase 5
  - Phase 3는 착수 보류 (Recovery Onboarding은 다음 스프린트)
- Week 2 (2026-03-16 ~ 2026-03-20):
  - JUnit 통합/회귀 테스트 강화
  - Grafana 대시보드 기반 실패율/지연/복구시간 측정
  - 리팩토링 및 OpenAPI-코드 계약 동기화
- Week 3 (2026-03-21 ~ 2026-03-22):
  - 지원서 작성/제출 패키지 정리 (프로젝트 개요, 문제 해결 사례 카드, 아키텍처/데이터 플로우, 수치 근거)
- 2026-03-23:
  - 채용 지원 최종 제출

## Strategic Changes (확정)
- 브랜드명은 `RebootFocus`로 통일하고 `FocusKeeper` 표기는 사용하지 않는다.
- 초기 ICP는 "전날 계획이 무너지면 다음날 첫 집중 블록을 다시 못 잡는 25~34세 지식노동 직장인" 단일 세그먼트로 고정한다.
- 취준생, ADHD 성향 성인, B2B 라이트는 핵심 KPI 검증 전까지 범위에서 제외한다.
- MVP 범위는 복귀 엔진 6개 기능으로 제한한다.
- 핵심 KPI는 완료율보다 복귀율로 본다.
- 실행 루프는 `Brain Dump -> Big3 -> Timeboxing -> Re-timeboxing`으로 고정한다.
- 뽀모도로/집중 타이머는 핵심 가치가 아니라 복귀 행동을 실행시키는 보조 인터랙션으로만 다룬다.
- 페이월/체험기간/가격은 고정하지 않고 A/B 실험으로 결정한다.
- Phase 12/13은 핵심 복귀 KPI가 고정되기 전까지 실험 단계에 머문다.
- 고난도 기능(과부하 추론 고도화, Spark, 인접 세그먼트 확장)은 출시 후로 이연한다.

## Career-First Deliverables
- 필수: API 일관성, 예외/로깅 표준, 테스트 자동화, CI/CD, 운영 관측성
- 필수: ADR/Spec/Refactor 기록으로 의사결정 추적 가능 상태 유지
- 필수: 핵심 도메인(복귀 루프) E2E 시나리오 테스트
- 필수: 복귀 이벤트 -> 증분 ETL -> KPI mart -> 재처리 -> DQ까지 한 흐름으로 설명 가능한 데이터 엔지니어링 결과물
- 선택: 대규모 인프라/빅데이터 과시성 기능

## Data Engineering Hiring Signal
- 목표: 이 프로젝트를 "백엔드 앱 + 데이터 문서"가 아니라 "복귀 행동 이벤트를 수집하고 ETL/품질/재처리/지표화를 끝까지 연결한 데이터 엔지니어링 프로젝트"로 설명 가능하게 만든다.

### P0 Must-Have
- 원천 이벤트 적재: `failure_events`, `restart_events`, `recovery_sessions`, `cycle_events`, `timeboxes`
- 증분 배치 1개 구현: `daily_kpi_pipeline`
- KPI mart 적재: `Recovery24`, `Recovery48`, `RestartCount24/48`, `TTR`, `CycleCompletionRate`
- 워터마크 + 멱등 upsert + 기간 백필 구현

### P1 Strong Signal
- DQ 체크 구현: 중복/누락/timezone/late-arrival/enum 유효성
- 운영 메트릭/알림 구현: `batch_duration_seconds`, `batch_failed_runs_total`, `dq_duplicate_count`, `recovery24_ratio`
- 데이터 문제 해결 사례 1건 기록: 중복 이벤트, 잘못된 집계, 지각 데이터 중 최소 1건
- 대시보드 또는 SQL 리포트로 KPI 결과 확인 가능 상태

### P2 Bonus
- `weekly_retrospective_input` 배치 구현
- `recovery_friction_signals` 계산 구현
- Outbox Stage 1 검증 및 운영 근거 수집
- 코호트/실험군 분석 리포트

## Portfolio Packaging Principle
- 포트폴리오는 기능 목록이 아니라 `문제 -> 해결 방법 -> 결과` 구조로 정리한다.
- 각 핵심 Phase는 최소 1개의 문제 해결 사례 카드와 1개의 시각 자료를 남긴다.
- 시각 자료는 아키텍처 다이어그램, 데이터 플로우, 시퀀스 다이어그램 중 하나 이상을 포함한다.
- 결과는 기능 나열 대신 테스트, KPI, 운영 지표, 재처리 결과로 증명한다.

## Feature Decomposition Rule
- `Phase`는 방향/우선순위 단위로 유지하고, 실제 구현/검증/포트폴리오 관리는 `F-00x` 단위로 진행한다.
- P0/P1 Phase는 가능한 한 `2~4개` 이상의 `F-00x` 기능으로 쪼개고, 각 기능은 하나의 핵심 문제를 중심으로 정의한다.
- 각 `F-00x`는 최소한 `문제`, `핵심 설계 결정`, `검증 방식`, `추천 시각 자료`를 남긴다.
- Phase 종료 보고는 "무엇을 만들었는가"보다 "어떤 문제를 어떤 증거로 해결했는가"를 우선으로 적는다.
- 다음 Phase로 넘어가기 전에는 `docs/PHASE_EXIT_PROTOCOL.md` 기준의 종료 검토를 먼저 수행한다.

### Required Artifacts By Phase
- 제출 첫 장: `Project Overview` 1장 (`문제`, `Tech`, `Architecture`, `핵심 기능 3~4개`)
- Phase 4~5: 복귀 루프 문제 정의 + 시퀀스 다이어그램 + API/도메인 테스트
- Phase 11~13: KPI 계산 문제 정의 + 데이터 플로우 + 배치/DQ/재처리 증거
- Phase 14~15: 운영 문제 정의 + 아키텍처/알림 흐름 + 장애/비용 가드레일 증거
- 구체적인 사례 백로그는 `docs/PORTFOLIO_CASEBOARD.md`에서 관리한다.
- 제출 패킷 구성 템플릿은 `docs/PORTFOLIO_PACKET_TEMPLATE.md`를 따른다.
- Phase 종료 멈춤 기준은 `docs/PHASE_EXIT_PROTOCOL.md`를 따른다.

## Legacy Baseline
- 구 구현(12.x까지)은 `archive/pre-reboot-2026-02-28` 브랜치에 보관한다.

## North-Star Metrics
- Activation: 가입 후 24시간 내 첫 세션 시작률
- Recovery24: 첫 실패 후 24시간 내 재시작률 (메인)
- Recovery48: 첫 실패 후 48시간 내 재시작률 (보조)
- RestartCount24/48: 실패 후 24/48시간 내 재시작 횟수
- TTR: 평균 복귀 시간(Time To Recovery)
- CycleCompletionRate: 집중-휴식 사이클 완료 비율
- EffectiveFocusMinutes: 유효 집중 시간 합
- PlanExecutionRate: 계획된 타임박스 대비 실제 실행 완료 비율
- EstimationError: 계획 시간 대비 실제 소요 시간 오차
- Retention: D1 / D7 / D30
- Revenue: Free -> Paid, MRR, Churn, Blended ARPPU
- 상세 수식/해석: `docs/spec/RECOVERY_METRICS.md`

## Experiment Policy
- Trial 실험: 7일 vs 14일 vs 21일
- Paywall 실험: 온보딩 직후 vs 첫 회고 직후
- Pricing 실험: 월 9,900원 vs 12,900원, 연 89,000원 유지
- 모든 실험은 2주 단위, 최소 표본 충족 시에만 결론 처리

## Branch Strategy (Main vs Lab)
- `main`: 출시 가능한 단순 경로만 유지 (`Spring Batch + RDB`)
- `feature/*`: GitHub Flow로 main 반영용 기능 개발
- `lab/kafka-adapter`: 이벤트 릴레이 실험 브랜치 (메인 기본 비활성)
- `lab/airflow-orchestration`: 배치 오케스트레이션 실험 브랜치 (메인 기본 비활성)
- `lab/spark-adapter`: 분석 엔진 실험 브랜치 (메인 기본 비활성)
- 규칙: `lab/*`의 결과는 트리거 조건 충족 전까지 `main` 기본 경로를 바꾸지 않는다

## Polymorphic Extension Points (최소 2개만)
- `EventRelayPort`: 기본 `DB Relay`, 확장 `Kafka Relay`
- `AnalyticsEnginePort`: 기본 `Batch SQL`, 확장 `Spark Engine`
- 원칙: 인터페이스 남발 금지, 확장 필요가 증명된 지점만 Port로 분리

## Phase Roadmap
### Phase 1 Foundation
- 프로젝트 부트스트랩, CI/CD, 환경 프로파일 구성
- Definition of Done: 기본 빌드/테스트/배포 파이프라인 정상

### Phase 2 Core Conventions
- 공통 응답/예외/로깅/트레이스 표준화
- Definition of Done: API/에러 응답 형식 단일화

### Phase 3 Recovery Onboarding & Identity
- 인증/인가, 사용자 기본 프로필
- 온보딩: 전날 실패 패턴 1개와 오늘 첫 복귀 목표 1개 설정
- 온보딩: Brain Dump 입력 + 오늘 Big3 선택 + 첫 복귀 블록 선택
- Definition of Done: 가입 첫날 "오늘 다시 붙잡을 첫 블록"까지 완료

### Phase 4 Recovery Domain Core
- 복귀 세션(시작/완료/중단) 도메인 모델
- 실패 사유 체크인 모델
- 복귀 액션(더 작은 다음 행동) 모델
- Task Inbox(Brain Dump) / Big3 / Recovery Timebox 블록 도메인 모델
- Definition of Done: 실패/복귀/계획 모델과 예외 규칙이 테스트 가능 상태

### Phase 5 Recovery Action API
- 복귀 시작 버튼
- Big3 기반 첫 복귀 블록/일일 타임박스 배정
- 실패 직후 복귀 액션 자동 제안
- 실패 시 10분 복귀 재시작 API
- Definition of Done: 실패 직후와 다음날 첫 복귀 블록까지 API E2E 완료

### Phase 6 Weekly Retrospective (Rule-Based)
- 주간 회고 생성/열람
- "왜 다음날까지 끌렸는가" 요약
- 다음 주 anti-slip action 1개 추천

### Phase 7 Next-Morning Recovery Reminder
- 다음날 오전 첫 복귀 블록 리마인더
- 실패 다음날 미복귀 사용자 복귀 메시지 플로우
- Definition of Done: 다음날 오전 복귀 시나리오 메시지 검증 완료

### Phase 8 Billing Foundation
- 구독 도입(월/연)
- 구매/복원/권한 체크

### Phase 9 Monetization Experiments
- Trial/Paywall/Pricing A/B 인프라
- 실험 리포트 자동 집계

### Phase 10 Single-ICP Growth Loop
- 단일 ICP 랜딩/메시지 실험
- 실패 다음날 복귀 진단 결과 기반 딥링크 온보딩
- 직장인 커뮤니티/뉴스레터 채널 실험

### Phase 11 Product Analytics
- KPI 대시보드(Activation, Recovery24, Recovery48, RestartCount24/48, TTR, CycleCompletionRate, PlanExecutionRate, EstimationError, D1/D7/D30)
- 코호트/전환 퍼널 분석

### Phase 12 Light Accountability (Deferred)
- 1:1 또는 소규모 실행 확인/격려
- 대형 소셜 피드/팔로우 시스템은 미포함

### Phase 13 Recovery Friction Analytics (Pragmatic)
- Spring Batch + RDB 기반 반복 실패/다음날 미복귀 패턴 분석
- 과부하/번아웃 신호는 복귀 실패 보조 지표로만 사용

### Phase 14 Watchtower
- Sentry + Prometheus/Grafana + 알림 룰
- 운영 장애 대응 룬북 정리

### Phase 15 Executive Assistant
- 비동기 AI 주간 회고/코칭
- 지연/실패 처리 및 비용 가드레일 적용

## Feature/Process Reference
- 기능 목록 및 프로세스 상세 기준: `docs/spec/FEATURE_PROCESS_SPEC.md`

## Priority Order (취업 관점)
- P0: Phase 1, 2, 4, 5, 8, 11, 14
- P1: Phase 3, 6, 7, 9, 15
- P2: Phase 10
- P3: Phase 12, 13

## Venture Validation (20% Scope)
- 생산성 앱 이탈 경험이 있는 직장인 30~50명 대상 4주 유료 베타로 복귀 KPI만 검증
- 검증 지표: Recovery24(메인), Recovery48(보조), RestartCount24/48, TTR, CycleCompletionRate, PlanExecutionRate, EstimationError, D7, 유료 지속 의사
- 검증 실패 시: 기능 확장 대신 같은 ICP에 대한 메시지/온보딩/복귀 액션부터 수정

검증 결과물 원칙:
- 베타 결과는 기능 소개보다 이벤트 수집률, 배치 집계 성공률, KPI 변화, 재처리 사례 중심으로 정리한다.

## Architecture Rules
- 도메인 비즈니스 로직은 외부 메시징 기술을 직접 참조하지 않는다.
- 이벤트 신뢰성 요구가 높아지기 전까지는 단순 비동기/배치 재처리 우선.
- Outbox/메시지 브로커 도입은 트리거 조건 충족 시에만 진행한다.

## Outbox/Broker Adoption Triggers
- 이벤트 유실 시 수동 복구 시간이 30분 초과
- 외부 소비자(서비스) 2개 이상
- 이벤트 재처리 요구 월 5회 이상
- 비동기 실패율 0.1% 초과

## Data Pipeline Orchestration Policy
- Airflow는 실시간 API 경로가 아니라 배치 오케스트레이션에만 사용한다.
- 1차 도입 대상 DAG:
  - `daily_kpi_pipeline`: 원천 -> 정제/클렌징 -> KPI mart 적재
  - `weekly_retrospective_input`: 주간 회고 입력 집계 테이블 생성
  - `backfill_reprocess`: 기간 파라미터 기반 재처리
- 사용자 요청 동기 경로(복귀 시작, 실패 직후 10분 복귀 재시작)는 Spring 애플리케이션에서 즉시 처리한다.

## Deferred Scope (Post-Release)
- Spark 기반 오프라인 대규모 파이프라인
- 센서/헬스 데이터 결합 과부하/복귀 실패 예측 모델
- 핵심 KPI 고정 전 인접 세그먼트 확장 논의
- MCP 도입 검토
