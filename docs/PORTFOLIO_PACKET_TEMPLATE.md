# RebootFocus Portfolio Packet Template

> Version: v0.1  
> Updated: 2026-03-16  
> Scope: 실제 제출용 포트폴리오/발표 자료를 구성할 때 바로 채워 넣는 템플릿

## 1. 한 페이지 프로젝트 요약 (`Project Overview`)

### 1.1 넣어야 하는 항목

- 프로젝트 한 줄 설명
- 해결하려는 문제
- 사용 기술
- 전체 아키텍처 요약
- 핵심 기능 3~4개

### 1.2 RebootFocus 초안

```text
RebootFocus
전날 계획이 무너지면 다음날까지 다시 못 붙잡는 직장인을 위해, 놓친 일을 다시 시작하게 만드는 복귀 코치 백엔드

문제
- 사용자가 전날 계획에 실패하면 다음날 첫 집중 블록을 다시 못 잡고 루프에서 이탈한다.
- 일반적인 생산성 앱은 "계획"은 돕지만 "실패 다음날 복귀"는 충분히 다루지 않는다.

Tech
- Spring Boot / Java / PostgreSQL / Spring Batch / Docker

Architecture
- Client -> Recovery API -> PostgreSQL -> Batch -> KPI Mart -> Dashboard

핵심 기능
- Brain Dump / Big3 / 첫 복귀 블록 배정
- 실패 체크인과 10분 복귀 재시작
- Recovery24 / TTR KPI 집계
- 복귀 마찰 신호와 운영 관측
```

## 2. 문제 정의 (`Problem`)

```text
생산성 앱을 여러 번 써본 직장인도 하루가 한 번 무너지면 다음날까지 끌려가는 경우가 많다.
문제는 계획 기능 부족이 아니라, 실패 직후와 다음날 오전에 다시 붙잡는 구조가 약하다는 점이다.
이 프로젝트는 "실패 다음날 복귀"를 별도 문제로 보고 제품과 데이터 구조를 설계했다.
```

## 3. 시스템 설계 (`Architecture`)

### 3.1 넣어야 하는 그림

- System Architecture
- Recovery Loop Sequence
- Data Flow / ETL Flow

### 3.2 설명 템플릿

```text
핵심 복귀 경로는 동기 API + RDB 트랜잭션으로 단순하게 유지했다.
복귀 KPI 계산, 재처리, DQ는 배치 경로로 분리해 사용자 요청 경로와 장애 반경을 분리했다.
AI 회고는 비동기 워커로 격리해 코어 경로를 오염시키지 않도록 설계했다.
```

## 4. 핵심 문제 해결 (`Core Challenges`)

### 4.1 사례 선택 규칙

- 2~4개만 넣는다.
- 기능이 아니라 문제 단위로 고른다.
- 각 사례는 `문제 -> 접근 -> 해결 -> 결과` 순서를 지킨다.

### 4.2 RebootFocus 권장 사례

#### 사례 A. 실패 다음날 복귀 루프 설계

```text
문제
- 전날 실패 후 다음날 첫 복귀 블록이 없으면 사용자가 다시 시작할 계기를 잃는다.

접근
- Big3, 첫 복귀 블록, 실패 체크인, 10분 재시작을 하나의 상태 전이 루프로 묶었다.

해결
- Timebox, Recovery Session, Failure Event, Restart Event를 분리된 도메인으로 설계했다.

결과
- API/통합 테스트로 상태 전이와 예외 규칙을 검증하고, Recovery24 입력 이벤트를 수집 가능하게 했다.
```

#### 사례 B. 복귀 KPI 신뢰성 확보

```text
문제
- 중복/누락/지각 데이터가 있으면 Recovery24와 TTR이 왜곡된다.

접근
- raw/clean/mart 계층과 워터마크, 멱등 upsert, 백필을 기준선으로 잡았다.

해결
- `daily_kpi_pipeline`에서 KPI mart와 DQ 체크를 함께 설계했다.

결과
- 재처리 가능한 집계 구조와 데이터 품질 검증 경로를 확보했다.
```

#### 사례 C. 운영 문제와 사용자 문제 분리

```text
문제
- 복귀 실패 패턴과 배치/AI 장애가 섞이면 원인 파악과 대응이 늦어진다.

접근
- API 경로, Batch 경로, AI 경로를 아키텍처/관측 수준에서 분리했다.

해결
- API/복귀 루프 대시보드와 Batch/DQ 대시보드를 별도로 두는 방향으로 설계했다.

결과
- 사용자 행동 문제와 시스템 운영 문제를 다른 메트릭으로 설명할 수 있게 했다.
```

## 5. 기술적 의사결정 (`Tech Decisions`)

### 5.1 넣어야 하는 질문

- 왜 `Spring Boot`를 썼는가
- 왜 `PostgreSQL` 중심으로 시작하는가
- 왜 `Kafka/Spark`를 기본값으로 두지 않았는가
- 왜 `Spring Batch + RDB`로 KPI 파이프라인을 먼저 구현하는가

### 5.2 RebootFocus 초안

```text
- 복귀 코어 경로는 강한 정합성과 빠른 구현/테스트가 중요해서 Spring Boot + RDB를 선택했다.
- KPI 집계는 데이터량보다 재처리 가능성과 운영 단순성이 더 중요해 Spring Batch + PostgreSQL을 우선 채택했다.
- Kafka/Spark는 확장 트리거가 발생할 때만 도입하도록 늦춰, 과도한 기술 복잡도를 피했다.
```

## 6. 성능 / 개선 (`Improvement`)

### 6.1 넣을 수 있는 항목

- Timebox 충돌 검증
- 세션 상태 전이 예외 처리
- 배치 워터마크/멱등 upsert
- DQ 체크와 알림
- 인덱스/쿼리 개선

### 6.2 작성 템플릿

```text
개선 전:
- ...

개선:
- ...

결과:
- ...
```

## 7. 배운 점 (`Lessons`)

### 7.1 RebootFocus 초안

```text
- 넓은 생산성 앱 메시지보다, "실패 다음날 복귀"처럼 한 문제를 선명하게 잡는 편이 설계와 마케팅 모두 쉬웠다.
- 복귀 KPI를 나중에 붙이려 하면 이벤트 설계가 흔들리기 때문에 도메인 모델 단계에서 같이 정의해야 한다.
- 포트폴리오는 기능 수보다 문제 해결 구조와 기술적 판단 근거가 더 중요하다는 점을 문서 구조를 바꾸면서 확인했다.
```

## 8. 연결 문서

- `docs/PORTFOLIO_PLAYBOOK.md`
- `docs/PORTFOLIO_CASEBOARD.md`
- `docs/ARCHITECTURE_OVERVIEW.md`
- `docs/spec/FEATURE_PROCESS_SPEC.md`
- `docs/spec/DATA_PIPELINE_ETL_BLUEPRINT.md`
