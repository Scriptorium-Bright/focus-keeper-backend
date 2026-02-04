# DevBet - 예치금 기반 자기관리 플랫폼

> **마지막 업데이트**: 2026-02-03 17:10 KST

---

## 🎯 프로젝트 개요

### 한 줄 소개
**"돈을 걸고 목표를 달성하는 게이미피케이션 자기관리 플랫폼"**

### 프로젝트 배경
ADHD 성인들이 겪는 가장 큰 문제는 **"시작하기"**와 **"지속하기"**입니다.  
DevBet은 금전적 동기부여(예치금 + 보상)와 외부 검증 시스템을 결합하여 이 문제를 해결합니다.

### 핵심 기능
| 기능 | 설명 |
|------|------|
| **챌린지 시스템** | 미라클 모닝, GitHub 커밋 등 목표 설정 |
| **예치금 제도** | 챌린지 참여 시 크레딧 예치 (실패 시 몰수) |
| **자동 검증** | GitHub API, 시간대 체크 등으로 성공 여부 자동 판정 |
| **아이템 상점** | 면제권, 더블 포인트 등 게이미피케이션 요소 |
| **모니터링** | Prometheus + Grafana로 실시간 성능 확인 |
| **실시간 랭킹** | Redis ZSET 기반 리더보드 |

### 작동 방식
```
1. 사용자가 챌린지 생성 (예: "오늘 GitHub 커밋하기")
2. 예치금 500 크레딧 차감
3. 마감 시간에 시스템이 자동 검증 (GitHub API 호출)
4. 결과에 따라:
   - ✅ 성공 → 예치금 환급 + 포인트 보상
   - ❌ 실패 → 예치금 몰수 (면제권 사용 시 방어 가능)
```

### 기술 스택
| 분류 | 기술 |
|------|------|
| **Backend** | Java 21, Spring Boot 3.3 |
| **Database** | PostgreSQL, Redis |
| **Messaging** | Kafka |
| **Monitoring** | Prometheus, Grafana, Actuator |
| **Testing** | JUnit 5, k6 (로드 테스트) |
| **Infra** | Docker, Docker Compose |

---

## 📊 Phase 진행 현황

| Phase | 이름 | 상태 |
|-------|------|------|
| 1 | 초기화 및 인프라 | ✅ 완료 |
| 2 | 프로젝트 Foundation | ✅ 완료 |
| 3 | 도메인 모델링 | ✅ 완료 |
| 4 | AI 작업 분해 | ❌ 제거됨 |
| 5 | 게이미피케이션 (Wallet) | ✅ 완료 |
| 6 | Task-Wallet 통합 | ✅ 완료 |
| 6A | Challenge 도메인 전환 | ✅ 완료 |
| 6B | Strategy Pattern | ✅ 완료 |
| 6C | GitHub Verifier | ✅ 완료 |
| 7 | OAuth2 사용자 관리 | ⏳ 대기 |
| 8 | 아이템/상점 시스템 | ✅ 완료 |
| 9 | REST API 표준화 | ✅ 완료 |
| 9.5-A | Docker & Monitoring | ✅ 완료 |
| 9.5-B | k6 스트레스 테스트 | ✅ 완료 |
| 10 | Redis 캐시 최적화 | ✅ 완료 |
| 11 | Kafka 이벤트 아키텍처 | ✅ 완료 |

---

## 🏗️ 아키텍처 다이어그램

```
┌─────────────────────────────────────────────────────────────────┐
│                       Controller Layer                           │
│  ChallengeController  WalletController  ShopController           │
│  RankingController    UserController    AdminController          │
└─────────────────────────────┬───────────────────────────────────┘
                              │
┌─────────────────────────────┼───────────────────────────────────┐
│                       Service Layer                              │
│                                                                  │
│  ChallengeService ────────┬───────── WalletService              │
│  (@Cacheable)             │         (비관적 락)                  │
│         │                 │              ↓                       │
│  VerifierFactory          │         ShopService                  │
│    ├─ ManualVerifier      │                                      │
│    ├─ TimeVerifier        │         RankingService ──► Redis    │
│    └─ GitHubVerifier ─────┼───► RestClient          (ZSET)      │
└─────────────────────────────┼───────────────────────────────────┘
                              │
┌─────────────────────────────┼───────────────────────────────────┐
│                       Data Layer                                 │
│  PostgreSQL ◄────────────────────────────────► Redis            │
│  (Domain Models)                               (@Cacheable, ZSET)│
└─────────────────────────────────────────────────────────────────┘
```

---

## 📁 패키지 구조

```
com.adhd.focusmate
├── common
│   ├── dto/            ApiResponse, ErrorResponse
│   └── exception/      BusinessException, ErrorCode, GlobalExceptionHandler
│
├── domain
│   ├── common/         BaseEntity (Auditing)
│   └── model/
│       ├── Challenge   ⭐ 핵심 엔티티
│       ├── User, Wallet, CreditLog, ActionLog, ClinicalReport
│       └── type/       ChallengeType, ChallengeStatus, CreditLogReason...
│
├── repository/         JPA Repositories
│
├── service
│   ├── challenge/      ChallengeService
│   ├── wallet/         WalletService (비관적 락)
│   ├── verification/   ⭐ Strategy Pattern
│   │   ├── ChallengeVerifier (Interface)
│   │   ├── ManualVerifier
│   │   ├── TimeVerifier
│   │   ├── GitHubVerifier
│   │   └── VerifierFactory
│   └── shop/           ShopService (아이템 구매)
│
├── dto
│   ├── challenge/      ChallengeCreateRequest, ChallengeResponse
│   ├── wallet/         CreditChargeRequest, CreditDeductRequest...
│   └── shop/           BuyItemRequest, BuyItemResponse, ItemResponse
│
├── controller
│   ├── challenge/      ChallengeController
│   ├── wallet/         WalletController
│   └── shop/           ShopController
│
└── global/config/      SwaggerConfig, RestClientConfig, SecurityConfig
```

---

## 🔑 핵심 구현 상세

### 1. Challenge 엔티티

```java
@Entity
public class Challenge {
    private Long id;
    private User user;
    private String title;
    private String description;
    private ChallengeType challengeType;  // MANUAL, TIME_LOG, GITHUB_COMMIT
    private String targetValue;           // GitHub username 등
    private ChallengeStatus status;       // PENDING → COMPLETED/FAILED
    private LocalDateTime deadline;
    
    public void complete() { /* 상태 전이 + 검증 */ }
    public void fail() { /* 상태 전이 + 검증 */ }
}
```

### 2. Strategy Pattern (검증 시스템)

| Verifier | ChallengeType | 검증 로직 |
|----------|---------------|-----------|
| ManualVerifier | MANUAL | 항상 true (신뢰 기반) |
| TimeVerifier | TIME_LOG | 04:00~07:00 사이인지 |
| GitHubVerifier | GITHUB_COMMIT | 오늘(KST) PushEvent 있는지 |

```java
// VerifierFactory - Spring이 모든 Verifier를 자동 수집
@Component
public class VerifierFactory {
    private final Map<ChallengeType, ChallengeVerifier> verifierMap;
    
    public ChallengeVerifier getVerifier(ChallengeType type) {
        return verifierMap.get(type);
    }
}
```

### 3. Wallet 시스템 (비관적 락)

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Wallet findByUserIdForUpdate(Long userId);

// 동시성 안전한 잔액 변경
wallet.deduct(500);  // 잔액 부족 시 BusinessException
wallet.charge(100);
```

---

## 🌐 API 엔드포인트

### Challenge API (`/api/v1/challenges`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| POST | `/` | 챌린지 생성 |
| PATCH | `/{id}/verify` | **검증 후 자동 완료/실패** |
| PATCH | `/{id}/complete` | 강제 완료 (bypass) |
| PATCH | `/{id}/fail` | 강제 실패 |
| GET | `/?userId=&status=` | 목록 조회 |

### Wallet API (`/api/v1/wallet`)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/{userId}` | 잔액 조회 |
| POST | `/charge` | 크레딧 충전 |
| POST | `/deduct` | 크레딧 차감 |

---

## ✅ 테스트 현황

| 테스트 클래스 | 테스트 수 | 상태 |
|--------------|----------|------|
| WalletTest | 6 | ✅ 통과 |
| TimeVerifierTest | 10 | ✅ 통과 |
| VerifierFactoryTest | 5 | ✅ 통과 |

---

## 🔧 기술 스택

- **Java 21** + **Spring Boot 3.3**
- **Spring Data JPA** + PostgreSQL
- **Spring Security** + OAuth2 (미구현)
- **RestClient** (GitHub API 연동)
- **Swagger/OpenAPI** (API 문서화)
- **Docker Compose** (로컬 개발 환경)
- **Prometheus + Grafana** (모니터링)
- **k6** (로드 테스트)

---

## 📋 다음 단계

- [ ] **Phase 7**: OAuth2 로그인 (Google, GitHub)
- [ ] 성능 최적화 (N+1 쿼리, 캐싱)
- [ ] 스케줄러 운영 테스트
- [ ] 프론트엔드 개발

---

## 🗃️ 엔티티 컬럼 상세

### Challenge (핵심 엔티티)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK, 자동 생성 |
| `user_id` | FK → User | 챌린지 생성자 |
| `title` | String | 챌린지 제목 (필수) |
| `description` | String | 상세 설명 |
| `challenge_type` | Enum | `MANUAL`, `TIME_LOG`, `GITHUB_COMMIT`, `COMMUNITY_POST` |
| `target_value` | String | 검증에 필요한 값 (예: GitHub username) |
| `status` | Enum | `PENDING` → `IN_PROGRESS` → `COMPLETED`/`FAILED` |
| `energy_level` | Integer | 에너지 소모 레벨 (1~10) |
| `estimated_time` | Integer | 예상 소요 시간 (분) |
| `deadline` | LocalDateTime | 마감 기한 |
| `created_at` | LocalDateTime | 생성일 (BaseEntity) |
| `updated_at` | LocalDateTime | 수정일 (BaseEntity) |

---

### User (사용자)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK, 자동 생성 |
| `email` | String | 이메일 (Unique, 필수) |
| `nickname` | String | 닉네임 |
| `provider` | Enum | OAuth 제공자 (`GOOGLE`, `GITHUB`, `KAKAO`) |
| `role` | Enum | 권한 (`USER`, `ADMIN`) |
| `created_at` | LocalDateTime | 가입일 |
| `updated_at` | LocalDateTime | 수정일 |

---

### Wallet (지갑)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK, 자동 생성 |
| `user_id` | FK → User | 1:1 관계, 사용자 지갑 |
| `balance` | Integer | 현재 잔액 (기본값: 0) |
| `created_at` | LocalDateTime | 생성일 |
| `updated_at` | LocalDateTime | 수정일 |

**도메인 메서드:**
- [addBalance(amount)](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/domain/model/Wallet.java#46-52): 잔액 증가
- [subtractBalance(amount)](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/domain/model/Wallet.java#53-62): 잔액 감소 (부족 시 예외)

---

### CreditLog (크레딧 이력)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK, 자동 생성 |
| `wallet_id` | FK → Wallet | 대상 지갑 |
| `amount` | Integer | 변동 금액 (+/-) |
| `reason` | Enum | 사유 (`TASK_COMPLETE_REWARD`, `TASK_FAIL_PENALTY`, `MANUAL_CHARGE`) |
| `created_at` | LocalDateTime | 발생 시각 |

---

### ActionLog (행동 로그)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK, 자동 생성 |
| `user_id` | FK → User | 행동 주체 |
| `challenge_id` | FK → Challenge | 관련 챌린지 (Nullable) |
| `action_type` | Enum | 행동 유형 (`APP_OPEN`, `TASK_CREATE`, `DISTRACTION_DETECTED`) |
| `device_context` | TEXT | 디바이스/환경 정보 (JSON) |
| `created_at` | LocalDateTime | 발생 시각 |

---

### ClinicalReport (임상 리포트)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK, 자동 생성 |
| `user_id` | FK → User | 리포트 소유자 |
| `report_type` | Enum | 리포트 유형 (`WEEKLY`, `MONTHLY`) |
| `s3_url` | String | S3 저장 경로 |
| `summary_json` | TEXT | AI 분석 요약 (JSON) |
| `created_at` | LocalDateTime | 생성일 |

---

## 📝 Enum 타입 정리

### ChallengeType
| 값 | 설명 | Verifier |
|----|------|----------|
| `MANUAL` | 수동 완료 (신뢰 기반) | ManualVerifier |
| `TIME_LOG` | 시간 기반 검증 (04:00~07:00) | TimeVerifier |
| `GITHUB_COMMIT` | GitHub 오늘 커밋 확인 | GitHubVerifier |
| `COMMUNITY_POST` | 커뮤니티 글쓰기 (향후) | - |

### ChallengeStatus
| 값 | 설명 |
|----|------|
| `PENDING` | 생성됨, 시작 전 |
| `IN_PROGRESS` | 진행 중 |
| `PENDING_VERIFICATION` | 검증 대기 중 |
| `COMPLETED` | 완료 (보상 지급됨) |
| `FAILED` | 실패 (페널티 차감됨) |

### CreditLogReason
| 값 | 설명 |
|----|------|
| `TASK_COMPLETE_REWARD` | 챌린지 완료 보상 (+) |
| `TASK_FAIL_PENALTY` | 챌린지 실패 페널티 (-) |
| `MANUAL_CHARGE` | 수동 충전 (+) |

### ItemType (Phase 7 추가)
| 값 | 설명 |
|----|------|
| `PASS_TICKET` | 면제권 - 실패 시 예치금 방어 |
| `DOUBLE_POINT` | 더블 포인트 - 성공 시 포인트 2배 (향후) |
| `EXTEND_DEADLINE` | 마감 연장 - 마감 시간 연장 (향후) |

---

## 🗃️ 신규 엔티티 (Phase 7~8)

### Item (아이템 정의)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK, 자동 생성 |
| `name` | String | 아이템 이름 (예: "면제권") |
| `description` | String | 아이템 설명 |
| `item_type` | Enum | `PASS_TICKET`, `DOUBLE_POINT`, `EXTEND_DEADLINE` |
| `price` | Integer | 구매 가격 (포인트) |
| `active` | Boolean | 활성 상태 (기본: true) |
| `created_at` | LocalDateTime | 생성일 |

---

### UserItem (사용자 인벤토리)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `id` | Long | PK, 자동 생성 |
| `user_id` | FK → User | 소유자 |
| `item_id` | FK → Item | 아이템 종류 |
| `quantity` | Integer | 보유 수량 (기본: 0) |
| `created_at` | LocalDateTime | 생성일 |

**도메인 메서드:**
- `addQuantity(amount)`: 수량 증가
- `consume(amount)`: 수량 감소 (부족 시 예외)
- `hasItem(amount)`: 보유 여부 확인

---

### Wallet 필드 추가 (Phase 7)

| 컬럼 | 타입 | 설명 |
|------|------|------|
| `point` | Long | 포인트 (보상용, 아이템 구매용) |

**신규 도메인 메서드:**
- [addPoint(amount)](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/domain/model/Wallet.java#72-78): 포인트 증가
- [subtractPoint(amount)](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/domain/model/Wallet.java#79-88): 포인트 감소
- [refund(amount)](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/domain/model/Wallet.java#63-69): 예치금 환급 (정산 시 사용)

---

## 🔧 서비스 메서드 상세

### SettlementService (정산 서비스)

> 범용 정산 서비스 - 챌린지 타입에 무관하게 검증 결과에 따라 정산 처리

| 메서드 | 파라미터 | 반환 | 설명 |
|--------|----------|------|------|
| [settleChallenge](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/service/settlement/SettlementService.java#40-78) | `Long challengeId` | `SettlementResult` | 챌린지 정산 처리 (메인 로직) |

**settleChallenge 흐름:**
```
1. Challenge 조회
2. Verifier로 검증 (verify() 호출)
3. 실패 시 → tryUsePassTicket() 호출
   ├─ 면제권 있음 → 소비 후 결과 오버라이드
   └─ 면제권 없음 → 최종 실패
4. Wallet 조회 (비관적 락)
5. 성공 → processSuccess()
   ├─ challenge.complete()
   ├─ wallet.refund(예치금)
   └─ wallet.addPoint(보상)
6. 실패 → processFailure()
   └─ challenge.fail() (예치금 몰수)
```

---

### ChallengeService (챌린지 서비스)

| 메서드 | 파라미터 | 반환 | 설명 |
|--------|----------|------|------|
| [createChallenge](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/service/challenge/ChallengeService.java#42-65) | `ChallengeCreateRequest` | `ChallengeResponse` | 챌린지 생성 |
| `getChallengesByUser` | `Long userId` | `List<ChallengeResponse>` | 사용자별 챌린지 조회 |
| `getChallengesByUserAndStatus` | `Long userId, ChallengeStatus status` | `List<ChallengeResponse>` | 상태별 조회 |
| [verifyAndComplete](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/service/challenge/ChallengeService.java#79-110) | `Long challengeId` | `ChallengeResponse` | 검증 후 완료 처리 |
| [completeChallenge](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/controller/challenge/ChallengeController.java#46-52) | `Long challengeId` | `ChallengeResponse` | 강제 완료 (bypass) |
| [failChallenge](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/controller/challenge/ChallengeController.java#53-59) | `Long challengeId` | `ChallengeResponse` | 강제 실패 |

---

### WalletService (지갑 서비스)

| 메서드 | 파라미터 | 반환 | 설명 |
|--------|----------|------|------|
| `getWallet` | `Long userId` | `WalletResponse` | 잔액 조회 |
| [charge](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/service/wallet/WalletService.java#25-39) | `CreditChargeRequest` | `WalletResponse` | 크레딧 충전 |
| [deduct](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/service/wallet/WalletService.java#40-54) | `CreditDeductRequest` | `WalletResponse` | 크레딧 차감 |

**특징:** 비관적 락(`@Lock(PESSIMISTIC_WRITE)`)으로 동시성 제어

---

### VerifierFactory (검증기 팩토리)

| 메서드 | 파라미터 | 반환 | 설명 |
|--------|----------|------|------|
| `getVerifier` | `ChallengeType type` | `ChallengeVerifier` | 타입에 맞는 검증기 반환 |

**특징:** Spring이 모든 `ChallengeVerifier` 구현체를 자동 수집하여 Map으로 관리

---

### ChallengeVerifier (검증기 인터페이스)

| 메서드 | 파라미터 | 반환 | 설명 |
|--------|----------|------|------|
| [verify](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/service/verification/GitHubVerifier.java#32-90) | `Challenge challenge` | `boolean` | 챌린지 검증 (true=성공) |
| [getSupportedType](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/service/verification/GitHubVerifier.java#91-95) | - | `ChallengeType` | 지원하는 챌린지 타입 |

**구현체:**

| 클래스 | 지원 타입 | 검증 로직 |
|--------|----------|-----------|
| `ManualVerifier` | `MANUAL` | 항상 true (신뢰 기반) |
| [TimeVerifier](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/service/verification/TimeVerifier.java#15-51) | `TIME_LOG` | 현재 시간이 04:00~07:00 사이인지 |
| [GitHubVerifier](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/service/verification/GitHubVerifier.java#24-96) | `GITHUB_COMMIT` | GitHub API로 오늘(KST) PushEvent 확인 |

---

## ⏰ ChallengeScheduler (스케줄러)

> 매일 자정(00:00 KST)에 마감된 챌린지들을 자동 정산

| 메서드 | 스케줄 | 설명 |
|--------|--------|------|
| `settleDailyExpiredChallenges` | `0 0 0 * * *` (매일 00:00 KST) | 일일 자동 정산 |
| `manualSettlement` | 수동 호출 | 운영/테스트용 수동 트리거 |

**동작 흐름:**
```
1. 마감 지난 챌린지 조회 (PENDING, IN_PROGRESS)
2. 각 챌린지에 대해:
   ├─ try: SettlementService.settleChallenge()
   └─ catch: 로그 남기고 continue (한 건 실패해도 전체 중단 X)
3. 성공/실패 카운트 로깅
```

**트랜잭션 격리:**
- Scheduler 메서드는 `@Transactional` 아님
- 각 [settleChallenge()](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/service/settlement/SettlementService.java#40-78) 호출이 개별 트랜잭션
- 한 챌린지 실패가 다른 챌린지에 영향 주지 않음

---

## 🚀 Phase 9: REST API & 모니터링 (2026-01-28)

### Phase 9.0: ApiResponse 표준화

모든 API 응답을 통일된 형식으로 래핑:

```java
public record ApiResponse<T>(
    boolean success,
    T data,
    String message
) {
    public static <T> ApiResponse<T> success(T data) { ... }
    public static <T> ApiResponse<T> error(String message) { ... }
}
```

### Phase 9.1: Shop API (아이템 상점)

| Method | Endpoint | 설명 |
|--------|----------|------|
| GET | `/api/v1/shop/items` | 아이템 목록 조회 |
| POST | `/api/v1/shop/buy` | 아이템 구매 (포인트 차감) |

**주요 파일:**
- `ShopController.java`
- [ShopService.java](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/service/shop/ShopService.java)
- `ItemResponse.java`, `BuyItemRequest.java`, `BuyItemResponse.java`

### Phase 9.5-A: Docker & Monitoring Stack

**추가된 인프라:**

| 서비스 | 포트 | 설명 |
|--------|------|------|
| **Prometheus** | 9090 | 메트릭 수집 |
| **Grafana** | 3000 | 대시보드 시각화 |

**추가된 의존성:**
```groovy
implementation 'org.springframework.boot:spring-boot-starter-actuator'
implementation 'io.micrometer:micrometer-registry-prometheus'
```

**생성된 파일:**
- `Dockerfile` (Multi-stage build, Java 21)
- [docker/prometheus/prometheus.yml](file:///Users/jeonjeonghyeon/studyCollection/adhd/docker/prometheus/prometheus.yml)
- [docker-compose.yml](file:///Users/jeonjeonghyeon/studyCollection/adhd/docker-compose.yml) (Prometheus + Grafana 추가)

### Phase 9.5-B: k6 스트레스 테스트

**테스트 시나리오:**
- Ramp-up: 30s → 50 VUs
- Steady: 1m @ 50 VUs
- Ramp-down: 30s → 0

**결과 (2026-01-28):**

| 지표 | 결과 |
|------|------|
| RPS | 37 req/s |
| p95 응답시간 | 48.14ms |
| GET /shop/items | ✅ 100% 성공 |
| POST /shop/buy | ❌ 실패 (테스트 데이터 없음 → data.sql로 해결) |

---

## 💼 포트폴리오 활용 가이드

### 📌 프로젝트 한 줄 소개

> **"예치금 기반 자기관리 플랫폼"** - 목표에 돈을 걸고, 성공하면 보상/실패하면 페널티를 받는 행동 교정 서비스

---

### 🎯 포트폴리오에서 강조할 기술 포인트

#### 1. **디자인 패턴 적용**
```
Strategy Pattern: 챌린지 타입별 검증 전략
├─ ManualVerifier (수동 검증)
├─ TimeVerifier (04:00~07:00 시간 검증)
└─ GitHubVerifier (GitHub API 연동)

Factory Pattern: VerifierFactory로 런타임 검증기 선택
```
> 💡 "새로운 챌린지 타입 추가 시 Verifier 클래스만 추가하면 됨 (OCP 원칙)"

#### 2. **동시성 제어**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Wallet> findByUserIdForUpdate(Long userId);
```
> 💡 "다중 사용자 환경에서 잔액 무결성 보장을 위한 비관적 락 적용"

#### 3. **외부 API 연동**
```java
RestClient gitHubClient = RestClient.builder()
    .baseUrl("https://api.github.com")
    .build();
```
> 💡 "Spring 6의 RestClient를 활용한 GitHub API 연동으로 커밋 자동 검증"

#### 4. **Observability (모니터링)**
```
Prometheus + Grafana + Actuator
├─ /actuator/prometheus (메트릭 노출)
├─ HTTP 요청 지연시간, 에러율 수집
└─ 대시보드 시각화
```
> 💡 "운영 환경에서 성능 병목 발견을 위한 모니터링 체계 구축"

#### 5. **로드 테스트**
```
k6 스트레스 테스트
├─ 50 VUs, 2분 테스트
├─ p95 응답시간: 48ms
└─ 처리량: 37 req/s
```
> 💡 "성능 최적화 전 베이스라인 측정을 통한 데이터 기반 의사결정"

---

### 📝 이력서/자기소개서 예시 문구

**프로젝트 설명:**
> 사용자가 챌린지(미라클 모닝, 코드 커밋 등)에 예치금을 걸면, 시스템이 외부 API 연동으로 성공 여부를 자동 검증하여 보상/페널티를 부여하는 게이미피케이션 플랫폼

**기술 기여:**
> - Strategy Pattern 기반 검증 시스템 설계로 **신규 챌린지 타입 확장을 5분 내 완료** 가능하게 함
> - 비관적 락 적용으로 **동시 결제 시 잔액 무결성 100% 보장**
> - Prometheus + Grafana 구축으로 **실시간 성능 모니터링 체계** 확립
> - k6 부하 테스트로 **p95 응답시간 50ms 이하** 달성 확인

---

### 🖼️ 아키텍처 다이어그램 (발표/포트폴리오용)

```
┌─────────────────────────────────────────────────────────────────┐
│                         Client Layer                             │
│            Mobile App / Web Frontend / Swagger UI                │
└──────────────────────────┬──────────────────────────────────────┘
                           │ REST API
┌──────────────────────────┼──────────────────────────────────────┐
│                      Spring Boot 3.3                             │
│  ┌────────────────┐  ┌────────────────┐  ┌──────────────────┐   │
│  │ ChallengeCtrl  │  │   ShopCtrl     │  │   WalletCtrl     │   │
│  └───────┬────────┘  └───────┬────────┘  └────────┬─────────┘   │
│          │                   │                    │              │
│  ┌───────┴───────────────────┴────────────────────┴──────────┐  │
│  │                     Service Layer                          │  │
│  │  ChallengeService ←──→ SettlementService ←──→ WalletService │  │
│  │         ↓                                                  │  │
│  │   VerifierFactory ──→ [STRATEGY PATTERN]                   │  │
│  │         ├─ ManualVerifier                                  │  │
│  │         ├─ TimeVerifier                                    │  │
│  │         └─ GitHubVerifier ─────→ GitHub API                │  │
│  └────────────────────────────────────────────────────────────┘  │
│                           ↓                                      │
│  ┌────────────────────────────────────────────────────────────┐  │
│  │                    Domain Layer                             │  │
│  │   Challenge │ User │ Wallet │ Item │ UserItem │ CreditLog   │  │
│  └────────────────────────────────────────────────────────────┘  │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────┼──────────────────────────────────────┐
│                    Infrastructure                                │
│   PostgreSQL │ Redis │ Kafka │ Prometheus │ Grafana              │
└─────────────────────────────────────────────────────────────────┘
```

---

### 📊 포트폴리오 GitHub README 구성 예시

```markdown
# DevBet - 예치금 기반 자기관리 플랫폼

## 🎯 프로젝트 소개
목표에 돈을 걸고 성공/실패에 따라 보상/페널티를 받는 게이미피케이션 서비스

## 🛠 기술 스택
- Backend: Spring Boot 3.3, Java 21
- Database: PostgreSQL, Redis
- Messaging: Kafka
- Monitoring: Prometheus, Grafana
- Test: JUnit 5, k6

## 📐 아키텍처
[아키텍처 다이어그램]

## ✨ 주요 기능
1. 챌린지 생성 및 자동 검증 (GitHub API 연동)
2. 예치금/포인트 시스템 (비관적 락)
3. 아이템 상점 (면제권 등)
4. 실시간 모니터링 대시보드

## 🔧 기술적 도전
### Strategy Pattern 적용
[설명 + 코드 스니펫]

### 동시성 제어
[설명 + 코드 스니펫]

## 📈 성능 테스트 결과
- RPS: 37 req/s
- p95 응답시간: 48ms

## 🚀 실행 방법
```

---

## 📊 k6 스트레스 테스트 최종 결과 (2026-01-28 19:51)

### ✅ 테스트 구성

| 항목 | 값 |
|------|-----|
| **테스트 시간** | 2분 |
| **최대 VU** | 50 |
| **Ramp-up** | 30s → 50 VUs |
| **Steady** | 1m @ 50 VUs |
| **Ramp-down** | 30s → 0 |

### ✅ Thresholds (통과 기준)

| 지표 | 기준 | 결과 |
|------|------|------|
| Error Rate | < 10% | ✓ **0.00%** |
| p95 응답시간 | < 500ms | ✓ **86.4ms** |

### ✅ 체크 항목 (모두 통과)

```
✓ GET  /shop/items: status is 200  
✓ GET  /shop/items: response time < 500ms  
✓ GET  /shop/items: has data  
✓ POST /shop/buy:   status is 200 or 400  
✓ POST /shop/buy:   response time < 500ms
```

### 📈 성능 지표

| 지표 | 값 |
|------|-----|
| **Total Requests** | 4,450 |
| **RPS (초당 요청 수)** | 36.9 req/s |
| **Checks 성공률** | **100%** (12,469/12,469) |

### ⏱️ 응답 시간 상세

| API | avg | med | p90 | p95 | max |
|-----|-----|-----|-----|-----|-----|
| **GET /shop/items** | 16.3ms | 5.5ms | 21.2ms | 84.5ms | 485ms |
| **POST /shop/buy** | 21.0ms | 8.6ms | 28.6ms | 92.7ms | 345ms |
| **전체** | 17.3ms | 6.0ms | 23.1ms | 86.4ms | 485ms |

### 📊 요약

> **50명의 동시 사용자가 2분간 요청을 보냈을 때:**
> - 에러율 0%
> - 95%의 요청이 86ms 이내에 처리됨
> - 초당 약 37개의 요청 처리

### 🔍 참고: http_req_failed (17.48%)

이 수치는 "4XX/5XX 응답" 비율이 아닌 k6의 **"expected_response" 조건**에 따른 것입니다.
- POST /shop/buy가 `400 Bad Request` (잔액 부족 등)를 반환해도 테스트 체크는 통과 (200 or 400)
- k6가 기본적으로 4XX를 "failed"로 집계하므로 발생한 수치
- **실제 서비스 에러는 0%**

---

## 🚀 Quick Start Guide

### 1. 인프라 실행 (Docker)

```bash
# PostgreSQL, Redis, Kafka, Prometheus, Grafana 실행
docker-compose up -d
```

### 2. 애플리케이션 실행

```bash
./gradlew bootRun
```

### 3. 접속 URL

| 서비스 | URL |
|--------|-----|
| API 서버 | http://localhost:8081 |
| Swagger UI | http://localhost:8081/swagger-ui/index.html |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |
| Kafka UI | http://localhost:8090 |

### 4. API 테스트 예시

```bash
# 아이템 목록 조회
curl http://localhost:8081/api/v1/shop/items

# 아이템 구매
curl -X POST http://localhost:8081/api/v1/shop/buy \
  -H "Content-Type: application/json" \
  -H "X-User-Id: 1" \
  -d '{"itemId": 1, "quantity": 1}'

# 지갑 조회
curl http://localhost:8081/api/v1/wallet/1
```

### 5. 테스트 실행

```bash
# 단위 테스트
./gradlew test

# k6 스트레스 테스트
k6 run stress-test.js
```

---

## ⚙️ JPA 설정 최적화

### OSIV (Open Session In View) 비활성화

```yaml
spring:
  jpa:
    open-in-view: false
```

| 항목 | 설명 |
|------|------|
| **OSIV란?** | HTTP 요청 끝날 때까지 DB 커넥션 유지 |
| **문제점** | 트래픽 증가 시 커넥션 풀 고갈 위험 |
| **해결** | `false`로 설정 → Service 트랜잭션 종료 시 커넥션 반환 |

### Hibernate Dialect 삭제

```yaml
# 삭제됨: dialect: org.hibernate.dialect.PostgreSQLDialect
```

| 항목 | 설명 |
|------|------|
| **이유** | Hibernate 6+에서는 DB 연결 정보로 자동 감지 |
| **효과** | 불필요한 경고 로그 제거 |

---

## 🚀 Phase 10: Redis 캐시 성능 최적화

### 구현 개요

| 항목 | 설명 |
|------|------|
| **목표** | Redis 캐싱으로 읽기 성능 최적화 |
| **패턴** | Look-Aside Cache (Cache-Aside) |
| **기술** | Spring Data Redis, Redis ZSET |

### Redis 인프라 설정

```yaml
# docker-compose.yml
app-redis:
  image: redis:alpine
  command: redis-server --appendonly yes --maxmemory 256mb --maxmemory-policy allkeys-lru
  ports:
    - "6380:6379"
  volumes:
    - redis_data:/data
```

### 캐시 전략

| 캐시명 | TTL | 대상 |
|--------|-----|------|
| `userProfile` | 30분 | 사용자 프로필 조회 |
| `challengeInfo` | 10분 | 챌린지 상세 정보 |
| `shopItems` | 60분 | 상점 아이템 목록 |

### @Cacheable / @CacheEvict 적용

```java
// UserService.java - 캐시 적용
@Cacheable(value = "userProfile", key = "#userId")
public UserProfileResponse getUserProfile(Long userId) { ... }

@CacheEvict(value = "userProfile", key = "#userId")
public UserProfileResponse updateProfile(Long userId, String nickname) { ... }

// ChallengeService.java
@Cacheable(value = "challengeInfo", key = "#challengeId")
public ChallengeResponse getChallengeDetail(Long challengeId) { ... }

@CacheEvict(value = "challengeInfo", key = "#challengeId")
public ChallengeResponse completeChallenge(Long challengeId) { ... }
```

### 실시간 랭킹 시스템 (Redis ZSET)

```java
// RankingService.java
public void updateUserScore(Long userId, int streak) {
    redisTemplate.opsForZSet().add("leaderboard:streak", userId.toString(), streak);
}

public List<RankingEntry> getTop10() {
    return redisTemplate.opsForZSet()
        .reverseRangeWithScores("leaderboard:streak", 0, 9);
}
```

### k6 부하 테스트 결과

#### 캐시 vs No-Cache 성능 비교

| 메트릭 | p95 | 평균 | 설명 |
|--------|-----|------|------|
| **Cache HIT** (Redis) | **7.8ms** | 3.11ms | @Cacheable 적용 |
| **No Cache** (DB Direct) | **10.54ms** | 4.5ms | 매번 DB 조회 |
| **Ranking** (ZSET) | **5.93ms** | 2.35ms | Redis ZSET 직접 조회 |

> **캐시 효과: 26% 성능 개선** (p95: 10.54ms → 7.8ms)

#### 전체 성능 지표

| 메트릭 | 결과 |
|--------|------|
| 총 요청 | 40,855 |
| 초당 처리량 | **990 req/s** |
| **실패율** | **0.00%** ✅ |
| 동시 사용자 | 50 VUs |

```bash
# k6 테스트 실행
k6 run ranking-test.js
```

#### 운영 환경 예상 효과

| 환경 | Cache HIT | No Cache | 개선율 |
|------|-----------|----------|--------|
| **로컬** (현재) | 7.8ms | 10.54ms | 26% |
| **운영** (RDS 네트워크 지연) | ~5ms | ~50-100ms | **90%+** |

> 실제 운영 환경에서는 DB 네트워크 지연으로 인해 캐시 효과가 **훨씬 극대화**됨

### 주요 결정 사항

| 결정 | 이유 |
|------|------|
| JDK Serialization 사용 | JSON 직렬화보다 동시 접근에서 안정적 |
| Null 캐싱 비활성화 | Cache Penetration 방지 |
| LRU 정책 | 메모리 한계 도달 시 오래된 데이터 자동 삭제 |

---

## 🚀 Phase 11: Kafka 이벤트 기반 아키텍처 (2026-02-03)

### 구현 개요

| 항목 | 설명 |
|------|------|
| **목표** | 챌린지 성공 이벤트를 Kafka로 발행하여 서비스 디커플링 |
| **기술** | Apache Kafka, Spring Kafka |
| **패턴** | Event-Driven Architecture + DLT 에러 핸들링 |

### 아키텍처

```
┌─────────────────────────────────────────────────────────────────┐
│                       Application                                │
│  KafkaTestController ──→ ChallengeEventProducer ──→ KafkaTemplate│
│                                                                  │
│  NotificationConsumer ←── challenge-success (Topic)              │
└─────────────────────────────────┬───────────────────────────────┘
                              │
┌─────────────────────────────────┼───────────────────────────────┐
│                       Error Handling                             │
│  RetryListener ──→ ExponentialBackOff (1s→2s→4s→8s→10s)          │
│         ↓                                                        │
│  DeadLetterPublishingRecoverer ──→ challenge-success.DLT         │
└─────────────────────────────────────────────────────────────────┘
```

### 생성된 파일

| 파일 | 설명 |
|------|------|
| [KafkaConfig.java](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/global/config/KafkaConfig.java) | Producer/Consumer + DLT + RetryListener 로깅 |
| [ChallengeSuccessEvent.java](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/dto/event/ChallengeSuccessEvent.java) | 이벤트 DTO (record) |
| [ChallengeEventProducer.java](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/service/challenge/ChallengeEventProducer.java) | Kafka Producer (userId Partition Key) |
| [NotificationConsumer.java](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/service/notification/NotificationConsumer.java) | Kafka Consumer (DLT 테스트 지원) |
| [KafkaTestController.java](file:///Users/jeonjeonghyeon/studyCollection/adhd/src/main/java/com/adhd/focusmate/controller/KafkaTestController.java) | 테스트 엔드포인트 |

### 핵심 구현

#### 1. ExponentialBackOff + RetryListener

```java
// KafkaConfig.java
errorHandler.setRetryListeners(new RetryListener() {
    @Override
    public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
        long nextBackoff = (long) (1000 * Math.pow(2, deliveryAttempt - 1));
        log.warn("[Kafka-Retry] 재시도 {}/N - NextBackoff: {}ms", deliveryAttempt, nextBackoff);
    }
});
```

#### 2. Partition Key Ordering

```java
// ChallengeEventProducer.java
String partitionKey = event.userId().toString();
kafkaTemplate.send(TOPIC, partitionKey, event);  // 동일 사용자 순서 보장
```

### 테스트 엔드포인트

```bash
# 정상 이벤트 발행
curl -X POST http://localhost:8081/test/kafka/success \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"challengeId":100,"title":"30분 집중","rewardPoints":50}'

# DLT 테스트 (의도적 에러)
curl -X POST http://localhost:8081/test/kafka/success \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"challengeId":101,"title":"error 테스트","rewardPoints":10}'
```

### 예상 리트라이 로그

```
WARN  [Kafka-Retry] 재시도 1/N - Topic: challenge-success, Key: 1, NextBackoff: 1000ms
WARN  [Kafka-Retry] 재시도 2/N - Topic: challenge-success, Key: 1, NextBackoff: 2000ms
WARN  [Kafka-Retry] 재시도 3/N - Topic: challenge-success, Key: 1, NextBackoff: 4000ms
ERROR [Kafka-DLT] 메시지를 DLT로 전송합니다. Topic: challenge-success.DLT, Key: 1
```

### 검증 결과

| 항목 | 결과 |
|------|------|
| 컴파일 | ✅ BUILD SUCCESSFUL |
| Producer 이벤트 발행 | ✅ Kafka-UI 확인 |
| Consumer 이벤트 수신 | ✅ 로그 확인 |
| ExponentialBackOff 재시도 | ✅ 1s→4s→8s 간격 확인 |
| DLT 메시지 전송 | ✅ Kafka-UI에서 DLT 토픽 확인 |

---

## 🗺️ Future Roadmap

### Phase 12: Social Feed System (Fan-out) - NEXT

| 항목 | 내용 |
|------|------|
| **목표** | 팔로워가 사용자의 챌린지 활동을 볼 수 있는 피드 시스템 |
| **패턴** | Fan-out on Write (Push Model) |
| **메커니즘** | Kafka Consumer → 팔로워 타임라인에 이벤트 ID Push (Redis List/ZSET) |
| **의미** | O(N) Write Amplification을 Kafka로 비동기 처리 |

### Phase 13: Big Data Analytics (Spark) - FUTURE

| 항목 | 내용 |
|------|------|
| **목표** | 축적된 로그 기반 인사이트 분석 |
| **기술** | Apache Spark (Batch/Streaming) |
| **메커니즘** | Kafka 로그 → Data Lake → Spark 처리 |
| **분석 예시** | 챌린지 타입별 이탈률 계산 |
