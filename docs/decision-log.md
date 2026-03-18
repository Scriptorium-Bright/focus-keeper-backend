# Decision Evolution Log

> 목적: 대화 원문이 아니라, 설계 판단이 어떻게 바뀌었는지 추적하기 위한 기록

## 기록 원칙

- 각 항목은 `이전 생각 -> 변경된 생각 -> 근거` 순서로 남긴다.
- 같은 주제의 최종 확정은 ADR에 기록하고, 이 문서는 변화 과정을 보존한다.
- 구현 영향이 있는 변경은 관련 문서/코드 링크를 함께 남긴다.

## 2026-03-03 의사결정 변화

### 1) 리부트 운영 방식

- 이전 생각: 기존 코드베이스 위에서 점진 리팩토링
- 변경된 생각: `main`을 초기화하고 Phase 1부터 재구축
- 근거:
  - 기존 구조 부채와 실험 코드가 커서 기준선이 불명확
  - 설계 기준을 먼저 고정한 뒤 재구현하는 편이 추적성/설명력이 높음
- 구현 영향:
  - 아카이브 브랜치 보존 후 리부트 진행
  - 현재 개발 기준: `docs/newPlan.md`

### 2) 브랜치 전략

- 이전 생각: 단계별 큰 브랜치 사용
- 변경된 생각: GitHub Flow + 기능 단위 `feature/*` 브랜치
- 근거:
  - PR 크기 축소
  - 리뷰/롤백 단순화
  - N.x 단위 테스트/커밋 규칙과 호환
- 구현 영향:
  - `docs/refactor.md`에 N.x 테스트/커밋 로그 정책 반영

### 3) 메시징/이벤트 아키텍처

- 이전 생각: Kafka/Outbox를 초기부터 기본 적용할지 고민
- 변경된 생각: 도메인 중요도 기반 단계적 진화 전략 채택
- 근거:
  - 모든 이벤트 중요도가 동일하지 않음
  - 운영 복잡도와 정합성 요구를 분리해서 판단해야 함
- 최종 정책:
  - Stage 0: 메시징 미도입, 단순 비동기/배치
  - Stage 1: Transactional Outbox + Relay
  - Stage 2: Outbox + Message Broker
- 구현 영향:
  - `docs/spec/ENGINEERING_SPEC.md`의 Stage/임계치 기준 반영
  - `docs/adr/ADR-0002-event-criticality-strategy.md` 추가

### 4) 출시 범위

- 이전 생각: Phase 16 포함 여부 논의
- 변경된 생각: Phase 15 완료 시점 출시, 이후 확장
- 근거:
  - 초기 출시 속도와 운영 안정성 우선
  - Growth/MCP는 출시 후 데이터로 판단
- 구현 영향:
  - `docs/newPlan.md`와 관련 스펙에 출시 범위 반영

## 2026-03-08 의사결정 변화

### 5) 단기 실행 우선순위

- 이전 생각: Phase 3부터 순차 진행 여부를 열어둠
- 변경된 생각: 단기 스프린트에서는 Phase 4/5 구현과 검증을 우선
- 근거:
  - 채용 마감(`2026-03-23`) 전까지 설명 가능한 구현/검증 결과 확보 필요
  - 복귀 루프 핵심 기능의 완성도가 포트폴리오 설득력에 직접 연결됨
- 구현 영향:
  - `docs/newPlan.md`에 2026-03-09~2026-03-23 실행 스프린트 추가
  - `docs/spec/ENGINEERING_SPEC.md`에 단기 실행 계획 추가

### 6) Kafka/Outbox 도입 판정 방식

- 이전 생각: Kafka 도입 시점을 구현 체감으로 판단
- 변경된 생각: 테스트/관측 수치 기반 트리거로 판정
- 근거:
  - 과도한 조기 도입은 복잡도만 증가시키고 기본 경로 안정성을 해칠 수 있음
  - 채용 관점에서도 "도입 이유와 근거 수치"가 명확할수록 설명력이 높음
- 구현 영향:
  - `docs/spec/KPI_TRACKS.md`에 Trigger Evidence KPI 추가
  - `docs/spec/ENGINEERING_SPEC.md`에 트리거 판정 증빙 방식 추가

### 7) Airflow 적용 경계

- 이전 생각: Airflow를 어디에 적용할지 불명확
- 변경된 생각: Airflow는 배치 오케스트레이션에만 적용하고 실시간 API 경로는 제외
- 근거:
  - 실시간 API는 지연/장애 전파 위험을 줄이기 위해 앱 서비스 경로에 집중
  - 배치 집계/재처리/품질검증은 DAG 오케스트레이션 이점이 큼
- 구현 영향:
  - `docs/newPlan.md`에 Airflow 정책 및 대상 DAG 명시
  - `docs/spec/BATCH_RUNBOOK.md`에 DAG별 운영 규칙 반영

## 2026-03-12 의사결정 변화

### 8) 복귀 KPI 정의 재정의 (48시간 단일 -> 24/48 팩)

- 이전 생각: `Recovery48` 중심 단일 지표로 복귀 성과를 판단
- 변경된 생각: `Recovery24`를 메인 KPI로, `Recovery48`은 보조 KPI로 운영
- 근거:
  - 사용자 행동 해석에서 24시간 내 복귀가 실제 실행 복구력을 더 잘 반영
  - 48시간 지표는 롱테일 회수율 확인에는 유용하나 단독 KPI로는 해석이 거칠 수 있음
- 구현 영향:
  - `docs/spec/RECOVERY_METRICS.md` 신규 추가
  - `docs/newPlan.md`, `docs/spec/KPI_TRACKS.md`, `docs/spec/ENGINEERING_SPEC.md` 지표 정의 동기화

### 9) 재시작 횟수/사이클 품질 지표 동시 운영

- 이전 생각: 복귀 여부 중심으로 판단
- 변경된 생각: 복귀 여부 + 복귀 강도 + 실행 품질을 함께 본다
- 근거:
  - 재시작 횟수 단독 증가는 실질 집중 품질 개선을 보장하지 않음
  - `CycleCompletionRate`, `EffectiveFocusMinutes`를 함께 추적해야 왜곡 감소
- 구현 영향:
  - `RestartCount24/48`, `CycleCompletionRate`를 공통 지표 팩에 반영
  - ETL 파이프라인 변환/집계 항목 업데이트

### 10) 실행 방법론 명시 (5단계 실행 알고리즘)

- 이전 생각: 실행 원칙은 산발적으로 문서에 분산
- 변경된 생각: 공통 실행 알고리즘(의문 제기 -> 삭제 -> 최적화 -> 가속 -> 자동화)을 명시
- 근거:
  - 지표/요구사항 변경 시 우선순위 판단과 실행 순서를 빠르게 통일하기 위함
- 구현 영향:
  - `docs/newPlan.md`, `docs/PRODUCT_INTENT.md`, `docs/spec/ENGINEERING_SPEC.md`, `career/GLOBAL_EXPANSION_PLAYBOOK.md`에 실행 원칙 반영

## 2026-03-14 의사결정 변화

### 11) 초기 ICP 재정의 (혼합 세그먼트 -> 단일 직장인 세그먼트)

- 이전 생각: 25~34세 직장인/취준생을 함께 다루고, 후속 확장으로 ADHD/B2B까지 문서에 병행 표기
- 변경된 생각: "전날 계획이 무너지면 다음날 첫 집중 블록을 다시 못 잡는 25~34세 지식노동 직장인" 단일 세그먼트로 고정
- 근거:
  - 세그먼트가 섞이면 메시지, 온보딩, KPI 해석이 동시에 흐려짐
  - 이 제품의 가장 선명한 문제 정의는 "실패 다음날 복귀"라는 순간에 있음
  - 범용 생산성 앱 포지셔닝을 피해야 기능 우선순위와 마케팅 문구가 함께 단순해짐
- 구현 영향:
  - `docs/PRODUCT_INTENT.md`, `docs/business-plan.md`, `docs/sales-onepager.md`, `docs/investor-onepager.md`, `docs/newPlan.md`, `docs/spec/ENGINEERING_SPEC.md`의 ICP/메시지 통일
  - `README.md`, `docs/README.md`, `docs/stack.md`, `api/openapi.yaml`, `src/main/resources/application.yml`의 브랜드 표기 통일

### 12) 타이머 포지셔닝 재정의 (핵심 가치 -> 보조 인터랙션)

- 이전 생각: 집중 세션/타이머가 제품 정체성으로 읽힐 여지를 남김
- 변경된 생각: 뽀모도로/집중 타이머는 복귀 행동을 실행시키는 보조 인터랙션으로만 다룬다
- 근거:
  - 타이머를 전면에 세우면 다시 범용 생산성/포모도로 카테고리로 읽히기 쉬움
  - 이 제품의 핵심 가치는 타이머 자체가 아니라 실패 다음날 복귀를 설계하는 데 있음
- 구현 영향:
  - `docs/PRODUCT_INTENT.md`, `docs/newPlan.md`, `docs/spec/ENGINEERING_SPEC.md`, `docs/business-plan.md`, `docs/sales-onepager.md`, `docs/investor-onepager.md`에 타이머 역할 명시

### 13) Phase 의미 재정의 (범용 생산성 흐름 -> 실패 다음날 복귀 흐름)

- 이전 생각: Phase 이름과 설명이 Identity, Growth, Burnout, Social 등 범용 생산성 제품의 로드맵처럼 읽힘
- 변경된 생각: Phase 3/5/7/10/12/13을 "실패 다음날 복귀" 문제에 맞게 재정의
- 근거:
  - Phase 설명이 흐리면 구현 우선순위와 포트폴리오 메시지가 다시 넓어짐
  - 단일 ICP 기준에서는 다음날 복귀 온보딩, 복귀 시작, 오전 리마인더, 단일 ICP 성장, 복귀 실패 패턴 분석이 더 직접적임
- 구현 영향:
  - `docs/newPlan.md`, `docs/spec/FEATURE_PROCESS_SPEC.md`, `docs/spec/WEEKLY_EXECUTION_CHECKLIST.md`, `docs/spec/KEY_FLOWS.md`의 phase/기능/DoD 재정렬

### 14) 레거시 도메인 스펙 정리 (challenge/wallet -> recovery domain)

- 이전 생각: 일부 시스템 스펙에 챌린지/지갑 예시가 남아 있어도 우선순위가 낮다고 판단
- 변경된 생각: 복귀 코치 방향으로 고정한 이상, 시스템/데이터 문서도 recovery domain 기준으로 즉시 정리
- 근거:
  - 레거시 예시는 포트폴리오 설명력과 설계 신뢰도를 직접 떨어뜨림
  - 데이터 모델, 품질, 배치 문서가 현재 제품 메시지와 같은 언어를 써야 함
- 구현 영향:
  - `docs/spec/ENGINEERING_SPEC.md`, `docs/spec/DATA_MODEL.md`, `docs/spec/DATA_QUALITY.md`, `docs/spec/BATCH_RUNBOOK.md`, `docs/spec/DATA_PIPELINE_ETL_BLUEPRINT.md`, `docs/adr/*.md`의 예시/용어 정리

## 추적 체크리스트

- [ ] 새로운 아키텍처 선택 시 이 문서에 변화 과정 먼저 기록
- [ ] 확정 결정은 ADR로 승격
- [ ] Phase 종료 시 `docs/refactor.md`의 High/Mid/Low 업데이트
- [ ] 블로그로 남길 가치가 있는 의사결정은 `docs/BLOG_DECISION_PLAYBOOK.md` 기준으로 후보화
