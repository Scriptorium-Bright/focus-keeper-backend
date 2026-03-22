# Phase 14 Airflow Rough Assets

이 디렉토리는 Phase 14 Watchtower의 `14.4 Airflow 오케스트레이션` rough 자산을 모아둔 곳이다.

## 목적

- Airflow를 배치 엔진으로 교체하지 않고, 기존 Spring Batch/API 실행 경로 위에 오케스트레이션 계층을 얹는 예시를 남긴다.
- 실제 로컬 Airflow 배포본이 아니라, 어떤 DAG와 task 구조로 연결할지 보여주는 rough 자산이다.

## 포함된 DAG

- `dags/daily_kpi_pipeline.py`
  - 일간 KPI 생성 후 watermark 확인
- `dags/backfill_reprocess.py`
  - 지정한 기간의 KPI backfill 재처리
- `dags/weekly_retrospective_input.py`
  - 주간 회고 생성

## 공통 환경 변수

- `API_BASE_URL`
  - 예: `http://host.docker.internal:8080`
- `API_USER_ID`
  - 예: `demo-analytics-user`
- `API_START_DATE`
  - backfill 기본 시작일
- `API_END_DATE`
  - backfill 기본 종료일

## 주의

- DAG는 `BashOperator + curl` 기준 rough 예시다.
- 실제 운영 전에는 Airflow connection, secret 관리, retry 정책, timeout, alert routing을 인프라 환경에 맞게 다시 붙여야 한다.
