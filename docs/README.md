# FocusKeeper Design Docs

이 디렉토리는 리부트 이후 설계 기준선을 담는 문서 모음이다.

## 문서 목록

- `docs/spec/ENGINEERING_SPEC.md`: 요구사항(기능/비기능), 아키텍처, 수용 기준
- `docs/spec/API_CONTRACT.md`: API 규약, 응답/에러 표준, 버전 정책
- `api/openapi.yaml`: 현재 구현 기준 OpenAPI 명세
- `docs/spec/DATA_MODEL.md`: 핵심 데이터 모델, 인덱스, 정합성 규칙
- `docs/spec/KEY_FLOWS.md`: 핵심 시퀀스(정상/예외/트랜잭션 경계)
- `docs/adr/ADR-0001-system-baseline.md`: 핵심 기술 의사결정 기록
- `docs/RISK_REGISTER.md`: 위험요소, 완화전략, 모니터링/대응 방안
- `docs/refactor.md`: N.x 테스트/커밋 로그 + Phase 종료 High/Mid/Low 개선 과제

## 원칙

- 설계는 코드보다 먼저 정의하되, 구현 과정에서 변경되면 문서를 즉시 업데이트한다.
- 설계 문서는 "왜 이 결정을 했는가"를 남기는 기록이다.
- 비기능 요구사항은 반드시 수치로 기록한다.
