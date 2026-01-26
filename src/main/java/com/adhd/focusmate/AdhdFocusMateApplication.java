package com.adhd.focusmate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class AdhdFocusMateApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdhdFocusMateApplication.class, args);
    }

}
