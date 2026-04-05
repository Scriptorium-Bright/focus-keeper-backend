# Airflow Rough Assets

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

## Docker로 띄우기

이 디렉토리에는 로컬에서 Airflow UI를 직접 볼 수 있도록 Docker 자산도 포함돼 있다.

추가 파일:
- `Dockerfile`
  - `apache/airflow` 이미지 위에 `curl`을 설치한다.
  - 현재 DAG가 `BashOperator + curl`을 사용하기 때문에 필요하다.
- `compose.yaml`
  - 단일 Airflow 컨테이너에서 `scheduler + webserver`를 함께 띄운다.
- `.env.example`
  - API base URL, 기본 userId, backfill 기간, admin 계정 예시값

### 실행 순서

1. Docker Desktop을 켠다.
2. 필요하면 `.env.example`을 참고해 `.env`를 만든다.
3. 이 디렉토리에서 아래 명령을 실행한다.

```bash
docker compose up --build -d
```

4. 브라우저에서 아래 주소로 접속한다.

```text
http://localhost:8088
```

기본 로그인 값:
- username: `admin`
- password: `admin`

### 중지/정리

```bash
docker compose down
```

로그까지 지우려면:

```bash
docker compose down -v
```

## UI에서 무엇을 볼 수 있나

- DAG 목록
  - `daily_kpi_pipeline`
  - `backfill_reprocess`
  - `weekly_retrospective_input`
- Graph View
  - task 의존성 시각화
- Task log
  - `curl` 호출 결과와 실패 여부

## 주의

- 이 구성은 rough local demo용이다.
- 실제 운영용 멀티 컨테이너 Airflow stack이 아니라, 빠르게 UI와 DAG를 확인하기 위한 단일 컨테이너 구성이다.
- DAG task는 호스트의 Spring 앱을 호출하므로, API 대상 앱도 먼저 실행돼 있어야 한다.
- 기본 `API_BASE_URL`은 macOS Docker 환경을 가정한 `http://host.docker.internal:8080`이다.
