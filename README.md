# RebootFocus

실패 직후 재시작과 24/48시간 내 복귀 여부를 기록하고 분석하는 Spring Boot 백엔드 포트폴리오입니다.

RebootFocus는 단순한 할 일 앱이 아니라, `실패 기록 -> 재시작 -> 일간 KPI -> 운영 관측` 흐름을 하나의 백엔드 시스템으로 연결하는 데 초점을 둔 프로젝트입니다. CRUD를 넘어서 상태 전이, 일정 검증, 배치 집계, 데이터 품질, 운영 API까지 한 저장소 안에서 설계하고 구현했습니다.

## Portfolio Positioning

- 복귀 세션 시작, 완료, 중단과 실패 체크인, 재시작 이벤트를 별도 도메인 모델로 추적합니다.
- Inbox, Big3, Timebox를 통해 계획 데이터를 받고 시간 겹침과 배분 규칙을 검증합니다.
- Spring Batch 기반 일간 KPI mart, quality report, backfill 흐름을 제공합니다.
- PostgreSQL `ON CONFLICT` upsert와 `lastProcessedDate` 추적으로 재실행 안전성을 확보합니다.
- 운영 개요, 알림, 런북, Prometheus 지표까지 포함해 관측 가능한 시스템을 지향합니다.

## System Architecture

```mermaid
flowchart LR
    Client[Client / Swagger UI]

    subgraph API[Spring Boot API Layer]
        PlanningAPI[Planning API]
        ExecAPI[Recovery Execution API]
        AnalyticsAPI[Analytics API]
        OpsAPI[Ops Overview API]
    end

    subgraph Domain[Recovery Domain Events]
        Inbox[Inbox / Big3 / Timebox]
        Sessions[Recovery Sessions]
        Failures[Failure Events]
        Restarts[Restart Events]
        Retro[Weekly Retrospective]
    end

    subgraph Batch[Analytics Processing]
        KPI[Daily KPI Pipeline]
        Quality[Quality Validation]
        LastProcessedDate[lastProcessedDate Advance]
        Friction[Friction Analytics]
    end

    subgraph Ops[Operational Layer]
        Metrics[Prometheus Metrics]
        Alerts[Alert / Runbook Flow]
        Airflow[Airflow Rough DAGs]
    end

    DB[(PostgreSQL)]

    Client --> PlanningAPI
    Client --> ExecAPI
    Client --> AnalyticsAPI
    Client --> OpsAPI

    PlanningAPI --> Inbox
    ExecAPI --> Sessions
    ExecAPI --> Failures
    ExecAPI --> Restarts
    Retro --> DB

    Sessions --> KPI
    Failures --> KPI
    Restarts --> KPI
    Inbox --> KPI

    Failures --> Friction
    Restarts --> Friction

    KPI --> Quality
    KPI --> LastProcessedDate
    KPI --> DB
    Quality --> DB
    LastProcessedDate --> DB
    Friction --> DB

    AnalyticsAPI --> DB
    OpsAPI --> DB
    DB --> Metrics
    Metrics --> Alerts

    Airflow --> AnalyticsAPI
    Airflow --> OpsAPI
```

## Backend Highlights

- Package-by-domain 구조로 `planning`, `execution`, `analytics`, `friction`, `retrospective`, `common` 책임을 분리했습니다.
- Recovery session, failure event, restart event를 분리 저장해 복귀 lifecycle을 쿼리 가능한 데이터 모델로 구성했습니다.
- 일간 KPI 파이프라인은 raw event를 읽어 mart 저장, quality report 생성, `lastProcessedDate` 갱신까지 한 흐름으로 묶었습니다.
- PostgreSQL에서는 자연키(`user_id`, `metric_date`) 기준 `ON CONFLICT` upsert를 사용해 idempotent 저장을 처리합니다.
- H2 기반 기본 테스트와 PostgreSQL 기반 analytics integration test를 분리해 속도와 런타임 검증을 같이 확보했습니다.
- `Actuator`, `Prometheus`, `/api/v1/ops/**` API, Docker image artifact 생성까지 포함해 운영성과 배포 자동화를 같이 다뤘습니다.

## Representative APIs

- Planning
  - `POST /api/v1/recovery/inbox-items`
  - `POST /api/v1/recovery/big3`
  - `POST /api/v1/recovery/timeboxes`
- Execution
  - `POST /api/v1/recovery/sessions/start`
  - `POST /api/v1/recovery/sessions/complete`
  - `POST /api/v1/recovery/sessions/interrupt`
  - `POST /api/v1/recovery/failures/check-in`
  - `POST /api/v1/recovery/restarts`
- Analytics
  - `POST /api/v1/recovery/analytics/kpis/daily`
  - `GET /api/v1/recovery/analytics/kpis/daily`
  - `GET /api/v1/recovery/analytics/kpis/daily/quality`
  - `POST /api/v1/recovery/analytics/kpis/daily/backfill`
  - `GET /api/v1/recovery/analytics/kpis/daily/last-processed-date`
  - `POST /api/v1/recovery/analytics/failure-hours`
  - `GET /api/v1/recovery/analytics/failure-hours`
  - `POST /api/v1/recovery/analytics/friction-signals`
  - `GET /api/v1/recovery/analytics/friction-signals`
  - `GET /api/v1/recovery/analytics/friction-segments`
- Ops
  - `GET /api/v1/ops/overview/recovery-loop`
  - `GET /api/v1/ops/overview/batch`
  - `GET /api/v1/ops/alerts`
  - `GET /api/v1/ops/runbooks`

`/api/v1/ops/alerts`는 `activeOnly=true|false`로 현재 활성 incident만 볼지, resolved 이력까지 같이 볼지를 고를 수 있습니다. 응답에는 `status`, `firstSeenAt`, `lastSeenAt`, `resolvedAt`, `occurrenceCount`, `reopenCount`가 포함되어 alert lifecycle을 API만으로도 읽을 수 있습니다.

## Realtime Ops Alert Webhook

alert lifecycle event는 `OPENED`, `REOPENED`, `ESCALATED`, `RESOLVED` 네 종류로만 외부에 전달됩니다. 반복 refresh는 외부 알림을 보내지 않습니다.

설정 예시:

```yaml
ops:
  notifications:
    webhook:
      enabled: true
      url: http://127.0.0.1:18081/hooks
      connect-timeout-ms: 1000
      read-timeout-ms: 1000
      headers:
        X-Ops-Token: local-phase4
```

sample payload:

```json
{
  "eventType": "ESCALATED",
  "service": "rebootfocus-api",
  "emittedAt": "2026-05-08T10:00:00+09:00",
  "previousStatus": "ACTIVE",
  "previousSeverity": "WARNING",
  "alert": {
    "alertKey": "processing_lag:daily_kpi_pipeline:demo-user",
    "pipelineKey": "daily_kpi_pipeline",
    "stage": "processing_lag",
    "userId": "demo-user",
    "severity": "CRITICAL",
    "active": true,
    "status": "ACTIVE",
    "summary": "Processing lag exceeded the configured threshold.",
    "details": {
      "lastProcessedDate": "2026-05-05",
      "lagDays": "3"
    },
    "firstSeenAt": "2026-05-08T09:50:00+09:00",
    "lastSeenAt": "2026-05-08T10:00:00+09:00",
    "resolvedAt": null,
    "occurrenceCount": 2,
    "reopenCount": 0,
    "lastChangedAt": "2026-05-08T10:00:00+09:00"
  }
}
```

로컬 검증은 간단한 HTTP 수신기를 띄운 뒤, alert를 발생시키고 수신 JSON을 확인하는 방식으로 할 수 있습니다.

## Tech Stack

- Java 21
- Spring Boot 3.3.8
- Spring Web, Validation, Data JPA, Batch, Actuator
- PostgreSQL, H2, JdbcTemplate
- Micrometer Prometheus, springdoc-openapi
- Gradle, Docker Compose, GitHub Actions

## Run

```bash
docker compose up -d postgres
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootRun
```

기본 로컬 프로필은 PostgreSQL을 사용합니다.

- `DB_HOST=localhost`
- `DB_PORT=5432`
- `DB_NAME=rebootfocus`
- `DB_USERNAME=rebootfocus`
- `DB_PASSWORD=rebootfocus`

## Docs And Ops Endpoints

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Alert webhook
  - `ops.notifications.webhook.enabled`
  - `ops.notifications.webhook.url`
  - `ops.notifications.webhook.connect-timeout-ms`
  - `ops.notifications.webhook.read-timeout-ms`
  - `ops.notifications.webhook.headers.*`
- OpenAPI JSON: `http://localhost:8080/api-docs`
- App Health: `http://localhost:8080/api/v1/health`
- Actuator Health: `http://localhost:8080/actuator/health`
- Prometheus: `http://localhost:8080/actuator/prometheus`

## Test

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon
```

- 기본 테스트 프로필은 H2 메모리 DB를 사용합니다.
- CI는 PostgreSQL service container로 analytics integration test를 추가 검증합니다.

## CI/CD

- CI: `main`, `feature/**` push와 `main` 대상 PR에서 테스트 실행
- CI: PostgreSQL-backed analytics integration test 별도 실행
- CD: `main` push 시 `bootJar`와 Docker image artifact 생성
- Release: `v*` 태그 push 시 jar와 Docker image tar를 릴리즈 자산으로 업로드
