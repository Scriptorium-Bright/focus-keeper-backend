# Refactor Log

> 목적: Phase 진행 중 테스트/커밋 이력과 Phase 종료 시점의 개선 과제를 관리한다.

## 운영 규칙

1. 각 `N.x` 작업이 끝날 때 테스트를 수행한다.
2. 각 `N.x` 작업이 끝날 때 커밋한다. (1 작업 = 1 논리 커밋)
3. `refactor.md`의 High/Mid/Low 정리는 `N` Phase 종료 시점에만 업데이트한다.
4. 커밋 메시지는 컨벤션(`feat`, `fix`, `refactor`, `docs`, `chore`)을 유지한다.

## 심각도 기준

- `High`: 다음 Phase 시작 전 반드시 수정해야 하는 항목 (정합성/보안/장애 위험)
- `Mid`: 다음 1~2개 Phase 내 해결 권장 항목 (성능/구조 개선)
- `Low`: 기능 개발을 막지 않는 품질 개선 항목 (가독성/중복/네이밍)

## N.x 테스트/커밋 로그

| Date | Phase | N.x | Branch | Test Command | Result | Commit |
|---|---|---|---|---|---|---|
| 2026-03-03 | 1 | 1.0 | `feature/project-bootstrap` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `4c33390` |
| 2026-03-03 | 2 | 2.0 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `b630faf` |

## Phase 종료 리팩토링 정리

### Phase 1 종료

#### High

- 없음

#### Mid

- 없음

#### Low

- Health API 응답 구조를 Phase 2 공통 Envelope로 통일 필요

---

### Phase 2 종료

#### High

- 없음

#### Mid

- `api/openapi.yaml` 스키마를 공통 Envelope(`ApiResponse`/`ErrorResponse`) 기준으로 동기화 필요

#### Low

- 없음

---

### 템플릿 (다음 Phase용)

#### Phase N 종료

##### High

- [ ] 항목

##### Mid

- [ ] 항목

##### Low

- [ ] 항목
