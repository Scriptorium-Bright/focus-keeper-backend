# Engineering Spec (Reboot Baseline)

> Version: v0.5  
> Updated: 2026-03-14  
> Scope: Phase 1~2 구현 기준선 + 복귀 지표 팩 + Phase 15 출시 목표

## 1. 문제 정의

RebootFocus는 전날 계획이 무너지면 다음날까지 다시 못 붙잡는 25~34세 지식노동 직장인을 위해, 놓친 일을 실패 맥락에 맞는 다음 행동과 첫 복귀 블록으로 다시 시작하게 만드는 복귀 코치 서비스다.  
리부트 목표는 범용 생산성 앱처럼 넓게 가지 않고, "실패 다음날 복귀"라는 하나의 문제에 맞춰 데이터 신뢰성과 운영 안정성을 우선 확보하는 것이다.

## 2. 목표 및 비목표

### 목표

- Phase 15까지 출시 가능한 백엔드 베이스라인 완성
- 도메인 중요도 기반 이벤트 전략 수립 (필요 시 Outbox/Kafka 단계 도입)
- 운영 가시성(Sentry + Prometheus/Grafana) 확보
- AI 코칭은 반드시 비동기 경로로 처리

### 비목표

- 초기 출시 시점에 Spark 분산 파이프라인 필수 도입
- 초기 출시 시점에 MCP 연동 필수 도입

## 3. 요구사항 정의

### 3.1 기능 요구사항 (Functional Requirements)

- `FR-001` 시스템은 헬스체크 API를 제공해야 한다.
  - 입력: 없음
  - 출력: 서비스 상태, 타임스탬프, 활성 프로파일
- `FR-002` API 응답은 공통 Envelope 규약을 따라야 한다. (Phase 2)
- `FR-003` 예외는 표준 에러 코드 체계로 응답해야 한다. (Phase 2)
- `FR-004` 이벤트 전달 방식은 도메인 중요도에 따라 결정되어야 한다.
  - Hard-consistency 이벤트: 동기 트랜잭션 우선
  - Soft-consistency 이벤트: 단순 비동기/배치 재생성 허용
  - High-critical async 이벤트: Outbox 단계 도입
- `FR-005` 복귀 실패 패턴/과부하 신호 계산은 배치로 수행되고 결과를 조회 API에서 제공해야 한다. (Phase 13)
- `FR-006` 주간 AI 회고는 비동기 작업으로 생성되어야 한다. (Phase 15)
- `FR-007` 확장 실험을 위해 최소 Port 2개를 유지해야 한다.
  - `EventRelayPort`: DB Relay 기본, Kafka Relay 확장
  - `AnalyticsEnginePort`: Batch SQL 기본, Spark 확장
- `FR-008` 일일 실행 루프를 제공해야 한다.
  - Brain Dump 입력
  - Big3 선택
  - Timebox 배정
  - 실패 시 Re-timeboxing 또는 더 작은 다음 행동 제안
  - 이 루프는 범용 계획 관리가 아니라 실패 다음날 복귀를 위한 보조 루프여야 한다.
  - 집중 세션/뽀모도로 타이머는 핵심 가치가 아니라 복귀 행동 실행을 보조하는 인터랙션이어야 한다.
  - 계획된 휴식은 MVP 이후 `Phase 6`에서 `BREAK timebox`로 도입하고, 실패/이탈과 분리해야 한다.
- `FR-009` 배치 파이프라인은 오케스트레이션 계층을 분리해야 한다.
  - 실시간 경로와 분리된 스케줄/재처리 제어를 지원해야 한다.
  - 1차 대상: KPI 집계, 주간 회고 입력 집계, 기간 백필
- `FR-010` Kafka/Outbox 도입 여부는 계측 수치로 판정해야 한다.
  - JUnit/통합테스트, 부하테스트, Grafana 운영지표로 근거를 남긴다.
- `FR-011` 복귀 지표는 단일 지표가 아니라 Metric Pack으로 정의해야 한다.
  - `Recovery24`(메인), `Recovery48`(보조)
  - `RestartCount24/48`, `TTR`, `CycleCompletionRate`, `EffectiveFocusMinutes`

### 3.2 비기능 요구사항 (Non-Functional Requirements)

- `NFR-001 Performance`: 일반 조회 API p95 < 300ms (50 RPS 기준)
- `NFR-002 Throughput`: 단일 인스턴스에서 100 RPS까지 기능 저하 없이 처리
- `NFR-003 Availability`: 월간 99.5% 이상 (초기), 출시 안정화 후 99.9% 목표
- `NFR-004 Consistency`: 계획 상태/복귀 핵심 이벤트/Outbox 기록은 Strong Consistency
- `NFR-005 Event Delivery`: Outbox 도입 시 relay 처리 지연 p95 < 5s
- `NFR-006 Recovery`: 장애 복구 시 데이터 유실 0건(영속 스토리지 기준)
- `NFR-007 Observability`: 에러율, 지연시간, Outbox lag, 배치 시간 메트릭 수집
- `NFR-008 Data Quality`: 필수 컬럼 완전성 >= 99.5%, 중복률 0%
- `NFR-009 Batch Recoverability`: 배치 실패 시 워터마크 기반 재실행 가능
- `NFR-010 Planning Observability`: PlanExecutionRate/EstimationError를 주간 단위로 추적 가능
- `NFR-011 Trigger Evidence`: 기술 전환 판단에 사용한 메트릭은 14일 보관 및 비교 가능해야 한다.
- `NFR-012 Metric Integrity`: 복귀 지표 계산에서 중복/스팸 이벤트가 필터링되어야 한다.

## 4. 시스템 아키텍처

### 4.1 구조

- 기본 구조: Modular Monolith (Spring Boot)
- 계층: `domain` / `application` / `infrastructure` / `interface`
- 데이터 경로:
  - OLTP: API -> Service -> RDB
  - Event (Stage 0): Domain Event -> Internal Async/Batch Reconcile
  - Event (Stage 1): Domain Event -> Outbox -> Relay Worker -> External Target
  - Event (Stage 2): Domain Event -> Outbox -> Message Broker -> Multi Consumers
  - Analytics (Track A, current): RDB -> Spring Batch (Job) -> RDB
  - Analytics (Track A, Phase 14 target): RDB -> Spring Batch (Job) -> Airflow (Orchestration) -> RDB

### 4.2 기술 스택 및 근거

- Spring Boot: 빠른 개발/운영 표준화
- PostgreSQL: 트랜잭션 무결성 및 SQL 집계
- Redis: 캐시/랭킹/저지연 조회
- Outbox 패턴: 비동기 전달에서 유실 허용 불가 시 원자성 보장
- Spring Batch: 현재 데이터 규모에서 비용 대비 최적
- Airflow (Phase 14 예정): 배치 스케줄링/재처리/운영 가시성 오케스트레이션

### 4.3 확장 구조 (Port/Adapter 최소화)

- `application/port/out/EventRelayPort`
- `application/port/out/AnalyticsEnginePort`
- `infrastructure/relay/DbEventRelayAdapter` (기본)
- `infrastructure/relay/KafkaEventRelayAdapter` (lab)
- `infrastructure/analytics/BatchSqlAnalyticsAdapter` (기본)
- `infrastructure/analytics/SparkAnalyticsAdapter` (lab)

선택 규칙:
- 기본 프로파일은 DB Relay + Batch SQL 고정
- Kafka/Spark 어댑터는 `lab/*` 또는 별도 프로파일에서만 활성화
- 메인 경로 안정성(테스트/지표) 검증 전 기본값 전환 금지

## 5. 일관성 정책

- Strong Consistency:
  - Brain Dump / Big3 / Timebox 상태 변경
  - failure / restart / recovery session 핵심 이벤트 기록
  - outbox_events insert (Stage 1 이상)
- Eventual Consistency:
  - 다음날 오전 복귀 알림 발송
  - 휴면/이탈 복귀 메시지 fan-out
  - 분석 결과 캐시 반영

## 6. 출시 수용 기준 (Release Acceptance)

- `RA-001` CI 파이프라인에서 테스트 통과
- `RA-002` Outbox 원자성/멱등성 테스트 통과
- `RA-003` Sentry + Grafana 대시보드 활성
- `RA-004` 배치 SLA 및 (Outbox 도입 시) lag 알림 설정 완료
- `RA-005` AI 비동기 처리 timeout/retry/fallback 검증 완료

## 7. 이벤트 중요도 매트릭스 및 진화 기준

### 7.1 도메인 중요도 매트릭스

| 도메인 | 유실 영향 | 기본 전략 |
|---|---|---|
| 계획/복귀 코어 | 핵심 복귀 루프 오류, 사용자 신뢰 저하 | 동기 트랜잭션 확정, 필요 시 Stage 1 Outbox |
| 알림/복귀 메시지 | UX 저하, 재생성 가능 | 단순 비동기 + 배치 재동기화 |
| 분석/AI 회고 | 통계 왜곡, 재집계 가능 | 배치 재집계 우선 |

### 7.2 Stage 전략

- `Stage 0 (현재 기본)`: 메시징 미도입, 단순 비동기/배치
- `Stage 1`: Transactional Outbox + Relay
- `Stage 2`: Message Broker(Kafka 등) + Outbox

### 7.3 수치 기반 전환 기준

- Outbox 도입 기준 (아래 중 1개 이상):
  - 비동기 실패율 > 0.1% (최근 7일)
  - 수동 복구 평균 시간 > 30분
  - 외부 시스템 연동이 시작됨
- Broker 도입 기준 (아래 중 2개 이상):
  - 이벤트 소비자 수 >= 2
  - 이벤트 처리량 >= 1000 TPS
  - 월간 재처리/재구동 이슈 >= 5회

### 7.4 트리거 판정 증빙 방식

- 테스트 증빙:
  - JUnit/통합테스트 리포트로 실패 시나리오 재현 가능성 확인
- 운영 증빙:
  - Grafana에서 비동기 실패율, 수동 복구시간, lag 추세를 캡처
- 판정 규칙:
  - 단발성 이상치가 아니라 7일 추세 기준으로 전환 여부를 결정

## 8. 단기 실행 계획 (2026-03-09 ~ 2026-03-23)

- 2026-03-09 ~ 2026-03-15:
  - Phase 4/5 구현 완료
- 2026-03-16 ~ 2026-03-20:
  - 테스트/관측 강화, 리팩토링, 계약 문서 동기화
- 2026-03-21 ~ 2026-03-22:
  - 지원서용 기술/운영 근거 패키지 정리
- 2026-03-23:
  - 채용 지원 제출

### 8.1 Execution Algorithm (5-Step)

1. 요구사항에 의문 제기
2. 불필요한 과정 삭제
3. 단순화/최적화
4. 사이클 타임 가속
5. 자동화는 마지막

적용 원칙:
- `Recovery48` 단일 KPI 대신 `Recovery24` 중심 Metric Pack으로 재정의한다.
- 지표 정의/품질 보정이 확정되기 전에는 자동화 확장을 우선하지 않는다.

## 9. 변경 관리

- 기술 의사결정은 ADR로 기록한다.
- 요구사항 수치 변경 시 이 문서 버전을 갱신한다.
- 구현이 문서와 불일치하면 "코드 또는 문서" 중 하나를 즉시 수정한다.

## 10. 관련 운영 문서

- 데이터 품질 기준: `docs/spec/DATA_QUALITY.md`
- 배치 증분/재처리 절차: `docs/spec/BATCH_RUNBOOK.md`
- 복귀 지표 정의: `docs/spec/RECOVERY_METRICS.md`
- 취업/사업 KPI 분리: `docs/newPlan.md`
