# RebootFocus Portfolio Caseboard

> Version: v0.1  
> Updated: 2026-03-16  
> Scope: 취업 제출용 문제 해결 사례 후보를 `F-00x`와 연결해 관리하는 운영 보드

## 1. 목적

- 이 문서는 "무슨 기능을 만들었나"가 아니라 "어떤 문제를 어떤 증거로 해결했나"를 관리한다.
- `docs/PORTFOLIO_PLAYBOOK.md`가 원칙 문서라면, 이 문서는 실제 제출 후보 백로그다.
- 각 사례는 구현 진행과 함께 업데이트하며, 발표/자소서/면접 답변의 기준선으로 사용한다.

## 2. 사례 작성 규칙

- 사례 하나는 가능하면 하나의 핵심 문제만 다룬다.
- 각 사례는 `문제`, `해결 방법`, `결과`, `증빙`, `추천 시각 자료`를 가져야 한다.
- 결과에는 테스트, KPI, 운영 로그, 재처리 결과 중 최소 1개를 포함한다.
- "기능이 많다"보다 "어떤 판단으로 구조를 선택했는가"가 먼저 보여야 한다.
- 사례 초안은 각 Phase 종료 시 `docs/refactor.md`의 `Problem -> Solution -> Result Draft`에서 가져온다.

## 3. 사례 백로그

| 사례 ID | 연결 Phase / 기능 | 면접관 질문 | 핵심 문제 | 해결 방법 초점 | 결과/증빙 | 권장 시각 자료 | 상태 |
|---|---|---|---|---|---|---|---|
| C-01 | Phase 4~5 / F-003 ~ F-006 | 실패한 사용자를 다음날까지 다시 붙잡는 흐름을 어떻게 설계했나? | 전날 실패 이후 다음날 첫 복귀 블록이 사라지면 사용자가 루프에서 이탈한다. | Big3 -> 첫 복귀 블록 -> 실패 체크인 -> 10분 재시작으로 복귀 경로를 명시적 상태 전이로 고정한다. | API/통합 테스트, 상태 전이 검증, Recovery24 입력 이벤트 완전성 | 시퀀스 다이어그램 | draft |
| C-02 | Phase 11 / F-022 (+ 백필, DQ) | 복귀 KPI를 어떻게 신뢰성 있게 계산했나? | 이벤트가 쌓여도 중복/누락/지각 데이터가 있으면 Recovery24와 TTR이 왜곡된다. | KPI mart, 워터마크, 멱등 upsert, 기간 백필, DQ 검증으로 집계 신뢰성을 확보한다. | `daily_kpi_pipeline`, 재처리 로그, DQ 결과, SQL/대시보드 출력 | 데이터 플로우 | draft |
| C-03 | Phase 13~14 / F-027 ~ F-031 | 반복 실패와 운영 이상을 어떻게 조기 감지했나? | 사용자의 복귀 실패 패턴과 시스템 장애가 섞이면 원인 분리가 늦어진다. | friction signal 계산과 API/Batch 관측 대시보드를 분리해 사용자 문제와 운영 문제를 각각 본다. | signal report, alert rule, runbook drill 기록 | 아키텍처 다이어그램 | idea |
| C-04 | Phase 15 / F-032 ~ F-034 | AI 기능을 넣으면서 코어 경로를 어떻게 보호했나? | 회고 생성 실패나 비용 폭증이 핵심 복귀 경로까지 오염시키면 제품 신뢰도가 떨어진다. | AI 회고를 비동기 큐/워커로 격리하고 timeout, retry, fallback, cost guardrail을 둔다. | worker metric, timeout/fallback 테스트, 비용 상한 로그 | 비동기 흐름도 | idea |
| C-05 | Phase 3 / Recovery Onboarding | 왜 온보딩을 나중으로 미뤘나? | 온보딩을 먼저 만들면 정작 제품 차별점인 복귀 루프 없이 메시지만 과장될 위험이 있다. | 가입 플로우보다 핵심 복귀 루프를 먼저 만들고, 온보딩은 첫 복귀 블록 선택으로 좁힌다. | 우선순위 결정 로그, 스프린트 계획, 기능 범위 축소 근거 | 우선순위 의사결정 도식 | idea |

## 4. P0 제출 우선순위

1. `C-01`을 가장 먼저 완성한다.
2. `C-02`를 데이터 엔지니어링 대표 사례로 완성한다.
3. `C-03`을 반복 실패 신호와 운영 관측을 함께 보여주는 핵심 사례로 끌어올린다.
4. `C-04`는 코어 경로가 안정화된 뒤 선택적으로 넣는다.

## 5. 제출 패킷 배치 규칙

- `Project Overview`: 제품 한 줄 설명, 문제, Tech, Architecture, 핵심 기능만 넣는다.
- `Problem`: 왜 "실패 다음날 복귀"를 별도 문제로 정의했는지 설명한다.
- `Architecture`: `docs/ARCHITECTURE_OVERVIEW.md`와 `docs/spec/KEY_FLOWS.md`의 그림을 사용한다.
- `Core Challenges`: `C-01`, `C-02`, `C-03` 순으로 배치한다.
- `Tech Decisions`: `왜 RDB + Batch SQL부터 시작했는가`, `왜 Kafka/Spark를 미뤘는가`를 넣는다.
- `Improvement`: 테스트, DQ, 재처리, 관측 개선을 수치/로그와 함께 넣는다.
- `Lessons`: 범위 축소, 이벤트 설계, KPI 우선 설계에서 배운 점을 정리한다.

## 6. Phase 종료 후 갱신 규칙

- 각 Phase 종료 시 `docs/refactor.md`의 High / Mid / Low를 먼저 본다.
- `High`가 해결되지 않으면 해당 사례는 `ready`로 올리지 않는다.
- `Problem -> Solution -> Result Draft`가 채워진 항목만 포트폴리오 사례 후보로 승격한다.
- 사례 상태는 아래 4단계로 관리한다.
  - `idea`: 아이디어만 있음
  - `draft`: 문제/해결/결과 초안 작성됨
  - `ready`: 증빙과 다이어그램까지 연결됨
  - `used`: 실제 제출 자료에 반영됨

## 7. 사례 카드 템플릿

```text
[사례 ID]
[문제]
[왜 중요한가]
[검토한 대안]
[선택한 해결]
[검증 방법]
[결과]
[남은 리스크]
[연결 다이어그램]
```

## 8. 연계 문서

- `docs/PORTFOLIO_PLAYBOOK.md`
- `docs/PHASE_EXIT_PROTOCOL.md`
- `docs/spec/FEATURE_PROCESS_SPEC.md`
- `docs/spec/WEEKLY_EXECUTION_CHECKLIST.md`
- `docs/newPlan.md`
