# Refactor Log

> 목적: Phase 진행 중 테스트/커밋 이력과 Phase 종료 시점의 개선 과제를 관리한다.

## 운영 규칙

1. 각 `N.x` 작업이 끝날 때 테스트를 수행한다.
2. 각 `N.x` 작업이 끝날 때 커밋한다. (1 작업 = 1 논리 커밋)
3. `refactor.md`의 High/Mid/Low 정리는 `N` Phase 종료 시점에만 업데이트한다.
4. 커밋 메시지는 컨벤션(`feat`, `fix`, `refactor`, `docs`, `chore`)을 유지한다.
5. 각 Phase 종료 시에는 `docs/PHASE_EXIT_PROTOCOL.md` 기준으로 한 번 멈춰서 High/Mid/Low와 포트폴리오 사례를 정리한다.
6. 블로그로 남길 가치가 있는 의사결정이 있으면 각 Phase 종료 시점에 함께 기록한다.

## 심각도 기준

- `High`: 다음 Phase 시작 전 반드시 수정해야 하는 항목 (정합성/보안/장애 위험)
- `Mid`: 다음 1~2개 Phase 내 해결 권장 항목 (성능/구조 개선)
- `Low`: 기능 개발을 막지 않는 품질 개선 항목 (가독성/중복/네이밍)

## N.x 테스트/커밋 로그

| Date | Phase | N.x | Branch | Test Command | Result | Commit |
|---|---|---|---|---|---|---|
| 2026-03-03 | 1 | 1.0 | `feature/project-bootstrap` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `4c33390` |
| 2026-03-03 | 2 | 2.0 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `b630faf` |
| 2026-03-12 | 4 | 4.1 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `(local)` |
| 2026-03-12 | 4 | 4.2 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `(local)` |
| 2026-03-16 | 4 | 4.3 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `39c462b` |
| 2026-03-16 | 4 | 4.4 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `4ba4d74` |
| 2026-03-16 | 4 | 4.5 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `8e5da4c` |
| 2026-03-19 | 4 | 4.6 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `67a9e80` |
| 2026-03-19 | 5 | 5.0 | `feature/core-conventions` | `JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew test --no-daemon` | PASS | `b82f5ce` |

## Phase 종료 리팩토링 정리

작성 규칙:
- Phase 종료 시 High / Mid / Low를 반드시 채운다.
- 각 항목은 "무엇을 바꿔야 하는가"보다 "왜 다음 Phase에 영향을 주는가"가 먼저 보이게 적는다.
- 각 Phase별로 포트폴리오 적용 가능 항목도 함께 남긴다.
- 각 Phase별로 블로그로 확장 가능한 의사결정 주제도 함께 남긴다.

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

#### Portfolio Candidates

- 공통 응답/예외/트레이스 표준화는 "기능보다 API 일관성을 먼저 고정한 이유" 사례로 설명 가능
- OpenAPI 동기화는 문서-코드 계약 관리 사례로 확장 가능

#### Blog Candidates

- 왜 기능 구현보다 공통 응답/예외/트레이스를 먼저 고정했는가
- OpenAPI를 코드와 같이 관리해야 하는 이유

---

### Phase 4 종료

#### High

- [x] 실패 체크인 이후 `10분 복귀 재시작`이 아직 없어 복귀 루프가 닫히지 않았다.

#### Mid

- [ ] 세션/실패 이벤트를 KPI 입력 스키마와 직접 연결하는 테스트가 아직 없다.

#### Low

- [ ] execution 패키지의 상태 전이 규칙을 service 단위 테스트로도 보강할 수 있다.
- [ ] 로컬 H2 기준 저장 모델을 향후 PostgreSQL 운영 스키마와 비교 검증해야 한다.

#### Portfolio Candidates

- `첫 복귀 블록 충돌 정책`은 계획을 실행으로 이어지게 만든 제약 설계 사례로 설명 가능
- `복귀 세션 상태 전이`는 도메인 상태 모델링 사례로 설명 가능
- `실패 체크인 reason taxonomy`는 행동 이벤트 구조화 사례로 설명 가능

#### Blog Candidates

- 복귀 앱의 핵심은 타이머가 아니라 상태 전이였다
- 왜 첫 복귀 블록을 강제하는가
- 실패 사유를 구조화 이벤트로 저장해야 하는 이유

#### Problem -> Solution -> Result Draft

- 문제:
  - 전날 실패한 사용자는 다음날 첫 복귀 블록이 없거나 세션 상태가 불명확하면 다시 시작할 계기를 잃는다.
- 해결:
  - Big3 기반 첫 복귀 블록을 고정하고, 세션과 실패를 명시적 상태 전이/구조화 이벤트로 모델링한 뒤 JPA 영속 저장소로 바꿨다.
- 결과:
  - 타임박스, 세션, 실패 체크인 API와 통합 테스트를 통해 핵심 복귀 루프를 영속 저장소 기준으로 검증했다.

---

### 템플릿 (다음 Phase용)

#### Phase N 종료

##### High

- [ ] 항목

##### Mid

- [ ] 항목

##### Low

- [ ] 항목

##### Blog Candidates

- [ ] 항목

---

## 포트폴리오 사례 템플릿

각 Phase 종료 시 최소 1개 이상 작성한다.

### Case N

- 문제:
- 해결:
- 결과:
- 사용한 증거:
  - 테스트:
  - KPI/로그:
  - 다이어그램:

## Phase 종료 템플릿

```text
### Phase N 종료

#### High
- [ ] ...

#### Mid
- [ ] ...

#### Low
- [ ] ...

#### Portfolio Candidates
- ...

#### Problem -> Solution -> Result Draft
- 문제:
- 해결:
- 결과:
```
