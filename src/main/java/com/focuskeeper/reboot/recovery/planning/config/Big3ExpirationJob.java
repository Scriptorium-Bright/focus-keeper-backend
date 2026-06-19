package com.focuskeeper.reboot.recovery.planning.config;

import com.focuskeeper.reboot.recovery.planning.service.Big3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class Big3ExpirationJob {

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Big3Service big3Service;


    public Big3ExpirationJob(Big3Service big3Service) {
        this.big3Service = big3Service;
    }

    public ExpirationJobResult run() {
        if(running.compareAndSet(false, true)) {
            return ExpirationJobResult.skipped("already_running");
        }


        try {
            int expireLastWeekTasks = big3Service.expireLastWeekTasks();
            return ExpirationJobResult.successed(expireLastWeekTasks);
        } catch (Exception e) {
            running.set(false);
        }

        return null;
    }

}
