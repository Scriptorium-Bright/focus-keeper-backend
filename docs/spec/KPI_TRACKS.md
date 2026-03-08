# KPI Tracks (Career vs Venture)

> Version: v0.1  
> Updated: 2026-03-03  
> Scope: 취업 우선(80) + 창업 검증(20) 운영 기준

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
| Activation(24h) | >= 60% | 온보딩 효율 |
| Recovery48 | >= 25% | 핵심 가치(복귀) 검증 |
| TTR | 지속 하락 추세 | 복귀 속도 개선 |
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
- Recovery48, TTR 개선 추세, Paid Intent 목표 충족 시에만 확장 기능 투자

## 6. 리포팅 주기

- Career KPI: 주 1회
- Venture KPI: 베타 기간 중 주 2회
- Phase 종료 시: `docs/refactor.md`에 High/Mid/Low와 함께 요약
