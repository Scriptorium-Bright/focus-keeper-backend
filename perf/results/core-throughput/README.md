# Core Throughput Evidence

> Measured: 2026-07-12  
> Environment: local macOS, Java 21.0.6, Spring Boot 3.3.8, PostgreSQL 14.21, Hikari maximum-pool-size 17

## Scenario

한 iteration은 다음 5개 HTTP write request와 13개 도메인 row 생성을 포함한다.

```text
POST inbox-items             -> InboxItem 3
POST big3                    -> DailyBig3Board 1 + Big3Item 3 + DailyBig3Entry 3
POST execution-units/multiple x 3 -> ExecutionUnit 6
```

## Results

| offered rate | completed | completed rate | success | dropped | flow p95 | flow p99 | HTTP req/s |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 40 flow/s | 1,201 | 40.01/s | 100% | 0 | 68 ms | 375 ms | 200.07 |
| 100 flow/s | 3,001 | 99.89/s | 100% | 0 | 535 ms | 765 ms | 499.46 |
| 150 flow/s | 4,118 | 130.37/s | 100% | 384 | 2.73 s | 3.23 s | 651.86 |

해석:

- 100 flow/s까지 요청 유실 없이 p95 1초 기준을 만족했다.
- 150 flow/s에서는 max 300 VU에 도달하고 384 iteration이 drop됐다.
- 성공한 flow의 데이터 오류는 없었지만 queueing으로 p95가 2.73초까지 증가했다.
- 따라서 현재 환경의 검증된 안정 처리량은 100 flow/s이며 최대 관측 완료 처리량은 130.37 flow/s다.

## Final-state verification

| run | boards | inbox rows | Big3 items | ExecutionUnits |
|---|---:|---:|---:|---:|
| rate40 | 1,201 | 3,603 | 3,603 | 7,206 |
| rate100 | 3,001 | 9,003 | 9,003 | 18,006 |
| rate150 | 4,118 | 12,354 | 12,354 | 24,708 |

모든 완료 flow는 `1 board : 3 inbox : 3 Big3Item : 6 ExecutionUnit` 비율과 일치했다.

## Expiration benchmark

| rows | elapsed | throughput | peak heap increase | GC | final state |
|---:|---:|---:|---:|---:|---|
| 300,000 | 4,417 ms | 67,919 rows/s | 3.00 MiB | 0회 / 0 ms | EXPIRED 300,000, past OPEN 0 |

이 측정은 CTE target selection, `FOR UPDATE SKIP LOCKED`, set-based update, 최대 100,000 row chunk를 사용했다.

## Reproduction

```bash
FLOW_RATE=40 DURATION=30s RUN_ID=rate40 k6 run perf/k6/load-test.js
FLOW_RATE=100 DURATION=30s PRE_ALLOCATED_VUS=80 MAX_VUS=200 RUN_ID=rate100 \
  k6 run perf/k6/load-test.js
FLOW_RATE=150 DURATION=30s PRE_ALLOCATED_VUS=120 MAX_VUS=300 RUN_ID=rate150 \
  k6 run perf/k6/load-test.js

PERF_EXPIRATION_ROWS=300000 \
PERF_EXPIRATION_MAX_HEAP=512m \
PERF_EXPIRATION_CONFIRM_DEDICATED_DB=true \
  ./gradlew expirationMemoryHarness --no-daemon --rerun-tasks
```

## Limitations

- 부하 발생기와 API/DB가 같은 로컬 머신을 사용했다.
- 30초 steady window이므로 장기 GC, vacuum, connection churn은 포함하지 않는다.
- 실제 네트워크 지연, multi-instance, replica, container resource limit은 포함하지 않는다.
- 100 flow/s는 SLA가 아니라 이 환경에서 재현된 evidence다.

