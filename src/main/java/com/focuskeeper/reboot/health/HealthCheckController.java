package com.focuskeeper.reboot.health;

import com.focuskeeper.reboot.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Health", description = "Application health endpoints")
public class HealthCheckController {

    private final Environment environment;

    public HealthCheckController(Environment environment) {
        this.environment = environment;
    }

    @GetMapping("/health")
    @Operation(summary = "Application health check", description = "Returns service status, active profiles, and timestamp.")
    public ApiResponse<Map<String, Object>> health() {
        List<String> activeProfiles = List.of(environment.getActiveProfiles());
        String serviceName = environment.getProperty("spring.application.name", "rebootfocus-api");

        Map<String, Object> payload = Map.of(
                "status", "UP",
                "service", serviceName,
                "activeProfiles", activeProfiles,
                "timestamp", OffsetDateTime.now().toString()
        );
        return ApiResponse.success(payload);
    }
}
