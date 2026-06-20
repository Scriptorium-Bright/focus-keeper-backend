package com.focuskeeper.reboot;

import com.focuskeeper.reboot.recovery.planning.config.Big3ExpirationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("local") // 실수로 운영 환경에 배포되어 서버 구동 시마다 1000만 건을 도는 참사를 방지
public class Big3ExpirationStartupRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(Big3ExpirationStartupRunner.class);
    private final Big3ExpirationJob expirationJob;

    public Big3ExpirationStartupRunner(Big3ExpirationJob expirationJob) {
        this.expirationJob = expirationJob;
    }

    @Override
    public void run(String... args) {
        log.info("[Monitor] 애플리케이션 구동 완료. Grafana 모니터링을 위한 대용량 Job을 백그라운드에서 시작합니다.");

        // 핵심: 메인 스레드를 블로킹하지 않기 위해 새로운 스레드에서 Job 실행
        Thread monitorThread = new Thread(() -> {
            try {
                // 약간의 지연을 주어 Prometheus가 첫 스크래핑을 성공적으로 마칠 여유를 제공 (선택 사항)
                Thread.sleep(60000);
                expirationJob.run("startup_monitor");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("[Monitor] 모니터링 Job 실행 중 예외 발생", e);
            }
        }, "monitoring-job-thread");

        monitorThread.setDaemon(true); // 애플리케이션 종료 시 함께 종료되도록 데몬 스레드 설정
        monitorThread.start();
    }
}