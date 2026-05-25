# Execution Grain Spec

> Version: v0.1  
> Updated: 2026-05-25  
> Scope: Big3 하위 실행 단위, timebox, session, 완료 상태 경계

## 1. 목적

`ExecutionUnit` 도입 이후 계획/실행 grain이 섞이지 않도록 도메인 계층과 상태 의미를 고정한다.

## 2. 확정 구조

```text
Big3Selection
-> Big3SelectionItem
-> ExecutionUnit
-> Timebox
-> RecoverySession
```

각 계층의 의미는 아래처럼 분리한다.

| 계층 | 의미 | 직접 상태 |
|---|---|---|
| `Big3Selection` | 하루 Big3 선택 헤더 | 선택 날짜/시각 |
| `Big3SelectionItem` | Big3 안의 상위 과업 | 하위 unit roll-up |
| `ExecutionUnit` | timebox에 배정 가능한 실제 실행 단위 | unit 완료 여부 |
| `Timebox` | 특정 시간에 실행하기로 한 계획 블록 | 일정/시각/첫 복귀 블록 |
| `RecoverySession` | timebox를 실제 수행한 시도 기록 | started/completed/interrupted |

## 3. 소유권과 FK

- `ExecutionUnit`의 소유자는 `Big3SelectionItem`이다.
- `Timebox`는 `ExecutionUnit`을 참조한다.
- `RecoverySession`은 기존처럼 `Timebox`를 참조한다.

필수 FK:

```text
execution_units.big3_selection_item_id -> big3_selection_items.id
recovery_timeboxes.execution_unit_id -> execution_units.id
recovery_sessions.timebox_id -> recovery_timeboxes.id
```

권한 검증은 `Big3Selection.userId`를 통해 최종 사용자 소유 여부를 확인한다. 조회 성능이 필요하면 `ExecutionUnit.userId`는 보조 컬럼으로 둘 수 있지만, 정합성 기준 소유자는 `Big3SelectionItem`이다.

## 4. 완료 정책

### 4.1 Session Complete

`RecoverySession.COMPLETED`는 특정 timebox에 대한 한 번의 실행 시도가 정상 종료됐다는 뜻이다.

세션 완료는 아래 상태를 자동으로 의미하지 않는다.

- timebox 완료
- execution unit 완료
- Big3 item 완료

### 4.2 ExecutionUnit Complete

`ExecutionUnit` 완료는 사용자가 실제 세부 작업을 끝냈다고 명시했을 때만 전이한다.

초기 상태:

```text
PLANNED
```

완료 상태:

```text
COMPLETED
```

완료 시각은 `completedAt`에 저장한다. 완료 취소/재오픈은 후속 기능으로 둔다.

### 4.3 Big3SelectionItem Roll-Up

`Big3SelectionItem` 완료는 직접 명령으로 처리하지 않는다. 하위 `ExecutionUnit` 상태를 기준으로 계산한다.

초기 roll-up 규칙:

```text
NOT_STARTED = execution unit이 없음
IN_PROGRESS = unit이 1개 이상 있고, 완료되지 않은 unit이 있음
COMPLETED = unit이 1개 이상 있고, 모든 unit이 COMPLETED
```

## 5. API 계약 방향

- Big3 응답은 `big3SelectionItemId`, `itemId`, `content`를 함께 내려준다.
- `ExecutionUnit` 생성/수정 API는 `big3SelectionItemId`를 기준으로 동작한다.
- Timebox 생성 요청은 `itemId`가 아니라 `executionUnitId`를 받는다.
- Timebox 응답도 `executionUnitId`를 노출한다.

## 6. 해석 금지

아래 해석은 금지한다.

```text
session completed
=> execution unit completed
=> Big3 item completed
```

올바른 해석은 아래다.

```text
session completed = 실행 시도 완료
execution unit completed = 세부 작업 완료
Big3 item completed = 하위 unit roll-up 완료
```
