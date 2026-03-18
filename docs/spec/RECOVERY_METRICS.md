# Recovery Metrics Spec

> Version: v0.2  
> Updated: 2026-03-16  
> Scope: 복귀/재시작/사이클 KPI 정의 및 계산 규칙

## 1. 목적

- 복귀 지표를 단일 문구가 아닌 측정 가능한 지표 팩으로 정의한다.
- 제품/데이터/운영 문서 간 KPI 해석을 일치시킨다.
- 대시보드/실험/자소서에서 같은 수치를 일관되게 사용한다.

## 2. Metric Pack

### 2.1 복귀 여부 (Binary)

- `Recovery24`
  - 정의: 실패 이벤트 발생 후 24시간 내 1회 이상 재시작한 사용자 비율
  - 용도: 메인 복귀 KPI
- `Recovery48`
  - 정의: 실패 이벤트 발생 후 48시간 내 1회 이상 재시작한 사용자 비율
  - 용도: 롱테일 복귀 회수율 보조 KPI

### 2.2 복귀 강도 (Count)

- `RestartCount24`
  - 정의: 실패 후 24시간 내 재시작 횟수
- `RestartCount48`
  - 정의: 실패 후 48시간 내 재시작 횟수

### 2.3 복귀 속도 (Latency)

- `TTR` (Time To Recovery)
  - 정의: 실패 시점부터 첫 재시작 시점까지 경과 시간
  - 집계: 평균/중앙값(p50)/p90 동시 추적 권장

### 2.4 집중 사이클 (Execution Quality)

- `CycleStarted`: 집중-휴식 사이클 시작 횟수
- `CycleCompleted`: 집중-휴식 사이클 완료 횟수
- `CycleCompletionRate` = `CycleCompleted / CycleStarted`
- `EffectiveFocusMinutes`: 유효 집중 시간 합

### 2.5 복귀 마찰 진단 (Diagnostic)

- `FailureCountByHour`
  - 정의: 사용자 또는 코호트 기준 로컬 시간대별 실패 이벤트 건수
  - 용도: 어떤 시간대에 실패가 몰리는지 진단
- `FailureRatioByHour`
  - 정의: 특정 시간대 실패 건수 / 전체 실패 건수
  - 용도: 실패 집중 시간대 비중 확인
- `PeakFailureHour`
  - 정의: 실패가 가장 많이 발생한 로컬 시간대(0~23)
  - 용도: 개인화된 복귀 개입 시점 또는 리마인더 시간 최적화
- `PeakFailureWindow`
  - 정의: `06-09`, `09-12`, `12-15`, `15-18`, `18-21`, `21-24`, `00-06` 같은 시간대 버킷 중 실패가 집중된 구간
  - 용도: 세그먼트 리포트와 복귀 마찰 분석

## 3. 해석 규칙

- `Recovery24`를 메인 KPI로 본다.
- `Recovery48`은 메인 KPI를 보완하는 롱테일 지표로 본다.
- `RestartCount`는 단독 해석하지 않고 `CycleCompletionRate`, `EffectiveFocusMinutes`와 함께 본다.
- 복귀 횟수 증가가 실제 집중 품질 개선으로 이어졌는지 반드시 동시 검증한다.
- 시간대별 실패 분포는 `왜 실패했는가`를 설명하는 진단 지표이지, 제품의 메인 성공 지표는 아니다.
- 시간대 통계는 반드시 사용자 로컬 타임존 기준으로 계산한다. 서버 시간 기준 집계는 해석에 사용하지 않는다.

## 4. 품질 보정 규칙

- 3분 미만 재시작은 스팸성 이벤트로 별도 분리(`is_effective_restart = false`)
- 중복 이벤트는 `event_id`/`session_id` 기준 제거
- locale/timezone 누락 이벤트는 KPI 계산에서 제외하고 품질 경고로 집계
- 시간대별 실패 통계는 `failure_events.occurred_at + timezone` 또는 동등한 로컬 시각 정보가 없는 경우 계산에서 제외한다.

## 5. 이벤트 스키마 (최소)

```json
{
  "eventId": "uuid",
  "userId": "string",
  "eventName": "failure_checked|restart_started|cycle_started|cycle_completed",
  "occurredAt": "2026-03-12T10:10:00+09:00",
  "localHour": 10,
  "durationMinutes": 10,
  "isEffectiveRestart": true,
  "locale": "ko-KR",
  "timezone": "Asia/Seoul"
}
```

## 6. 계산 윈도우

- 기본 윈도우:
  - Recovery24/RestartCount24: `failure_ts` 기준 24시간
  - Recovery48/RestartCount48: `failure_ts` 기준 48시간
- 집계 주기:
  - 일간: 사용자/일자 집계
  - 주간: 코호트/국가/실험군 집계

시간대 진단 집계:
- 일간: 사용자/일자/시간대 집계
- 주간: 사용자, 세그먼트, 코호트 기준 `FailureCountByHour`, `PeakFailureHour`

## 7. 운영 지표 연계

- 파이프라인:
  - `batch_duration_seconds`
  - `airflow_dag_success_ratio`
- 품질:
  - `dq_duplicate_count`
  - `dq_validity_error_rate`
  - `dq_consistency_mismatch_count`
- 제품:
  - `recovery24_ratio`
  - `recovery48_ratio`
  - `restart_count_24_avg`
  - `cycle_completion_rate`
  - `failure_count_by_hour`
  - `peak_failure_hour`

## 8. 연계 문서

- `docs/newPlan.md`
- `docs/spec/KPI_TRACKS.md`
- `docs/spec/DATA_PIPELINE_ETL_BLUEPRINT.md`
- `career/GLOBAL_EXPANSION_PLAYBOOK.md`
