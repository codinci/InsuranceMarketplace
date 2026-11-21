package com.example.insurance.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(
    description = "A calling service's name and its secret, from the environment of both services.")
public record ServiceTokenRequest(
    @NotBlank @Schema(example = "policy-service") String service,
    @NotBlank @Schema(description = "The shared secret. Never logged.") String secret) {}
