# KPI Tracks (Career vs Venture)

> Version: v0.3  
> Updated: 2026-03-12  
> Scope: 취업 우선(80) + 창업 검증(20) + 복귀 지표 팩 + 기술 도입 트리거 운영 기준

## 1. 목적

- KPI를 한 바구니에 넣지 않고, 목표별로 분리 운영한다.
- 취업 경쟁력 지표와 시장성 검증 지표가 충돌할 때 우선순위를 명확히 한다.

## 2. Track A: Career KPI (우선)

| KPI | 초기 목표 | 의미 |
|---|---|---|
| 테스트 통과율 | 100% (main 기준) | 안정성/기본 품질 |
| 핵심 플로우 E2E | 복귀 루프 100% 자동화 | 도메인 설명력 |
| CI 안정성 | 최근 14일 성공률 >= 95% | 협업 가능성 |
| API 계약 일치율 | 계약 위반 0건 | 백엔드 표준 준수 |
| ADR/Spec 최신성 | 주요 결정 후 24시간 내 반영 | 의사결정 추적성 |
| 관측성 커버리지 | 에러/지연/배치 지표 수집 100% | 운영 역량 |

## 3. Track B: Venture KPI (검증)

| KPI | 초기 가설 | 의미 |
|---|---|---|
| Activation(24h) | >= 60% | 가입 첫날 첫 복귀 블록 설정/시작 효율 |
| Recovery24 | >= 20% | 핵심 가치(빠른 복귀) 메인 지표 |
| Recovery48 | >= 25% | 롱테일 복귀 회수율 보조 지표 |
| RestartCount24/48 | 상승 추세 | 복귀 강도 및 반복 실행성 |
| TTR | 지속 하락 추세 | 복귀 속도 개선 |
| CycleCompletionRate | >= 65% | 재시작 이후 실제 실행 품질 |
| EffectiveFocusMinutes | 지속 상승 추세 | 유효 집중 시간 확보 |
| PlanExecutionRate | >= 60% | 계획한 타임박스 실행력 |
| EstimationError | 지속 하락 추세 | 시간 예측 정확도 개선 |
| D7 Retention | >= 25% | 초기 유지력 |
| D14 Retention | >= 35% | 중기 유지력 가설 |
| Paid Intent | >= 20% | 과금 가능성 |

## 4. 우선순위 규칙

1. Career KPI 미달 시 신규 기능 개발보다 품질/운영 개선 우선
2. Venture KPI 미달 시 기능 확장보다 ICP/메시지/온보딩 수정 우선
3. 두 트랙 충돌 시 Career Track을 우선한다

## 5. 판단 게이트

- Go to Phase+:
- Career KPI의 필수 항목(테스트, CI, 계약 일치, 관측성)을 통과해야 다음 핵심 Phase 진행

- Go to Venture Scale:
- Recovery24(메인), Recovery48(보조), TTR/CycleCompletionRate 개선 추세, Paid Intent 목표 충족 시에만 확장 기능 투자

## 6. 리포팅 주기

- Career KPI: 주 1회
- Venture KPI: 베타 기간 중 주 2회
- Phase 종료 시: `docs/refactor.md`에 High/Mid/Low와 함께 요약

## 7. Trigger Evidence KPI (Kafka/Outbox 판정용)

| KPI | 임계치 | 의미 |
|---|---|---|
| AsyncFailureRate(7d) | > 0.1% | 비동기 처리 안정성 한계 |
| ManualRecoveryTimeAvg | > 30분 | 운영 복구 비용 증가 |
| RelayReprocessCount(month) | >= 5회 | 재처리 운영 부담 증가 |
| EventConsumers | >= 2 | 브로커 기반 분리 필요성 |

운영 규칙:
- 트리거 KPI는 Grafana에서 7일 추세로 확인한다.
- 임계치 초과 시 `lab/kafka-adapter` 검증 결과와 함께 도입 여부를 결정한다.
- 단발성 스파이크만으로 기본 경로를 변경하지 않는다.

## 8. Recovery KPI 운영 규칙

- `Recovery24`를 메인 KPI로 사용한다.
- `Recovery48`은 롱테일 회수율 확인용 보조 KPI로 사용한다.
- `RestartCount24/48`은 `CycleCompletionRate`, `EffectiveFocusMinutes`와 함께 해석한다.
- 상세 수식/정의는 `docs/spec/RECOVERY_METRICS.md`를 단일 기준으로 사용한다.

## 9. Diagnostic Metrics (보조 진단)

- `FailureCountByHour`, `FailureRatioByHour`, `PeakFailureHour`는 메인 KPI가 아니라 복귀 마찰 진단용 지표다.
- 이 지표는 아래 목적에 사용한다.
  - 어떤 시간대에 실패가 몰리는지 파악
  - 리마인더/복귀 개입 시간대 가설 수립
  - 세그먼트별 실패 패턴 비교
- 이 지표만으로 제품이 좋아졌다고 판단하지 않는다.
- 반드시 `Recovery24`, `TTR`, `CycleCompletionRate`와 함께 본다.
