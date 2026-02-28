package com.focuskeeper.reboot.health;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class HealthCheckController {

    private final Environment environment;

    public HealthCheckController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        List<String> activeProfiles = List.of(environment.getActiveProfiles());

        return Map.of(
                "status", "UP",
                "service", "focuskeeper-reboot",
                "activeProfiles", activeProfiles,
                "timestamp", OffsetDateTime.now().toString()
        );
    }
}
