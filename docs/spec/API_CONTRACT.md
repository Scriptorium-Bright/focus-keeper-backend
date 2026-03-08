# API Contract

> Version: v0.1  
> Updated: 2026-03-03  
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

## 7. 현재 구현 API (Phase 1)

- `GET /api/v1/health`
  - 설명: 앱 기본 헬스체크
  - 현재 응답: 상태/서비스명/프로파일/타임스탬프 JSON
- `GET /actuator/health`
  - 설명: Spring Actuator 헬스체크
  - 현재 응답: 컴포넌트별 상태 JSON

상세 스키마는 `api/openapi.yaml`을 기준으로 한다.
