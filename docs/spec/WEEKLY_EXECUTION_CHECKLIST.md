# Weekly Execution Checklist

> Version: v0.5  
> Updated: 2026-03-16  
> Scope: 기능 상태(`planned -> in_progress -> done`) 전환을 위한 주간 실행 체크리스트

## 1. 상태 전환 규칙

### 1.1 `planned -> in_progress`

아래 4개가 충족되면 `in_progress`로 전환:
- [ ] 기능 범위(입력/출력/예외) 확정
- [ ] API/도메인 수용 기준(DoD) 문서화
- [ ] 테스트 케이스 초안 작성
- [ ] KPI/로그 포인트 지정

### 1.2 `in_progress -> done`

아래 6개가 충족되면 `done`으로 전환:
- [ ] 기능 구현 완료
- [ ] 정상/예외 테스트 통과
- [ ] 회귀 테스트 통과
- [ ] KPI 수집 정상 확인
- [ ] 관련 문서 동기화
- [ ] 리팩터 로그 반영

### 1.3 `phase_exit_review`

아래 5개가 충족되면 다음 Phase 착수 가능:
- [ ] `docs/refactor.md`에 High / Mid / Low 작성
- [ ] 포트폴리오 사례 후보와 `문제 -> 해결 -> 결과` 초안 작성
- [ ] 다음 Phase를 막는 `High` 항목 유무 판정
- [ ] 시각 자료 후보 1개 이상 지정
- [ ] `docs/PORTFOLIO_CASEBOARD.md` 상태 업데이트

## 2. 이번 주 실행판 (2026-03-12 ~ 2026-03-18)

목표:
- Phase 4/5 핵심 복귀 기능(F-003~F-006)을 `planned`에서 `in_progress` 이상으로 전환
- 최소 2개 기능은 `done`까지 도달
- 데이터 엔지니어 채용 관점 P0 증거(원천 이벤트, 증분 배치, KPI mart, 재처리 경로) 정의를 완료
- 이번 주 작업에서 포트폴리오 사례 카드 1개와 시퀀스/데이터 플로우 1개 초안을 남김

### 2.1 기능별 상태 보드

| ID | 기능 | 현재 상태 | 목표 상태 | 우선순위 | 비고 |
|---|---|---|---|---|---|
| F-001 | Brain Dump 등록 | done | done | P0 | 2026-03-12 API+통합테스트 완료 |
| F-002 | Big3 선택 | done | done | P0 | 2026-03-12 API+통합테스트 완료 |
| F-003 | 첫 복귀 블록 포함 Timebox 배정 | done | done | P0 | 2026-03-16 API+통합테스트+충돌 검증 완료 |
| F-004 | 복귀 세션 시작/완료/중단 | done | done | P0 | 2026-03-16 상태 전이 API+통합테스트 완료 |
| F-005 | 실패 체크인 | done | done | P0 | 2026-03-16 failure reason 검증+통합테스트 완료 |
| F-006 | 10분 복귀 재시작 | done | done | P0 | 제안/실행 이벤트 기록 포함 |

### 2.2 일자별 체크리스트

#### Day 1-2
- [x] F-001 API 계약 및 요청/응답 스키마 확정
- [x] F-002 API 계약 및 요청/응답 스키마 확정
- [x] F-001 테스트 케이스 작성(통합 테스트)
- [x] F-002 테스트 케이스 작성
- [x] Big3 제한(최대 3개) 예외 케이스 정의

#### Day 3-4
- [x] F-001 구현 완료
- [x] F-002 구현 완료
- [x] F-001 통합 테스트 통과
- [x] F-002 통합 테스트 통과
- [x] F-003 Timebox 충돌 정책(409) 구현 착수
- [x] T-003-1 요청/응답 스키마 초안 작성
- [x] T-003-2 충돌 검증 테스트 초안 작성

#### Day 5-6
- [ ] F-004/F-005 복귀 이벤트 스키마 정의
- [ ] 실패 체크인 -> 10분 복귀 재시작 연결 규칙 확정
- [ ] Recovery Metric Pack 수집 포인트 연결
- [ ] `C-01` 사례 카드 초안 작성 (`문제 -> 해결 -> 결과`)
- [ ] T-004-1 세션 상태 전이 표 작성
- [ ] T-005-1 failure reason taxonomy 확정

#### Day 6-7
- [ ] `daily_kpi_pipeline` 입력/출력 스키마 확정
- [ ] 워터마크/멱등 upsert/기간 백필 정책 문서-코드 매핑
- [ ] KPI mart 초안(`Recovery24`, `RestartCount24`, `TTR`) 계산 경로 점검
- [ ] `C-01` 시퀀스 다이어그램 또는 상태 전이 도식 초안 작성
- [ ] T-006-1 재시작 제안 규칙 문서화
- [ ] T-006-4 Recovery24 입력 필드 점검

#### Day 7
- [ ] 주간 회고: 완료/미완료 원인 정리
- [ ] 다음 주 우선순위 재정렬
- [ ] `docs/refactor.md` 업데이트 항목 정리
- [ ] `docs/PORTFOLIO_CASEBOARD.md` 상태 업데이트
- [ ] `docs/PHASE_EXIT_PROTOCOL.md` 기준으로 멈춤 시점 점검

## 3. 테스트 체크포인트

- [x] Controller 테스트: 입력 검증, 상태코드, 응답 포맷
- [ ] Service 테스트: 비즈니스 규칙, 예외 처리
- [x] Integration 테스트: 계획 루프(Brain Dump -> Big3)
- [ ] Integration 테스트: 핵심 플로우(계획 -> 첫 복귀 블록 -> 실패 -> 재시작)
- [ ] Recovery KPI 테스트: Recovery24/48 계산 입력 이벤트 검증
- [ ] ETL 테스트: 워터마크 기반 증분 집계 + 멱등 upsert 검증
- [ ] Backfill 테스트: 동일 기간 재처리 후 결과 일관성 검증

## 4. 관측 체크포인트

- [ ] recovery_session/failure/restart/cycle 이벤트 로그 출력 확인
- [ ] Recovery24/48, RestartCount24/48 지표 수집 확인
- [ ] 오류율/지연시간 대시보드 수치 확인
- [ ] `batch_duration_seconds`, `batch_failed_runs_total`, `dq_duplicate_count` 수집 확인

## 5. 데이터 엔지니어 채용 체크리스트

### P0 Must-Have
- [ ] 원천 이벤트 테이블 정의 완료: `failure_events`, `restart_events`, `recovery_sessions`, `cycle_events`, `timeboxes`
- [ ] 증분 배치 1개 구현 계획 완료: `daily_kpi_pipeline`
- [ ] KPI mart 적재 경로 정의 완료: `Recovery24`, `Recovery48`, `RestartCount24/48`, `TTR`, `CycleCompletionRate`
- [ ] 워터마크 + 멱등 upsert + 기간 백필 전략 확정

### P1 Strong Signal
- [ ] DQ 체크 범위 정의: 중복/누락/timezone/late-arrival/enum
- [ ] 운영 메트릭/알림 정의: `batch_duration_seconds`, `batch_failed_runs_total`, `dq_duplicate_count`, `recovery24_ratio`
- [ ] 데이터 문제 해결 사례 후보 1건 선정
- [ ] 대시보드 또는 SQL 리포트 출력 포맷 결정

### P2 Bonus
- [ ] `weekly_retrospective_input` 배치 후속 작업 정의
- [ ] `recovery_friction_signals` 계산 요구사항 정리
- [ ] Outbox Stage 1 검증 조건 정리
- [ ] 코호트/실험군 리포트 산출 경로 정리

## 6. 포트폴리오 증빙 체크리스트

- [ ] 이번 주 작업 중 사례 카드로 남길 문제 1개를 선택했다.
- [ ] 문제 상황을 2~3줄로 적었다.
- [ ] 해결 방법을 2~3줄로 적었다.
- [ ] 결과/검증을 2~3줄과 수치 1개 이상으로 적었다.
- [ ] 아키텍처/데이터 플로우/시퀀스 다이어그램 중 1개 이상 연결했다.
- [ ] 기능 설명 슬라이드가 아니라 문제 해결 흐름으로 정리했다.
- [ ] `docs/PORTFOLIO_CASEBOARD.md`에 사례 상태와 다음 액션을 반영했다.
- [ ] `docs/refactor.md`의 `Problem -> Solution -> Result Draft`와 내용이 일치한다.

## 7. 주간 결과 기록 템플릿

```text
[주차]
[완료 기능]
[미완료 기능]
[막힌 원인]
[다음 주 우선순위]
[테스트 결과 요약]
[KPI/관측 요약]
[포트폴리오 사례 후보]
[문제]
[해결]
[결과]
```

## 8. 연계 문서

- `docs/spec/FEATURE_PROCESS_SPEC.md`
- `docs/spec/KEY_FLOWS.md`
- `docs/spec/RECOVERY_METRICS.md`
- `docs/PHASE_EXIT_PROTOCOL.md`
- `docs/PORTFOLIO_CASEBOARD.md`
- `docs/PORTFOLIO_PLAYBOOK.md`
