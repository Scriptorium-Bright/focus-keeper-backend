# FocusKeeper Design Docs

이 디렉토리는 리부트 이후 설계 기준선을 담는 문서 모음이다.

## 문서 목록

- `docs/DOCS_POLICY.md`: 문서 용도/기록 시점/기록 규칙
- `docs/spec/ENGINEERING_SPEC.md`: 요구사항(기능/비기능), 아키텍처, 수용 기준
- `docs/spec/API_CONTRACT.md`: API 규약, 응답/에러 표준, 버전 정책
- `api/openapi.yaml`: 현재 구현 기준 OpenAPI 명세
- `docs/spec/DATA_MODEL.md`: 핵심 데이터 모델, 인덱스, 정합성 규칙
- `docs/spec/DATA_QUALITY.md`: 데이터 품질 기준(누락/중복/유효성/정합성/지연)
- `docs/spec/BATCH_RUNBOOK.md`: 배치 증분 처리/재처리 운영 절차
- `docs/spec/KPI_TRACKS.md`: 취업 KPI vs 사업 검증 KPI 분리 운영 기준
- `docs/spec/KEY_FLOWS.md`: 핵심 시퀀스(정상/예외/트랜잭션 경계)
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
| 생각이 왜 바뀌었지? | `docs/decision-log.md` |
| 이번 기능에서 API 계약이 뭐지? | `docs/spec/API_CONTRACT.md`, `api/openapi.yaml` |
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
