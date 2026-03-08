# ADR-0001: Reboot System Baseline

- Status: Accepted
- Date: 2026-03-03

## Context

기존 구현(Phase 12.x까지)은 아카이브 브랜치로 보존되었고, 현재는 Phase 1부터 재구축 중이다.  
초기 설계의 목표는 "최소 복잡도로 출시 가능한 안정성"을 확보하는 것이다.

## Decision

1. 아키텍처는 Modular Monolith로 시작한다.
2. 이벤트 전달 신뢰성은 Transactional Outbox 패턴으로 보장한다.
3. 분석은 Spring Batch + RDB(Track A)로 시작한다.
4. Spark/Data Lake(Track B)는 임계치 충족 시에만 도입한다.
5. AI 코칭은 비동기 워커 경로를 강제한다.

## Alternatives Considered

### A. 초기부터 Kafka 중심 이벤트 아키텍처

- 장점: 느슨한 결합, 확장성
- 단점: 운영 복잡도/비용 증가, 초기 속도 저하
- 결론: 초기 출시 단계에서는 기각

### B. 초기부터 Spark 기반 분석

- 장점: 대규모 데이터 처리 확장성
- 단점: 현재 데이터 규모 대비 과도한 비용/복잡도
- 결론: Track B로 보류

## Consequences

- 장점:
  - 초기 구현 속도 증가
  - 트랜잭션 정합성 보장
  - 운영 단순성 확보
- 단점:
  - 대규모 확장 시 구조 전환 작업 필요
  - Outbox relay 운영 부담(모니터링/재시도 정책 필요)

## Follow-up

- Outbox DDL/Relay/멱등 테스트를 Phase 12.9에서 우선 완료
- 성능 임계치 초과 시 Track B ADR 추가 작성
