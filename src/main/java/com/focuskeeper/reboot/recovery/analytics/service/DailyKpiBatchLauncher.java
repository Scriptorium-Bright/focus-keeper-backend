package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.common.observability.OperationsAlertService;
import com.focuskeeper.reboot.common.observability.OperationsMetricRecorder;
import com.focuskeeper.reboot.common.observability.OperationsPipelineKeys;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.stereotype.Service;

@Service
/**
 * Spring Batch JobLauncher를 감싼 얇은 실행 서비스다.
 *
 * 컨트롤러가 배치 인프라 세부 구현을 몰라도 되게 숨기고,
 * 실행 성공/실패를 observability 계층의 메트릭과 alert로 연결하는 책임을 가진다.
 */
public class DailyKpiBatchLauncher {

    private final JobLauncher jobLauncher;
    private final Job dailyKpiPipelineJob;
    private final OperationsMetricRecorder operationsMetricRecorder;
    private final OperationsAlertService operationsAlertService;

    public DailyKpiBatchLauncher(
            JobLauncher jobLauncher,
            Job dailyKpiPipelineJob,
            OperationsMetricRecorder operationsMetricRecorder,
            OperationsAlertService operationsAlertService
    ) {
        this.jobLauncher = jobLauncher;
        this.dailyKpiPipelineJob = dailyKpiPipelineJob;
        this.operationsMetricRecorder = operationsMetricRecorder;
        this.operationsAlertService = operationsAlertService;
    }

    /**
     * 일간 KPI Job을 실행하고, 배치가 정상 종료되지 않으면 서비스 레벨 예외로 감싼다.
     * Q. Batch의 자세한 동작과 필요한 이유, 등등에 대해 좀 설명이 필요해 갑자기 헷갈려, 이게 observability를 위한건지 .. 뭔지
     * A. Batch 자체의 주목적은 observability가 아니라 "사용자+날짜 기준 KPI 생성 작업"을 독립 실행 단위로 관리하는 것이다.
     *    현재는 단일 Tasklet Job이라 구조가 가볍지만, JobParameter/JobExecution/Step 트랜잭션 경계를 갖기 때문에
     *    나중에 스케줄 실행, 재시도, 백필, 실행 이력 추적을 같은 경로로 확장하기 좋다.
     *    observability는 그 실행 단위의 성공/실패/소요 시간을 밖에서 볼 수 있게 붙인 운영 보강 책임이다.
     */

    public void launch(String userId, LocalDate metricDate) {
        Timer.Sample sample = operationsMetricRecorder.startSample();
        try {
            JobParameters parameters = new JobParametersBuilder()
                    .addString("userId", userId)
                    .addString("metricDate", metricDate.toString())
                    .addLong("requestedAt", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution jobExecution = jobLauncher.run(dailyKpiPipelineJob, parameters);
            if (jobExecution.getStatus() != BatchStatus.COMPLETED) {
                throw new IllegalStateException("Daily KPI pipeline did not complete successfully.");
            }
            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "launch",
                    "success"
            );
            operationsAlertService.resolveBatchFailure(
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "launch",
                    userId,
                    "Daily KPI batch launcher completed successfully.",
                    Map.of("metricDate", metricDate.toString())
            );
        } catch (Exception exception) {
            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "launch",
                    "failure"
            );
            operationsAlertService.reportBatchFailure(
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "launch",
                    userId,
                    "Failed to launch daily KPI pipeline.",
                    Map.of(
                            "metricDate", metricDate.toString(),
                            "error", exception.getClass().getSimpleName()
                    )
            );
            throw new IllegalStateException("Failed to launch daily KPI pipeline.", exception);
        }
    }
}
