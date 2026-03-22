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
