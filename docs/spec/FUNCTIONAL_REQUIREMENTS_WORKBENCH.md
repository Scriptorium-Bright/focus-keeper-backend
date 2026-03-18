# Functional Requirements Workbench

> Version: v0.1  
> Updated: 2026-03-18  
> Scope: rough 아이디어를 작업용 기능 요구사항 형식으로 정리하는 문서

## 1. 목적

- 이 문서는 대화 중 나온 rough 아이디어를 `FR-001 형식`으로 정리하는 작업대다.
- 아직 확정되지 않은 요구사항도 담되, `open question`을 함께 적는다.
- 확정된 내용은 `docs/spec/ENGINEERING_SPEC.md`, `docs/spec/FEATURE_PROCESS_SPEC.md`, `docs/spec/DATA_MODEL.md`로 승격한다.

## 2. 번호 규칙

- 이 문서는 `WFR-001` 형식을 사용한다.
- 이유:
  - `docs/spec/ENGINEERING_SPEC.md`의 정식 `FR-001`과 충돌하지 않기 위해
  - 아직 확정 전인 작업용 요구사항임을 표시하기 위해
- 승격 시 정식 `FR-xxx` 또는 기능 문서 항목으로 재배치한다.

## 3. 작성 템플릿

### WFR-XXX 제목

- Requirement:
- Why:
- Input:
- Output:
- Rules:
- Open Questions:
- Promotion Target:

## 4. Current Draft

### WFR-001 사용자는 할 일을 등록할 수 있어야 한다

- Requirement:
  - 시스템은 사용자가 할 일 제목과 선택적 메모를 등록할 수 있게 해야 한다.
- Why:
  - 복귀 제안은 항상 "어떤 일을 놓쳤는가"를 알아야 의미가 생긴다.
- Input:
  - `title`
  - `note` (optional)
- Output:
  - 생성된 task 식별자
- Rules:
  - task는 실행/복귀 루프의 출발점이다.
  - 등록 자체가 목적이 아니라 이후 실행/이탈/복귀와 연결되어야 한다.
- Open Questions:
  - due date를 초기부터 둘지 여부
- Promotion Target:
  - `docs/spec/ENGINEERING_SPEC.md`
  - `docs/spec/FEATURE_PROCESS_SPEC.md`

### WFR-002 시스템은 task를 상태 기반으로 저장해야 한다

- Requirement:
  - 시스템은 task를 `active`, `completed`, `archived` 상태로 저장하고 전환할 수 있어야 한다.
- Why:
  - "영구 저장"보다 현재/완료/보관 상태 관리가 실제 운영에 더 적합하다.
- Input:
  - task 상태 전환 요청
- Output:
  - 업데이트된 task 상태
- Rules:
  - `archived`는 삭제보다 가벼운 보관 개념이다.
  - task 삭제보다 보관/복구가 우선이다.
- Open Questions:
  - hard delete를 허용할지 여부
- Promotion Target:
  - `docs/spec/DATA_MODEL.md`

### WFR-002A 시스템은 프로덕션에서 영속 DB를 사용해야 한다

- Requirement:
  - 시스템은 프로덕션 환경에서 task, session, failure, restart 데이터를 영속 DB에 저장해야 한다.
- Why:
  - 복귀 기록과 실패 패턴 분석은 앱 재시작 이후에도 남아 있어야 의미가 있기 때문이다.
- Input:
  - task / session / failure / restart 이벤트
- Output:
  - durable persistence
- Rules:
  - In-Memory DB는 테스트, 로컬 개발, 초기 프로토타입 용도로만 사용한다.
  - 프로덕션 기본 저장소는 PostgreSQL 같은 영속 RDB를 전제로 한다.
- Open Questions:
  - read replica를 초기부터 둘지 여부
- Promotion Target:
  - `docs/spec/ENGINEERING_SPEC.md`
  - `docs/spec/DATA_MODEL.md`

### WFR-003 카테고리는 초기 필수 입력이 아니어야 한다

- Requirement:
  - 시스템은 초기 MVP에서 카테고리를 필수로 요구하지 않아야 한다.
- Why:
  - 입력 마찰을 줄이고 범용 to-do 앱처럼 보이는 위험을 낮추기 위해
- Input:
  - task 생성 요청
- Output:
  - 카테고리 없이도 생성 가능
- Rules:
  - 필요 시 `optional tag` 또는 `task_type`은 후순위로 검토한다.
- Open Questions:
  - `task_type`이 실패 패턴 분석에 실제로 도움이 되는지
- Promotion Target:
  - `docs/PRODUCT_VALUE_QA.md`
  - `docs/spec/DATA_MODEL.md`

### WFR-004 사용자는 task와 별도로 실행 세션을 시작할 수 있어야 한다

- Requirement:
  - 시스템은 사용자가 task 자체가 아니라 `첫 복귀 블록` 또는 `짧은 실행 세션`을 시작할 수 있게 해야 한다.
- Why:
  - "끝날 때까지 하는 타이머"보다 "다시 시작할 수 있는 작은 블록"이 제품 핵심 가치에 맞기 때문이다.
- Input:
  - task 식별자
  - planned block or session duration
- Output:
  - 세션 시작 기록
- Rules:
  - task와 session은 분리된 개념이다.
  - 세션은 10~25분 범위의 작은 블록으로 시작할 수 있어야 한다.
- Open Questions:
  - 기본 세션 길이를 고정할지, 사용자가 선택하게 할지
- Promotion Target:
  - `docs/spec/FEATURE_PROCESS_SPEC.md`
  - `docs/spec/DATA_MODEL.md`

### WFR-004A 하나의 task는 여러 session으로 나뉘어 진행될 수 있어야 한다

- Requirement:
  - 시스템은 하나의 task가 단일 타이머로 끝나는 구조가 아니라 여러 session/timebox에 걸쳐 진행될 수 있게 해야 한다.
- Why:
  - 실제 업무는 한 번에 끝나지 않는 경우가 많고, 제품 핵심도 `task 완료 강제`보다 `짧은 실행 블록 유지`에 있기 때문이다.
- Input:
  - task 식별자
  - session/timebox 생성 요청
- Output:
  - task에 연결된 다수의 session/timebox
- Rules:
  - task 완료와 session 종료는 분리된 상태여야 한다.
  - session은 task 전체가 아니라 현재 실행 블록을 보호하는 단위다.
- Open Questions:
  - task 진행률을 퍼센트로 보여줄지 여부
- Promotion Target:
  - `docs/spec/FEATURE_PROCESS_SPEC.md`
  - `docs/spec/KEY_FLOWS.md`

### WFR-005 시스템은 세션 시작/완료/중단을 기록해야 한다

- Requirement:
  - 시스템은 사용자의 세션 시작 시각, 완료 시각, 중단 시각을 기록해야 한다.
- Why:
  - 복귀 KPI와 실패 패턴 해석의 원천 데이터이기 때문이다.
- Input:
  - 세션 상태 변경 요청
- Output:
  - 상태 전이 결과
- Rules:
  - 상태는 최소 `started`, `interrupted`, `completed`를 지원한다.
- Open Questions:
  - `paused` 상태를 별도로 둘지 여부
- Promotion Target:
  - `docs/spec/FEATURE_PROCESS_SPEC.md`
  - `docs/spec/DATA_MODEL.md`

### WFR-005A 세션 종료 시 완료/계속/휴식/실패를 구분할 수 있어야 한다

- Requirement:
  - 시스템은 세션 종료 시 사용자가 `완료`, `계속하기`, `휴식`, `실패 체크인` 중 하나로 이동할 수 있게 해야 한다.
- Why:
  - 세션 타이머의 목적은 강제가 아니라 다음 상태 전이를 명확히 만드는 것이기 때문이다.
- Input:
  - 세션 종료 이벤트
- Output:
  - next action 선택 결과
- Rules:
  - 사용자가 예정 시간보다 빨리 끝내는 것은 허용한다.
  - 단, 조기 종료 시 그것이 task 완료인지 session 종료인지 구분해야 한다.
- Open Questions:
  - 조기 종료 시 추가 확인 문구를 둘지 여부
- Promotion Target:
  - `docs/spec/KEY_FLOWS.md`
  - `docs/CONSUMER_MESSAGING.md`

### WFR-006 시스템은 이탈을 다층 신호로 판단해야 한다

- Requirement:
  - 시스템은 이탈을 단일 신호가 아니라 `강한 신호`와 `약한 신호`로 나눠 기록해야 한다.
- Why:
  - 앱 백그라운드 전환만으로 실패를 단정하면 오탐이 많기 때문이다.
- Input:
  - failure check-in
  - missed planned block
  - session interrupt
  - app background / inactivity
- Output:
  - 이탈 이벤트 또는 약한 이탈 신호
- Rules:
  - 강한 신호:
    - 명시적 실패 체크인
    - 계획된 블록 미시작
    - 세션 중단 처리
  - 약한 신호:
    - 앱 이탈
    - 무반응
- Open Questions:
  - 무반응 임계 시간을 몇 분으로 둘지
- Promotion Target:
  - `docs/spec/KEY_FLOWS.md`
  - `docs/spec/RECOVERY_METRICS.md`

### WFR-007 시스템은 실패 이유를 기록해야 한다

- Requirement:
  - 시스템은 사용자가 실패 시 이유를 체크인할 수 있어야 한다.
- Why:
  - 맥락 있는 복귀 제안과 패턴 학습의 핵심 입력값이기 때문이다.
- Input:
  - failure reason
- Output:
  - failure event 저장
- Rules:
  - failure reason은 표준화된 enum으로 시작한다.
- Open Questions:
  - free text 메모를 함께 받을지 여부
- Promotion Target:
  - `docs/spec/DATA_MODEL.md`
  - `docs/spec/FEATURE_PROCESS_SPEC.md`

### WFR-008 시스템은 실패 시 맥락 있는 재시작 제안을 해야 한다

- Requirement:
  - 시스템은 실패 발생 시 `더 작은 다음 행동` 또는 `10분 재시작`을 제안해야 한다.
- Why:
  - "복귀하세요" 같은 일반 알람만으로는 가치가 약하기 때문이다.
- Input:
  - task
  - planned block
  - failure reason
  - current time
- Output:
  - restart suggestion
- Rules:
  - 제안은 task 맥락을 반영해야 한다.
  - 제안은 즉시 실행 가능한 수준으로 작아야 한다.
- Open Questions:
  - 추천 규칙을 rule-based로 시작할지, 개인화까지 같이 갈지
- Promotion Target:
  - `docs/spec/FEATURE_PROCESS_SPEC.md`
  - `docs/PRODUCT_VALUE_QA.md`

### WFR-009 시스템은 완료 후 간단한 강화 피드백을 제공해야 한다

- Requirement:
  - 시스템은 세션 완료 시 간단한 완료 피드백과 다음 행동 제안을 제공해야 한다.
- Why:
  - 제품의 보상은 게임화보다 `복귀 안정감`에 가까워야 하기 때문이다.
- Input:
  - completed session
- Output:
  - completion feedback
  - optional next step suggestion
- Rules:
  - 초기 MVP에서 포인트 상점, 유료 아이템, 테마 구매는 포함하지 않는다.
  - 강화 피드백은 `첫 복귀 블록 완료`, `재시작까지 걸린 시간` 같은 복귀 중심 문구를 우선한다.
- Open Questions:
  - streak를 도입할지 여부
- Promotion Target:
  - `docs/CONSUMER_MESSAGING.md`
  - `docs/PRICING_ENTITLEMENT.md`

### WFR-010 시스템은 휴식을 보조적으로 제안할 수 있어야 한다

- Requirement:
  - 시스템은 세션 완료 후 짧은 휴식을 제안할 수 있어야 한다.
- Why:
  - 긴 집중 이후의 피로를 완화하되, 복잡한 타이머 앱으로 변하는 것을 막기 위해
- Input:
  - completed session duration
- Output:
  - break suggestion
- Rules:
  - 휴식은 보조 기능이다.
  - 초기 MVP에서 복잡한 포모도로 규칙을 강제하지 않는다.
- Open Questions:
  - 세션 길이 기준을 몇 분으로 둘지
- Promotion Target:
  - `docs/spec/FEATURE_PROCESS_SPEC.md`

### WFR-010A 타이머는 task 완료 강제보다 session 보호에 쓰여야 한다

- Requirement:
  - 시스템의 타이머는 사용자가 task를 끝낼 때까지 묶어두는 용도가 아니라, 현재 session/timebox를 보호하는 용도로 동작해야 한다.
- Why:
  - 복귀 앱의 핵심은 규율 강제가 아니라 재시작 가능한 작은 실행 단위를 만드는 것이기 때문이다.
- Input:
  - session duration
- Output:
  - protected execution block
- Rules:
  - 예정 시간 전 종료는 허용한다.
  - 타이머 종료 후에는 `완료/계속/휴식/실패` 전이를 제공한다.
  - 복잡한 포모도로 강제 규칙은 초기 MVP에 포함하지 않는다.
- Open Questions:
  - 기본 추천 길이를 10/25/50분 중 어떻게 둘지
- Promotion Target:
  - `docs/PRODUCT_VALUE_QA.md`
  - `docs/spec/FEATURE_PROCESS_SPEC.md`

### WFR-011 시스템은 실패 패턴 분석에 필요한 원천 데이터를 저장해야 한다

- Requirement:
  - 시스템은 계획된 시간대, 시작 여부, 중단 여부, 실패 이유, 재시작까지 걸린 시간을 저장해야 한다.
- Why:
  - 시간대별/업무별 실패 패턴을 해석하려면 원천 이벤트가 먼저 필요하다.
- Input:
  - planning events
  - session events
  - failure events
  - restart events
- Output:
  - analytics-ready raw events
- Rules:
  - 패턴 분석 자체는 비동기/배치 경로로 처리한다.
- Open Questions:
  - 업무 종류별 실패 패턴을 위해 어떤 추가 필드가 필요한지
- Promotion Target:
  - `docs/spec/DATA_MODEL.md`
  - `docs/spec/DATA_PIPELINE_ETL_BLUEPRINT.md`

## 5. 승격 규칙

- rough Q&A에서 반복 등장한 항목만 이 문서로 승격한다.
- 이 문서에서 `open question`이 해소되면 정식 spec 문서로 승격한다.
- 구현 시작 전에는 최소 `Requirement`, `Why`, `Rules`가 채워져 있어야 한다.
