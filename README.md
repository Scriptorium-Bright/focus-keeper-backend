# FocusKeeper Reboot

Reboot baseline after archiving pre-reset history to `archive/pre-reboot-2026-02-28`.

- Master plan: [docs/newPlan.md](./docs/newPlan.md)
- Design docs index: [docs/README.md](./docs/README.md)

## Phase 1~2 Status

- Spring Boot + Gradle bootstrap
- Environment profiles: `local`, `test`
- 공통 응답/예외/트레이스 표준 적용
- Health endpoints:
  - `GET /api/v1/health`
  - `GET /actuator/health`

## Run

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew bootRun
```

## Test

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test
```

## CI/CD

- CI: `main`, `feature/**` push 및 `main` 대상 PR에서 테스트 실행
- CD: `main` push 시 `bootJar` 빌드 아티팩트 생성, `v*` 태그 push 시 릴리즈 자산 업로드
