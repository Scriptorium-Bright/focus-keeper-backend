# FocusKeeper 기술 스택 정리

> Updated: 2026-03-03  
> 기준: 현재 구현 + 확정된 아키텍처 의사결정

## 1) 현재 확정 스택 (Phase 1~2)

- Language: `Java 21`
- Framework: `Spring Boot 3.3.8`
- Build: `Gradle 8.13 (Wrapper)`
- API: `REST (/api/v1)`
- Validation: `spring-boot-starter-validation`
- Observability Base: `Spring Actuator`
- Test: `JUnit 5`, `Spring Boot Test`, `MockMvc`
- CI: `GitHub Actions` (`.github/workflows/ci.yml`)
- CD: `GitHub Actions` (`.github/workflows/cd.yml`, bootJar artifact/release)

## 2) 데이터/메시징/분석 전략 (확정)

### 데이터

- 기본 트랜잭션 저장소: `PostgreSQL` (도입 예정)
- 캐시/랭킹/저지연 조회: `Redis` (도입 예정)

### 메시징/이벤트

- `Stage 0 (현재 기본)`: 메시지 브로커 없이 내부 비동기 + 배치 재동기화
- `Stage 1 (조건부)`: `Transactional Outbox + Relay Worker`
- `Stage 2 (조건부)`: `Outbox + Message Broker (Kafka 등)`

### 분석

- 기본 경로(확정): `Spring Batch + RDB Native SQL` (Track A)
- 확장 경로(옵션): `Spark + Data Lake + Redis` (Track B)

## 3) Spark 관련 결론

- 현재 기본 스택에 `Spark`는 포함하지 않는다.
- 이유:
  - 현재 단계에서 운영 복잡도/비용 대비 이점이 낮음
  - 번아웃 분석은 `Batch + RDB`로 충분히 시작 가능
- Spark는 임계치 충족 시 선택 도입:
  - 고처리량, 다중 소비자, 대규모 재처리 요구가 명확할 때

## 4) 출시(Phase 15) 기준 스택

- 백엔드 코어: `Spring Boot + PostgreSQL + Redis`
- 운영 가시성: `Sentry + Actuator + Prometheus/Grafana`
- 분석: `Spring Batch`
- AI 코칭: `OpenAI 또는 Gemini API` (비동기 워커 경로)

## 5) 현재 미도입/보류

- `Kafka` (조건부 도입)
- `Transactional Outbox` (조건 충족 시 도입)
- `Spark` (옵션 Track B)
- `MCP` (출시 후 검토)

## 6) 다형성 확장 정책 (취업 우선 + 실험 병행)

- 메인 기본 경로:
  - `EventRelayPort` -> `DbEventRelayAdapter`
  - `AnalyticsEnginePort` -> `BatchSqlAnalyticsAdapter`
- 실험 경로(lab):
  - `EventRelayPort` -> `KafkaEventRelayAdapter`
  - `AnalyticsEnginePort` -> `SparkAnalyticsAdapter`

브랜치 운영:
- `main`/`feature/*`: 안정 경로만
- `lab/kafka-adapter`, `lab/spark-adapter`: 확장 실험

원칙:
- 기술 시연 목적의 인터페이스 남발 금지
- Port는 위 2개만 유지하고, 신규 Port는 트리거/근거 있을 때만 추가
