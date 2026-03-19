package com.focuskeeper.reboot.recovery.analytics.service;

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

    public DailyKpiBatchLauncher(JobLauncher jobLauncher, Job dailyKpiPipelineJob) {
        this.jobLauncher = jobLauncher;
        this.dailyKpiPipelineJob = dailyKpiPipelineJob;
    }

    public void launch(String userId, LocalDate metricDate) {
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
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to launch daily KPI pipeline.", exception);
        }
    }
}
