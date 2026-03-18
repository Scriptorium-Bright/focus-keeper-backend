# RebootFocus Document Catalog

> Version: v0.1  
> Updated: 2026-03-16  
> Scope: 이 저장소에 있는 주요 문서가 무엇을 하는 문서인지 한 번에 파악하기 위한 카탈로그

## 1. 목적

- 이 문서는 "어디에 어떤 문서가 있고, 왜 존재하는가"를 한 번에 보여주는 문서다.
- `docs/README.md`가 빠른 입구라면, 이 문서는 더 자세한 문서 지도다.
- 문서를 추가하거나 역할이 바뀌면 이 카탈로그도 같이 업데이트한다.

## 2. 읽는 방법

- `Source of Truth = Yes`
  - 해당 주제에서 가장 먼저 확인해야 하는 문서다.
- `When to Read`
  - 언제 이 문서를 열어야 하는지 짧게 적는다.

## 3. 문서 카탈로그

| Path | What It Does | When to Read | Source of Truth |
|---|---|---|---|
| `README.md` | 저장소 전체 소개와 빠른 시작 안내 | 프로젝트를 처음 열었을 때 | No |
| `docs/README.md` | 설계 문서 진입점과 빠른 질문-문서 매핑 | 어떤 문서를 먼저 봐야 할지 모를 때 | No |
| `docs/DOCUMENT_CATALOG.md` | 문서 전체 목록과 역할 설명 | 문서 구조 자체를 파악하고 싶을 때 | No |
| `docs/DOCS_POLICY.md` | 어떤 상황에서 어떤 문서에 기록할지 정하는 정책 | 문서를 어디에 써야 할지 헷갈릴 때 | Yes |
| `docs/PRODUCT_INTENT.md` | 제품 존재 이유, ICP, 가치, 비목표 정의 | 제품 방향과 메시지를 맞출 때 | Yes |
| `docs/ROUGH_PRODUCT_QA_LOG.md` | 사용자 질문과 Assistant 답변을 요약 없이 분류/누적하는 rough 로그 | 대화 아이디어를 먼저 쌓아둘 때 | No |
| `docs/newPlan.md` | 전체 Phase 로드맵, 우선순위, 실행 원칙 | 지금 무엇을 먼저 할지 정할 때 | Yes |
| `docs/ARCHITECTURE_OVERVIEW.md` | 시스템 전체 구조를 한 장으로 설명하는 개요 | 아키텍처를 상위 수준에서 설명할 때 | No |
| `docs/PHASE_EXIT_PROTOCOL.md` | 각 Phase 종료 시 어디서 멈추고 무엇을 남길지 정하는 기준 | 다음 Phase로 넘어가도 되는지 판단할 때 | Yes |
| `docs/PRICING_ENTITLEMENT.md` | 무료/유료 기능 경계와 과금 원칙을 고정하는 문서 | paywall, entitlement, 요금제 기준을 정할 때 | Yes |
| `docs/CONSUMER_MESSAGING.md` | 소비자 대상 제품 설명법과 to-do 앱 대비 차이를 정리한 문서 | 랜딩, 앱스토어, 소개 문구 메시지를 맞출 때 | Yes |
| `docs/PRODUCT_VALUE_QA.md` | 복귀 제안의 의미, 핵심 루프, 타깃 적합성에 대한 내부 질문/답변 정리 | 제품 가치가 흔들릴 때 다시 기준을 확인할 때 | Yes |
| `docs/BLOG_DECISION_PLAYBOOK.md` | Phase별 블로그 의사결정 주제, 작성 시점, 증빙 기준 정리 | 블로그에 어떤 판단을 남길지 고를 때 | Yes |
| `docs/refactor.md` | N.x 테스트/커밋 로그와 Phase 종료 High/Mid/Low 정리 | 구현 이력과 리팩터 우선순위를 볼 때 | Yes |
| `docs/decision-log.md` | 생각이나 방향이 바뀐 이유를 기록 | "왜 생각이 바뀌었지?"를 추적할 때 | No |
| `docs/RISK_REGISTER.md` | 리스크, 완화책, 트리거, 대응 기준 관리 | 장애/품질/범위 리스크를 정리할 때 | Yes |
| `docs/stack.md` | 확정 기술 스택과 도입/보류 기준 정리 | 기술 선택 근거를 빠르게 보고 싶을 때 | No |
| `docs/business-plan.md` | 사업 관점 제품/시장/수익 구조 정리 | 시장성, 수익성 스토리를 볼 때 | No |
| `docs/sales-onepager.md` | 판매/소개용 1페이지 문서 | 짧게 제품을 소개해야 할 때 | No |
| `docs/investor-onepager.md` | 투자자 시점 요약 문서 | 투자 관점 서사를 요약할 때 | No |
| `docs/PORTFOLIO_PLAYBOOK.md` | 포트폴리오를 문제 해결 스토리로 만드는 원칙 | 포트폴리오 구조를 설계할 때 | Yes |
| `docs/PORTFOLIO_CASEBOARD.md` | 포트폴리오 사례 후보와 우선순위를 관리 | 어떤 사례를 제출 자료에 넣을지 정할 때 | Yes |
| `docs/PORTFOLIO_PACKET_TEMPLATE.md` | 실제 제출 자료의 권장 목차와 템플릿 | 슬라이드/문안을 바로 작성할 때 | Yes |
| `docs/PORTFOLIO_DRAFT.md` | 현재 구현 상태를 반영한 제출 초안 | 실제 문안을 다듬을 때 | No |
| `docs/spec/ENGINEERING_SPEC.md` | 요구사항, NFR, 아키텍처 기준선 정의 | 기능/비기능 요구사항을 정리할 때 | Yes |
| `docs/spec/FUNCTIONAL_REQUIREMENTS_WORKBENCH.md` | rough 아이디어를 WFR 형식으로 정리하는 작업용 요구사항 문서 | 정식 spec 전에 요구사항을 다듬을 때 | Yes |
| `docs/spec/API_CONTRACT.md` | API 규약, 에러 코드, 버전 정책 | API 정책이나 응답 규칙을 볼 때 | Yes |
| `api/openapi.yaml` | 실제 API 스키마와 엔드포인트 명세 | 요청/응답 필드와 계약을 확인할 때 | Yes |
| `docs/spec/FEATURE_PROCESS_SPEC.md` | 기능 목록, 상태, 사용자/운영 프로세스 정리 | 어떤 기능이 있고 어디까지 됐는지 볼 때 | Yes |
| `docs/spec/KEY_FLOWS.md` | 핵심 시퀀스, 트랜잭션/예외 흐름, 서술형 작동 설명 | 다이어그램과 글 설명을 함께 보고 싶을 때 | Yes |
| `docs/spec/WEEKLY_EXECUTION_CHECKLIST.md` | 이번 주 구현 범위, done 기준, 증빙 체크리스트 | 주간 실행판을 업데이트할 때 | Yes |
| `docs/spec/DATA_MODEL.md` | 데이터 모델, 인덱스, 정합성 기준 | 테이블/엔티티 구조를 결정할 때 | Yes |
| `docs/spec/DATA_QUALITY.md` | 데이터 품질 기준과 검증 항목 | DQ 체크와 품질 임계치를 정의할 때 | Yes |
| `docs/spec/DATA_PIPELINE_ETL_BLUEPRINT.md` | ETL/파이프라인 전략과 적재 구조 설명 | 데이터 흐름을 설계할 때 | Yes |
| `docs/spec/BATCH_RUNBOOK.md` | 배치 실행, 워터마크, 백필, 재처리 절차 | 배치 운영이나 장애 대응을 정리할 때 | Yes |
| `docs/spec/RECOVERY_METRICS.md` | Recovery24/48, TTR 등 복귀 KPI 정의 | KPI 정의와 계산 기준을 볼 때 | Yes |
| `docs/spec/KPI_TRACKS.md` | 취업 KPI와 사업 KPI를 분리해 관리 | 어떤 지표를 어느 목적에 쓰는지 정할 때 | Yes |
| `docs/adr/ADR-0001-system-baseline.md` | 시스템 기본 구조 관련 ADR | 왜 이 아키텍처를 택했는지 볼 때 | Yes |
| `docs/adr/ADR-0002-event-criticality-strategy.md` | 이벤트 중요도와 메시징 진화 전략 ADR | Outbox/Kafka 진화 기준을 볼 때 | Yes |
| `career/JD_SELF_INTRO_SKELETON.md` | JD 기반 자기소개서/지원서 초안 템플릿 | 지원서 문안을 작성할 때 | No |
| `career/GLOBAL_EXPANSION_PLAYBOOK.md` | 글로벌 확장/커리어 확장 관점의 플레이북 | 장기 커리어/시장 확장을 고민할 때 | No |

## 4. 가장 자주 쓰는 문서 묶음

### 제품 방향을 맞출 때

- `docs/PRODUCT_INTENT.md`
- `docs/ROUGH_PRODUCT_QA_LOG.md`
- `docs/newPlan.md`
- `docs/decision-log.md`
- `docs/PRICING_ENTITLEMENT.md`
- `docs/CONSUMER_MESSAGING.md`
- `docs/PRODUCT_VALUE_QA.md`

### 지금 구현할 일을 정할 때

- `docs/newPlan.md`
- `docs/spec/FEATURE_PROCESS_SPEC.md`
- `docs/spec/WEEKLY_EXECUTION_CHECKLIST.md`
- `docs/PHASE_EXIT_PROTOCOL.md`

### API/도메인 설계를 할 때

- `docs/spec/ENGINEERING_SPEC.md`
- `docs/spec/FUNCTIONAL_REQUIREMENTS_WORKBENCH.md`
- `docs/spec/API_CONTRACT.md`
- `api/openapi.yaml`
- `docs/spec/KEY_FLOWS.md`
- `docs/spec/DATA_MODEL.md`

### 데이터 엔지니어링 스토리를 만들 때

- `docs/spec/RECOVERY_METRICS.md`
- `docs/spec/DATA_PIPELINE_ETL_BLUEPRINT.md`
- `docs/spec/DATA_QUALITY.md`
- `docs/spec/BATCH_RUNBOOK.md`
- `docs/spec/KPI_TRACKS.md`

### 포트폴리오/취업 자료를 만들 때

- `docs/PORTFOLIO_PLAYBOOK.md`
- `docs/PORTFOLIO_CASEBOARD.md`
- `docs/PORTFOLIO_PACKET_TEMPLATE.md`
- `docs/PORTFOLIO_DRAFT.md`
- `docs/refactor.md`

### 블로그 글감을 고를 때

- `docs/BLOG_DECISION_PLAYBOOK.md`
- `docs/decision-log.md`
- `docs/refactor.md`
- `docs/PHASE_EXIT_PROTOCOL.md`

## 5. 유지 규칙

- 문서를 새로 추가하면 이 카탈로그에도 추가한다.
- 문서 역할이 바뀌면 `What It Does`와 `Source of Truth`를 같이 수정한다.
- 비슷한 역할 문서가 생기면 하나를 기준선으로 승격하고 나머지는 보조 문서로 표시한다.

## 6. 연계 문서

- `docs/README.md`
- `docs/DOCS_POLICY.md`
- `docs/newPlan.md`
