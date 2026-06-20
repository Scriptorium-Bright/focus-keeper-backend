package com.focuskeeper.reboot.common.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * Observability 계층의 "기록기" 역할을 한다.
 *
 * Micrometer/MeterRegistry에 counter, timer, gauge를 등록하고 값을 갱신한다.
 * 즉 배치 실패, DQ issue 수, processing lag 같은 운영 신호를 숫자 메트릭으로 바꿔
 * Prometheus가 수집할 수 있게 만드는 책임을 가진다.
 */
// medium
@Service
public class OperationsMetricRecorder {

    private final MeterRegistry meterRegistry;
    private final ConcurrentMap<String, AtomicInteger> dqIssueGaugeByUser = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicLong> processingLagGaugeByUser = new ConcurrentHashMap<>();
    private final AtomicInteger expirationRunning = new AtomicInteger();
    private final AtomicLong expirationLastSuccessTimestampSeconds = new AtomicLong();
    private final AtomicLong expirationLastDurationNanos = new AtomicLong();

    public OperationsMetricRecorder(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        Gauge.builder("reboot_expiration_running", expirationRunning, AtomicInteger::get)
                .description("Whether the Big3 expiration job is currently running")
                .register(meterRegistry);
        Gauge.builder(
                        "reboot_expiration_last_success_timestamp_seconds",
                        expirationLastSuccessTimestampSeconds,
                        AtomicLong::get
                )
                .description("Unix timestamp of the last successful Big3 expiration run")
                .baseUnit(TimeUnit.SECONDS.name().toLowerCase())
                .register(meterRegistry);
        Gauge.builder(
                        "reboot_expiration_last_duration_seconds",
                        expirationLastDurationNanos,
                        value -> value.get() / 1_000_000_000.0
                )
                .description("Duration in seconds of the last completed Big3 expiration run")
                .baseUnit(TimeUnit.SECONDS.name().toLowerCase())
                .register(meterRegistry);
    }

    /**
     * 하나의 요청 또는 배치 stage 실행 시간을 재기 위한 Timer.Sample을 시작한다.
     *
     * 호출 시점에는 아직 메트릭 이름을 확정하지 않고, 나중에 성공/실패 여부와 함께
     * 어떤 timer에 기록할지 결정하기 위해 sample만 먼저 만든다.
     */
    public Timer.Sample startSample() {
        return Timer.start(meterRegistry);
    }

    /**
     * recovery loop API 호출의 건수와 소요 시간을 함께 기록한다.
     *
     * action/start-restart-failure_check_in 같은 사용자 행동별로 나누고,
     * status 태그로 success/failure를 구분해 나중에 Prometheus/Grafana에서
     * 어떤 액션이 느리거나 자주 실패하는지 볼 수 있게 한다.
     */
    public void recordRecoveryLoopAction(Timer.Sample sample, String action, String status) {
        Counter.builder("reboot_recovery_loop_actions_total")
                .description("Recovery loop API action count")
                .tag("action", action)
                .tag("status", status)
                .register(meterRegistry)
                .increment();

        Timer.builder("reboot_recovery_loop_action_duration")
                .description("Recovery loop API action duration")
                .tag("action", action)
                .tag("status", status)
                .register(meterRegistry);
        sample.stop(meterRegistry.timer(
                "reboot_recovery_loop_action_duration",
                "action",
                action,
                "status",
                status
        ));
    }

    /**
     * 배치 stage 실행 시간을 기록하고, 실패한 경우 실패 횟수 counter도 증가시킨다.
     *
     * pipeline과 stage 태그를 남겨서 "어느 파이프라인의 어느 단계가 느리거나 실패했는지"
     * 구분할 수 있게 한다. 배치 운영에서는 전체 job 성공/실패보다 stage 단위 병목이 중요해
     * 이 정도 세분화가 필요하다.
     */
    public void recordBatchStage(Timer.Sample sample, String pipelineKey, String stage, String status) {
        sample.stop(meterRegistry.timer(
                "reboot_batch_duration",
                "pipeline",
                pipelineKey,
                "stage",
                stage,
                "status",
                status
        ));

        if ("failure".equals(status)) {
            Counter.builder("reboot_batch_failed_runs_total")
                    .description("Failed batch stage count")
                    .tag("pipeline", pipelineKey)
                    .tag("stage", stage)
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * 특정 사용자/파이프라인 기준 현재 DQ issue 개수를 gauge로 반영한다.
     *
     * 이 값은 누적 counter가 아니라 "지금 남아 있는 문제 개수"를 보여주기 때문에 gauge가 적합하다.
     * 배치 실행 직후 issue가 늘었는지, 재처리 후 다시 0으로 내려왔는지를 운영 화면에서 확인할 수 있다.
     */
    public void recordDqIssueCount(String pipelineKey, String userId, int totalIssueCount) {
        dqGauge(pipelineKey, userId).set(totalIssueCount);
    }

    /**
     * 현재 lastProcessedDate가 최신 시점을 얼마나 따라가지 못하고 있는지 초 단위 lag로 기록한다.
     *
     * 이 값은 파이프라인의 freshness를 보여주는 대표 운영 신호다.
     * 값이 커질수록 지표 생성이 밀리고 있다는 뜻이므로 alert나 backfill 판단 근거로 사용된다.
     */
    public void recordProcessingLagSeconds(String pipelineKey, String userId, long lagSeconds) {
        processingLagGauge(pipelineKey, userId).set(Math.max(lagSeconds, 0));
    }

    /**
     * 한 번의 backfill 실행이 며칠 구간을 처리했는지 summary에 기록한다.
     *
     * backfill 성능 개선 전후를 보거나, 예상보다 적은 구간만 처리된 실행을 감지할 때 쓰인다.
     * duration과 함께 보면 "얼마나 오래 걸렸는지"와 "얼마나 많이 처리했는지"를 함께 해석할 수 있다.
     */
    public void recordBackfillProcessedDays(String pipelineKey, int processedDays) {
        meterRegistry.summary(
                        "reboot_backfill_processed_days",
                        "pipeline",
                        pipelineKey
                )
                .record(processedDays);
    }

    public void recordAlertNotification(String event, String result) {
        Counter.builder("reboot_ops_alert_notifications_total")
                .description("Operations alert webhook notification count")
                .tag("event", event)
                .tag("result", result)
                .register(meterRegistry)
                .increment();
    }

    public void setExpirationRunning(boolean running) {
        expirationRunning.set(running ? 1 : 0);
    }

    public void recordExpirationSuccess(Timer.Sample sample, int processedItems) {
        recordExpirationRun(sample, "success");
        DistributionSummary.builder("reboot_expiration_processed_items")
                .description("Number of Big3 items processed per successful expiration run")
                .register(meterRegistry)
                .record(processedItems);
        expirationLastSuccessTimestampSeconds.set(Instant.now().getEpochSecond());
    }

    public void recordExpirationFailure(Timer.Sample sample) {
        recordExpirationRun(sample, "failure");
    }

    public void recordExpirationSkipped(String reason) {
        Counter.builder("reboot_expiration_skipped_runs_total")
                .description("Skipped Big3 expiration run count")
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    private void recordExpirationRun(Timer.Sample sample, String status) {
        Counter.builder("reboot_expiration_runs_total")
                .description("Big3 expiration run count")
                .tag("status", status)
                .register(meterRegistry)
                .increment();

        Timer timer = Timer.builder("reboot_expiration_duration")
                .description("Big3 expiration job duration")
                .tag("status", status)
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofSeconds(30),
                        Duration.ofMinutes(1),
                        Duration.ofMinutes(3),
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(10)
                )
                .register(meterRegistry);
        expirationLastDurationNanos.set(sample.stop(timer));
    }

    /**
     * user/pipeline 조합별 DQ gauge를 lazy 생성하거나 기존 gauge를 재사용한다.
     *
     * gauge 객체는 한 번 등록한 뒤 값을 계속 갱신해야 하므로, AtomicInteger를 캐시에 보관해
     * 동일 태그 조합에 대해 매번 새 meter를 만들지 않게 한다.
     */
    private AtomicInteger dqGauge(String pipelineKey, String userId) {
        String key = pipelineKey + ":" + userId;
        return dqIssueGaugeByUser.computeIfAbsent(key, unused -> {
            AtomicInteger gauge = new AtomicInteger();
            Gauge.builder("reboot_dq_issue_count", gauge, AtomicInteger::get)
                    .description("Current DQ issue count")
                    .tag("pipeline", pipelineKey)
                    .tag("user_id", userId)
                    .register(meterRegistry);
            return gauge;
        });
    }

    /**
     * user/pipeline 조합별 processing lag gauge를 lazy 생성하거나 기존 gauge를 재사용한다.
     *
     * lag는 누적값이 아니라 현재 상태 값이므로 AtomicLong gauge로 관리한다.
     * 이 값을 통해 파이프라인이 최신 날짜를 얼마나 따라오고 있는지 지속적으로 관찰할 수 있다.
     */
    private AtomicLong processingLagGauge(String pipelineKey, String userId) {
        String key = pipelineKey + ":" + userId;
        return processingLagGaugeByUser.computeIfAbsent(key, unused -> {
            AtomicLong gauge = new AtomicLong();
            Gauge.builder("reboot_batch_processing_lag_seconds", gauge, AtomicLong::get)
                    .description("Current processing lag in seconds")
                    .tag("pipeline", pipelineKey)
                    .tag("user_id", userId)
                    .baseUnit(TimeUnit.SECONDS.name().toLowerCase())
                    .register(meterRegistry);
            return gauge;
        });
    }
}
