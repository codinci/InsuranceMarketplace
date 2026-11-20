package com.example.insurance.auth.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines the bearer scheme that secured operations reference by name (CLAUDE.md: every endpoint
 * has one).
 */
@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI authOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("auth-service")
                .version("v1")
                .description("Identity: users, roles, tokens and JWKS."))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
