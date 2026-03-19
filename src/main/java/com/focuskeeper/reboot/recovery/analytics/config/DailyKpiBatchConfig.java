package com.focuskeeper.reboot.recovery.analytics.config;

import com.focuskeeper.reboot.recovery.analytics.service.DailyKpiPipelineService;
import java.time.LocalDate;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class DailyKpiBatchConfig {

    @Bean
    public Job dailyKpiPipelineJob(
            JobRepository jobRepository,
            Step dailyKpiPipelineStep
    ) {
        return new JobBuilder("dailyKpiPipelineJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(dailyKpiPipelineStep)
                .build();
    }

    @Bean
    public Step dailyKpiPipelineStep(
            JobRepository jobRepository,
            PlatformTransactionManager transactionManager,
            Tasklet dailyKpiPipelineTasklet
    ) {
        return new StepBuilder("dailyKpiPipelineStep", jobRepository)
                .tasklet(dailyKpiPipelineTasklet, transactionManager)
                .build();
    }

    @Bean
    @org.springframework.batch.core.configuration.annotation.StepScope
    public Tasklet dailyKpiPipelineTasklet(
            DailyKpiPipelineService dailyKpiPipelineService,
            @Value("#{jobParameters['userId']}") String userId,
            @Value("#{jobParameters['metricDate']}") String metricDate
    ) {
        return (contribution, chunkContext) -> {
            dailyKpiPipelineService.generate(userId, LocalDate.parse(metricDate));
            return RepeatStatus.FINISHED;
        };
    }
}
