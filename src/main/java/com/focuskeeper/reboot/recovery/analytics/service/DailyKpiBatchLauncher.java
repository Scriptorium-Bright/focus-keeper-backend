package com.focuskeeper.reboot.recovery.analytics.service;

import com.focuskeeper.reboot.common.observability.OperationsMetricRecorder;
import com.focuskeeper.reboot.common.observability.OperationsPipelineKeys;
import io.micrometer.core.instrument.Timer;
import java.time.LocalDate;
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

    public DailyKpiBatchLauncher(
            JobLauncher jobLauncher,
            Job dailyKpiPipelineJob,
            OperationsMetricRecorder operationsMetricRecorder
    ) {
        this.jobLauncher = jobLauncher;
        this.dailyKpiPipelineJob = dailyKpiPipelineJob;
        this.operationsMetricRecorder = operationsMetricRecorder;
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
        } catch (Exception exception) {
            operationsMetricRecorder.recordBatchStage(
                    sample,
                    OperationsPipelineKeys.DAILY_KPI_PIPELINE,
                    "launch",
                    "failure"
            );
            throw new IllegalStateException("Failed to launch daily KPI pipeline.", exception);
        }
    }
}
