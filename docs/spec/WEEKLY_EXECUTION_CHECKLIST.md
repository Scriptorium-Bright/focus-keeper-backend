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

## 2. 이번 주 실행판 (2026-03-21 ~ 2026-03-27)

목표:
- Phase 11 기준선을 `11.1 / 11.4 / 11.5`로 잠그고 문서/계약을 동기화한다.
- Phase 13을 `13.1 ~ 13.x`로 분해하고, `13.1` 구현 준비까지 끝낸다.
- 데이터 엔지니어 채용 관점 다음 대표 사례를 `failure hour -> friction signal` 축으로 연결한다.
- 이번 주 작업에서 Phase 13 사례 카드 1개와 데이터 플로우 초안 1개를 남긴다.

### 2.1 기능별 상태 보드

| ID | 기능 | 현재 상태 | 목표 상태 | 우선순위 | 비고 |
|---|---|---|---|---|---|
| F-007 | 주간 회고 집계 생성 | done | done | P1 | 규칙 기반 회고 생성 API 완료 |
| F-008 | 주간 회고 조회 | done | done | P1 | 회고 조회 API 완료 |
| F-009 | anti-slip action 추천 | done | done | P1 | rule-based action 추천 완료 |
| F-022 | KPI 일간 mart 적재 | done | done | P0 | KPI mart + 백필 + DQ + k6 smoke 완료 |
| F-027 | 복귀 실패 패턴 신호 계산 | planned | in_progress | P1 | Phase 13 시작점 |
| F-028 | 복귀 마찰 세그먼트 리포트 | planned | planned | P1 | signal table 이후 |

### 2.2 일자별 체크리스트

#### Day 1
- [ ] Phase 11 잠금 기준을 `newPlan`, `FEATURE_PROCESS_SPEC`, `PHASE_EXIT_PROTOCOL`에 반영
- [ ] `API_CONTRACT`, `openapi.yaml`과 현재 구현 경로 일치 확인
- [ ] `C-02` 상태를 `draft` 이상으로 고정
- [ ] `11.2`, `11.3` 재오픈 조건을 체크리스트/스펙에 명시

#### Day 2
- [ ] Phase 13을 `13.1 ~ 13.x`로 세부 분해
- [ ] `13.1 failure hour / peak failure hour` 입력/출력/예외 정의
- [ ] `13.1` 테스트 케이스 초안 작성
- [ ] `13.1` 포트폴리오 문제 정의 초안 작성

#### Day 3-4
- [ ] `13.1` 구현 시작: 사용자 로컬 시간대 기준 실패 분포 집계
- [ ] `13.1` 구현 시작: `PeakFailureHour` 계산
- [ ] `13.1` 단위/통합 테스트 추가
- [ ] `13.1` 결과 조회 형식 확정

#### Day 5-6
- [ ] `13.2` 반복 실패/지연 재시작 signal 후보 정의
- [ ] signal table 초안 스키마 정의
- [ ] `13.2` 테스트 초안 작성
- [ ] `C-03` 사례 초안에 문제/해결 방향 기록

#### Day 7
- [ ] 주간 회고: 완료/미완료 원인 정리
- [ ] 다음 주 우선순위 재정렬
- [ ] `docs/refactor.md` 업데이트 항목 정리
- [ ] `docs/PORTFOLIO_CASEBOARD.md` 상태 업데이트
- [ ] `docs/PHASE_EXIT_PROTOCOL.md` 기준으로 멈춤 시점 점검

## 3. 테스트 체크포인트

- [x] Controller 테스트: 입력 검증, 상태코드, 응답 포맷
- [ ] Service 테스트: failure-hour, signal 계산 규칙
- [x] Integration 테스트: KPI mart / backfill / DQ
- [ ] Integration 테스트: Phase 13 failure-hour 집계 경로
- [x] Recovery KPI 테스트: Recovery24/48 계산 입력 이벤트 검증
- [x] ETL 테스트: 워터마크 기반 증분 집계 + 멱등 upsert 검증
- [x] Backfill 테스트: 동일 기간 재처리 후 결과 일관성 검증
- [ ] k6 또는 최소 성능 검증: Phase 13 집계 경로 smoke

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
- [x] `weekly_retrospective_input` 배치 후속 작업 정의
- [ ] `recovery_friction_signals` 계산 요구사항 정리
- [ ] Outbox Stage 1 검증 조건 정리
- [ ] 실제 로그 확보 후 코호트/실험군 리포트 재오픈 조건 정리

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
