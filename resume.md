# Backend Engineering Portfolio Cases

실측 수치가 없는 항목은 임의로 만들지 않고 `측정 필요`로 표시한다.

## 1. 활성 상태 유일성을 partial unique index로 보장

### 문제 정의

다중 트랜잭션에서 활성 세션 선행 조회가 동시에 통과하면 한 사용자에게 여러 실행 세션이 생겨 실행 시간과 실패 지표가 중복될 수 있었다.

### 기술적 원인

`exists -> insert` check-then-act와 미커밋 INSERT 가시성 한계 때문에 서비스 검증만으로 현재 상태 유일성을 보장할 수 없었다.

### 해결 전략

`status='STARTED'` row만 대상으로 사용자 partial unique index를 적용하고, constraint 충돌을 HTTP 409로 변환했다.

### 의사결정

전체 이력을 보존하면서 활성 row만 하나여야 하므로 `(user_id, status)` 일반 unique 대신 partial unique를 선택했다.

### 결과

동시 시작 2건에서 성공 1건, 충돌 1건, 최종 활성 세션 1건을 테스트로 확인했다. 처리량 수치: 측정 필요.

### 남은 한계

명령 수준 idempotency key는 없어 timeout 이후 재시도와 별도 충돌을 구분하지 못한다.

### 면접 방어 질문

- unique conflict를 재시도할지 즉시 409로 반환할지?
- 기존 중복 데이터가 있는 운영 DB에 index를 어떻게 배포할지?

## 2. 삭제 이력과 현재 배치 유일성을 분리

### 문제 정의

보드 entry 교체 이력을 보존하면서 활성 slot과 활성 item 중복은 막아야 했다.

### 기술적 원인

물리 삭제는 이력을 잃고, 전체 unique는 과거 removed row 때문에 정상 재배치를 막는다.

### 해결 전략

`removed_at`으로 이력을 남기고 `removed_at is null` 조건의 slot/item partial unique index 두 개로 현재 상태만 제약했다.

### 의사결정

상태 컬럼 덮어쓰기나 hard delete 대신 temporal history와 current invariant를 schema에서 분리했다.

### 결과

동시 교체에서도 active slot/item 중복이 커밋되지 않음을 PostgreSQL 테스트로 확인했다. 처리량 수치: 측정 필요.

### 남은 한계

같은 사용자·주차·원본 inbox의 Big3Item 자연키 unique는 별도 과제로 남아 있다.

### 면접 방어 질문

- partial index predicate와 실제 조회 predicate가 일치하는가?
- removed history 증가에 따른 index/table 크기는 어떻게 관리할 것인가?

## 3. 시간 범위 불변식을 exclusion constraint로 방어

### 문제 정의

겹치는 기존 row가 없을 때 `SELECT FOR UPDATE`는 잠글 대상이 없어 동시 timebox INSERT를 막지 못한다.

### 기술적 원인

시간 범위 overlap은 단순 equality unique로 표현할 수 없고, 애플리케이션 overlap 검사는 phantom insert 경쟁에 취약하다.

### 해결 전략

유효 구간 check와 PostgreSQL `tstzrange` GiST exclusion constraint를 적용해 PLANNED 시간 구간의 교차를 schema에서 거절했다.

### 의사결정

사용자별 guard row 직렬화보다 DB range invariant가 모든 쓰기 경로를 보호하고 다른 사용자의 병렬성을 유지한다.

### 결과

동시 최초 INSERT 경쟁에서도 최종 겹치는 timebox가 하나만 남도록 검증했다. 성능 수치: 측정 필요.

### 남은 한계

GiST index 크기, write amplification, 충돌률별 지연을 측정하지 않았다.

### 면접 방어 질문

- `[)` 경계로 인접 구간을 허용한 이유는?
- cancellation과 constraint predicate가 같은 상태 정의를 사용하는가?

## 4. Carryover lineage 1:1 불변식

### 문제 정의

동일한 이전 작업에서 여러 후속 작업이 만들어지면 실행 이력과 주간 집계 identity가 분산된다.

### 기술적 원인

두 트랜잭션이 `existsByDerivedFromItem_Id=false`를 함께 읽고 insert하는 write skew가 가능했다.

### 해결 전략

nullable `derived_from_item_id`에 partial unique를 적용하고 알려진 충돌을 HTTP 409로 변환했다.

### 의사결정

source row lock보다 DB unique를 최종 방어선으로 선택해 모든 쓰기 경로에 lineage 1:1을 강제했다.

### 결과

동시 요청에서 성공 1건, 제약 실패 1건, 최종 후속 item 1건을 확인했다. 성능 수치: 측정 필요.

### 남은 한계

versioned migration과 기존 중복 cleanup runbook이 필요하다.

### 면접 방어 질문

- `NULL`을 허용하면서 1:1을 어떻게 표현했는가?
- unique 위반을 멱등 성공으로 반환하지 않고 409로 둔 이유는?

## 5. Aggregate 최대 child 수의 동시성 제어

### 문제 정의

ExecutionUnit 4개 상태에서 동시 생성 두 건이 모두 검증을 통과하면 최대 5개 규칙이 깨진다.

### 기술적 원인

부모별 최대 N개는 단일 child unique로 표현하기 어렵고, count와 insert가 같은 직렬화 경계에 없었다.

### 해결 전략

부모 row를 `PESSIMISTIC_WRITE`로 잠근 후 child count 검증과 insert를 수행했다.

### 의사결정

별도 counter row의 상태 중복과 optimistic retry 복잡도를 피하고, 짧고 낮은 빈도의 임계 구역에 parent lock을 사용했다.

### 결과

동시 생성 결과가 성공 1건, 실패 1건, 최종 child 5개로 수렴했다. lock wait/throughput: 측정 필요.

### 남은 한계

lock timeout 오류 정책과 hot-key 경합 측정이 없다.

### 면접 방어 질문

- 왜 DB trigger나 counter table을 선택하지 않았는가?
- deadlock 방지를 위한 lock order는 무엇인가?

## 6. 대량 상태 전이의 ORM 메모리 병목 제거

### 문제 정의

주간 만료 대상 전체를 entity로 로딩하면 persistence context가 커져 heap과 dirty checking 비용이 증가한다.

### 기술적 원인

row-by-row entity materialization이 대상 수에 비례해 애플리케이션 메모리와 flush 비용을 사용한다.

### 해결 전략

`FOR UPDATE SKIP LOCKED` 대상 CTE와 set-based update를 bounded chunk transaction으로 반복하고 version도 함께 증가시켰다.

### 의사결정

전체 단일 update는 lock/WAL burst가 크고 entity 순회는 heap을 사용하므로, chunk 단위 DB update로 두 위험을 절충했다.

### 결과

로컬 PostgreSQL 14.21, JVM max heap 512 MiB에서 300,000건을 4,417ms(67,919 rows/s)에 전이했다. peak heap 증가는 3.00 MiB였고 GC는 0회였다.

### 남은 한계

현재 chunk size 100,000의 산정 근거와 다중 instance scheduler 정책이 충분하지 않다.

### 면접 방어 질문

- chunk size를 어떤 지표로 조정할 것인가?
- 장애 시 이미 처리한 chunk를 어떻게 재실행 안전하게 판별하는가?

## 7. 실행 lifecycle과 이벤트 이력 분리

### 문제 정의

세션 상태, 실패 원인, 재시작 사실을 한 row에 덮어쓰면 상태 전이 이력과 분석 grain이 섞인다.

### 기술적 원인

현재 상태와 사건 기록은 변경 빈도·유일성·조회 목적이 다르다.

### 해결 전략

`RecoverySession`, `FailureEvent`, `RestartEvent`를 분리하고 session은 versioned 상태 전이, event는 append 중심 기록으로 구성했다.

### 의사결정

도메인 이벤트 인프라를 새로 도입하지 않고 관계형 테이블 grain을 분리해 현재 프로젝트 규모에서 추적 가능성을 확보했다.

### 결과

완료·중단·실패 체크인·재시작의 정상/충돌 흐름을 service/controller 테스트로 고정했다. 운영 복구 시간: 측정 필요.

### 남은 한계

failure check-in idempotency와 session terminal 경쟁의 API taxonomy가 남아 있다.

### 면접 방어 질문

- append-only event와 mutable session의 트랜잭션 경계는 어디인가?
- failure 저장이 실패하면 session interrupt도 롤백되는가?

## 8. Core write flow 처리량과 포화 지점 측정

### 문제 정의

개별 API가 빠른지만으로는 Inbox→Planning→ExecutionUnit 전체 command 흐름의 안정 처리량과 tail latency를 설명할 수 없었다.

### 기술적 원인

하나의 사용자 flow가 5개 HTTP write, 여러 transaction, 13개 row 생성을 포함하므로 connection pool과 DB write 지연이 누적된다.

### 해결 전략

k6 constant-arrival-rate로 40/100/150 flow/s를 각각 30초 측정하고, 성공률뿐 아니라 completed throughput, dropped iteration, flow p95/p99, 최종 DB row 비율을 검증했다.

### 의사결정

단일 endpoint benchmark 대신 실제 도메인 전이 순서와 parent-child 생성을 포함한 flow benchmark를 선택했다. offered load와 completed throughput을 분리해 queueing 포화를 숨기지 않았다.

### 결과

- 100 flow/s: 3,001 flow, 99.89 flow/s, 성공률 100%, p95 535ms, p99 765ms
- 150 flow/s: 130.37 flow/s, p95 2.73초, dropped iteration 384, max 300 VU 도달
- 완료 flow 모두 `board 1 : inbox 3 : Big3Item 3 : ExecutionUnit 6` 최종 상태 유지

### 남은 한계

부하 발생기·API·DB가 같은 로컬 머신이며 30초 window다. 운영 SLA로 일반화하려면 분리된 부하 발생기와 장시간 soak test가 필요하다.

### 면접 방어 질문

- 성공률 100%인데 왜 150 flow/s를 실패 구간으로 보는가?
- arrival rate와 completion rate 차이는 무엇을 의미하는가?
- Hikari pool 17과 tail latency 관계를 어떻게 추가 검증할 것인가?
