package com.adhd.focusmate.global.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "DevBet API", version = "v1.0", description = "범용 습관 챌린지 플랫폼 백엔드 API\n\n" +
        "- **Challenge**: 챌린지 생성/검증/완료\n" +
        "- **Shop**: 아이템 상점\n" +
        "- **Wallet**: 잔액/포인트 관리", contact = @Contact(name = "DevBet Team")), servers = {
                @Server(url = "http://localhost:8080", description = "Local Development")
        })
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("X-User-Id",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.HEADER)
                                        .name("X-User-Id")
                                        .description("사용자 ID (임시 - OAuth 구현 전까지 사용)")));
    }
}
