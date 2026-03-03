# Engineering Spec (Reboot Baseline)

> Version: v0.1  
> Updated: 2026-03-03  
> Scope: Phase 1~2 기준선 + Phase 15 출시 목표

## 1. 문제 정의

FocusKeeper는 ADHD 사용자에게 "실행-유지-회고" 루프를 제공하는 자기관리 서비스다.  
리부트 목표는 복잡한 초기 설계를 줄이고, 데이터 신뢰성과 운영 안정성을 우선 확보하는 것이다.

## 2. 목표 및 비목표

### 목표

- Phase 15까지 출시 가능한 백엔드 베이스라인 완성
- 도메인 순수성 + Transactional Outbox 기반 이벤트 신뢰성 확보
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
- `FR-004` 이벤트 전달은 Outbox 테이블을 통해 비동기 처리되어야 한다. (Phase 12.9+)
- `FR-005` 번아웃 지수 계산은 배치로 수행되고 결과를 조회 API에서 제공해야 한다. (Phase 13)
- `FR-006` 주간 AI 회고는 비동기 작업으로 생성되어야 한다. (Phase 15)

### 3.2 비기능 요구사항 (Non-Functional Requirements)

- `NFR-001 Performance`: 일반 조회 API p95 < 300ms (50 RPS 기준)
- `NFR-002 Throughput`: 단일 인스턴스에서 100 RPS까지 기능 저하 없이 처리
- `NFR-003 Availability`: 월간 99.5% 이상 (초기), 출시 안정화 후 99.9% 목표
- `NFR-004 Consistency`: 정산/지갑/Outbox 기록은 Strong Consistency
- `NFR-005 Event Delivery`: Outbox relay 처리 지연 p95 < 5s
- `NFR-006 Recovery`: 장애 복구 시 데이터 유실 0건(영속 스토리지 기준)
- `NFR-007 Observability`: 에러율, 지연시간, Outbox lag, 배치 시간 메트릭 수집

## 4. 시스템 아키텍처

### 4.1 구조

- 기본 구조: Modular Monolith (Spring Boot)
- 계층: `domain` / `application` / `infrastructure` / `interface`
- 데이터 경로:
  - OLTP: API -> Service -> RDB
  - Event: Domain Event -> Outbox -> Relay Worker -> External Target
  - Analytics (Track A): RDB -> Spring Batch -> RDB

### 4.2 기술 스택 및 근거

- Spring Boot: 빠른 개발/운영 표준화
- PostgreSQL: 트랜잭션 무결성 및 SQL 집계
- Redis: 캐시/랭킹/저지연 조회
- Outbox 패턴: 비즈니스 트랜잭션과 이벤트 기록 원자성 보장
- Spring Batch: 현재 데이터 규모에서 비용 대비 최적

## 5. 일관성 정책

- Strong Consistency:
  - 챌린지 상태 변경
  - 지갑 잔액/거래 내역
  - outbox_events insert
- Eventual Consistency:
  - 알림 발송
  - 피드 fan-out
  - 분석 결과 캐시 반영

## 6. 출시 수용 기준 (Release Acceptance)

- `RA-001` CI 파이프라인에서 테스트 통과
- `RA-002` Outbox 원자성/멱등성 테스트 통과
- `RA-003` Sentry + Grafana 대시보드 활성
- `RA-004` 배치 SLA 및 Outbox lag 알림 설정 완료
- `RA-005` AI 비동기 처리 timeout/retry/fallback 검증 완료

## 7. 변경 관리

- 기술 의사결정은 ADR로 기록한다.
- 요구사항 수치 변경 시 이 문서 버전을 갱신한다.
- 구현이 문서와 불일치하면 "코드 또는 문서" 중 하나를 즉시 수정한다.
