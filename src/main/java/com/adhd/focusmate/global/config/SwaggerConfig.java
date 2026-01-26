package com.adhd.focusmate.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "NeuroLink API", version = "v1", description = "ADHD Focus Mate Backend API Documentation"))
public class SwaggerConfig {
}
