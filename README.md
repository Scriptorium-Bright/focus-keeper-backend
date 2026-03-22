# RebootFocus Reboot

Reboot baseline after archiving pre-reset history to `archive/pre-reboot-2026-02-28`.

- One-liner: 하루가 한 번 무너지면 다음날까지 끌려가는 직장인을 위해, 실패 직후 10분 재시작과 24시간 내 복귀를 설계한 복귀 코치

- Master plan: [docs/newPlan.md](./docs/newPlan.md)
- Design docs index: [docs/README.md](./docs/README.md)

## Current Status

- Phase 4 core implemented
  - Brain Dump
  - Big3
  - 첫 복귀 블록 Timebox
  - 복귀 세션 시작/완료/중단
  - 실패 체크인
- 공통 응답/예외/트레이스 표준 적용
- Swagger/OpenAPI 문서 노출
- Spring Data JPA + PostgreSQL 기반 영속 저장소 적용
- Health endpoints:
  - `GET /api/v1/health`
  - `GET /actuator/health`

## Current Gap

- `F-006` 10분 복귀 재시작 API는 아직 미구현

## Run

```bash
docker compose up -d postgres
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootRun
```

기본 로컬 프로필은 PostgreSQL을 사용합니다.
- 기본 접속 정보: `rebootfocus / rebootfocus / rebootfocus`
- 환경변수로 변경 가능: `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`

## API Docs

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/api-docs`

## Test

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
```

테스트는 별도 `test` 프로필에서 H2 메모리 DB로 실행됩니다.

## CI/CD

- CI: `main`, `feature/**` push 및 `main` 대상 PR에서 테스트 실행
- CD: `main` push 시 `bootJar` 빌드 아티팩트 생성, `v*` 태그 push 시 릴리즈 자산 업로드
