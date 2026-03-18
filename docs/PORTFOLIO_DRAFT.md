# RebootFocus Portfolio Draft

> Version: v0.1  
> Updated: 2026-03-16  
> Scope: 실제 취업 제출용으로 발전시키기 위한 1차 문안 초안

## 1. Project Overview

### 한 줄 설명

RebootFocus는 전날 계획이 무너지면 다음날까지 다시 못 붙잡는 직장인을 위해, 놓친 일을 다시 시작하게 만드는 복귀 코치 백엔드다.

### 해결하려는 문제

- 생산성 앱은 많지만 "실패 다음날 다시 시작"을 구조적으로 돕는 제품은 드물다.
- 사용자가 전날 계획에 실패하면 다음날 첫 집중 블록을 다시 못 잡고 루프에서 이탈한다.
- 이 프로젝트는 계획 관리보다 복귀 행동 설계와 복귀 KPI 측정을 핵심 문제로 본다.

### 사용 기술

- Spring Boot
- Java
- PostgreSQL 중심 데이터 모델
- Spring Batch
- Docker

### 전체 아키텍처 요약

- Client -> Recovery API -> PostgreSQL
- PostgreSQL -> Batch -> KPI Mart
- Observability -> Grafana / Alerting
- AI Retrospective -> Async Worker 분리

### 핵심 기능

- Brain Dump / Big3 / 첫 복귀 블록 배정
- 복귀 세션 시작/완료/중단
- 실패 체크인과 10분 복귀 재시작
- Recovery24 / TTR / RestartCount 집계

## 2. Problem

생산성 앱을 여러 번 써본 직장인도 하루가 한 번 무너지면 다음날까지 끌려가는 경우가 많다.  
문제는 계획 기능이 부족해서가 아니라, 실패 직후와 다음날 오전에 다시 붙잡는 구조가 약하다는 점이다.  
그래서 이 프로젝트는 "계획 앱"이 아니라 "실패 다음날 복귀"를 별도 문제로 정의하고, 복귀 행동 이벤트와 KPI를 중심으로 제품을 설계했다.

## 3. Architecture

### 시스템 설계 의도

- 핵심 복귀 경로는 동기 API + RDB 트랜잭션으로 단순하게 유지한다.
- KPI 계산, 재처리, DQ는 배치 경로로 분리해 사용자 요청 경로와 장애 반경을 나눈다.
- AI 회고는 비동기 워커로 격리해 코어 경로를 오염시키지 않는다.

### 설명에 사용할 그림

- System Architecture
- Recovery Loop Sequence
- Data Flow / ETL Flow

## 4. Core Challenges

### Challenge 1. 실패 다음날 복귀 루프 설계

문제:
- 사용자가 전날 실패한 뒤 다음날 첫 복귀 블록을 다시 못 잡으면 계획 루프 자체가 끊긴다.

접근:
- Brain Dump, Big3, 첫 복귀 블록, 실패 체크인, 10분 재시작을 하나의 상태 전이 루프로 묶는다.

해결:
- `Timebox`, `RecoverySession`, `FailureEvent`, `RestartEvent`를 분리된 도메인으로 설계한다.
- 일반적인 타이머 기능이 아니라 "복귀 행동을 실행시키는 인터랙션"으로 제한한다.

결과:
- 현재 Brain Dump / Big3 / 첫 복귀 블록 배정은 구현 완료했고, 이어서 Session / Failure API를 연결 중이다.
- 최종적으로는 Recovery24 입력 이벤트를 완전하게 수집하는 구조를 목표로 한다.

### Challenge 2. 복귀 KPI 신뢰성 확보

문제:
- 실패/재시작 이벤트가 쌓여도 중복, 누락, 지각 데이터가 있으면 Recovery24와 TTR이 왜곡된다.

접근:
- raw/clean/mart 계층, 워터마크, 멱등 upsert, 백필 가능성을 먼저 정의한다.

해결:
- `daily_kpi_pipeline`을 기준으로 복귀 이벤트를 일간 KPI mart로 적재하는 구조를 설계했다.
- DQ와 재처리 경로를 제품 기능과 따로가 아니라 같은 설계 묶음으로 관리한다.

결과:
- 현재는 문서와 지표 정의를 정리한 단계고, 다음 구현 우선순위는 원천 이벤트 적재와 증분 집계다.

### Challenge 3. 운영 문제와 사용자 문제 분리

문제:
- 사용자의 복귀 실패 패턴과 시스템 장애가 섞이면 원인 분석과 대응이 늦어진다.

접근:
- API 경로, Batch 경로, AI 경로를 분리하고 각각 다른 메트릭으로 본다.

해결:
- 복귀 루프 대시보드와 Batch/DQ 대시보드를 분리하는 방향으로 아키텍처를 설계했다.

결과:
- 이후 운영/관측 Phase에서 사용자 행동 문제와 시스템 운영 문제를 다른 증거로 설명할 수 있게 된다.

## 5. Tech Decisions

- 왜 Spring Boot인가:
  - 빠른 구현, 테스트, 운영 표준화가 필요했고 현재 프로젝트 범위에 가장 실용적이었다.
- 왜 PostgreSQL 중심인가:
  - 복귀 코어 경로는 강한 정합성과 SQL 기반 집계가 중요하다.
- 왜 Spring Batch + RDB부터 시작하는가:
  - 현재는 데이터 규모보다 재처리 가능성과 운영 단순성이 더 중요하다.
- 왜 Kafka/Spark를 기본값으로 두지 않았는가:
  - 확장 트리거가 없는 상태에서 복잡도를 올리는 것은 제품과 포트폴리오 모두에 불리하다고 판단했다.

## 6. Improvement

현재 기록 예정 항목:

- Timebox 충돌 검증
- Session 상태 전이 예외 처리
- Recovery 이벤트 입력 완전성 점검
- KPI 집계의 워터마크/멱등 upsert/백필
- DQ 체크와 운영 알림

개선 섹션은 수치, 테스트, 로그가 확보된 뒤 실제 결과로 보강한다.

## 7. Lessons

- 넓은 생산성 앱보다 "실패 다음날 복귀"처럼 문제를 한 점으로 좁히는 편이 설계와 메시지 모두 선명했다.
- 복귀 KPI는 나중에 붙이는 지표가 아니라 도메인 이벤트 설계 단계에서 같이 정의해야 한다.
- 포트폴리오는 기능 수보다 문제 해결 구조와 기술적 판단 근거를 얼마나 일관되게 설명하느냐가 더 중요했다.

## 8. 현재 상태 메모

- 구현 완료: `F-001 Brain Dump`, `F-002 Big3`, `F-003 Timebox`
- 진행 중: `F-004 Recovery Session`, `F-005 Failure Check-in`
- 예정: `F-006 10분 복귀 재시작`, `daily_kpi_pipeline`, DQ/재처리, 관측 대시보드

## 9. 연계 문서

- `docs/PORTFOLIO_PACKET_TEMPLATE.md`
- `docs/PORTFOLIO_CASEBOARD.md`
- `docs/ARCHITECTURE_OVERVIEW.md`
- `docs/spec/FEATURE_PROCESS_SPEC.md`
- `docs/spec/KEY_FLOWS.md`
