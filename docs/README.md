# RebootFocus Design Docs

이 디렉토리는 리부트 이후 설계 기준선을 담는 문서 모음이다.
제품 기준선은 "전날 계획이 무너지면 다음날까지 다시 못 붙잡는 직장인을 위해, 놓친 일을 다시 시작하게 만드는 복귀 코치"라는 단일 방향으로 고정한다.

## 문서 목록

- `docs/DOCS_POLICY.md`: 문서 용도/기록 시점/기록 규칙
- `docs/DOCUMENT_CATALOG.md`: 주요 문서 전체 목록과 역할, 언제 읽는지 설명하는 카탈로그
- `docs/ARCHITECTURE_OVERVIEW.md`: 시스템 전체 구조/경계/런타임 경로 아키텍처 개요
- `docs/PHASE_EXIT_PROTOCOL.md`: Phase 종료 시 어디서 멈추고 무엇을 남길지 정하는 기준
- `docs/PRICING_ENTITLEMENT.md`: 무료/유료 기능 경계와 과금 원칙, entitlement 기준선
- `docs/CONSUMER_MESSAGING.md`: 소비자에게 제품을 어떻게 설명할지, to-do 앱과 무엇이 다른지 정리한 메시지 가이드
- `docs/PRODUCT_VALUE_QA.md`: 복귀 알람의 의미, 핵심 포인트, 타깃 적합성에 대한 내부 Q&A 정리
- `docs/ROUGH_PRODUCT_QA_LOG.md`: 내 질문과 Assistant 답변을 요약 없이 분류해 쌓아두는 rough 로그
- `docs/BLOG_DECISION_PLAYBOOK.md`: 어떤 Phase에서 어떤 의사결정을 블로그로 남기면 좋은지 정리한 문서
- `docs/PORTFOLIO_PLAYBOOK.md`: 취업용 포트폴리오를 문제 해결 스토리 중심으로 구성하는 기준
- `docs/PORTFOLIO_CASEBOARD.md`: 취업 제출용 사례 후보를 `F-00x`와 연결해 관리하는 보드
- `docs/PORTFOLIO_PACKET_TEMPLATE.md`: 실제 제출용 포트폴리오/발표 자료 구조 템플릿
- `docs/PORTFOLIO_DRAFT.md`: 현재 구현 상태를 반영한 1차 제출 문안 초안
- `docs/spec/ENGINEERING_SPEC.md`: 요구사항(기능/비기능), 아키텍처, 수용 기준
- `docs/spec/FUNCTIONAL_REQUIREMENTS_WORKBENCH.md`: rough 아이디어를 작업용 WFR 형식으로 정리하는 기능 요구사항 작업대
- `docs/spec/API_CONTRACT.md`: API 규약, 응답/에러 표준, 버전 정책
- `api/openapi.yaml`: 현재 구현 기준 OpenAPI 명세
- `docs/PRODUCT_INTENT.md`: 제품 목적/대상/가치/비목표 텍스트 기준선
- `docs/spec/DATA_MODEL.md`: 핵심 데이터 모델, 인덱스, 정합성 규칙
- `docs/spec/DATA_QUALITY.md`: 데이터 품질 기준(누락/중복/유효성/정합성/지연)
- `docs/spec/DATA_PIPELINE_ETL_BLUEPRINT.md`: 데이터 파이프라인/ETL 구축 전략 및 효과
- `docs/spec/RECOVERY_METRICS.md`: Recovery24/48, 재시작 횟수, 사이클 지표 정의
- `docs/spec/FEATURE_PROCESS_SPEC.md`: 기능 목록과 사용자/운영 프로세스 명세
- `docs/spec/WEEKLY_EXECUTION_CHECKLIST.md`: 주간 기능 전환 체크리스트(`planned -> in_progress -> done`)
- `docs/spec/BATCH_RUNBOOK.md`: 배치 증분 처리/재처리 운영 절차
- `docs/spec/KPI_TRACKS.md`: 취업 KPI vs 사업 검증 KPI 분리 운영 기준
- `docs/spec/KEY_FLOWS.md`: 핵심 시퀀스(정상/예외/트랜잭션 경계)와 서술형 흐름 설명
- `docs/adr/ADR-0001-system-baseline.md`: 핵심 기술 의사결정 기록
- `docs/adr/ADR-0002-event-criticality-strategy.md`: 도메인 중요도 기반 이벤트/메시징 진화 전략
- `docs/decision-log.md`: 의사결정이 바뀐 과정(이전 생각 -> 변경 이유) 추적 로그
- `docs/RISK_REGISTER.md`: 위험요소, 완화전략, 모니터링/대응 방안
- `docs/refactor.md`: N.x 테스트/커밋 로그 + Phase 종료 High/Mid/Low 개선 과제
- `docs/newPlan.md`: 제품 로드맵 및 Phase 우선순위 기준선
- `docs/stack.md`: 현재 확정 기술 스택 및 도입/보류 기준
- `docs/business-plan.md`: 사업계획서(복귀 코치 포지셔닝, 수익/지표/로드맵)
- `docs/sales-onepager.md`: 판매용 1페이지 문서
- `docs/investor-onepager.md`: 투자용 1페이지 문서

## 언제 어디에 쓸지 (요약)

| 질문 | 문서 |
|---|---|
| 왜 이 결정을 했지? | `docs/adr/*.md` |
| 전체 문서가 각각 무슨 역할인지 한 번에 보고 싶다 | `docs/DOCUMENT_CATALOG.md` |
| 생각이 왜 바뀌었지? | `docs/decision-log.md` |
| 우리가 정확히 무엇을 왜 만들지? | `docs/PRODUCT_INTENT.md` |
| 전체 시스템 구조를 한 장으로 보고 싶다 | `docs/ARCHITECTURE_OVERVIEW.md` |
| 지금 멈추고 정리할 시점인지 어떻게 판단하지? | `docs/PHASE_EXIT_PROTOCOL.md` |
| 무료 기능과 유료 기능 경계가 뭐지? | `docs/PRICING_ENTITLEMENT.md` |
| 소비자에게 이 앱을 어떻게 설명하고 to-do 앱과 뭐가 다른지 정리하고 싶다 | `docs/CONSUMER_MESSAGING.md` |
| 복귀 제안이 왜 의미 있는지, 이 제품의 가치 포인트가 정확히 뭔지 다시 확인하고 싶다 | `docs/PRODUCT_VALUE_QA.md` |
| 내 질문과 답변을 raw하게 쌓아두고 싶다 | `docs/ROUGH_PRODUCT_QA_LOG.md` |
| 어떤 의사결정을 어느 Phase에서 블로그로 쓰면 좋지? | `docs/BLOG_DECISION_PLAYBOOK.md` |
| 취업용 포트폴리오는 어떤 구조로 보여줘야 하지? | `docs/PORTFOLIO_PLAYBOOK.md` |
| 지금 어떤 문제 해결 사례를 우선 완성해야 하지? | `docs/PORTFOLIO_CASEBOARD.md` |
| 실제 제출 슬라이드/문서는 어떤 순서로 구성하지? | `docs/PORTFOLIO_PACKET_TEMPLATE.md` |
| 지금 바로 제출 초안 문안은 어디서 다듬지? | `docs/PORTFOLIO_DRAFT.md` |
| 이번 기능에서 API 계약이 뭐지? | `docs/spec/API_CONTRACT.md`, `api/openapi.yaml` |
| 데이터 파이프라인/ETL은 어떻게 설계하지? | `docs/spec/DATA_PIPELINE_ETL_BLUEPRINT.md` |
| Recovery24/Recovery48/재시작 횟수 지표 정의가 뭐지? | `docs/spec/RECOVERY_METRICS.md` |
| 정확히 어떤 기능이 있고 어떤 프로세스로 동작하지? | `docs/spec/FEATURE_PROCESS_SPEC.md` |
| 대화에서 나온 rough 기능 아이디어를 WFR 형식으로 먼저 정리하고 싶다 | `docs/spec/FUNCTIONAL_REQUIREMENTS_WORKBENCH.md` |
| 이번 주에 뭘 구현하고 어떤 기준으로 done 처리하지? | `docs/spec/WEEKLY_EXECUTION_CHECKLIST.md` |
| 데이터 스키마/정합성 규칙은? | `docs/spec/DATA_MODEL.md` |
| 데이터 품질 기준/알림 임계치는? | `docs/spec/DATA_QUALITY.md` |
| 배치 증분/재처리 절차는? | `docs/spec/BATCH_RUNBOOK.md` |
| 로직 흐름/트랜잭션 경계는? | `docs/spec/KEY_FLOWS.md` |
| 취업 우선 KPI와 사업 KPI를 어떻게 나누지? | `docs/spec/KPI_TRACKS.md`, `docs/newPlan.md` |
| 이번 N.x 테스트/커밋 이력은? | `docs/refactor.md` |
| 위험요소와 대응 기준은? | `docs/RISK_REGISTER.md` |

## 원칙

- 설계는 코드보다 먼저 정의하되, 구현 과정에서 변경되면 문서를 즉시 업데이트한다.
- 설계 문서는 "왜 이 결정을 했는가"를 남기는 기록이다.
- 비기능 요구사항은 반드시 수치로 기록한다.
