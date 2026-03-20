# Data Pipeline & ETL Blueprint

> Version: v0.4  
> Updated: 2026-03-16  
> Scope: 데이터 파이프라인 정의, 구축 전략, ETL 설계, 기대 효과

## 1. 데이터 파이프라인이란 무엇인가

데이터 파이프라인은 "원천 데이터가 생성된 뒤, 수집/정제/저장/활용까지 흐르는 전체 운영 경로"다.  
이 프로젝트에서는 사용자 행동 데이터를 안정적으로 가공해 KPI와 회고/추천 입력으로 제공하는 시스템을 의미한다.

## 2. 왜 필요한가

- 복귀 코치 제품의 핵심 지표(Recovery24, Recovery48, RestartCount24/48, TTR, CycleCompletionRate)를 신뢰성 있게 계산하기 위해
- 기능 개선 판단을 감이 아니라 데이터로 하기 위해
- 장애 발생 시 재처리 가능한 운영 체계를 확보하기 위해

## 3. 목표 상태 (What Good Looks Like)

- 매일 KPI 집계가 자동 수행되고 지연/실패가 관측된다.
- 지각 데이터(late arrival)와 배치 실패를 재처리로 복구할 수 있다.
- 데이터 품질(결측/중복/유효성/정합성)이 수치로 관리된다.
- 실시간 API 경로와 배치 경로가 분리되어 상호 장애 전파를 줄인다.

## 4. 아키텍처 원칙

- 기본 경로: `Spring Batch + RDB`
- 현재 오케스트레이션: 애플리케이션 내부 실행 + `Spring Batch`
- 확장 오케스트레이션: `Airflow`는 `Phase 14`에서 스케줄/재처리/의존성 제어 계층으로 도입
- 이벤트 확장: `Kafka`는 트리거 충족 시 도입
- 핵심 원칙:
  - 사용자 동기 API 경로와 배치 경로 분리
  - 증분 처리 + 워터마크 + 멱등 upsert
  - 품질 체크 실패 시 알림 및 워터마크 유지

## 5. 구축 방법 (단계별)

### 5.1 데이터 소스 정의 (Extract 대상)

- OLTP 이벤트:
  - 복귀 세션 시작/완료/중단
  - 실패 체크인 및 복귀 액션
  - Brain Dump/Big3/Timebox 변경 이벤트
- 기준 테이블:
  - 사용자, 복귀 세션, 계획, 복귀 행동, 회고 관련 테이블

### 5.2 변환 규칙 정의 (Transform)

- 정제:
  - 필수 컬럼 누락 제거
  - 중복 키 제거
  - enum/타입 유효성 검증
- 표준화:
  - 타임존/시간 단위 정규화
  - 상태코드/사유코드 표준 사전 매핑
- 파생 계산:
  - Recovery24/Recovery48
  - RestartCount24/RestartCount48
  - TTR
  - CycleCompletionRate/EffectiveFocusMinutes
  - PlanExecutionRate, EstimationError
  - FailureCountByHour / FailureRatioByHour / PeakFailureHour
  - recovery_friction_signals

### 5.3 적재 전략 (Load)

- raw -> clean -> mart 3계층 적재
- 결과 테이블은 자연키 기반 upsert
- 워터마크 메타테이블로 증분 범위 제어

권장 저장 구조:
- `raw_*`: 원천 이벤트 스냅샷
- `clean_*`: 정제/표준화 완료 데이터
- `mart_kpi_daily`, `mart_kpi_weekly`: 서비스/리포트 제공 계층
- `mart_failure_hourly`: 로컬 시간대별 실패 분포 및 피크 시간대

## 6. ETL 파이프라인 상세 설계

### 6.1 E (Extract)

- 윈도우: `[last_success_at, current_cutoff)` 반개구간
- 지각 데이터 보정: 최근 N일(기본 3일) 슬라이딩 재처리 포함
- 실패 시: 워터마크 유지, 동일 구간 재실행

### 6.2 T (Transform)

- pre-check:
  - 입력 건수/필수 컬럼/중복/유효성 검사
- business transform:
  - 복귀 KPI 팩 계산식 적용
  - 사용자/날짜 단위 집계
  - `failure_events` 기준 로컬 시간(`local_hour`) 파생
  - 시간대별 실패 건수/비율/피크 시간대 계산
- post-check:
  - 원천 대비 결과 건수 검증
  - 정합성 오류 탐지 시 실패 처리

### 6.3 L (Load)

- mart 테이블 upsert
- 실행 메타 기록(run_id, processed_rows, duration, status)
- 성공 시 워터마크 갱신

## 7. Airflow 도입 예정 위치와 역할

현재 기본 경로는 `Spring Batch + 애플리케이션 내부 실행`이다.  
Airflow는 `Phase 14`에서 "작업 오케스트레이션" 계층으로 정식 도입한다.  
실제 계산 로직은 SQL/Spring Batch에 두고, Airflow는 순서/스케줄/재시도/백필을 관리한다.

정식 도입 시 1차 DAG:
- `daily_kpi_pipeline`
  - extract -> cleanse -> compute_kpi -> load_mart -> dq_check
  - 부가 산출물: `mart_failure_hourly`, `peak_failure_hour`
- `weekly_retrospective_input`
  - weekly_aggregate -> load_retrospective_input -> dq_check
- `backfill_reprocess`
  - parameter_validate -> replay_window -> load_mart -> audit_report

## 8. Kafka 도입 위치와 조건

- 기본 상태에서는 Kafka 없이 운영한다.
- 아래 트리거 충족 시 `lab/kafka-adapter`에서 검증 후 도입 판단:
  - 비동기 실패율(7d) > 0.1%
  - 수동 복구 평균 시간 > 30분
  - 이벤트 소비자 수 >= 2
  - 월 재처리/재구동 이슈 >= 5회

Kafka를 도입하면:
- 이벤트 수집 레이어를 decouple
- 실시간 처리량 확장성 확보
- 소비자 분리(분석/알림/외부연동) 용이

## 9. 구축 효과 (도입 시 기대값)

제품/운영 효과:
- KPI 계산 자동화 및 리포팅 신뢰도 향상
- 배치 실패 복구 시간 단축
- 지표 기반 기능 우선순위 결정 가능
- 사용자별 실패 집중 시간대를 찾아 리마인더/복귀 개입 시점을 조정할 수 있음

엔지니어링 효과:
- 데이터 품질 기준의 수치화
- 장애 재현/재처리 절차 표준화
- 실시간/배치 경계 명확화로 장애 반경 축소

채용 포트폴리오 효과:
- "데이터 파이프라인 개발 및 운영" 경험을 구조적으로 설명 가능
- 현재는 `Java/SQL + Spring Batch` 기반 파이프라인으로 설명하고, 이후 `Airflow + (조건부) Kafka` 승격 경로를 근거로 제시 가능

## 10. 운영 지표

- 파이프라인:
  - `batch_duration_seconds`
  - `batch_failed_runs_total`
  - `batch_watermark_lag_seconds`
  - `airflow_dag_success_ratio` (Phase 14 이후)
  - `airflow_task_retry_total` (Phase 14 이후)
- 데이터 품질:
  - `dq_completeness_ratio`
  - `dq_duplicate_count`
  - `dq_validity_error_rate`
  - `dq_consistency_mismatch_count`
  - `dq_missing_timezone_count`

## 11. 구현 우선순위 (단기)

1. `daily_kpi_pipeline`부터 구현
2. 데이터 품질 pre/post-check 자동화
3. `backfill_reprocess`로 복구 경로 확보
4. `weekly_retrospective_input` 추가
5. 트리거 충족 여부 관측 후 Kafka 도입 판단

시간대 실패 진단 규칙:
- 시간대 통계는 반드시 사용자 로컬 타임존 기준으로 계산한다.
- 기본 버킷은 `0~23`시이며, 리포트용으로는 `3시간` 또는 `6시간` 묶음 버킷을 추가할 수 있다.
- 개인화 기능에 바로 쓰기 전에 최소 최근 14일 또는 실패 이벤트 5건 이상 누적을 권장한다.

## 12. 연계 문서

- 제품 방향: `docs/PRODUCT_INTENT.md`
- 전체 로드맵: `docs/newPlan.md`
- 엔지니어링 기준: `docs/spec/ENGINEERING_SPEC.md`
- 복귀 지표 정의: `docs/spec/RECOVERY_METRICS.md`
- 데이터 모델: `docs/spec/DATA_MODEL.md`
- 데이터 품질: `docs/spec/DATA_QUALITY.md`
- 배치 운영 절차: `docs/spec/BATCH_RUNBOOK.md`

## 13. Execution Algorithm 적용

1. 요구사항에 의문 제기: 지표 목적/활용처 확인
2. 불필요한 과정 삭제: 쓰이지 않는 집계/테이블 제거
3. 단순화/최적화: Metric Pack 기준으로 집계 로직 통합
4. 사이클 타임 가속: 일간 DAG + 주간 리포트 루프 고정
5. 자동화는 마지막: 지표 정의/품질 안정화 후 확장 자동화 적용
