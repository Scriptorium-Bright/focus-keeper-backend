# Alert Lifecycle Phase Log

## Usage

- 이 문서는 Phase 1부터 Phase 4까지의 변경 evidence를 한 파일에 누적하는 로그다.
- 각 phase가 끝날 때마다 아래 섹션을 채운다.
- phase 사이에 문서를 쪼개지 않는다.

## Phase 1. State Transition Model

### 수정 전 코드 스냅샷

- 대상 파일: `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertService.java`
- 핵심 구조:
  - `ConcurrentMap<String, OperationsAlertResponse>`에 API DTO를 그대로 저장했다.
  - `upsert(...)`가 `active=true/false`만 받아 DTO를 덮어썼다.
  - `new resolved`도 그대로 저장되어 phantom resolved alert가 생길 수 있었다.
- 한계:
  - 내부 incident 상태와 외부 응답 DTO가 분리되지 않았다.
  - 동일 alertKey 재발과 resolve/reopen을 같은 identity로 설명하기 어려웠다.
  - 다음 phase의 lifecycle metadata와 event contract를 올릴 내부 상태 모델이 없었다.

### 대상

- 변경 대상 클래스/파일:
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertService.java`
  - `src/test/java/com/focuskeeper/reboot/common/observability/OperationsAlertServiceTest.java`

### 변경 이유

- 왜 이 phase가 먼저 필요한가:
  - notifier, overview, metadata surface보다 먼저 alert identity와 상태 전이 의미를 고정해야 이후 phase가 흔들리지 않는다.
- 기존 동작에서 무엇이 문제였는가:
  - resolve 호출만으로도 resolved alert가 새로 생길 수 있었다.
  - repeated active와 reopened active가 같은 incident의 refresh인지 새 incident인지 코드 구조만으로는 드러나지 않았다.

### 변경 내용

- `OperationsAlertService`의 내부 저장 모델을 `OperationsAlertResponse`에서 `OperationsAlertState`로 분리했다.
- 내부 상태에 `status=ACTIVE|RESOLVED`, `firstSeenAt`, `lastSeenAt`, `resolvedAt`, `occurrenceCount`, `reopenCount`, `lastChangedAt` 필드를 도입했다.
- 전이 규칙을 코드로 고정했다.
  - `new active -> opened`
  - `active -> active -> refresh`
  - `active -> resolved -> resolve`
  - `resolved -> active -> reopen`
  - `new resolved -> no-op`
- `getAlerts(...)`는 내부 상태를 기존 `OperationsAlertResponse`로 변환해 외부 API 스키마는 유지했다.

### 동작 변경 요약

- 이전:
  - API DTO를 그대로 저장했고, resolve-only 호출도 resolved alert를 만들었다.
- 이후:
  - 내부 incident state를 기준으로만 상태를 바꾸고, prior active가 없는 resolve는 무시한다.
  - reopen은 같은 alertKey identity를 재사용한다.

### 테스트 결과

- 실행 테스트:
  - `gradle test --tests com.focuskeeper.reboot.common.observability.OperationsAlertServiceTest`
  - `gradle test --tests com.focuskeeper.reboot.common.observability.OperationsControllerIntegrationTest`
- 검증 시나리오:
  - `new resolved -> no-op`
  - repeated active refresh 시 alert record count 유지
  - `active -> resolved -> reopened`에서 같은 alertKey 재사용
  - 기존 ops controller integration 동작 유지
- 결과:
  - 둘 다 성공
  - 서비스 단위 테스트와 기존 integration 테스트 모두 통과

### 커밋

- `feat : alert lifecycle 상태 전이 모델 도입`
- `test : alert lifecycle 상태 전이 테스트 추가`

---

## Phase 2. Lifecycle Metadata Surface

### 수정 전 코드 스냅샷

- 대상 파일:
  - `src/main/java/com/focuskeeper/reboot/common/observability/dto/OperationsAlertResponse.java`
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertService.java`
- 핵심 구조:
  - 외부 응답은 `alertKey, pipelineKey, stage, userId, severity, active, summary, details, lastChangedAt`만 노출했다.
  - `getAlerts(activeOnly=false)`도 resolved lifecycle metadata 없이 단순 최신순 정렬만 했다.
- 한계:
  - 운영자가 API 응답만 보고 active/resolved 이력, 최초 발생 시각, 반복 여부를 알 수 없었다.
  - resolved alert를 포함해 조회해도 active incident와 historical incident를 같은 기준으로 읽기 어려웠다.

### 대상

- 변경 대상 클래스/파일:
  - `src/main/java/com/focuskeeper/reboot/common/observability/dto/OperationsAlertResponse.java`
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertService.java`
  - `src/test/java/com/focuskeeper/reboot/common/observability/OperationsControllerIntegrationTest.java`

### 변경 이유

- 왜 이 phase가 필요한가:
  - Phase 1에서 고정한 incident state를 외부 API에서도 읽을 수 있어야 notifier, overview, UI가 같은 의미를 재사용할 수 있다.
- 운영자가 기존 응답으로 무엇을 판단할 수 없었는가:
  - 언제 처음 발생했는지
  - 현재 살아 있는지 resolved인지
  - 몇 번 반복됐는지

### 변경 내용

- `OperationsAlertResponse`에 `status`, `firstSeenAt`, `lastSeenAt`, `resolvedAt`, `occurrenceCount`, `reopenCount`를 추가했다.
- `active`, `lastChangedAt`는 그대로 유지해 하위 호환을 남겼다.
- `OperationsAlertService.getAlerts(...)` 정렬을 `ACTIVE first -> lastChangedAt desc`로 바꿨다.
- integration test에 `activeOnly=false` 조회, lifecycle field serialization, active-first ordering 검증을 추가했다.

### 동작 변경 요약

- 이전:
  - alert API는 현재 active 여부와 마지막 변경 시각만 노출했다.
  - resolved alert를 포함해도 lifecycle 의미가 거의 드러나지 않았다.
- 이후:
  - alert API가 incident lifecycle metadata를 응답으로 노출한다.
  - `activeOnly=false`에서 active alert가 먼저, resolved alert가 뒤에 정렬된다.

### 테스트 결과

- 실행 테스트:
  - `gradle test --tests com.focuskeeper.reboot.common.observability.OperationsAlertServiceTest`
  - `gradle test --tests com.focuskeeper.reboot.common.observability.OperationsControllerIntegrationTest`
- 검증 시나리오:
  - active alert 응답에 lifecycle metadata 포함
  - resolved alert 응답 포함 여부
  - `activeOnly=false` 정렬에서 active-first 보장
  - 기존 ops controller flow 유지
- 결과:
  - 둘 다 성공
  - lifecycle metadata 추가 후 기존 integration도 깨지지 않음

### 커밋

- `feat : ops alerts lifecycle 메타데이터 응답 확장`
- `test : ops alerts lifecycle 응답 테스트 추가`

---

## Phase 3. Transition Event Contract

### 수정 전 코드 스냅샷

- 대상 파일:
- 핵심 구조:
- 한계:

### 대상

- 변경 대상 클래스/파일:

### 변경 이유

- 왜 notifier/overview 이전에 event semantics를 고정해야 하는가:

### 변경 내용

- 

### 동작 변경 요약

- 이전:
- 이후:

### 테스트 결과

- 실행 테스트:
- 검증 시나리오:
- 결과:

### 커밋

- `feat : `
- `test : `

---

## Phase 4. Consumer Integration

### 수정 전 코드 스냅샷

- 대상 파일:
- 핵심 구조:
- 한계:

### 대상

- 변경 대상 클래스/파일:

### 변경 이유

- 왜 이 consumer가 필요한가:
- lifecycle contract를 어떻게 재사용하는가:

### 변경 내용

- 

### 동작 변경 요약

- 이전:
- 이후:

### 테스트 결과

- 실행 테스트:
- 검증 시나리오:
- 결과:

### 커밋

- `feat : `
- `test : `
