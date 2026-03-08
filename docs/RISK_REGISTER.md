# Risk Register

> Updated: 2026-03-03  
> Scope: Phase 1~15 출시 경로

## Risk Table

| ID | Risk | Impact | Likelihood | Mitigation | Detection |
|---|---|---|---|---|---|
| R-001 | Outbox relay 정체로 이벤트 지연 증가 | High | Medium | `status+retry+next_retry_at` 정책, 워커 수평 확장 | Outbox lag 대시보드/알림 |
| R-002 | 중복 이벤트 처리로 데이터 이상 | High | Medium | `event_id` 기반 멱등 키, unique 제약 | 중복 키 충돌 로그/지표 |
| R-003 | 배치 장기 실행으로 OLTP 성능 저하 | High | Medium | 배치 시간대 분리, 인덱스 튜닝, chunk 조정 | 배치 시간/DB 부하 모니터링 |
| R-004 | AI API 지연/장애로 요청 지연 전파 | High | Medium | 비동기 큐 분리, timeout/retry/fallback | 워커 실패율/지연 알림 |
| R-005 | 단일 DB 장애 시 서비스 중단 | High | Low | 백업/복구 리허설, read replica 검토 | DB health check, failover 알림 |
| R-006 | 잘못된 에러 표준으로 클라이언트 혼선 | Medium | Medium | 공통 에러 코드 규약 문서화/테스트 | 계약 테스트, API 리뷰 |
| R-007 | 관측성 부족으로 장애 원인 파악 지연 | High | Medium | Sentry + Prometheus + Grafana 기본 도입 | MTTR 추적, 알림 체계 |
| R-008 | 설계-구현 불일치 누적 | Medium | High | ADR/Spec 업데이트를 PR 체크리스트화 | PR 템플릿 문서 항목 |

## SPOF 식별

- Primary DB 단일 인스턴스
- Relay worker 단일 인스턴스
- LLM provider 단일 공급자

## 대응 전략

- DB: 백업 주기 + 복구 시나리오 검증
- Relay: 다중 워커 + `SKIP LOCKED`
- LLM: provider adapter 추상화 및 fallback 메시지

## 트리거 기반 대응

- Outbox lag p95 > 5s, 15분 지속: 워커 스케일/병목 점검
- 배치 시간 30분 초과, 2주 연속: Track B 검토
- API p95 > 300ms, 3일 연속: 쿼리/캐시/리소스 튜닝
