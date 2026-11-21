package com.example.insurance.claimservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The bearer scheme that secured operations reference by name (CLAUDE.md). */
@Configuration
public class OpenApiConfig {

  @Bean
  OpenAPI claimOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("claim-service")
                .version("v1")
                .description(
                    "The policy read model used for claims, and from M5b claim intake (M5a)."))
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
