# FocusKeeper Reboot

실패 직후 10분 재시작과 24/48시간 복귀 여부를 기록하고 분석하는 Spring Boot 백엔드 포트폴리오입니다.

이 저장소는 단순 CRUD보다 다음 문제를 백엔드 관점에서 풀어내는 데 집중합니다.

- 상태 전이가 있는 복귀 세션 도메인 설계
- 일정 충돌과 배분 규칙이 있는 planning validation
- raw event를 KPI mart로 집계하는 배치 파이프라인
- 데이터 품질 리포트와 `lastProcessedDate` 기반 재처리 관리
- 운영 대시보드, 알림, 런북 API를 포함한 observability

## Portfolio Summary

- Planning: Inbox, Big3, Timebox 생성 API와 시간 겹침/배분 검증
- Execution: recovery session 시작, 완료, 중단과 failure check-in, restart 기록
- Retrospective: 주간 회고 집계와 anti-slip 액션 추천
- Analytics: 일간 KPI 생성/조회/백필, friction signal/segment, failure hour 분석
- Ops: recovery loop overview, batch overview, alert, runbook 조회
- Platform: 공통 응답 포맷, 전역 예외 처리, traceId 로깅, Swagger/OpenAPI, Prometheus, Health endpoint

## Backend Highlights

- Package-by-domain 구조로 `planning`, `execution`, `analytics`, `friction`, `retrospective` 책임을 분리했습니다.
- Recovery session, failure event, restart event를 분리 저장해 복귀 lifecycle을 추적 가능한 데이터 모델로 만들었습니다.
- 일간 KPI 파이프라인은 raw event를 읽어 mart 저장, quality report 생성, `lastProcessedDate` 갱신까지 한 흐름으로 묶었습니다.
- PostgreSQL에서는 `ON CONFLICT` upsert를 사용해 자연키(`user_id`, `metric_date`) 기준 idempotent 저장을 처리합니다.
- 테스트는 기본 H2 메모리 DB로 빠르게 돌리고, CI에서는 PostgreSQL 기반 analytics integration test를 한 번 더 검증합니다.
- 운영성 강화를 위해 `Actuator`, `Prometheus`, `/api/v1/ops/**` API, Docker image artifact 생성까지 포함했습니다.

## Tech Stack

- Java 21
- Spring Boot 3.3.8
- Spring Web, Validation, Data JPA, Batch, Actuator
- PostgreSQL, H2, JdbcTemplate
- Micrometer Prometheus, springdoc-openapi
- Gradle, Docker Compose, GitHub Actions

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
- Friction
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

## Run

```bash
docker compose up -d postgres
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootRun
```

기본 로컬 프로필은 PostgreSQL을 사용합니다.

- host: `localhost:5432`
- database: `rebootfocus`
- username: `rebootfocus`
- password: `rebootfocus`
- override: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`

## Docs And Ops Endpoints

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`
- App Health: `http://localhost:8080/api/v1/health`
- Actuator Health: `http://localhost:8080/actuator/health`
- Prometheus: `http://localhost:8080/actuator/prometheus`

## Test

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon
```

- 기본 테스트 프로필은 H2 메모리 DB를 사용합니다.
- CI는 기본 테스트 외에 PostgreSQL service container로 analytics integration test를 추가 검증합니다.

## CI/CD

- CI: `main`, `feature/**` push와 `main` 대상 PR에서 테스트 실행
- CI: PostgreSQL-backed analytics integration test 별도 실행
- CD: `main` push 시 `bootJar`와 Docker image artifact 생성
- Release: `v*` 태그 push 시 jar와 Docker image tar를 릴리즈 자산으로 업로드
