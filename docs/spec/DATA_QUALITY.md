# Data Quality Spec

> Version: v0.3  
> Updated: 2026-03-14  
> Scope: OLTP + Batch + Analytics 품질 기준

## 1. 목적

- 데이터 품질을 "느낌"이 아니라 수치로 관리한다.
- 복귀 지표와 회고 결과의 신뢰도를 운영 지표로 추적한다.
- 취업 포트폴리오 관점에서 데이터 엔지니어링 기본 역량(검증/재처리/알림)을 명시한다.

## 2. 품질 차원과 기준

| 차원 | 정의 | 기준(초기) | 측정 주기 |
|---|---|---|---|
| Completeness | 필수 컬럼 누락 여부 | 필수 컬럼 완전성 >= 99.5% | 매 배치 실행 |
| Uniqueness | 중복 키 발생 여부 | 핵심 키 중복률 = 0% | 매 배치 실행 |
| Validity | 타입/범위/enum 유효성 | 유효성 실패율 < 0.1% | 매 배치 실행 |
| Consistency | 테이블 간 정합성 | 정합성 체크 실패 0건 | 일 1회 |
| Timeliness | 데이터 최신성 | 배치 지연 p95 < 30분 | 일 1회 |

## 3. 핵심 체크 항목

### 3.1 계획/복귀 세션 데이터

- `inbox_items.content` 누락 금지, 최대 길이 200자 유지
- `timeboxes`는 동일 사용자 기준 겹침 금지
- `recovery_sessions`는 `user_id`, `status`, `started_at` 필수
- 종료된 세션은 `ended_at >= started_at` 보장

### 3.2 복귀 이벤트 데이터

- `failure_events`, `restart_events`는 `event_id` 중복 금지
- 3분 미만 재시작은 `is_effective_restart=false`로 분리 저장
- `restart_events`는 대응되는 `failure_event` 또는 `timebox` 문맥을 가져야 함
- `cycle_completed`는 대응되는 `cycle_started`가 존재해야 함

### 3.3 분석/회고 데이터

- `recovery_friction_signals`는 `(user_id, signal_date)` 중복 금지
- `ai_retrospectives`는 `(user_id, week_start)` 중복 금지
- 원천 데이터 없는 사용자의 신호/회고 생성 금지

## 4. 검증 레이어

1. Write-time 검증
- DB 제약조건(`NOT NULL`, `CHECK`, `UNIQUE`, `FK`)으로 1차 차단

2. Batch pre-check
- 입력 데이터 건수/필수 필드/중복 사전 체크

3. Batch post-check
- 결과 건수, 정합성 체크, 품질 리포트 저장

4. 소비 전 검증
- API 응답 직전 핵심 필드 누락 여부 샘플 검증

## 5. 알림 기준

- Completeness < 99.5%: `WARN`
- Uniqueness 실패(중복 발생): `CRITICAL`
- Consistency 실패: `CRITICAL`
- Timeliness p95 >= 30분(2회 연속): `WARN`, 4회 연속 `CRITICAL`

## 6. 장애 대응 규칙

- `CRITICAL` 발생 시 해당 배치 결과를 "임시 비공개" 상태로 전환
- 재처리(runbook) 완료 전까지 최신 정상 스냅샷을 서빙
- 원인/영향/조치 내역을 `docs/refactor.md` 및 장애 로그에 기록

## 7. 추적 메트릭

- `dq_completeness_ratio`
- `dq_duplicate_count`
- `dq_validity_error_rate`
- `dq_consistency_mismatch_count`
- `dq_batch_lag_seconds`

## 8. 책임 경계

- 도메인 팀: 입력 모델/제약/도메인 유효성
- 배치 팀(동일 인원 가능): 사전/사후 검증 및 재처리
- 운영: 알림 임계치 관리와 에스컬레이션
