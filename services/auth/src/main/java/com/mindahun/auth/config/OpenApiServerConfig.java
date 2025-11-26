package com.mindahun.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.servers.Server;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        servers = {
                @Server(url = "http://localhost:8222")
        }
)
@Configuration
public class OpenApiServerConfig {
        @Bean
        public OpenAPI customOpenAPI() {
                return new OpenAPI()
                        .components(new Components()
                                .addSecuritySchemes("BearerAuthentication",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT"))
                        );

        }
}