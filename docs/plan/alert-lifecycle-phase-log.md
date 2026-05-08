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
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertService.java`
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertSeverity.java`
- 핵심 구조:
  - lifecycle state는 있었지만 전이 결과를 외부 소비자가 재사용할 contract는 없었다.
  - reopen/escalation/resolve가 모두 내부 상태 변경으로만 끝났다.
- 한계:
  - notifier나 overview가 붙더라도 어떤 전이를 이벤트로 볼지 서비스 바깥에서 다시 해석해야 했다.
  - severity 상승만 별도 incident 신호로 다루는 기준이 코드에 고정돼 있지 않았다.

### 대상

- 변경 대상 클래스/파일:
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertService.java`
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertSeverity.java`
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertTransitionType.java`
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertTransitionEvent.java`
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertTransitionPublisher.java`
  - `src/main/java/com/focuskeeper/reboot/common/observability/NoopOperationsAlertTransitionPublisher.java`
  - `src/test/java/com/focuskeeper/reboot/common/observability/OperationsAlertServiceTest.java`

### 변경 이유

- 왜 notifier/overview 이전에 event semantics를 고정해야 하는가:
  - 외부 소비자가 `opened`, `reopened`, `escalated`, `resolved`를 서비스 밖에서 다시 계산하면 의미가 흔들린다.
  - lifecycle engine이 전이 의미를 먼저 고정해야 notifier와 UI가 같은 규칙을 그대로 재사용할 수 있다.

### 변경 내용

- `OperationsAlertTransitionType`를 `OPENED`, `REOPENED`, `ESCALATED`, `RESOLVED`로 도입했다.
- `OperationsAlertTransitionEvent`를 추가해 `eventType`, `emittedAt`, `previousStatus`, `previousSeverity`, `alert snapshot`을 고정했다.
- `OperationsAlertTransitionPublisher` 인터페이스와 phase 3용 no-op 구현을 추가했다.
- `OperationsAlertService`는 상태 전이 시 event를 생성하고 publisher에 전달한다.
- active 상태에서 severity가 `WARNING -> CRITICAL`로 올라갈 때만 `ESCALATED`를 발행하고, severity 하향이나 동일 severity refresh는 event를 만들지 않는다.

### 동작 변경 요약

- 이전:
  - alert state는 바뀌었지만, 어떤 전이가 운영 이벤트인지 외부에서 알 방법이 없었다.
- 이후:
  - lifecycle engine이 event semantics를 같이 생산한다.
  - no-op transition은 event가 없고, severity 상승만 `ESCALATED`로 구분된다.

### 테스트 결과

- 실행 테스트:
  - `gradle test --tests com.focuskeeper.reboot.common.observability.OperationsAlertServiceTest`
  - `gradle test --tests com.focuskeeper.reboot.common.observability.OperationsControllerIntegrationTest`
- 검증 시나리오:
  - `new resolved -> no event`
  - repeated active refresh -> `OPENED`만 발행
  - `active -> resolved -> reopened` -> `OPENED`, `RESOLVED`, `REOPENED`
  - warning -> critical severity 상승 -> `ESCALATED`
- 결과:
  - 둘 다 성공
  - controller integration도 기존대로 유지됨

### 커밋

- `feat : alert transition event contract 도입`
- `test : alert transition event semantics 테스트 추가`

---

## Phase 4. Consumer Integration

### 수정 전 코드 스냅샷

- 대상 파일:
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertTransitionPublisher.java`
  - `src/main/resources/static/index.html`
  - `src/main/resources/static/app.js`
  - `README.md`
  - `api/openapi.yaml`
- 핵심 구조:
  - event contract는 있었지만 실제 consumer가 없어서 webhook, UI, 문서 surface가 lifecycle semantics를 소비하지 못했다.
  - static ops 화면은 active alert raw JSON만 보여줬고, active/resolved toggle이나 lifecycle metadata 노출이 없었다.
- 한계:
  - 외부 알림 경로가 없어 alert transition이 운영자에게 푸시되지 않았다.
  - OpenAPI/README/runbook도 lifecycle metadata와 webhook 설정을 설명하지 못했다.

### 대상

- 변경 대상 클래스/파일:
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertNotifier.java`
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertNotifierPublisher.java`
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsAlertWebhookProperties.java`
  - `src/main/java/com/focuskeeper/reboot/common/observability/WebhookOperationsAlertNotifier.java`
  - `src/main/java/com/focuskeeper/reboot/common/observability/OperationsMetricRecorder.java`
  - `src/main/resources/application.yml`
  - `src/main/resources/static/index.html`
  - `src/main/resources/static/app.js`
  - `README.md`
  - `docs/spec/PHASE_14_OPERATIONS_RUNBOOK.md`
  - `api/openapi.yaml`
  - `src/test/java/com/focuskeeper/reboot/common/observability/WebhookOperationsAlertNotifierTest.java`

### 변경 이유

- 왜 이 consumer가 필요한가:
  - alert transition이 메모리 안에서만 끝나면 운영 알림 시스템으로 설명하기 어렵다.
  - UI와 문서도 같은 lifecycle semantics를 써야 운영 surface가 일관된다.
- lifecycle contract를 어떻게 재사용하는가:
  - webhook notifier는 `OperationsAlertTransitionEvent`를 그대로 payload로 전송한다.
  - static ops 화면은 alert API의 lifecycle metadata를 그대로 읽어 active/resolved, occurrence, reopen 정보를 노출한다.

### 변경 내용

- `OperationsAlertNotifier`와 `WebhookOperationsAlertNotifier`를 추가했다.
- `ops.notifications.webhook.*` 설정을 도입하고, enabled/url/timeout/headers를 바인딩한다.
- `OperationsAlertNotifierPublisher`가 transition event를 notifier 목록에 전달한다.
- notifier 성공/실패를 `reboot_ops_alert_notifications_total{event,result}`로 계측한다.
- static ops 화면에 active/all alert toggle과 lifecycle card surface를 추가했다.
- README, runbook, manual OpenAPI에 alert lifecycle metadata와 webhook surface를 반영했다.

### 동작 변경 요약

- 이전:
  - alert transition은 서비스 내부에서만 소비됐고, 외부 알림과 운영 화면은 raw state 수준에 머물렀다.
- 이후:
  - transition event가 webhook notifier로 전달될 수 있다.
  - 운영 화면에서 active/resolved, first seen, resolvedAt, occurrence/reopen count를 함께 읽을 수 있다.
  - 문서와 OpenAPI도 같은 contract를 설명한다.

### 테스트 결과

- 실행 테스트:
  - `gradle test --tests com.focuskeeper.reboot.common.observability.WebhookOperationsAlertNotifierTest --tests com.focuskeeper.reboot.common.observability.OperationsAlertServiceTest --tests com.focuskeeper.reboot.common.observability.OperationsControllerIntegrationTest`
- 검증 시나리오:
  - enabled webhook success payload + success metric
  - webhook 500 failure + failure metric, no throw
  - disabled webhook no-op
  - 기존 lifecycle service test와 controller integration 유지
- 결과:
  - 성공
  - notifier, service, controller observability 경로 모두 통과
  - static UI는 별도 자동화 테스트 없이 코드 반영만 수행

### 커밋

- `feat : webhook notifier와 ops overview 연동`
- `test : webhook notifier와 ops overview 연동 테스트 추가`
