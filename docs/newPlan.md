# RebootFocus Product Plan (Phase 1 ~ 15)

## Goal
- 코드베이스를 Phase 1부터 재구축한다.
- 출시 기준은 Phase 15 완료로 유지한다.
- 제품 핵심은 "집중 앱"이 아니라 "실패 후 복귀 코치"로 고정한다.

## Execution Mode (Career-First)
- 비중: 취업 80% / 창업 검증 20%
- 1순위: 채용 시장에서 바로 설명 가능한 엔지니어링 완성도 확보
- 2순위: 최소 비용으로 시장성 신호만 검증
- 원칙: "채용 경쟁력 향상" 또는 "핵심 시장성 검증"에 기여하지 않는 기능은 보류

## Strategic Changes (확정)
- 브랜드명 `FocusKeeper`는 사용하지 않는다. 가칭 `RebootFocus`로 운영하고 정식 네이밍은 별도 확정한다.
- 초기 ICP는 단일 세그먼트만 운영한다.
- MVP 범위는 복귀 엔진 6개 기능으로 제한한다.
- 핵심 KPI는 완료율보다 복귀율로 본다.
- 실행 루프는 `Brain Dump -> Big3 -> Timeboxing -> Re-timeboxing`으로 고정한다.
- 페이월/체험기간/가격은 고정하지 않고 A/B 실험으로 결정한다.
- 고난도 기능(센서 기반 번아웃, Spark, B2B)은 출시 후로 이연한다.

## Career-First Deliverables
- 필수: API 일관성, 예외/로깅 표준, 테스트 자동화, CI/CD, 운영 관측성
- 필수: ADR/Spec/Refactor 기록으로 의사결정 추적 가능 상태 유지
- 필수: 핵심 도메인(복귀 루프) E2E 시나리오 테스트
- 선택: 대규모 인프라/빅데이터 과시성 기능

## Legacy Baseline
- 구 구현(12.x까지)은 `archive/pre-reboot-2026-02-28` 브랜치에 보관한다.

## North-Star Metrics
- Activation: 가입 후 24시간 내 첫 세션 시작률
- Recovery48: 첫 실패 후 48시간 내 재시작률
- TTR: 평균 복귀 시간(Time To Recovery)
- PlanExecutionRate: 계획된 타임박스 대비 실제 실행 완료 비율
- EstimationError: 계획 시간 대비 실제 소요 시간 오차
- Retention: D1 / D7 / D30
- Revenue: Free -> Paid, MRR, Churn, Blended ARPPU

## Experiment Policy
- Trial 실험: 7일 vs 14일 vs 21일
- Paywall 실험: 온보딩 직후 vs 첫 회고 직후
- Pricing 실험: 월 9,900원 vs 12,900원, 연 89,000원 유지
- 모든 실험은 2주 단위, 최소 표본 충족 시에만 결론 처리

## Branch Strategy (Main vs Lab)
- `main`: 출시 가능한 단순 경로만 유지 (`Spring Batch + RDB`)
- `feature/*`: GitHub Flow로 main 반영용 기능 개발
- `lab/kafka-adapter`: 이벤트 릴레이 실험 브랜치 (메인 기본 비활성)
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

### Phase 3 Identity & Onboarding
- 인증/인가, 사용자 기본 프로필
- 온보딩: 이번 주 목표 1개 설정
- 온보딩: Brain Dump 입력 + 오늘 Big3 선택

### Phase 4 Recovery Domain Core
- 집중 세션(시작/완료/중단) 도메인 모델
- 실패 사유 체크인 모델
- 복귀 액션(더 작은 다음 행동) 모델
- Task Inbox(Brain Dump) / Big3 / Timebox 블록 도메인 모델

### Phase 5 Recovery Loop API
- 5분 시작 버튼
- Big3 기반 일일 타임박스 배정
- 실패 직후 복귀 액션 자동 제안
- 실패 시 10분 재타임박싱(quick restart) API
- Definition of Done: 실패 후 복귀 플로우 API E2E 완료

### Phase 6 Weekly Retrospective (Rule-Based)
- 주간 회고 생성/열람
- 다음 주 1개 행동 추천(규칙 기반)
- 계획 대비 실제 소요 비교 및 예측 오차 리포트

### Phase 7 Reminder & Re-engagement
- 개인화 알림(기본 규칙 기반)
- 휴면/이탈 사용자 복귀 메시지 플로우

### Phase 8 Billing Foundation
- 구독 도입(월/연)
- 구매/복원/권한 체크

### Phase 9 Monetization Experiments
- Trial/Paywall/Pricing A/B 인프라
- 실험 리포트 자동 집계

### Phase 10 Growth Loop
- Web-to-App 유입 퍼널
- 진단 결과 기반 딥링크 온보딩

### Phase 11 Product Analytics
- KPI 대시보드(Activation, Recovery48, TTR, PlanExecutionRate, EstimationError, D1/D7/D30)
- 코호트/전환 퍼널 분석

### Phase 12 Social Accountability (Light)
- P2P 기반 가벼운 실행 인증/격려
- 대형 소셜 피드/팔로우 시스템은 미포함

### Phase 13 Burnout Analytics (Pragmatic)
- Spring Batch + RDB 기반 분석 배치
- 번아웃은 규칙 기반 경고부터 시작

### Phase 14 Watchtower
- Sentry + Prometheus/Grafana + 알림 룰
- 운영 장애 대응 룬북 정리

### Phase 15 Executive Assistant
- 비동기 AI 주간 회고/코칭
- 지연/실패 처리 및 비용 가드레일 적용

## Priority Order (취업 관점)
- P0: Phase 1, 2, 4, 5, 8, 11, 14
- P1: Phase 3, 6, 7, 9, 15
- P2: Phase 10, 12, 13

## Venture Validation (20% Scope)
- 4주 유료 베타(30~50명)로 복귀 KPI만 검증
- 검증 지표: Recovery48, TTR, PlanExecutionRate, EstimationError, D7, 유료 지속 의사
- 검증 실패 시: 기능 확장 대신 ICP/메시지/온보딩부터 수정

## Architecture Rules
- 도메인 비즈니스 로직은 외부 메시징 기술을 직접 참조하지 않는다.
- 이벤트 신뢰성 요구가 높아지기 전까지는 단순 비동기/배치 재처리 우선.
- Outbox/메시지 브로커 도입은 트리거 조건 충족 시에만 진행한다.

## Outbox/Broker Adoption Triggers
- 이벤트 유실 시 수동 복구 시간이 30분 초과
- 외부 소비자(서비스) 2개 이상
- 이벤트 재처리 요구 월 5회 이상
- 비동기 실패율 0.1% 초과

## Deferred Scope (Post-Release)
- Spark 기반 오프라인 대규모 파이프라인
- 센서/헬스 데이터 결합 번아웃 예측 모델
- B2B 팀 플랜(관리자 권한/SSO 포함)
- MCP 도입 검토
