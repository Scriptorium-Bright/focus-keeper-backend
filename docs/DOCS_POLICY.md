# Docs Usage Policy

> 목적: "언제 어떤 문서에 기록할지"를 고정해 문서 중복과 누락을 줄인다.

## 1. 문서별 용도

| 상황 | 기록 위치 | 기록 내용 |
|---|---|---|
| 시스템 전체 구조/경계/런타임 경로를 요약 | `docs/ARCHITECTURE_OVERVIEW.md` | 시스템 컨텍스트, 동기/배치 경로, 주요 컴포넌트 |
| 문서 전체 목록과 역할을 한 번에 안내 | `docs/DOCUMENT_CATALOG.md` | 문서 경로, 용도, 언제 읽는지, source of truth 여부 |
| Phase 종료 시점/멈춤 기준/다음 Phase 진입 판단 정리 | `docs/PHASE_EXIT_PROTOCOL.md` | 종료 체크리스트, High/Mid/Low, 포트폴리오 초안 |
| Phase별 블로그 의사결정 주제와 작성 타이밍 정리 | `docs/BLOG_DECISION_PLAYBOOK.md` | 블로그 후보, 작성 시점, 필요한 증빙 |
| 취업용 포트폴리오 스토리/문제 해결 카드 기준 정리 | `docs/PORTFOLIO_PLAYBOOK.md` | 사례 구성 원칙, 금지 규칙, 추천 시각 자료 |
| 실제 취업 제출용 사례 후보/우선순위 운영 | `docs/PORTFOLIO_CASEBOARD.md` | 사례 백로그, 연결 기능, 증빙, 상태 |
| 실제 제출 슬라이드/문서 순서와 초안 템플릿 정리 | `docs/PORTFOLIO_PACKET_TEMPLATE.md` | Project Overview, Problem, Architecture, Core Challenges, Tech Decisions, Improvement, Lessons |
| 현재 구현 상태를 반영한 제출 문안 초안 작성/수정 | `docs/PORTFOLIO_DRAFT.md` | 1차 문안, 현재 구현 범위와 예정 범위 분리 |
| 제품/기술 목표, 범위, NFR 변경 | `docs/spec/ENGINEERING_SPEC.md` | 요구사항, 수치(SLO), 아키텍처 기준선 |
| 대화 중 나온 원문 질문/원문 답변을 rough하게 분류/누적 | `docs/ROUGH_PRODUCT_QA_LOG.md` | 요약 없이 raw 질문/답변, 태그, 상태 |
| rough 아이디어를 작업용 기능 요구사항으로 정리 | `docs/spec/FUNCTIONAL_REQUIREMENTS_WORKBENCH.md` | WFR 형식 요구사항, open question, 승격 대상 |
| 제품 존재 이유/대상/가치/비목표 정렬 | `docs/PRODUCT_INTENT.md` | 무엇을 위해/누구를 위해/어떤 가치를 줄지 |
| API 구조/에러 코드/버전 정책 변경 | `docs/spec/API_CONTRACT.md` + `api/openapi.yaml` | 계약 규약과 실제 스키마 |
| 기능 목록/동작 프로세스 변경 | `docs/spec/FEATURE_PROCESS_SPEC.md` + `docs/spec/KEY_FLOWS.md` | 기능 카탈로그, 사용자/운영 플로우 |
| DB 스키마/인덱스/정합성 정책 변경 | `docs/spec/DATA_MODEL.md` | 데이터 모델과 제약 |
| 데이터 파이프라인/ETL 설계 변경 | `docs/spec/DATA_PIPELINE_ETL_BLUEPRINT.md` + `docs/spec/BATCH_RUNBOOK.md` | 수집/정제/적재/재처리 전략 |
| 복귀 KPI(Recovery24/48, 재시작 횟수, 사이클) 정의 변경 | `docs/spec/RECOVERY_METRICS.md` + `docs/spec/KPI_TRACKS.md` | 지표 수식/해석/판단 기준 |
| 주간 기능 전환 계획/완료 기준 업데이트 | `docs/spec/WEEKLY_EXECUTION_CHECKLIST.md` + `docs/refactor.md` | 이번 주 실행 범위, done 기준, 회고 |
| 데이터 품질 기준/검증 임계치 변경 | `docs/spec/DATA_QUALITY.md` | 누락/중복/유효성/정합성 기준 |
| 배치 증분 처리/재처리 절차 변경 | `docs/spec/BATCH_RUNBOOK.md` | 워터마크, 백필, 재실행 절차 |
| 로직 흐름/트랜잭션 경계 변경 | `docs/spec/KEY_FLOWS.md` | 정상/예외 시퀀스 |
| 취업 KPI/사업 KPI 우선순위 변경 | `docs/spec/KPI_TRACKS.md` + `docs/newPlan.md` | 트랙별 지표와 게이트 |
| "왜 이 결정을 했는지" 확정 | `docs/adr/ADR-xxxx-*.md` | Context/Decision/Alternatives/Consequences |
| 의견 변화/판단 과정 추적 | `docs/decision-log.md` | 이전 생각 -> 변경 이유 -> 구현 영향 |
| 위험요소/완화책/알림 임계치 변경 | `docs/RISK_REGISTER.md` | Risk/SPOF/Trigger |
| 작업 단위 테스트/커밋 및 Phase 종료 개선 과제 | `docs/refactor.md` | N.x 로그 + High/Mid/Low |

## 2. 업데이트 타이밍

1. 구현 시작 전:
   - `spec` 문서와 API 계약, KPI 트랙 문서를 먼저 갱신한다.
2. 구현 중(N.x 완료 시):
   - 테스트/커밋 정보를 `docs/refactor.md`에 기록한다.
3. 아키텍처 결정 확정 시:
   - ADR을 작성하고 관련 `spec` 문서에 링크한다.
4. 생각이 바뀌는 시점:
   - `docs/decision-log.md`에 변화 과정을 먼저 남긴다.
5. Phase 종료 시:
   - `docs/refactor.md`의 High/Mid/Low를 업데이트한다.

## 3. 기록 규칙

- 대화 원문을 그대로 붙이지 않는다.
- 예외: `docs/ROUGH_PRODUCT_QA_LOG.md`는 raw 질문/답변 누적을 위한 rough 로그이므로 원문 유지 가능
- "무엇을 바꿨는지"보다 "왜 바꿨는지"를 우선 기록한다.
- 수치가 있는 항목은 반드시 수치로 기록한다. (예: p95, 실패율, 임계치)
- 확정된 결정은 반드시 ADR 번호로 추적 가능해야 한다.
- 로드맵 기준선 문서는 `docs/newPlan.md` 하나만 사용한다.
- `docs/` 디렉터리 문서의 Git 추적 여부는 현재 작업 정책(`.gitignore`)을 따른다.
