# Batch Incremental & Reprocessing Runbook

> Version: v0.2  
> Updated: 2026-03-08  
> Scope: Phase 13 분석 배치 + Airflow 오케스트레이션 + 파생 데이터 재처리

## 1. 목적

- 일 배치를 증분 처리로 운영해 OLTP 부하를 제어한다.
- 실패/지연/지각 데이터(late arrival) 발생 시 안전하게 재처리한다.
- 배치 운영 절차를 문서화해 1인 운영 리스크를 줄인다.

## 2. 증분 처리 원칙

1. 워터마크 기반 처리
- 각 배치는 마지막 성공 지점을 워터마크로 저장한다.

2. 반개구간 윈도우 사용
- 조회 구간은 `[last_success_at, current_cutoff)` 형태로 고정한다.

3. 결과 저장은 멱등 upsert
- 동일 사용자/기간 재계산 시 결과가 덮어써지도록 설계한다.

4. 지각 데이터 허용
- 최근 N일(예: 3일) 슬라이딩 재처리 구간을 항상 포함한다.

## 3. 메타 테이블

```sql
create table batch_job_watermarks (
  job_name varchar(100) primary key,
  last_success_at timestamptz not null,
  last_run_id varchar(100) not null,
  updated_at timestamptz not null default now()
);
```

## 4. 표준 실행 플로우

1. 워터마크 조회
2. 대상 윈도우 계산
3. 입력 데이터 품질 pre-check 수행
4. 비즈니스 계산 실행
5. 결과 upsert
6. post-check(건수/정합성)
7. 성공 시 워터마크 갱신
8. 실패 시 워터마크 유지 + 알림 발송

## 4A. Airflow 적용 범위

- 적용 원칙:
  - Airflow는 배치 오케스트레이션 전용으로 사용한다.
  - 사용자 동기 API(예: 5분 시작, quick restart) 경로에는 사용하지 않는다.
- 1차 DAG 목록:
  - `daily_kpi_pipeline`:
    - extract(raw events) -> cleanse -> mart upsert -> quality check
  - `weekly_retrospective_input`:
    - 최근 7일 집계 -> 회고 입력 테이블 upsert
  - `backfill_reprocess`:
    - `start_date`, `end_date` 파라미터 기반 재처리
- 실패 처리:
  - task retry + on-failure 알림 + 워터마크 유지

## 5. 재처리 시나리오

### 5.1 단일 실행 실패

- 증상: 배치 실패 알림 발생
- 조치:
1. 실패 원인 분류(데이터/코드/인프라)
2. 원인 수정
3. 동일 윈도우 재실행
4. 품질 체크 통과 후 워터마크 갱신

### 5.2 지각 데이터 반영 필요

- 증상: 원천 이벤트가 늦게 유입됨
- 조치:
1. 영향 기간 산정(예: 최근 3일)
2. 해당 기간 백필(backfill) 실행
3. 파생 테이블 변경 건수 검증

### 5.3 논리 버그 수정 후 전체 재계산

- 증상: 계산식 오류 확인
- 조치:
1. 신규 버전 태그 부여
2. 기간 단위 분할 재처리
3. 샘플 검증 후 전체 반영
4. 완료 리포트 문서화

## 6. 멱등성 규칙

- 결과 테이블은 자연키/업무키 유니크를 가진다.
- 재실행은 insert가 아니라 upsert를 기본으로 한다.
- 실행 단위마다 `run_id`를 부여해 추적 가능하게 유지한다.

## 7. 운영 SLO

- 일 배치 완료 시간: p95 < 30분
- 배치 실패율: 주간 < 2%
- 재처리 완료 시간: 표준 케이스 < 60분

## 8. 모니터링 지표

- `batch_duration_seconds`
- `batch_processed_rows`
- `batch_failed_runs_total`
- `batch_watermark_lag_seconds`
- `batch_reprocess_runs_total`
- `airflow_dag_success_ratio`
- `airflow_task_retry_total`
- `airflow_dag_duration_seconds`

## 9. 배포/변경 체크리스트

- 계산식 변경 시 샘플 데이터 회귀 테스트 통과
- 워터마크 마이그레이션 필요 여부 확인
- 재처리 계획(기간/영향/롤백) 사전 작성
- 변경 내용 `docs/refactor.md` 기록
- Airflow DAG 변경 시:
  - 로컬/스테이징에서 백필 DAG dry-run 수행
  - DAG SLA/재시도 횟수/timeout 설정 확인
  - 배치 품질 임계치(DQ) 알림 정상 동작 확인
