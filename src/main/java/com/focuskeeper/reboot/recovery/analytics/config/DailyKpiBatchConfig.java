package com.focuskeeper.reboot.recovery.analytics.config;

import com.focuskeeper.reboot.recovery.analytics.service.DailyKpiPipelineService;
import java.time.LocalDate;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
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
/**
 * Spring Batch에서 일간 KPI 파이프라인을 하나의 Job/Step/Tasklet으로 묶는 배치 설정이다.
 *
 * 이 설정은 복잡한 청크 처리를 구현하기보다,
 * "특정 사용자 + 특정 날짜" 기준 KPI 생성 로직을 배치 인프라에 태워
 * 수동 실행과 스케줄 실행이 같은 경로를 타게 만드는 데 목적이 있다.
 */
public class DailyKpiBatchConfig {

    /**
     * 일간 KPI 파이프라인을 한 번 실행하는 단일 Step Job을 구성한다.
     */
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

    /**
     * KPI 계산 Tasklet을 트랜잭션 경계 안에서 실행하는 Step을 구성한다.
     */
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

    /**
     * JobParameter로 받은 사용자와 날짜를 기준으로 KPI mart 생성 서비스를 호출하는 Tasklet을 만든다.
     */
    @Bean
    @StepScope
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
