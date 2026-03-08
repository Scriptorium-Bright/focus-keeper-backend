# Docs Usage Policy

> 목적: "언제 어떤 문서에 기록할지"를 고정해 문서 중복과 누락을 줄인다.

## 1. 문서별 용도

| 상황 | 기록 위치 | 기록 내용 |
|---|---|---|
| 제품/기술 목표, 범위, NFR 변경 | `docs/spec/ENGINEERING_SPEC.md` | 요구사항, 수치(SLO), 아키텍처 기준선 |
| API 구조/에러 코드/버전 정책 변경 | `docs/spec/API_CONTRACT.md` + `api/openapi.yaml` | 계약 규약과 실제 스키마 |
| DB 스키마/인덱스/정합성 정책 변경 | `docs/spec/DATA_MODEL.md` | 데이터 모델과 제약 |
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
- "무엇을 바꿨는지"보다 "왜 바꿨는지"를 우선 기록한다.
- 수치가 있는 항목은 반드시 수치로 기록한다. (예: p95, 실패율, 임계치)
- 확정된 결정은 반드시 ADR 번호로 추적 가능해야 한다.
- 로드맵 기준선 문서는 `docs/newPlan.md` 하나만 사용한다.
- `docs/` 디렉터리 문서는 Git 추적 대상이며 ignore하지 않는다.
