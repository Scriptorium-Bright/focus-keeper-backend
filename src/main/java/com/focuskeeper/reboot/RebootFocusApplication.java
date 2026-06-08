package com.focuskeeper.reboot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class RebootFocusApplication {

    public static void main(String[] args) {
        SpringApplication.run(RebootFocusApplication.class, args);
    }
}
