# ADR-0002: Event Criticality Strategy and Stage Evolution

- Status: Accepted
- Date: 2026-03-03

## Context

초기 제품 단계에서 Kafka/Outbox를 기본값으로 강제하면 운영 복잡도가 빠르게 증가한다.  
반대로 모든 이벤트를 단순 비동기로 처리하면 정합성 사고 가능성이 있다.  
따라서 이벤트를 도메인 중요도 기준으로 분리하고, 수치 기준으로 단계 전환해야 한다.

## Decision

1. 모든 이벤트에 동일 아키텍처를 적용하지 않는다.
2. 도메인 중요도에 따라 처리 전략을 다르게 적용한다.
   - 계획/복귀 코어: 동기 트랜잭션 확정
   - 알림/복귀 메시지: 단순 비동기 + 재동기화
   - 분석/AI: 배치 재집계
3. 단계 전략:
   - Stage 0: 메시징 미도입
   - Stage 1: Transactional Outbox + Relay
   - Stage 2: Outbox + Message Broker
4. 전환은 수치 임계치(실패율/복구시간/처리량/소비자 수)로 판단한다.

## Alternatives Considered

### A. 초기부터 Kafka + Outbox

- 장점: 확장성/재처리 체계 선제 확보
- 단점: 운영 복잡도와 초기 비용 과다
- 결론: 초기 단계에는 기각

### B. 영구적으로 단순 비동기만 사용

- 장점: 구현/운영 단순
- 단점: 고중요 이벤트에서 유실/정합성 리스크
- 결론: 고중요 영역 대응 불충분

## Consequences

- 장점:
  - 도메인별 적정 복잡도 유지
  - 감정 논쟁 대신 수치 기반 의사결정 가능
- 단점:
  - 단계 전환 시 문서/운영 절차 업데이트 필요
  - 두 가지 운영 모델(Stage 0/1+)을 병행 관리해야 함

## Follow-up

- Engineering Spec에 전환 임계치 유지
- 리스크 레지스터에 지표 알림 기준 반영
- 전환 시점마다 ADR 상태 재평가
