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
- 핵심 구조:
- 한계:

### 대상

- 변경 대상 클래스/파일:

### 변경 이유

- 왜 이 phase가 필요한가:
- 운영자가 기존 응답으로 무엇을 판단할 수 없었는가:

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
