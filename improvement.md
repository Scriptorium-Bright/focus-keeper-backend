# Backend Improvement Backlog

## Critical

- 주간 Big3 identity: `(user_id, week_start, origin_inbox_item_id)` unique와 known constraint 409가 없어 동일 source가 같은 주에 여러 item으로 생성될 수 있다.
- command idempotency: Inbox 저장, Big3 선택, failure check-in 등에 idempotency key가 없어 timeout 재시도와 새 명령을 구분하지 못한다.
- schema lifecycle: `DatabaseIndexInitializer`와 `ddl-auto`에 의존한다. Flyway/Liquibase migration, preflight, cleanup, rollback/roll-forward, verification 단계가 필요하다.

## High

- first recovery block: 요청 payload 내부에서만 하나인지 검사한다. 사용자·계획일 전체에 대한 DB 불변식이 필요하다.
- session terminal 경쟁: optimistic lock 예외가 generic 500으로 노출될 수 있다. 멱등 성공/409/retryable 정책을 정해야 한다.
- Big3 roll-up 경쟁: sibling ExecutionUnit 동시 완료 시 parent version 충돌의 API 오류와 retry 정책이 필요하다.
- test isolation: integration test가 같은 PostgreSQL 데이터를 공유한다. class별 cleanup 또는 격리 schema가 필요하다.
- overload protection: 150 flow/s에서 완료 처리량이 130.37 flow/s에 포화되고 p95가 2.73초로 증가했다. admission control, request timeout, connection pool wait 관측이 필요하다.

## Medium

- ExecutionUnit parent lock: 동일 parent hot-key의 lock wait p95/p99와 timeout taxonomy를 측정해야 한다.
- 시간 의존성: service/entity의 `OffsetDateTime.now()`를 `Clock`으로 주입해 날짜·주차 경계 테스트를 단순화해야 한다.
- Big3Service 책임: 선택, carryover, 만료 batch가 한 service에 모여 있다. command 단위 transaction service 분리를 검토한다.
- batch chunk: 300,000건에서 67,919 rows/s와 heap +3 MiB를 확인했지만 chunk 100,000의 WAL, I/O, lock 유지 시간은 측정하지 않았다.
- GiST 운영성: exclusion index의 크기, insert amplification, vacuum 영향을 측정해야 한다.
- frontend evidence: 현재 백엔드 중심 정리로 인해 REST API 연동 화면과 반응형 웹 증거가 약하다. 핵심 flow 1개를 React/Next.js로 구현하고 mobile/desktop screenshot, loading/error/409 conflict 상태를 검증해야 한다.
- Kubernetes evidence: Dockerfile과 CI/CD artifact는 있지만 Kubernetes manifest와 로컬 클러스터 smoke test가 없다. Deployment/Service/probe/config 주입과 `/actuator/health` 검증 로그가 필요하다.

## 다음 측정

- 100 flow/s 30분 soak test의 GC, heap, connection acquisition p95, DB CPU/I/O
- 120~140 flow/s 구간의 saturation curve
- ExecutionUnit 동일 parent/서로 다른 parent 경합 처리량 비교
- Timebox GiST exclusion 적용 전후 insert latency와 index size
- expiration chunk 10k/50k/100k의 throughput, WAL bytes, lock duration 비교

## 완료한 정리

- analytics, friction, retrospective, ops API, frontend, Airflow 제거
- common을 response/error/trace/config/metrics 기술 계층으로 축소
- planning→execution repository 직접 의존을 port로 제거
- core write flow와 대량 상태 전이 실측 완료
