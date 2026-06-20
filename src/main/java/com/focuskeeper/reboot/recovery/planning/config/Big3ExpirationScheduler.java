package com.focuskeeper.reboot.recovery.planning.config;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Big3ExpirationScheduler {

    private final Big3ExpirationJob expirationJob;

    public Big3ExpirationScheduler(Big3ExpirationJob expirationJob) {
        this.expirationJob = expirationJob;
    }

    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Seoul")
    public void expirePastOpenItems() {
        expirationJob.run("scheduled");
    }
}
