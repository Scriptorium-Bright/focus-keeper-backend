# API Contract

> Version: v0.2  
> Updated: 2026-03-12  
> Scope: 공통 응답/에러/버전 정책 + 현재 구현 엔드포인트 기준

## 1. API 스타일

- 기본 프로토콜: REST over HTTPS
- URL 버전: `/api/v1/...`
- Content-Type: `application/json; charset=utf-8`
- 시간 포맷: ISO-8601 (UTC offset 포함)

## 2. 공통 응답 규약 (Phase 2 적용 목표)

```json
{
  "success": true,
  "data": {},
  "message": "OK",
  "traceId": "2f4a4a5e8e0f..."
}
```

- `success`: 처리 성공 여부
- `data`: 실제 응답 페이로드
- `message`: 사용자/개발자 메시지
- `traceId`: 추적 식별자 (로그 연계)

## 3. 공통 에러 응답 규약 (Phase 2 적용 목표)

```json
{
  "success": false,
  "error": {
    "code": "COMMON-400",
    "message": "잘못된 요청입니다.",
    "details": null
  },
  "traceId": "2f4a4a5e8e0f..."
}
```

## 4. 에러 코드 체계

- 포맷: `{DOMAIN}-{HTTP}`
- 예시:
  - `COMMON-400` 잘못된 요청
  - `AUTH-401` 인증 실패
  - `AUTH-403` 인가 실패
  - `RESOURCE-404` 리소스 없음
  - `CONFLICT-409` 상태 충돌/중복 요청
  - `SYSTEM-500` 내부 오류

## 5. 상태 코드 정책

- `200 OK`: 조회/정상 처리
- `201 Created`: 생성 성공
- `204 No Content`: 삭제/빈 응답 성공
- `400 Bad Request`: 검증 실패
- `401 Unauthorized`: 인증 누락/실패
- `403 Forbidden`: 권한 부족
- `404 Not Found`: 리소스 없음
- `409 Conflict`: 멱등 충돌/중복 처리
- `429 Too Many Requests`: Rate limit 초과
- `500 Internal Server Error`: 서버 오류

## 6. 버전 정책

- Major 변경은 `/api/v2`로 신규 제공
- 하위 호환 가능한 필드 추가는 `v1` 유지
- 필드 제거/의미 변경은 deprecation 기간 후 제거

## 7. 현재 구현 API

- `GET /api/v1/health`
  - 설명: 앱 기본 헬스체크
  - 현재 응답: 상태/서비스명/프로파일/타임스탬프 JSON
- `GET /actuator/health`
  - 설명: Spring Actuator 헬스체크
  - 현재 응답: 컴포넌트별 상태 JSON
- `POST /api/v1/recovery/inbox-items`
  - 설명: Brain Dump 항목 등록(F-001)
  - 제약: `items` 1~20개, `content` 최대 200자
  - 성공 메시지: `INBOX_ITEMS_SAVED`
- `POST /api/v1/recovery/big3`
  - 설명: 오늘 Big3 선택(F-002)
  - 제약: `itemIds` 1~3개, 중복 itemId 금지
  - 에러: `itemIds` 3개 초과 시 `COMMON-400`, inbox 미존재 항목 시 `RESOURCE-404`
  - 성공 메시지: `BIG3_SELECTED`
- `POST /api/v1/recovery/timeboxes`
  - 설명: Big3 기반 첫 복귀 블록 포함 타임박스 배정(F-003)
  - 제약: `timeboxes` 1~3개, 첫 복귀 블록은 정확히 1개, 오늘 Big3 항목만 배정 가능
  - 에러: 첫 복귀 블록 규칙/시간 형식 오류 시 `COMMON-400`, 겹치는 블록 시 `CONFLICT-409`, Big3 미선택 시 `RESOURCE-404`
  - 성공 메시지: `TIMEBOXES_ALLOCATED`
- `POST /api/v1/recovery/sessions/start`
  - 설명: 복귀 세션 시작(F-004)
  - 제약: `userId`, `timeboxId` 필수, `BREAK` timebox는 시작할 수 없음
  - 성공 메시지: `RECOVERY_SESSION_STARTED`
- `POST /api/v1/recovery/sessions/complete`
  - 설명: 복귀 세션 완료(F-004)
  - 제약: `userId`, `sessionId` 필수
  - 성공 메시지: `RECOVERY_SESSION_COMPLETED`
- `POST /api/v1/recovery/sessions/interrupt`
  - 설명: 복귀 세션 중단(F-004)
  - 제약: `userId`, `sessionId` 필수
  - 성공 메시지: `RECOVERY_SESSION_INTERRUPTED`
- `POST /api/v1/recovery/failures/check-in`
  - 설명: 실패 체크인(F-005)
  - 제약: `sessionId`, `reason` 필수, `note` 최대 200자
  - 성공 메시지: `FAILURE_CHECKED_IN`
- `POST /api/v1/recovery/restarts`
  - 설명: 실패 직후 10분 복귀 재시작 실행(F-006)
  - 제약: `failureEventId` 필수
  - 성공 메시지: `RECOVERY_RESTARTED`
- `POST /api/v1/recovery/retrospectives/weekly`
  - 설명: 규칙 기반 주간 회고 생성(F-007)
  - 제약: `weekStart`는 `yyyy-MM-dd`
  - 성공 메시지: `WEEKLY_RETROSPECTIVE_GENERATED`
- `GET /api/v1/recovery/retrospectives/weekly`
  - 설명: 생성된 주간 회고 조회(F-008, F-009 포함)
  - 제약: `userId`, `weekStart` 필수
  - 성공 메시지: `WEEKLY_RETROSPECTIVE_FETCHED`
- `POST /api/v1/recovery/analytics/kpis/daily`
  - 설명: 일간 KPI mart 생성/갱신(F-022)
  - 제약: `metricDate`는 `yyyy-MM-dd`
  - 성공 메시지: `DAILY_KPI_GENERATED`
- `GET /api/v1/recovery/analytics/kpis/daily`
  - 설명: 일간 KPI mart 조회(F-022)
  - 제약: `userId`, `metricDate` 필수
  - 성공 메시지: `DAILY_KPI_FETCHED`
- `GET /api/v1/recovery/analytics/kpis/daily/quality`
  - 설명: 일간 KPI 데이터 품질 리포트 조회(T-022-5)
  - 제약: `userId`, `metricDate` 필수
  - 성공 메시지: `DAILY_KPI_QUALITY_FETCHED`
- `POST /api/v1/recovery/analytics/kpis/daily/backfill`
  - 설명: 일간 KPI 기간 백필 실행(T-022-4)
  - 제약: `startDate`, `endDate`는 `yyyy-MM-dd`
  - 성공 메시지: `DAILY_KPI_BACKFILL_COMPLETED`
- `GET /api/v1/recovery/analytics/kpis/daily/watermark`
  - 설명: 일간 KPI 파이프라인 워터마크 조회(T-022-4)
  - 제약: `userId` 필수
  - 성공 메시지: `DAILY_KPI_WATERMARK_FETCHED`
- `POST /api/v1/recovery/analytics/failure-hours`
  - 설명: 시간대별 실패 분포와 `PeakFailureHour` 생성(F-027 / 13.1)
  - 제약: `metricDate`는 `yyyy-MM-dd`
  - 성공 메시지: `FAILURE_HOUR_DISTRIBUTION_GENERATED`
- `GET /api/v1/recovery/analytics/failure-hours`
  - 설명: 생성된 시간대별 실패 분포 조회(F-027 / 13.1)
  - 제약: `userId`, `metricDate` 필수
  - 성공 메시지: `FAILURE_HOUR_DISTRIBUTION_FETCHED`
- `POST /api/v1/recovery/analytics/friction-signals`
  - 설명: 반복 실패 / 지연 재시작 signal table 생성(F-027 / 13.2)
  - 제약: `metricDate`는 `yyyy-MM-dd`
  - 성공 메시지: `FRICTION_SIGNALS_GENERATED`
- `GET /api/v1/recovery/analytics/friction-signals`
  - 설명: 생성된 friction signal 조회(F-027 / 13.2)
  - 제약: `userId`, `metricDate` 필수
  - 성공 메시지: `FRICTION_SIGNALS_FETCHED`
- `GET /api/v1/recovery/analytics/friction-segments`
  - 설명: failure-hour report와 friction signal을 조합한 최소 세그먼트 조회(F-028 / 13.3)
  - 제약: `userId`, `metricDate` 필수, failure-hour/signal 선행 생성 필요
  - 성공 메시지: `FRICTION_SEGMENTS_FETCHED`

상세 스키마는 `api/openapi.yaml`을 기준으로 한다.
