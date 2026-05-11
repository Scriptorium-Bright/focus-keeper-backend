# Ops Alert / Notifier / Overview 강화 계획

## Summary

- 범위는 3축 단계형으로 고정한다. 1차는 Alert lifecycle + Webhook notifier, 2차는 Ops overview + threshold-based judgement로 묶는다.
- 목표는 현재 common/observability 구조를 유지한 채 in-memory alert state -> lifecycle-aware state machine -> external notification -> operator judgement UI로 확장하는 것이다.
- 원칙은 alertKey 단위 identity 유지, 같은 문제는 새 레코드를 쌓지 않고 상태 전이로만 관리, 이번 단계에서는 DB 영속화는 하지 않는 것이다.

## Execution Strategy

- 구현 순서는 `Phase 1 -> Phase 2 -> Phase 3 -> Phase 4`로 고정한다.
- 각 phase는 독립적으로 끝내고, phase 종료 시점마다 커밋과 문서화를 같이 마친다.
- phase를 건너뛰지 않는다. notifier와 overview는 lifecycle semantics가 먼저 고정된 뒤에만 붙인다.
- phase 진행 중 공통 원칙은 유지한다.
  - alertKey 기반 identity 유지
  - in-memory 저장 유지
  - 새 endpoint 추가 없이 기존 `/api/v1/ops/**` surface 확장
  - batch_failure, processing_lag, dq가 같은 lifecycle engine을 공유

## Phase Plan

- Phase 1. State Transition Model
  - `OperationsAlertService` 내부 모델을 boolean `active` 중심에서 `status=ACTIVE|RESOLVED` 중심으로 바꾼다.
  - 같은 `alertKey`는 항상 같은 incident로 보고 새 레코드를 쌓지 않는다.
  - 전이 규칙을 고정한다.
    - `new active -> OPENED`
    - `active -> active -> refresh`
    - `active -> resolved -> RESOLVED`
    - `resolved -> active -> REOPENED`
    - `new resolved -> no-op`
  - 완료 기준
    - phantom resolve가 생기지 않는다.
    - 중복 active가 alert 수를 늘리지 않는다.
    - reopen이 같은 identity를 재사용한다.
- Phase 2. Lifecycle Metadata Surface
  - `OperationsAlertResponse`에 `status`, `firstSeenAt`, `lastSeenAt`, `resolvedAt`, `occurrenceCount`, `reopenCount`를 추가한다.
  - 기존 `active`, `lastChangedAt`는 하위 호환 필드로 유지한다.
  - `GET /api/v1/ops/alerts`는 `activeOnly=false`일 때 resolved까지 반환하고 정렬은 `ACTIVE first`, 이후 `lastChangedAt desc`로 고정한다.
  - 완료 기준
    - 운영자가 API 응답만 보고 언제 처음 발생했고, 몇 번 반복됐고, 지금 살아 있는지 판단할 수 있다.
- Phase 3. Transition Event Contract
  - lifecycle event type을 `OPENED`, `REOPENED`, `ESCALATED`, `RESOLVED`로 확정한다.
  - severity 상승만 `ESCALATED`로 취급하고, severity 하향은 일반 refresh로 본다.
  - notifier와 overview가 그대로 재사용할 수 있도록 alert 전이 결과를 별도 event object로 정리한다.
  - 완료 기준
    - 모든 전이가 로그와 테스트에서 동일한 event semantics로 검증된다.
- Phase 4. Consumer Integration
  - webhook notifier는 Phase 3의 event contract만 구독하게 연결한다.
  - ops overview/UI는 lifecycle metadata를 읽어 `active/resolved`, `firstSeenAt`, `reopenCount`를 보여준다.
  - README, OpenAPI, runbook 문구를 lifecycle semantics에 맞춰 갱신한다.
  - 완료 기준
    - 외부 알림과 운영 화면이 alert service 규칙을 중복 구현하지 않고 같은 lifecycle contract를 사용한다.

## Key Changes

- Alert lifecycle
    - OperationsAlertService는 DTO 직접 저장 대신 내부 alert state를 보관하고 상태를 ACTIVE / RESOLVED enum으로 관리한다.
    - OperationsAlertResponse는 기존 필드를 유지하면서 status, firstSeenAt, lastSeenAt, resolvedAt, occurrenceCount, reopenCount를 추가한다. active는 호환 필드로 유지하고 lastChangedAt
      는 마지막 상태 전이 시각으로 고정한다.
    - 전이 규칙은 고정한다. 신규 + active=true -> OPENED, 기존 ACTIVE + active=true -> same alert refresh, 기존 ACTIVE + active=false -> RESOLVED, 기존 RESOLVED + active=true ->
      REOPENED, 기존 RESOLVED + active=false와 신규 + active=false는 no-op으로 처리한다.
    - ACTIVE 상태에서 severity가 WARNING -> CRITICAL로 올라갈 때만 ESCALATED 전이로 취급하고 notifier를 보낸다. CRITICAL -> WARNING 하향은 내부 상태만 갱신하고 별도 알림은 보내지 않는
      다.
    - GET /api/v1/ops/alerts는 그대로 유지하고 activeOnly=false일 때 resolved lifecycle도 반환한다. 정렬은 ACTIVE 먼저, 이후 lastChangedAt desc로 고정한다.
    - 성공 경로에서 처음부터 resolved alert를 만들지 않는다. 이전에 한 번도 active였던 적 없는 alert key의 resolve 호출은 무시한다.
- Webhook notifier
    - ops.notifications.webhook.enabled, url, connect-timeout-ms, read-timeout-ms, headers 설정을 추가한다.
    - OperationsAlertNotifier 인터페이스와 Spring RestClient 기반 범용 HTTP 구현을 추가하고, OperationsAlertService가 전이 이벤트 발생 시 notifier를 호출한다.
    - 전송 payload는 eventType, service, emittedAt, previousStatus, previousSeverity, alert snapshot으로 고정한다. eventType은 OPENED, REOPENED, ESCALATED, RESOLVED만 사용한다.
    - notifier는 동기식 best-effort로 처리한다. HTTP 실패나 timeout은 alert 저장이나 배치 결과를 실패로 바꾸지 않고 warn 로그와 notifier metric만 남긴다.
    - OperationsMetricRecorder에 reboot_ops_alert_notifications_total counter를 추가하고 tag는 event, result=success|failure로 고정한다.
- Ops overview + threshold 기준
    - 하드코딩된 기준을 설정값으로 끌어올린다. ops.thresholds.processing-lag.warning-days=1, ops.thresholds.processing-lag.critical-days=2, ops.thresholds.dq.warning-issue-count=1,
      ops.thresholds.batch-failure.critical-count=1.
    - OperationsAlertService.evaluateProcessingLag, OperationsOverviewService, runbook trigger 문구가 같은 OperationsThresholdProperties를 공유하게 만든다.
    - BatchOverviewResponse에 opsJudgement 블록을 추가한다. 필드는 overallStatus, recommendedAction, batchFailure, freshness, dataQuality로 고정한다.
    - batchFailure는 activeFailureCount, criticalThreshold, status, affectedStages를 포함한다.
    - freshness는 lastProcessedDate, lagDays, warningThresholdDays, criticalThresholdDays, status를 포함한다.
    - dataQuality는 totalIssueCount, warningThresholdIssueCount, status를 포함한다.
    - overallStatus 우선순위는 batch failure CRITICAL > freshness CRITICAL > freshness WARNING > DQ WARNING > HEALTHY로 고정한다.
    - recommendedAction은 CHECK_BATCH_STAGE, RUN_BACKFILL, INVESTIGATE_DQ, NO_ACTION 중 하나로 고정하고 같은 우선순위로 선택한다.
    - static ops 화면은 stage 4에서 raw JSON은 유지하되, 그 위에 overall status, recommended action, batch failure / freshness / DQ 판단 카드, active/all alerts 토글을 추가한다. alert
      row에는 severity, status, firstSeenAt, resolvedAt, occurrenceCount를 노출한다.
    - README, api/openapi.yaml, runbook trigger 문구를 새 threshold와 lifecycle semantics에 맞춰 갱신하고 “rough” 표현은 제거한다.

## Public Interfaces

- OperationsAlertResponse 확장: status, firstSeenAt, lastSeenAt, resolvedAt, occurrenceCount, reopenCount 추가.
- BatchOverviewResponse 확장: opsJudgement 추가.
- 설정 추가: ops.notifications.webhook.*, ops.thresholds.processing-lag.*, ops.thresholds.dq.warning-issue-count, ops.thresholds.batch-failure.critical-count.
- 기존 API path와 query parameter는 유지하고 새 endpoint는 만들지 않는다.

## Commit Rules

- 각 phase마다 커밋 2개를 남긴다.
  - `feat : ...`
  - `test : ...`
- `feat` 커밋은 프로덕션 코드, 설정, 응답 스키마, 문서 구조 변경을 담는다.
- `test` 커밋은 단위 테스트, 통합 테스트, 테스트 보조 코드, phase 검증 로그 갱신을 담는다.
- phase 완료 커밋 메시지는 기본적으로 아래 패턴을 따른다.
  - Phase 1
    - `feat : alert lifecycle 상태 전이 모델 도입`
    - `test : alert lifecycle 상태 전이 테스트 추가`
  - Phase 2
    - `feat : ops alerts lifecycle 메타데이터 응답 확장`
    - `test : ops alerts lifecycle 응답 테스트 추가`
  - Phase 3
    - `feat : alert transition event contract 도입`
    - `test : alert transition event semantics 테스트 추가`
  - Phase 4
    - `feat : webhook notifier와 ops overview 연동`
    - `test : webhook notifier와 ops overview 연동 테스트 추가`
- 커밋 메시지는 한글로 쓰는 게 자연스러운 변경은 한글로, 영어 용어가 더 정확한 변경은 영어를 유지한다.
- phase를 넘기기 전에는 반드시 해당 phase의 `feat`와 `test`가 모두 존재해야 한다.

## Phase Evidence Logging

- phase 1부터 4까지의 결과 기록은 하나의 문서에 누적한다.
- 기록 파일은 [alert-lifecycle-phase-log.md](/Users/jeonjeonghyeon/studyCollection/adhd/docs/plan/alert-lifecycle-phase-log.md)로 고정한다.
- 각 phase 종료 시 아래 항목을 반드시 채운다.
  - 수정 전 코드 스냅샷
  - 대상
  - 변경 이유
  - 변경 내용
  - 동작 변경 요약
  - 테스트 결과
  - `feat` 커밋 메시지
  - `test` 커밋 메시지
- 로그 문서는 구현 회고가 아니라, 다음 phase로 넘어가기 전 상태를 고정하는 evidence로 사용한다.

## Test Plan

- 서비스 테스트
    - 첫 성공 실행은 phantom resolved alert를 만들지 않는다.
    - 같은 alert key의 반복 active는 레코드 수를 늘리지 않고 occurrenceCount만 증가시킨다.
    - ACTIVE -> RESOLVED -> REOPENED 흐름에서 timestamp와 count가 기대대로 바뀐다.
    - lag가 warning에서 critical로 올라갈 때만 ESCALATED notifier가 발생한다.
- notifier 테스트
    - webhook enabled일 때 payload가 설정 URL로 POST 된다.
    - webhook timeout/500이어도 alert 처리와 배치 결과는 유지되고 notifier metric만 failure로 증가한다.
    - disabled 또는 URL 미설정이면 외부 호출이 발생하지 않는다.
    - HTTP 검증은 MockRestServiceServer 기준으로 고정한다.
- controller/integration 테스트
    - GET /api/v1/ops/alerts?activeOnly=false가 resolved alert와 lifecycle fields를 반환한다.
    - GET /api/v1/ops/overview/batch가 opsJudgement와 threshold 값을 반환한다.
    - OpenAPI 문서에 확장된 schema 필드가 반영된다.
- UI/manual 검증
    - stage 4에서 active/all 토글, status badge, threshold card, recommended action이 정상 렌더링된다.
    - 실제 batch failure, DQ issue, lag scenario에서 화면 판단 결과가 alert state와 일치한다.

## Assumptions

- alert lifecycle은 이번 단계에서도 in-memory만 유지하고 애플리케이션 재시작 시 history가 초기화되는 동작을 그대로 둔다.
- webhook은 단일 URL 대상의 generic JSON POST만 지원하고 retry queue, dead letter, signature 검증은 포함하지 않는다.
- ops overview 판단 기준은 현재 Daily KPI 계열 observability 범위에 한정하고 Airflow/lab 자산 분리나 외부 observability 시스템 연동은 이번 계획에 포함하지 않는다.
