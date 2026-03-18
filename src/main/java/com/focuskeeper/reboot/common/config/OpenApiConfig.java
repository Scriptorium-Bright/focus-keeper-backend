package com.focuskeeper.reboot.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rebootFocusOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("RebootFocus API")
                        .version("0.1.0")
                        .description("Recovery coach API baseline for office workers")
                        .contact(new Contact().name("RebootFocus Team")));
    }
}
