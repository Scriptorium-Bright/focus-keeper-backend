# RebootFocus Observability

Spring Boot 애플리케이션은 호스트의 `8080` 포트에서 실행하고,
Prometheus와 Grafana는 Docker Compose로 실행한다.

```bash
./gradlew bootRun
docker compose --profile observability up -d
scripts/observability/verify_metrics.sh
```

접속 주소:

- Prometheus targets: `http://localhost:9090/targets`
- Prometheus alerts: `http://localhost:9090/alerts`
- Grafana: `http://localhost:3000`
- Dashboard: `RebootFocus / Portfolio Runtime Evidence`

기본 Grafana 계정은 `admin/admin`이다. 공유 환경에서는 `.env`의
`GRAFANA_ADMIN_PASSWORD`를 반드시 변경한다.

대시보드 범위:

- Big3 만료 작업 성공/실패, 실행 시간, 처리 건수, 중복 실행 skip
- JVM heap, GC pause, CPU
- HikariCP active/max, idle, pending
- HTTP 요청량, p95, 5xx 비율

부분 인덱스와 HOT update는 순간적인 실험 결과이므로 상시 dashboard metric으로
왜곡하지 않는다. HOT은 실제 status 인덱스에서 성립하지 않아 미채택한 실험이다.
각각 다음 재현 테스트와 SQL 결과를 포트폴리오 근거로 사용한다.

```bash
BTREE_BUFFER_TEST_ENABLED=true ./gradlew test --no-daemon --rerun-tasks --info \
  --tests com.focuskeeper.reboot.common.persistence.BTreePartialIndexBufferEfficiencyTest

BIG3_HOT_UPDATE_TEST_ENABLED=true ./gradlew test --no-daemon --rerun-tasks --info \
  --tests com.focuskeeper.reboot.common.persistence.Big3HotUpdateEfficiencyTest

psql -f perf/sql/big3_hot_update_observation.sql
```
